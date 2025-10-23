from flask import Flask, request, jsonify
import subprocess

app = Flask(__name__)

@app.route('/run-synthea', methods=['POST'])
def run_synthea():
    data = request.json
    command = data.get('command')
    
    try:
        # Run the command
        result = subprocess.run(command, shell=True, capture_output=True, text=True)
        
        # Return the output
        return jsonify({
            'output': result.stdout,
            'error': result.stderr,
            'returncode': result.returncode
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    app.run(host='localhost', port=5000)
