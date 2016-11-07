module Synthea
  class Rules
    cattr_accessor :metadata

    def initialize
      @rules ||= methods.grep(/_rule$/).map { |r| method(r) }
    end

    def run(time, entity)
      @rules.each { |r| r.call(time, entity) }
    end

    def pick(array)
      rand(array.first..array.last)
    end

    def self.apply(time, entity)
      modules.each { |r| r.run(time, entity) }
    end

    # rubocop:disable Style/ClassVars
    def self.modules
      @@modules ||= Synthea::Modules.constants.map { |m| "Synthea::Modules::#{m}".constantize.new }
    end

    def self.rule(name, inputs, outputs, &block)
      @@metadata ||= {}
      @@metadata[name] = {
        inputs: inputs,
        outputs: outputs,
        module_name: to_s.split('::').last
      }
      define_method "#{name}_rule".to_sym, block
    end
    # rubocop:enable Style/ClassVars

    # Let Y be the original period risk (in our example 3650 days) and X be the time-step risk. The chance of an event not happening in 10 years is the
    # probability of the event not occuring every time step. (1-X)^(3650/time_step). Subtract this from 1 to get the
    # probability of the event occuring in 10 years: Y = 1-(1-X)^(3650/time_step). Solve the equation for X to yield the
    # formula below:

    # original_period_days is the time period of the original risk in days. Risk is the risk probability
    def self.convert_risk_to_timestep(risk, original_period)
      1 - ((1 - risk)**(Synthea::Config.time_step.to_f / original_period))
    end

    # These functions are used to start/update medications and to stop medications, respectively.
    # The 'changes' parameter is an array of medications from the module that called the function used to keep track of
    # which meds were altered.
    # Similar functions were not written for care plans because a care plan is typically only started/updated in one
    # rule, whereas it is common for a medication to be used in multiple rules. This may change in future modules(?)
    def prescribe_medication(med, reason, time, entity, changes)
      entity[:medications] ||= {}
      if entity[:medications][med].nil?
        entity[:medications][med] = { 'time' => time, 'reasons' => [reason] }
        changes << med unless changes.include?(med)
      elsif !entity[:medications][med]['reasons'].include?(reason)
        entity[:medications][med]['reasons'] << reason
        changes << med unless changes.include?(med)
      end
    end

    def taking_medication_for?(reason)
      return false if entity[:medications].nil?
      meds = []
      entity[:medications].each do |med, info|
        meds << med if info['reasons'].include?(reason)
      end
      !meds.empty?
    end

    # 'reason' is the condition that the medication was prescribed for but has since abated.
    def stop_medication(med, reason, _time, entity, changes)
      return if entity[:medications].nil? || entity[:medications][med].nil?
      if entity[:medications][med]['reasons'].include?(reason)
        entity[:medications][med]['reasons'].delete(reason)
        changes << med unless changes.include?(med)
      end
      entity[:medications].delete(med) if entity[:medications][med]['reasons'].empty?
    end
  end
end
