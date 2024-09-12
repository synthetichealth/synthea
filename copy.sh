#!/bin/bash

while read line
do
    name=$line
    # echo "Text read from file - $name"
    find $name -exec cp {} $2 \;    

done < $1