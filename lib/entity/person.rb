module Synthea
  class Person < Synthea::Entity

  	# TODO: move the record into a separate class that tracks data
    attr_accessor :record, :record_conditions, :fhir_record ,:record_synthea# Health Data Standards Record

    def initialize
      super
      @record_conditions={}
      @record = Record.new
      @fhir_record = FHIR::Bundle.new
      @fhir_record.type = 'collection'
      @record_synthea = Synthea::Output::Record.new
    end

  end
end