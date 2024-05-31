#!/bin/sh

basedir=`pwd`

#./run_synthea -p 10 -a 55-70 -k keep_diabetes.json -fm src/test/resources/flexporter/eyes_on_fhir.yaml
# pre-processed files are now in ./output

cd src/main/python/coherent-data/
source ./venv/bin/activate

./venv/bin/python associate_images.py ${basedir}/images/fundus_index.csv ${basedir}/images/oct_index.csv ${basedir}/output/fhir --clean --output ${basedir}/coherent_eyes