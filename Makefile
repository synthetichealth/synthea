# Variables
PYTHON := python3
FLASK_APP := app.py
HTML_DIR := .
FLASK_PORT := 5000
HTTP_SERVER_PORT := 8000
VENV_DIR := .venv

# Targets
.PHONY: all setup serve flask run

all: setup serve flask

# Setup environment
setup:
	@echo "Setting up the environment..."
	$(PYTHON) -m venv $(VENV_DIR)
	. $(VENV_DIR)/bin/activate && pip install --upgrade pip && pip install flask

# Serve the HTML frontend
serve:
	@echo "Starting HTTP server..."
	cd $(HTML_DIR) && $(PYTHON) -m http.server $(HTTP_SERVER_PORT)

# Run Flask backend
flask:
	@echo "Starting Flask backend..."
	. $(VENV_DIR)/bin/activate && FLASK_APP=$(FLASK_APP) flask run --host=localhost --port=$(FLASK_PORT)

# Run both serve and flask targets in parallel
run: 
	@echo "Running both HTTP server and Flask backend..."
	$(MAKE) -j2 serve flask
