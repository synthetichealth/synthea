.PHONY: build install visualize

SHELL := /bin/bash

export PATH := ./bin:${PATH}

all: install build test visualize

install:
	hermit shell-hooks
	hermit install

build:
	./gradlew build

test:
	./gradlew check test

visualize:
	gradle graphviz
