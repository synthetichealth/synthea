module Synthea
  module World
    class Population
      attr_reader :date, :people, :dead

      def initialize
        @start_date = Synthea::Config.start_date
        @end_date = Synthea::Config.end_date
        @time_step = Synthea::Config.time_step
        @date = @start_date
        @birth_rate = BirthRate.new

        @people = []
        @dead = []
        @births = 0
      end

      def run
        year = 0
        while @date <= (@end_date - @time_step.days)
          advance
          if year != @date.year
            year = @date.year
            puts "#{year}: #{@people.count} living, #{@dead.count} dead"
          end
        end
        stats
      end

      def advance
        @date += @time_step.days
        handle_time_step
        raise "advanced date: #{@date} beyond the end date: #{@end_date}" if @date > @end_date
      end

      def handle_time_step
        died = @people.select { |p| p.had_event?(:death, @data) }
        @people -= died
        @dead += died
        @births += @birth_rate.births
        @births.floor.times do |_i|
          baby = Synthea::Person.new
          @people << baby
        end
        @births = @births % 1
        @people.each do |person|
          Synthea::Rules.apply(@date, person)
        end
      end

      def stats
        living_diabetics = @people.select { |p| p.had_event?(:diabetes) }.count
        dead_diabetics = @dead.select { |p| p.had_event?(:diabetes) }.count
        puts "    Living People: #{@people.count} (#{living_diabetics} diabetics)\n"\
              "        0-20: #{@people.select { |m| (0..20).cover?(m.attributes[:age]) }.count}"\
              ",\t21-40: #{@people.select { |m| (21..40).cover?(m.attributes[:age]) }.count}"\
              ",\t41-60: #{@people.select { |m| (41..60).cover?(m.attributes[:age]) }.count}"\
              ",\t61-80: #{@people.select { |m| (61..80).cover?(m.attributes[:age]) }.count}"\
              ",\t>80: #{@people.select { |m| m.attributes[:age] && m.attributes[:age] > 80 }.count}\n"\
              "    Dead People: #{@dead.count} (#{dead_diabetics} diabetics)\n" \
              "        0-20: #{@dead.select { |m| (0..20).cover?(m.attributes[:age]) }.count}"\
              ",\t21-40: #{@dead.select { |m| (21..40).cover?(m.attributes[:age]) }.count}"\
              ",\t41-60: #{@dead.select { |m| (41..60).cover?(m.attributes[:age]) }.count}"\
              ",\t61-80: #{@dead.select { |m| (61..80).cover?(m.attributes[:age]) }.count}"\
              ",\t>80: #{@dead.select { |m| m.attributes[:age] && m.attributes[:age] > 80 }.count}\n"
      end
    end
  end
end
