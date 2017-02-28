module Synthea
  module Generic
    module States
      # define a base state with common functionality that can be inherited by all other states
      class State
        include Synthea::Generic::Metadata
        include Synthea::Generic::Hashable

        attr_accessor :name, :entered, :exited, :start_time,
                      :assign_to_attribute,
                      :transition

        required_field :name
        required_field :transition

        # using this metadata format to allow for inheritance
        metadata 'name', type: 'String', min: 1, max: 1
        # TODO: transitions should work the same way as conditions,
        # ie as "transition" : { "transition_type" : "abc" ...}
        metadata 'direct_transition', store_as: 'transition', type: 'Transitions::DirectTransition'
        metadata 'conditional_transition', store_as: 'transition', type: 'Transitions::ConditionalTransition'
        metadata 'distributed_transition', store_as: 'transition', type: 'Transitions::DistributedTransition'
        metadata 'complex_transition', store_as: 'transition', type: 'Transitions::ComplexTransition'
        metadata 'type', ignore: true

        def initialize(context, name)
          @context = context
          @name = name
          @config = context.state_config(name) || {}
          from_hash(@config)
        end

        def run(time, entity)
          @entered ||= time
          @start_time ||= time
          begin
            exit = process(time, entity)
          rescue
            puts "FATAL ERROR State: #{@name}"
            raise
          end
          if exit
            if is_a?(Delay) || (is_a?(Procedure) && @duration)
              # Special handling for Delay, which may expire between run cycles
              @exited = @expiration
              @expiration = nil
              @start_time = @exited if @start_time > @exited
            elsif !is_a?(CallSubmodule)
              @exited = time
            end
          end

          entity[@assign_to_attribute] = symbol.to_s if @assign_to_attribute
          exit
        end

        # creates a symbol to be used in lookup tables (mainly for referencing codes)
        def symbol
          # prefer a code display value since the state name may not be unique between modules
          if !@codes.nil? && !@codes.empty?
            @codes.first.display.gsub(/\s+/, '_').downcase.to_sym
          else
            @name.gsub(/\s+/, '_').downcase.to_sym
          end
        end

        def concurrent_with_target_encounter(_time)
          current_encounter = @context.current_encounter
          if is_a?(OnsetState)
            # ConditionOnset and AllergyOnset are allowed to be processed before any Encounter state.
            # They must therefore specify a target_encounter and will be diagnosed when that state
            # is processed.
            if @target_encounter.nil? && current_encounter.nil?
              raise "State '#{@name}' must specify a target_encounter when processed before any encounter occurs"
            end
            # We allow OnsetStates to omit the target_encounter property if the OnsetState occurs directly
            # after AND concurrently with an Encounter state.
            target = @target_encounter || current_encounter
            past = @context.most_recent_by_name(target)
          else
            # All other clinical states are recorded at an encounter. They must come after
            # AND be concurrent with an Encounter state, otherwise an error is raised.
            unless current_encounter.nil?
              past = @context.most_recent_by_name(current_encounter)
            end

            if past.nil?
              raise "No encounter state was processed before state '#{@name}'"
            end
          end
          !past.nil?
        end

        # the record methods require the use of lookup tables.  Other (non-generic) modules statically define
        # lookups in the tables, but we must define the lookups dynamically before the record method is invoked.
        def add_lookup_code(lookup_hash)
          return if @codes.nil? || @codes.empty?

          sym = symbol
          value = {
            description: @codes.first.display,
            codes: {}
          }
          @codes.each do |c|
            value[:codes][c.system] ||= []
            value[:codes][c.system] << c.code
          end

          # intentionally returning the value for further modification (see Encounter.perform_encounter)
          lookup_hash[sym] = value
        end

        def add_lookup_codes(codes, lookup_hash)
          return if codes.nil? || codes.empty?
          symbols = []
          codes.each do |code|
            sym = code.to_sym
            symbols << sym
            value = {
              description: code.display,
              codes: {}
            }
            value[:codes][code.system] ||= []
            value[:codes][code.system] << code.code
            lookup_hash[sym] = value
          end
          symbols
        end

        def to_s
          "#<#{self.class}::#{object_id}> #{@name}"
        end
      end

      class Initial < State
        def process(_time, _entity)
          # initial state always goes to next
          true
        end
      end

      class Simple < State
        def process(_time, _entity)
          # simple state always goes to next
          true
        end
      end

      class CallSubmodule < State
        attr_accessor :submodule

        def initialize(context, name)
          super(context, name)
          @submodule_called = false
        end

        def process(time, entity)
          if !@submodule_called
            @submodule_called = true
            @context.call_submodule(time, entity, @submodule)
            false
          else
            true
          end
        end
      end

      class Terminal < State
        required_fields.delete(:transition) # transitions are explicitly disallowed for Terminal states

        def validate(context, path)
          messages = super
          if transition
            messages << build_message("Terminal state #{@name} has a transition defined", path)
          end
          messages.uniq
        end

        def process(time, entity)
          if @context.active_submodule?
            @context.return_from_submodule(time, entity)
          end
          # never pass through the terminal state
          false
        end
      end

      class Delay < State
        attr_accessor :range, :exact

        required_field or: [:range, :exact]

        metadata 'range', type: 'Components::RangeWithUnit', min: 0, max: 1
        metadata 'exact', type: 'Components::ExactWithUnit', min: 0, max: 1

        def process(time, _entity)
          if @expiration.nil?
            if !@range.nil?
              # choose a random duration within the specified range
              @expiration = @range.value.since(@start_time)
            elsif !@exact.nil?
              @expiration = @exact.value.since(@start_time)
            else
              @expiration = @start_time
            end
          end
          time >= @expiration
        end
      end

      class Guard < State
        attr_accessor :allow

        required_field :allow

        metadata 'allow', type: 'Logic::Condition', polymorphism: { key: 'condition_type', package: 'Logic' }, min: 1, max: 1

        def process(time, entity)
          # only indicate successful processing if the condition evaluates to true
          @allow.test(@context, time, entity)
        end
      end

      class SetAttribute < State
        attr_accessor :attribute, :value

        def process(_time, entity)
          entity[@attribute] = @value
          true
        end
      end

      class Counter < State
        attr_accessor :action, :attribute

        required_field and: [:action, :attribute]

        def process(_time, entity)
          entity[@attribute] ||= 0

          if @action == 'increment'
            entity[@attribute] += 1
          elsif @action == 'decrement'
            entity[@attribute] -= 1
          end

          true
        end
      end

      class Encounter < State
        attr_accessor :wellness, :processed, :time, :codes, :encounter_class, :reason

        required_field or: [:wellness, and: [:codes, :encounter_class]]

        metadata 'codes', type: 'Components::Code', min: 1, max: Float::INFINITY

        def process(time, entity)
          unless @wellness
            # This is a stand-alone (non-wellness) encounter, so proceed immediately!
            perform_encounter(time, entity)
          end

          @processed
        end

        # perform_encounter can be called from this state's process method, but it also might
        # be called as a result of a wellness encounter occurring
        def perform_encounter(time, entity, record_encounter = true)
          # set process to true so that the process method knows we've done a successful encounter
          @processed = true
          @time = time

          if @context.current_encounter
            # if there is a current non-wellness encounter, end it with no discharge type
            enc = @context.most_recent_by_name(@context.current_encounter)
            entity.record_synthea.encounter_end(enc.symbol, time) unless enc.wellness
          end
          @context.current_encounter = @name

          if @reason
            cond = @context.most_recent_by_name(@reason)
            cond = cond.symbol if cond
            cond = entity[@reason].to_sym if cond.nil? && entity[@reason]
          end

          if record_encounter
            value = add_lookup_code(Synthea::ENCOUNTER_LOOKUP)
            value[:class] = @encounter_class
            options = { 'reason' => cond }.compact
            entity.record_synthea.encounter(symbol, time, options)
          end

          # Look through the history for conditions to diagnose
          @context.history.each do |h|
            if h.is_a?(OnsetState) && !h.diagnosed && h.target_encounter == @name
              h.diagnose(time, entity)
            end
          end
        end
      end

      class EncounterEnd < State
        attr_accessor :discharge_disposition

        metadata 'discharge_disposition', type: 'Components::Code', min: 0, max: 1

        def process(time, entity)
          enc = @context.most_recent_by_name(@context.current_encounter)
          @context.current_encounter = nil
          return true if enc.wellness # don't record the end for wellness encounters, that happens elsewhere

          options = { 'discharge' => @discharge_disposition }.compact
          entity.record_synthea.encounter_end(enc.symbol, time, options)
          true
        end
      end

      class OnsetState < State
        attr_accessor :diagnosed, :target_encounter, :codes

        required_field :codes

        metadata 'codes', type: 'Components::Code', min: 1, max: Float::INFINITY
        metadata 'target_encounter', reference_to_state_type: 'Encounter', min: 0, max: 1

        def process(time, entity)
          diagnose(time, entity) if concurrent_with_target_encounter(time)
          true
        end
      end

      class ConditionOnset < OnsetState
        def diagnose(time, entity)
          add_lookup_code(Synthea::COND_LOOKUP)
          entity.record_synthea.condition(symbol, time)
          @diagnosed = true
        end
      end

      class ConditionEnd < State
        attr_accessor :referenced_by_attribute, :condition_onset, :codes

        required_field or: [:referenced_by_attribute, :condition_onset, :codes]

        metadata 'codes', type: 'Components::Code', min: 0, max: Float::INFINITY
        metadata 'condition_onset', reference_to_state_type: 'ConditionOnset', min: 0, max: 1

        def process(time, entity)
          if @referenced_by_attribute
            @type = entity[@referenced_by_attribute].to_sym
          elsif @condition_onset
            @type = @context.most_recent_by_name(@condition_onset).symbol
          elsif @codes
            @type = symbol
          else
            raise 'Condition End must define the condition to end either by code, a referenced entity attribute, or the name of the original ConditionOnset state'
          end

          entity.record_synthea.end_condition(@type, time)
          true
        end
      end

      class AllergyOnset < OnsetState
        def diagnose(time, entity)
          add_lookup_code(Synthea::COND_LOOKUP)
          entity.record_synthea.condition(symbol, time, :allergy, :condition)
          @diagnosed = true
        end
      end

      class AllergyEnd < State
        attr_accessor :referenced_by_attribute, :allergy_onset, :codes

        required_field or: [:referenced_by_attribute, :allergy_onset, :codes]

        metadata 'codes', type: 'Components::Code', min: 0, max: Float::INFINITY
        metadata 'allergy_onset', reference_to_state_type: 'AllergyOnset', min: 0, max: 1

        def process(time, entity)
          if @referenced_by_attribute
            @type = entity[@referenced_by_attribute].to_sym
          elsif @allergy_onset
            @type = @context.most_recent_by_name(@allergy_onset).symbol
          elsif @codes
            @type = symbol
          else
            raise 'Allergy End must define the allergy to end either by code, a referenced entity attribute, or the name of the original AllergyOnset state'
          end

          entity.record_synthea.end_condition(@type, time)
          true
        end
      end

      class MedicationOrder < State
        # target_encounter is deprecated and may be removed in a future release. Leaving it in for
        # now to maintain backwards compatibility with existing GMF modules.
        attr_accessor :prescribed, :codes, :reason, :prescription, :target_encounter

        required_field :codes

        metadata 'codes', type: 'Components::Code', min: 1, max: Float::INFINITY
        metadata 'prescription', type: 'Components::Prescription', min: 0, max: 1
        metadata 'target_encounter', reference_to_state_type: 'Encounter', min: 0, max: 1

        def process(time, entity)
          prescribe(time, entity) if concurrent_with_target_encounter(time)
          true
        end

        def prescribe(time, entity)
          add_lookup_code(Synthea::MEDICATION_LOOKUP)
          unless @reason.nil?
            cond = @context.most_recent_by_name(@reason)
            cond = cond.symbol if cond
            cond = entity[@reason].to_sym if cond.nil? && entity[@reason]
          end
          reasons = if cond.nil?
                      []
                    else
                      [cond]
                    end

          # Handle the prescription object
          rx_info = {}
          unless @prescription.nil?
            rx_info['as_needed'] = @prescription.as_needed || false
            unless @prescription.as_needed
              raise 'Prescription information must include dosage' if @prescription.dosage.nil?
              raise 'Prescription information must include duration' if @prescription.duration.nil?
              rx_info['total_doses'] = @prescription.doses
              rx_info['refills'] = @prescription.refills || 0
              rx_info['dosage'] = @prescription.dosage
              rx_info['duration'] = @prescription.duration
              rx_info['instructions'] = add_lookup_codes(@prescription.instructions, Synthea::INSTRUCTION_LOOKUP)
              rx_info['patient_instructions'] = @prescription.patient_instructions # for CCDA export
            end
          end
          entity.record_synthea.medication_start(symbol, time, reasons, rx_info)
          @prescribed = true
        end
      end

      class MedicationEnd < State
        attr_accessor :referenced_by_attribute, :medication_order, :reason, :codes

        metadata 'codes', type: 'Components::Code', min: 0, max: Float::INFINITY

        def initialize(context, name)
          super(context, name)
          @reason ||= :prescription_expired
        end

        def process(time, entity)
          end_prescription(time, entity)
          true
        end

        def end_prescription(time, entity)
          if @referenced_by_attribute
            @type = entity[@referenced_by_attribute].to_sym
          elsif @medication_order
            @type = @context.most_recent_by_name(@medication_order).symbol
          elsif @codes
            @type = symbol
          else
            raise 'Medication End must define the medication to end either by code, a referenced entity attribute, or the name of the original MedicationOrder state'
          end

          entity.record_synthea.medication_stop(@type, time, [@reason])
        end
      end

      class CarePlanStart < State
        # target_encounter is deprecated and may be removed in a future release. Leaving it in for
        # now to maintain backwards compatibility with existing GMF modules.
        attr_accessor :codes, :activities, :reason, :target_encounter

        required_field :codes

        metadata 'codes', type: 'Components::Code', min: 1, max: Float::INFINITY
        metadata 'activities', type: 'Components::Code', min: 0, max: Float::INFINITY
        metadata 'target_encounter', reference_to_state_type: 'Encounter', min: 0, max: 1

        def process(time, entity)
          start_plan(time, entity) if concurrent_with_target_encounter(time)
          true
        end

        def start_plan(time, entity)
          add_lookup_code(Synthea::CAREPLAN_LOOKUP)
          activities = add_lookup_codes(@activities, Synthea::CAREPLAN_LOOKUP)

          unless @reason.nil?
            rsn = @context.most_recent_by_name(@reason)
            rsn = rsn.symbol if rsn
            rsn = entity[@reason].to_sym if rsn.nil? && entity[@reason]
          end

          if rsn
            entity.record_synthea.careplan_start(symbol, activities, time, [rsn])
          else
            entity.record_synthea.careplan_start(symbol, activities, time, [])
          end
        end
      end

      class CarePlanEnd < State
        attr_accessor :careplan, :reason, :codes, :referenced_by_attribute

        metadata 'codes', type: 'Components::Code', min: 0, max: Float::INFINITY

        def initialize(context, name)
          super(context, name)
          @reason ||= :careplan_ended
        end

        def process(time, entity)
          end_plan(time, entity)
          true
        end

        def end_plan(time, entity)
          if @referenced_by_attribute
            @type = entity[@referenced_by_attribute].to_sym
          elsif @careplan
            @type = @context.most_recent_by_name(@careplan).symbol
          elsif @codes
            @type = symbol
          else
            raise 'CarePlanEnd must define the CarePlan to end either by code, a referenced entity attribute, or the name of the original CarePlanStart state'
          end

          entity.record_synthea.careplan_stop(@type, time)
        end
      end

      class Procedure < State
        # target_encounter is deprecated and may be removed in a future release. Leaving it in for
        # now to maintain backwards compatibility with existing GMF modules.
        attr_accessor :reason, :codes, :target_encounter, :duration

        required_field :codes

        metadata 'codes', type: 'Components::Code', min: 1, max: Float::INFINITY
        metadata 'target_encounter', reference_to_state_type: 'Encounter', min: 0, max: 1
        metadata 'duration', type: 'Components::RangeWithUnit', min: 0, max: 1

        def process(time, entity)
          operate(time, entity) if !@performed && concurrent_with_target_encounter(time)
          if @duration
            # choose a random duration within the specified range
            @expiration ||= @duration.value.since(@start_time)
            time >= @expiration
          else
            true
          end
        end

        def operate(time, entity)
          add_lookup_code(Synthea::PROCEDURE_LOOKUP)
          unless @reason.nil?
            cond = @context.most_recent_by_name(@reason)
            cond = cond.symbol if cond
            cond = entity[@reason].to_sym if cond.nil? && entity[@reason]
          end

          options = { 'reason' => cond,
                      'duration' => @duration ? @duration.value : nil }.compact

          entity.record_synthea.procedure(symbol, time, options)
          @performed = true
        end
      end

      class VitalSign < State
        attr_accessor :vital_sign, :range, :exact, :unit, :target_encounter

        required_field or: [:range, :exact]
        required_field and: [:vital_sign, :unit]

        metadata 'range', type: 'Components::Range', min: 0, max: 1
        metadata 'exact', type: 'Components::Exact', min: 0, max: 1
        metadata 'target_encounter', reference_to_state_type: 'Encounter', min: 0, max: 1

        def process(_time, entity)
          if @range
            @value = @range.value
          elsif @exact
            @value = @exact.value
          end

          entity.set_vital_sign(@vital_sign, @value, @unit)

          true
        end
      end

      class Observation < State
        # target_encounter is deprecated and may be removed in a future release. Leaving it in for
        # now to maintain backwards compatibility with existing GMF modules.
        attr_accessor :codes, :unit, :target_encounter, :attribute, :vital_sign, :range, :exact, :category

        required_field :codes
        required_field :category
        required_field or: [:vital_sign, and: [:unit, or: [:attribute, :range, :exact]]]

        metadata 'codes', type: 'Components::Code', min: 1, max: Float::INFINITY
        metadata 'target_encounter', reference_to_state_type: 'Encounter', min: 1, max: 1
        metadata 'range', type: 'Components::Range', min: 0, max: 1
        metadata 'exact', type: 'Components::Exact', min: 0, max: 1
        # continue to allow range and exact here, but prefer to use Vital Signs and Attributes where possible
        # some things, such as the MMSE score for Alzheimer's, don't feel like a good fit for 'Vital Sign'
        # since it's a quantitative evaluation and not a physical property of the patient

        def add_lookup_code(lookup_hash)
          # TODO: update the observation lookup hash so it's the same format as all the others
          # and then delete this method
          return if @codes.nil? || @codes.empty?
          code = @codes.first
          lookup_hash[symbol] = { description: code.display, code: code.code, unit: @unit }
        end

        def process(time, entity)
          if @vital_sign
            vs = entity.vital_sign(@vital_sign)
            raise "Value of vital sign #{@vital_sign} is nil" unless vs
            @value = vs[:value]

            if @unit && @unit != vs[:unit]
              raise "Observation #{@name} specifies unit '#{@unit}' but the Vital Sign specified unit '#{vs[:unit]}'"
            else
              @unit = vs[:unit]
            end
          elsif @range
            @value = @range.value
          elsif @exact
            @value = @exact.value
          elsif @attribute
            @value = entity[@attribute] || entity[@attribute.to_sym]
          end
          add_lookup_code(Synthea::OBS_LOOKUP)

          if concurrent_with_target_encounter(time)
            entity.record_synthea.observation(symbol, time, @value, 'category' => @category)
          end
          true
        end
      end

      class ObservationGroup < State
        attr_accessor :codes, :number_of_observations, :target_encounter

        required_field and: [:codes, :number_of_observations]

        metadata 'codes', type: 'Components::Code', min: 1, max: Float::INFINITY
        metadata 'target_encounter', reference_to_state_type: 'Encounter', min: 1, max: 1

        def add_lookup_code(lookup_hash)
          # TODO: update the observation lookup hash so it's the same format as all the others
          # and then delete this method
          return if @codes.nil? || @codes.empty?
          code = @codes.first
          lookup_hash[symbol] = { description: code.display, code: code.code, unit: @unit }
        end
      end

      class MultiObservation < ObservationGroup
        def process(time, entity)
          add_lookup_code(Synthea::OBS_LOOKUP)
          if concurrent_with_target_encounter(time)
            entity.record_synthea.observation(symbol, time, @number_of_observations, 'fhir' => :multi_observation, 'ccda' => :no_action)
          else
            raise "MultiObservation '#{@name}' is not concurrent with its target encounter '#{@target_encounter}'"
          end
          true
        end
      end

      class DiagnosticReport < ObservationGroup
        def process(time, entity)
          add_lookup_code(Synthea::OBS_LOOKUP)
          if concurrent_with_target_encounter(time)
            entity.record_synthea.diagnostic_report(symbol, time, @number_of_observations)
          else
            raise "DiagnosticReport '#{@name}' is not concurrent with its target encounter '#{@target_encounter}'"
          end
          true
        end
      end

      class Symptom < State
        attr_accessor :symptom, :cause, :range, :exact

        metadata 'range', type: 'Components::Range', min: 0, max: 1
        metadata 'exact', type: 'Components::Exact', min: 0, max: 1

        def initialize(context, name)
          super(context, name)
          @cause ||= context.config['name']
        end

        def process(_time, entity)
          if range
            @value = @range.value
          elsif exact
            @value = @exact.value
          else
            raise 'Symptom state must specify value using either "range" or "exact"'
          end
          entity.set_symptom_value(@cause, @symptom, @value)
        end
      end

      class Death < State
        attr_accessor :range, :exact, :referenced_by_attribute, :condition_onset, :codes

        metadata 'codes', type: 'Components::Code', min: 0, max: Float::INFINITY
        metadata 'range', type: 'Components::RangeWithUnit', min: 0, max: 1
        metadata 'exact', type: 'Components::ExactWithUnit', min: 0, max: 1
        metadata 'condition_onset', reference_to_state_type: 'ConditionOnset', min: 0, max: 1

        def process(time, entity)
          if @range
            value = @range.value.since(time)
          elsif @exact
            value = @exact.value.since(time)
          end

          # this is the same as the ConditionEnd logic, maybe we want to extract this somewhere
          if @referenced_by_attribute
            @reason = entity[@referenced_by_attribute].to_sym
          elsif @condition_onset
            @reason = @context.most_recent_by_name(@condition_onset).symbol
          elsif @codes
            @reason = symbol
          end

          if value
            # Record the future death... if there is a condition with a known life-expectancy
            # and you want the model to keep running in the mean time.
            entity.events.create(value, :death, :generic, false)
            Synthea::Modules::Lifecycle.record_death(entity, value, @reason)
          else
            entity.events.create(time, :death, :generic, true)
            Synthea::Modules::Lifecycle.record_death(entity, time, @reason)
          end
          true
        end
      end
    end
  end
end
