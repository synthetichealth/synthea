require 'rake/testtask'
require 'cane/rake_task'
require 'rubocop/rake_task'

require_relative 'lib/synthea'

# Pull in any rake task defined in lib/tasks
Dir['lib/tasks/**/*.rake'].sort.each do |ext|
  load ext
end

desc 'Run basic tests'
Rake::TestTask.new(:test_unit) do |t|
  t.libs << 'test'
  t.test_files = FileList['test/**/*_test.rb']
  t.verbose = true
  t.warning = false
end

desc 'Run rubocop'
task :rubocop do
  RuboCop::RakeTask.new
end

task test: [:test_unit, :rubocop] do
  system('open coverage/index.html')
end

task default: [:test]
