// scripts/node_wrapper/wrapper.js

const { spawn } = require('child_process');
const fs = require('fs').promises;
const path = require('path');
const os = require('os');

class SyntheaGenerator {
  constructor(options = {}) {
    const {
      state = "Massachusetts",
      gender = null,
      age = null,
      patients = 1,
      module = null,
      synthPath = null
    } = options;

    this.state = state;
    this.gender = gender;
    this.age = age;
    this.patients = patients;
    this.module = module;
    this.synthPath = synthPath || path.resolve(__dirname, "..", "..");
    this.outputPath = path.join(this.synthPath, "output", "fhir");
  }

  async _clearOutput() {
    try {
      const files = await fs.readdir(this.outputPath);
      const jsonFiles = files.filter(file => file.endsWith('.json'));
      
      await Promise.all(
        jsonFiles.map(file => 
          fs.unlink(path.join(this.outputPath, file))
        )
      );
    } catch (error) {
      // Directory might not exist yet, which is fine
      if (error.code !== 'ENOENT') {
        throw error;
      }
    }
  }

  _buildCommand() {
    const isWindows = os.platform() === 'win32';
    const script = isWindows ? 'run_synthea.bat' : './run_synthea';
    const cmd = [script, this.state, '-p', this.patients.toString()];
    
    if (this.age) {
      cmd.push('-a', this.age);
    }
    
    if (this.gender) {
      cmd.push('-g', this.gender);
    }
    
    if (this.module) {
      cmd.push('-m', this.module);
    }
    
    return cmd;
  }

  async _runCommand(cmd) {
    return new Promise((resolve, reject) => {
      const [command, ...args] = cmd;
      
      const child = spawn(command, args, {
        cwd: this.synthPath,
        stdio: 'inherit',
        shell: os.platform() === 'win32'
      });

      child.on('close', (code) => {
        if (code === 0) {
          resolve();
        } else {
          reject(new Error(`Synthea CLI failed with exit code ${code}`));
        }
      });

      child.on('error', (error) => {
        reject(new Error(`Failed to start Synthea process: ${error.message}`));
      });
    });
  }

  async generate() {
    try {
      await this._clearOutput();
      const cmd = this._buildCommand();
      await this._runCommand(cmd);

      // Check if output directory exists
      try {
        await fs.access(this.outputPath);
      } catch (error) {
        throw new Error('No output folder found after generation');
      }

      // Read and parse JSON files
      const files = await fs.readdir(this.outputPath);
      const jsonFiles = files.filter(file => file.endsWith('.json'));
      const patients = [];

      for (const file of jsonFiles) {
        const filePath = path.join(this.outputPath, file);
        const fileContent = await fs.readFile(filePath, 'utf8');
        
        try {
          const data = JSON.parse(fileContent);
          const entries = data.entry || [];
          const hasPatient = entries.some(
            entry => entry.resource?.resourceType === 'Patient'
          );
          
          if (data.resourceType === 'Bundle' && hasPatient) {
            patients.push(data);
          }
        } catch (parseError) {
          console.warn(`Warning: Could not parse JSON file ${file}:`, parseError.message);
        }
      }

      return patients;
    } catch (error) {
      throw new Error(`Synthea generation failed: ${error.message}`);
    }
  }

  async save(outputDir) {
    try {
      await fs.mkdir(outputDir, { recursive: true });
      
      const files = await fs.readdir(this.outputPath);
      const jsonFiles = files.filter(file => file.endsWith('.json'));
      
      await Promise.all(
        jsonFiles.map(async (file) => {
          const src = path.join(this.outputPath, file);
          const dst = path.join(outputDir, file);
          const data = await fs.readFile(src, 'utf8');
          await fs.writeFile(dst, data, 'utf8');
        })
      );
    } catch (error) {
      throw new Error(`Failed to save files: ${error.message}`);
    }
  }
}

module.exports = SyntheaGenerator;