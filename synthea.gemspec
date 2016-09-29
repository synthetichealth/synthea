# -*- encoding: utf-8 -*-

Gem::Specification.new do |s|
  s.name = 'synthea'
  s.summary = 'Synthetic Patient Populations'
  s.description = 'A Gem for Synthetic Patient Population Simulator'
  s.email = 'aquina@mitre.org'
  s.homepage = 'https://github.com/'
  s.authors = ['Andre Quina', 'Jason Walonoski', 'Peter Krautscheid', 'Joey Nichols']
  s.version = '1.0.0'

  s.files = s.files = `git ls-files`.split("\n")

  s.add_runtime_dependency 'distribution', '~> 0.7', '>= 0.7.0'
  s.add_runtime_dependency 'faker', '~> 1.6', '>= 1.6.0'
  s.add_runtime_dependency 'pickup', '~>0.0.11', '>= 0.0.11'
  s.add_runtime_dependency 'recursive-open-struct', '~> 1.0', '>= 1.0.0'
  s.add_runtime_dependency 'fhir_models'
  s.add_runtime_dependency 'fhir_client'
  s.add_runtime_dependency 'area'
  s.add_runtime_dependency 'georuby'
  s.add_runtime_dependency 'concurrent-ruby'
  s.add_runtime_dependency 'net-sftp'
  s.add_runtime_dependency 'highline'
  s.add_runtime_dependency 'json'
  s.add_runtime_dependency 'chunky_png'
end
