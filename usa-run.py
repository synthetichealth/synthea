import os
import pandas as pd

# Population size
full_population = 50000

# State populations source:[ https:[//www.infoplease.com/us/states/state-population-by-rank
state_pops = {
	'California':[0.1191],
	'Texas':[0.0874],
	'Florida':[0.0647],
	'New York':[0.0586],
	'Pennsylvania':[0.0386],
	'Illinois':[0.0382],
	'Ohio':[0.0352],
	'Georgia':[0.0320],
	'North Carolina':[0.0316],
	'Michigan':[0.0301],
	'New Jersey':[0.0268],
	'Virginia':[0.0257],
	'Washington':[0.0229],
	'Arizona':[0.0219],
	'Massachusetts':[0.0209],
	'Tennessee':[0.0206],
	'Indiana':[0.0203],
	'Maryland':[0.0185],
	'Missouri':[0.0182],
	'Wisconsin':[0.0175],
	'Colorado':[0.0174],
	'Minnesota':[0.0170],
	'South Carolina':[0.0155],
	'Alabama':[0.0148],
	'Louisiana':[0.0140],
	'Kentucky':[0.0135],
	'Oregon':[0.0127],
	'Oklahoma':[0.0119],
	'Connecticut':[0.0107],
	'Utah':[0.0097],
	'Iowa':[0.0095],
	'Nevada':[0.0093],
	'Arkansas':[0.0091],
	'Mississippi':[0.0090],
	'Kansas':[0.0088],
	'New Mexico':[0.0063],
	'Nebraska':[0.0058],
	'Idaho':[0.0054],
	'West Virginia':[0.0054],
	'Hawaii':[0.0043],
	'New Hampshire':[0.0041],
	'Maine':[0.0041],
	'Rhode Island':[0.0032],
	'Montana':[0.0032],
	'Delaware':[0.0029],
	'South Dakota':[0.0027],
	'North Dakota':[0.0023],
	'Alaska':[0.0022],
	'Vermont':[0.0019],
	'Wyoming':[0.0017]
}
state_pops_df = pd.DataFrame(data=state_pops)

for state, poppct in state_pops_df.iteritems():
	population = str(int(full_population * poppct))
	print("Simulating " + state + " with population " + population + ".")
	os.system("./run_synthea \"" + state + "\" -p " + population)