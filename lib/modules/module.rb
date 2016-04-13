module Synthea
  class Rules

    cattr_accessor :metadata

    def initialize
      @rules ||= methods.grep(/_rule$/).map {|r| method(r)}
    end

    def run(time, entity)
      @rules.each {|r| r.call(time, entity)}
    end

    def self.apply(time,entity)
      get_modules.each {|r| r.run(time, entity)}
    end

    def self.get_modules
      @@modules ||= Synthea::Modules.constants.map {|m| "Synthea::Modules::#{m}".constantize.new}
    end

    def self.rule(name,inputs,outputs,&block)
      @@metadata ||= {}
      @@metadata[name] = {
        inputs: inputs,
        outputs: outputs
      }
      define_method "#{name}_rule".to_sym, block
    end
    
  end
end