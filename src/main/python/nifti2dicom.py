import os
from pathlib import Path
import subprocess
import re
from shutil import copyfile

mango_translate_command = '/Users/andrewg/bin/mango-convert2dcm'
image_root_folder = '/Users/andrewg/Downloads/10ms'
output_folder = '/Users/andrewg/Desktop/dicom'

image_path = Path(image_root_folder)
nifti_images = image_path.glob('*/*.magnitude.nii.gz')

for i in nifti_images:
  filename = i.name
  subject = re.search(r'(subject\-\d\d\d).+', filename).group(1)
  subprocess.run([mango_translate_command, '-out', Path(output_folder, subject + '.dcm'), i])
  subject_folder = i.parent
  copyfile(Path(subject_folder, 'microbleeds.nii.json'), Path(output_folder, subject + '-microbleeds.json'))