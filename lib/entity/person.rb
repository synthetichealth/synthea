module Synthea
  class Person < Synthea::Entity
    attr_accessor :record_synthea, :active_conditions

    def initialize
      super
      @record_synthea = Synthea::Output::Record.new
      # Conditions that the patient currently has, but may not be diagnosed yet.
      # For a patient to have a 'diagnosed' condition, it must be both active and
      # already recorded in the patient's record.
      @active_conditions = {}
    end

    def onset_condition(type, time)
      # Sets a new active condition for the patient.
      @active_conditions[type] = {
        'type' => type,
        'time' => time
      }
    end

    def end_condition(type, time)
      @active_conditions[type] = nil
      @record_synthea.end_condition(type, time)
    end

    def active_condition?(type)
      !@active_conditions[type].nil?
    end

    def diagnosed_condition?(type)
      active_condition?(type) && @record_synthea.diagnosed_condition?(type)
    end
  end
end
