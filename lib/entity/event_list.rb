module Synthea
  class EventList < Array

    def select(&block)
      EventList.new(super.select(&block))
    end
    def next
      self.first
    end

    def next?
      !self.next.nil?
    end

    def unprocessed
      self.select{|x|x.processed==false}
    end

    def before(date)
      self.select{|x|x.time <= date}
    end

    def since(date)
      self.select{|x|x.time >= date}
    end

    def create(time, type, rule, processed=false)
      self << Synthea::Event.new(time, type, rule, processed)
    end

  end
end
