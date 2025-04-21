import subprocess
import os
import json

class SyntheaGenerator:
    def __init__(self, state="Massachusetts", gender=None, age=None, patients=1, module=None, synth_path=None):
        if synth_path is None:
            synth_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

        self.state = state
        self.gender = gender
        self.age = age
        self.patients = patients
        self.module = module
        self.synth_path = synth_path
        self.output_path = os.path.join(self.synth_path, "output", "fhir")

    def _clear_output(self):
        if os.path.exists(self.output_path):
            for f in os.listdir(self.output_path):
                if f.endswith(".json"):
                    os.remove(os.path.join(self.output_path, f))

    def _build_command(self):
        cmd = ["./run_synthea", self.state, "-p", str(self.patients)]
        if self.age:
            cmd += ["-a", self.age]
        if self.gender:
            cmd += ["-g", self.gender]
        if self.module:
            cmd += ["-m", self.module]
        return cmd

    def generate(self):
        self._clear_output()
        cmd = self._build_command()
        try:
            subprocess.run(cmd, cwd=self.synth_path, check=True)
        except subprocess.CalledProcessError as e:
            raise RuntimeError(f"[Synthea Error] CLI failed: {e}")

        if not os.path.exists(self.output_path):
            raise RuntimeError("No output folder found.")

        json_files = [f for f in os.listdir(self.output_path) if f.endswith(".json")]
        patients = []
        for file in json_files:
            with open(os.path.join(self.output_path, file), "r") as f:
                data = json.load(f)
                entries = data.get("entry", [])
                has_patient = any(
                    e.get("resource", {}).get("resourceType") == "Patient" for e in entries
                )
                if data.get("resourceType") == "Bundle" and has_patient:
                    patients.append(data)

        return patients

    def save(self, output_dir):
        os.makedirs(output_dir, exist_ok=True)
        for file in os.listdir(self.output_path):
            if file.endswith(".json"):
                src = os.path.join(self.output_path, file)
                dst = os.path.join(output_dir, file)
                with open(src, "r") as f_in, open(dst, "w") as f_out:
                    f_out.write(f_in.read())
