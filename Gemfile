source 'https://rubygems.org'
gemspec

gem 'rake'
gem 'pry'
gem 'tilt'
gem 'faker'
gem 'pickup'
gem 'recursive-open-struct'
gem 'health-data-standards', git: 'https://github.com/projectcypress/health-data-standards.git', branch: 'master'
gem 'fhir_models', git: 'https://github.com/fhir-crucible/fhir_models.git', branch: '3.0.0-release'
gem 'georuby'
gem 'net-sftp'
gem 'concurrent-ruby', require: 'concurrent'
gem 'rack', '~> 1.6' # locked at 1.6 to maintain compatibility with ruby 2.0.0
gem 'distribution', '~> 0.7.3'

group :test do
  gem 'rubocop', '~> 0.43.0', require: false
  gem 'cane', '~> 2.3.0'
  gem 'simplecov', require: false
  gem 'minitest', '~> 5.3'
  gem 'minitest-reporters'
  gem 'awesome_print', require: 'ap'
end
