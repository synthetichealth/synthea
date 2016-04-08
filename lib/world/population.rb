module Synthea
  module World
    class Population

      attr_reader :date, :patients
        
      def initialize(area=0.05, birth_std_dev=0.01, date=(Time.now - 100.years))
        @patients = []
        @date = date
        @area = area
        @births = 0
        @birth_std_dev = birth_std_dev
      end

      def time_travel(date)
        @date = date
      end

      def run
        year = 0
        while @date <= (Time.now - 1.day)
          advance
          if year != @date.year
            year = @date.year
            dead = @patients.select {|m| m.patient.expired}
            puts "#{year} \n"+
                 "    Living Patients: #{@patients.count}\n"+
                 "        0-20: #{@patients.select {|m| (0..20).include? m.age(@date)}.count}"+
                      ",\t21-40: #{@patients.select {|m| (21..40).include? m.age(@date)}.count}"+
                      ",\t41-60: #{@patients.select {|m| (41..60).include? m.age(@date)}.count}"+
                      ",\t61-80: #{@patients.select {|m| (61..80).include? m.age(@date)}.count}"+
                      ",\t>80: #{@patients.select {|m| m.age(@date) > 80}.count}\n"+
                 "    Dead Patients: #{dead.count}\n" +
                 "        0-20: #{dead.select {|m| (0..20).include? m.age(@date)}.count}"+
                      ",\t21-40: #{dead.select {|m| (21..40).include? m.age(@date)}.count}"+
                      ",\t41-60: #{dead.select {|m| (41..60).include? m.age(@date)}.count}"+
                      ",\t61-80: #{dead.select {|m| (61..80).include? m.age(@date)}.count}"+
                      ",\t>80: #{dead.select {|m| m.age(@date) > 80}.count}\n"
          end
        end
      end

      def advance
        @date += 1.day
        handle_day
        raise "the future is now" if @date > Time.now
      end

      def handle_day
        patients.each do |patient|
          patient.evaluate(@date)
        end
        @births += Synthea::Likelihood::Birth.likelihood(@area, @birth_std_dev)
        (0...(@births.floor)).each do |i|
          manager = Synthea::Patient::Manager.new
          manager.process(Synthea::Events::Core::Birth, @date)
          patients << manager
        end
        @births = @births % 1
      end

    end
  end
end