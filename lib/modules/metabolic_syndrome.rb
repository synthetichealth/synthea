module Synthea
  module Modules
    class MetabolicSyndrome < Synthea::Rules

      # People have a BMI that we can roughly use to estimate
      # blood glucose and diabetes
      rule :prediabetes?, [:bmi], [:blood_glucose,:prediabetic] do |time, entity|
        bmi = entity.attributes[:bmi]
        if bmi
          entity.attributes[:blood_glucose] = Synthea::Modules::MetabolicSyndrome.blood_glucose(bmi)
          if(entity.attributes[:blood_glucose] < 6.5)
            entity.components.delete(:prediabetic)
          else
            entity.components[:prediabetic]=true 
            entity.events << Synthea::Event.new(time,:prediabetic,:prediabetes?,false) if !entity.had_event?(:prediabetic)
          end
        end
      end

      # rough linear fit seen in Figure 1
      # http://www.microbecolhealthdis.net/index.php/mehd/article/viewFile/22857/34046/125897
      def self.blood_glucose(bmi)
        ((bmi - 6) / 6.5)
      end

    end
  end
end
