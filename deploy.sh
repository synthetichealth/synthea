#!/bin/bash

# --------------------------------------------------------------------------
# This script deploys rendered graphs of the Synthea modules and the
# Synthea binary distribution to github pages
# --------------------------------------------------------------------------

set -o errexit -o nounset

if [ "$TRAVIS_BRANCH" != "master" ]
then
  echo "Skipping publication of module graphs for non-master branches."
  exit 0
fi

rev=$(git rev-parse --short HEAD)

./gradlew graphviz uberJar
mkdir -p output/build/libs
mv build/libs/*.jar output/build/libs

cd output

git init
git config user.name "Jason Walonoski"
git config user.email "jwalonoski@mitre.org"

git remote add upstream "https://$GH_TOKEN@github.com/synthetichealth/synthea.git"
git fetch upstream gh-pages
git reset upstream/gh-pages

# echo "synthea.org" > CNAME

git add -A graphviz build
git commit -m "rebuild graphs and binary distribution at ${rev}"
git push -q upstream HEAD:gh-pages
