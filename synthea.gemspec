# -*- encoding: utf-8 -*-

Gem::Specification.new do |s|
  s.name = 'synthea'
  s.summary = 'Synthetic Patient Populations'
  s.description = 'A Gem for Synthetic Patient Population Simulator'
  s.email = 'jwalonoski@mitre.org'
  s.homepage = 'https://github.com/synthetichealth/synthea'
  s.authors = ['Andre Quina', 'Jason Walonoski', 'Peter Krautscheid', 'Joey Nichols', 'Dylan Hall', 'Carlton Duffett']
  s.version = '1.1.0'

  s.files = s.files = `git ls-files`.split("\n")

  s.add_runtime_dependency 'faker', '~> 1.6', '>= 1.6.0'
  s.add_runtime_dependency 'pickup', '~>0.0.11', '>= 0.0.11'
  s.add_runtime_dependency 'recursive-open-struct', '~> 1.0', '>= 1.0.0'
  # s.add_runtime_dependency 'fhir_models'
  s.add_runtime_dependency 'fhir_client', '~> 1.8'
  s.add_runtime_dependency 'area'
  s.add_runtime_dependency 'georuby'
  s.add_runtime_dependency 'concurrent-ruby'
  s.add_runtime_dependency 'net-sftp'
  s.add_runtime_dependency 'highline'
  s.add_runtime_dependency 'json'
  s.add_runtime_dependency 'chunky_png'
  s.add_runtime_dependency 'ruby-graphviz'
  s.add_runtime_dependency 'distribution', '~> 0.7.3'
end
