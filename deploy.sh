#!/bin/bash

# --------------------------------------------------------------------------
# This script deploys rendered graphs of the Synthea modules to github pages
# --------------------------------------------------------------------------

set -o errexit -o nounset

if [ "$TRAVIS_BRANCH" != "master" ]
then
  echo "Skipping publication of module graphs for non-master branches."
  exit 0
fi

rev=$(git rev-parse --short HEAD)

./gradlew graphviz
cd output

git init
git config user.name "Jason Walonoski"
git config user.email "jwalonoski@mitre.org"

git remote add upstream "https://$GH_TOKEN@github.com/synthetichealth/synthea.git"
git fetch upstream
git reset upstream/gh-pages

# echo "synthea.org" > CNAME

touch graphviz

git add -A graphviz/
git commit -m "rebuild graphs at ${rev}"
git push -q upstream HEAD:gh-pages
