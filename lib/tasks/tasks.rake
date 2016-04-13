namespace :synthea do
  
  desc 'console'
  task :console, [] do |t, args|
    binding.pry
  end

  desc 'generate'
  task :generate, [] do |t, args|
    start = Time.now
    world = Synthea::World::Population.new
    world.run
    finish = Time.now
    minutes = ((finish-start)/60)
    seconds = (minutes - minutes.floor) * 60
    puts "Completed in #{minutes.floor} minute(s) #{seconds.floor} second(s)."

    binding.pry
  end

end
