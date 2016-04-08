namespace :synthea do
  
  desc 'console'
  task :console, [] do |t, args|
    binding.pry
  end

  desc 'generate'
  task :generate, [] do |t, args|

    m = Synthea::World::Population.new
    m.run

    binding.pry
  end

end
