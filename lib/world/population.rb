module Synthea
  module World
    class Population

      attr_reader :date, :people, :dead
        
      def initialize(area=0.05, birth_std_dev=0.01, date=(Time.now - 100.years))
        @people = []
        @dead = []
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

            puts "#{year}: #{@people.count} living, #{@dead.count} dead"
          end
        end

        living_prediabetics = @people.select{|p|p.had_event?(:prediabetic)}.count
        dead_prediabetics = @dead.select{|p|p.had_event?(:prediabetic)}.count

        puts  "    Living People: #{@people.count} (#{living_prediabetics} prediabetics)\n"+
         "        0-20: #{@people.select {|m| (0..20).include?(m.attributes[:age])}.count}"+
              ",\t21-40: #{@people.select {|m| (21..40).include?(m.attributes[:age])}.count}"+
              ",\t41-60: #{@people.select {|m| (41..60).include?(m.attributes[:age])}.count}"+
              ",\t61-80: #{@people.select {|m| (61..80).include?(m.attributes[:age])}.count}"+
              ",\t>80: #{@people.select {|m| m.attributes[:age] && m.attributes[:age] > 80}.count}\n"+
         "    Dead People: #{@dead.count} (#{dead_prediabetics} prediabetics)\n" +
         "        0-20: #{@dead.select {|m| (0..20).include?(m.attributes[:age])}.count}"+
              ",\t21-40: #{@dead.select {|m| (21..40).include?(m.attributes[:age])}.count}"+
              ",\t41-60: #{@dead.select {|m| (41..60).include?(m.attributes[:age])}.count}"+
              ",\t61-80: #{@dead.select {|m| (61..80).include?(m.attributes[:age])}.count}"+
              ",\t>80: #{@dead.select {|m| m.attributes[:age] && m.attributes[:age] > 80}.count}\n" 
      end

      def advance
        @date += 1.day
        handle_day
        raise "the future is now" if @date > Time.now
      end

      def handle_day
        @people.each do |person|
          Synthea::Rules.apply(@date,person)
        end
        died = @people.select{|p|p.had_event?(:death)}
        @people -= died
        @dead += died
        @births += new_births(@area, @birth_std_dev)
        (0...(@births.floor)).each do |i|
          baby = Synthea::Person.new
          @people << baby
        end
        @births = @births % 1
      end

      def daily_births_per_square_mile
        ma_births_per_year = 73000.0
        ma_square_miles = 10554.0
        days_in_year = 365.0

        ma_births_per_year/ma_square_miles/days_in_year
      end

      def new_births(area, stddev)
        Synthea::Distributions.gaussian(daily_births_per_square_mile*area, stddev)
      end

    end
  end
end