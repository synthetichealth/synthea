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

  end
end