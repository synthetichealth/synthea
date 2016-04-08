# Top level include file that brings in all the necessary code
require 'bundler/setup'
require 'rubygems'
require 'yaml'
require 'faker'
require 'health-data-standards'

require 'pry'

root = File.expand_path '..', File.dirname(File.absolute_path(__FILE__))

Dir.glob(File.join(root, 'lib','ext','**','*.rb')).each do |file|
  require file
end

Dir.glob(File.join(root, 'lib','events','*.rb')).each do |file|
  require file
end
Dir.glob(File.join(root, 'lib','events','**','*.rb')).each do |file|
  require file
end

Dir.glob(File.join(root, 'lib','patient','**','*.rb')).each do |file|
  require file
end

Dir.glob(File.join(root, 'lib','world','**','*.rb')).each do |file|
  require file
end

Dir.glob(File.join(root, 'lib','likelihoods','**','*.rb')).each do |file|
  require file
end
