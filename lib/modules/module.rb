module Synthea
  class Rules

    cattr_accessor :time   # seconds since the epoch (Time)
    cattr_accessor :entity # entity being processed
    cattr_accessor :rules

    def self.rule(name,inputs,outputs,&block)
      metadata = {}
      metadata[:inputs] = inputs
      metadata[:outputs] = outputs
      metadata[:logic] = block
      @@rules = {} if @@rules.nil?
      @@rules[name] = metadata
    end

    def self.apply(time,entity)
      @@time = time
      @@entity = entity
      @@rules.each do |key,rule|
        instance_eval &rule[:logic]
      end
      @@entity
    end

  end
end