module Synthea
  module Likelihood
    class Death

      def self.likelihood(age)
        # http://www.cdc.gov/nchs/nvss/mortality/gmwk23r.htm: 820.4/100000
        case 
        when age < 1
          #508.1/1/365
          0.00001392054794520548
        when age >= 1  && age <=4
          #15.6/100000/365
          0.000004273972602739726
        when age >= 5  && age <=14
          #10.6/100000/365
          0.000002904109589041096
        when age >= 15 && age <=24
          #56.4/100000/365
          0.000015452054794520548
        when age >= 25 && age <=34
          #74.7/100000/365
          0.000020465753424657535
        when age >= 35 && age <=44
          #145.7/100000/365
          0.00003991780821917808
        when age >= 45 && age <=54
          #326.5/100000/365
          0.00008945205479452055
        when age >= 55 && age <=64
          #737.8/100000/365
          0.000020213698630136987
        when age >= 65 && age <=74
          #1817.0/100000/365
          0.00004978082191780822
        when age >= 75 && age <=84
          #4877.3/100000/365
          0.00013362465753424658
        when age >= 85 && age <=94
          #13499.4/100000/365
          0.00036984657534246574
        else
          #50000/100000/365
          0.0013698630136986301
        end
      end

      def self.evaluate(patient, date)
        rand <= likelihood(patient.age(date))
      end

    end


  end
end