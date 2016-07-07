# Top level include file that brings in all the necessary code
require 'bundler/setup'
require 'rubygems'
require 'yaml'
require 'faker'
require 'area'
require 'distribution'
require 'pickup'
require 'recursive-open-struct'
require 'health-data-standards'
require 'fhir_models'
require 'fhir_client'
require 'pry'
require 'georuby'
require 'geo_ruby/geojson'

root = File.expand_path '..', File.dirname(File.absolute_path(__FILE__))

module Synthea
end

Synthea::Config = RecursiveOpenStruct.new(YAML.load(ERB.new(File.read(File.join(root, 'config', 'synthea.yml'))).result)['synthea'])

Dir.glob(File.join(root, 'lib','ext','**','*.rb')).each do |file|
  require file
end

Dir.glob(File.join(root, 'lib','events','*.rb')).each do |file|
  require file
end
Dir.glob(File.join(root, 'lib','events','**','*.rb')).each do |file|
  require file
end

Dir.glob(File.join(root, 'lib','entity','**','*.rb')).each do |file|
  require file
end

require File.join(root,'lib','modules','module.rb')
Dir.glob(File.join(root, 'lib','modules','*.rb')).each do |file|
  require file
end

Dir.glob(File.join(root, 'lib','records','*.rb')).each do |file|
  require file
end

Dir.glob(File.join(root, 'lib','world','**','*.rb')).each do |file|
  require file
end

Dir.glob(File.join(root, 'lib','likelihoods','**','*.rb')).each do |file|
  require file
end