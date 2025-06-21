from wrapper import SyntheaGenerator
import os

gen = SyntheaGenerator(
    state="California",
    gender="F",
    age="30-40",
    patients=2,
    module="diabetes"
)

patients = gen.generate()


if not patients:
    print("No patients generated.")
else:
    print(f"Generated {len(patients)} patients")
    print("Sample patient resourceType:", patients[0].get("resourceType"))

gen.save("my_output/diabetes_test")

saved_files = os.listdir("my_output/diabetes_test")
print("Saved files:", saved_files)
