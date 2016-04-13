module Synthea
  class Rules

    cattr_accessor :time   # seconds since the epoch (Time)
    cattr_accessor :entity # entity being processed
    cattr_accessor :metadata

    # def self.rule(name,inputs,outputs,&block)
    #   metadata = {}
    #   metadata[:inputs] = inputs
    #   metadata[:outputs] = outputs
    #   metadata[:logic] = block


    #   @@rules = {} if @@rules.nil?
    #   @@rules[name] = metadata
    # end

    def self.apply(time,entity)
      @@rules ||= self.get_rules
      @@rules.each {|r| r.call(time, entity)}
      entity
    end

    def self.get_rules
      rules = []
      Synthea::Modules.constants.each do |m|
        instance = "Synthea::Modules::#{m}".constantize.new
        rules.concat (instance.methods.grep(/_rule$/).map {|r| instance.method(r)})
      end
      rules
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