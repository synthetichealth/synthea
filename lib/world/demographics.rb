module Synthea
  module World
    class Demographics
      # https://en.wikipedia.org/wiki/Demographics_of_Massachusetts#Race.2C_ethnicity.2C_and_ancestry
      RACES = Pickup.new(white: 75.1,
                         hispanic: 10.5,
                         black: 8.1,
                         asian: 6.0,
                         native: 0.5,
                         other: 0.1)

      ETHNICITY = {
        white: Pickup.new(irish: 22.8,
                          italian: 13.9,
                          english: 10.7,
                          french: 7.8,
                          german: 6.4,
                          polish: 5.0,
                          portuguese: 4.7,
                          american: 4.4,
                          french_canadian: 3.8,
                          scottish: 2.4,
                          russian: 1.9,
                          swedish: 1.8,
                          greek: 1.2),
        hispanic: Pickup.new(puerto_rican: 4.1,
                             mexican: 1,
                             central_american: 1,
                             south_american: 1),
        black: Pickup.new(african: 1.8,
                          dominican: 1.8,
                          west_indian: 1.8),
        asian: Pickup.new(chinese: 2.0,
                          asian_indian: 1.1),
        native: Pickup.new(american_indian: 1),
        other: Pickup.new(arab: 1)
      }.freeze

      # blood type data from http://www.redcrossblood.org/learn-about-blood/blood-types
      # data for :native and :other from https://en.wikipedia.org/wiki/Blood_type_distribution_by_country
      BLOOD_TYPES = {
        white: Pickup.new(o_positive: 37,
                          o_negative: 8,
                          a_positive: 33,
                          a_negative: 7,
                          b_positive: 9,
                          b_negative: 2,
                          ab_positive: 3,
                          ab_negative: 1),
        hispanic: Pickup.new(o_positive: 53,
                             o_negative: 4,
                             a_positive: 29,
                             a_negative: 2,
                             b_positive: 9,
                             b_negative: 1,
                             ab_positive: 2,
                             ab_negative: 1),
        black: Pickup.new(o_positive: 47,
                          o_negative: 4,
                          a_positive: 24,
                          a_negative: 2,
                          b_positive: 18,
                          b_negative: 1,
                          ab_positive: 4,
                          ab_negative: 1),
        asian: Pickup.new(o_positive: 39,
                          o_negative: 1,
                          a_positive: 27,
                          a_negative: 1,
                          b_positive: 25,
                          b_negative: 1,
                          ab_positive: 7,
                          ab_negative: 1),
        native: Pickup.new(o_positive: 37.4,
                           o_negative: 6.6,
                           a_positive: 35.7,
                           a_negative: 6.3,
                           b_positive: 8.5,
                           b_negative: 1.5,
                           ab_positive: 3.4,
                           ab_negative: 0.6),
        other: Pickup.new(o_positive: 37.4,
                          o_negative: 6.6,
                          a_positive: 35.7,
                          a_negative: 6.3,
                          b_positive: 8.5,
                          b_negative: 1.5,
                          ab_positive: 3.4,
                          ab_negative: 0.6)
      }.freeze
    end
  end
end
