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

      # http://www.cdc.gov/nchs/data/nhsr/nhsr077.pdf
      # Among all U.S. adults aged 18 and over, 96.6% identified as straight, 1.6% identified as gay or lesbian, and 0.7% identified as bisexual.
      # The remaining 1.1% of adults identified as 'something else' (0.2%), selected 'I dont know the answer' (0.4%), or refused to provide an answer (0.6%).
      SEXUAL_ORIENTATION = Pickup.new(heterosexual: 96.6,
                                      homosexual: 1.6,
                                      bisexual: 0.7)

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

      # https://apps.mla.org/map_data -> search by State MA
      # or see https://apps.mla.org/map_data_results&SRVY_YEAR=2010&geo=state&state_id=25&county_id=&mode=geographic&lang_id=&zip=&place_id=&cty_id=&region_id=&division_id=&ll=&ea=y&order=&a=y&pc=1
      # vietnamese and cambodian removed because our ethnicity/heritage info isn't that granular
      LANGUAGES = Pickup.new(english: 78.93,
                             spanish: 7.50,
                             portuguese: 2.97,
                             chinese: 1.59,
                             french: 1.11,
                             french_creole: 0.89,
                             italian: 0.72,
                             russian: 0.62,
                             # vietnamese: 0.58,
                             greek: 0.41,
                             arabic: 0.37,
                             # cambodian: 0.37,
                             german: 0.28,
                             hindi: 0.27)

      # these numbers are intended to produce the above numbers overall but correlated by ethnicity
      # ex, only people of chinese ethnicity speak chinese
      # these are "manufactured" #s and not based on real citations
      LANGUAGES_BY_ETHNICITY = {
        irish: # 22.8% overall
          Pickup.new(english: 100),
        english: # 10.7% overall
          Pickup.new(english: 100),
        american: # 4.4% overall
          Pickup.new(english: 100),
        scottish: # 2.4% overall
          Pickup.new(english: 100),
        italian: # 13.9% of overall population, 0.72% overall speak italian
          Pickup.new(english: 95,
                     italian: 5),
        french: # 7.8% of overall population, 1.11% overall speak french (split w/ french canadian)
          Pickup.new(english: 99,
                     french: 1),
        french_canadian: # 3.8% of overall population, 1.11% overall speak french (split w/ french)
          Pickup.new(english: 99,
                     french: 1),
        german: # 6.4% of overall population, 0.28% overall speak german
          Pickup.new(english: 96,
                     german: 4),
        polish: # 5.0% overall
          Pickup.new(english: 100),
        portuguese: # 4.7% of overall population, 2.97% overall speak portuguese (split w/ so americans)
          Pickup.new(english: 37,
                     portuguese: 63),
        russian: # 1.9% of overall population, 0.62% overall speak russian
          Pickup.new(english: 62,
                     russian: 38),
        swedish: # 1.8% overall
          Pickup.new(english: 100),
        greek: # 1.2% of overall population, 0.41% overall speak greek
          Pickup.new(english: 66,
                     greek: 34),
        puerto_rican: # 4.1% overall
          Pickup.new(english: 30,
                     spanish: 70),
        mexican: # 1% overall
          Pickup.new(english: 30,
                     spanish: 70),
        central_american: # 1% overall
          Pickup.new(english: 30,
                     spanish: 70),
        south_american: # 1% overall
          Pickup.new(english: 30,
                     spanish: 35,
                     portuguese: 35),
        african: # 1.8% overall
          Pickup.new(english: 95,
                     french: 5),
        dominican: # 1.8% overall
          Pickup.new(english: 30,
                     spanish: 70),
        west_indian: # 1.8% overall, 0.89% speak french creole
          Pickup.new(english: 25,
                     spanish: 35,
                     french_creole: 50),
        chinese: # 2.0% of overall population, 1.59% overall speak chinese
          Pickup.new(english: 25,
                     chinese: 75),
        asian_indian: # 1.1% of overall population, 0.27% overall speak hindi
          Pickup.new(english: 75,
                     hindi: 25),
        american_indian: # 1% overall
          Pickup.new(english: 100),
        arab: # 1% of overall population, 0.37% overall speak arabic
          Pickup.new(english: 63,
                     arabic: 37)
      }.freeze
    end
  end
end
