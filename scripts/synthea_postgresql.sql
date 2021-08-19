CREATE TABLE patients (
	id uuid primary key,
	birthdate date,
	deathdate date,
	ssn text,
	drivers text,
	passport text,
	prefix text,
	first text,
	last text,
	suffix text,
	maiden text,
	marital text,
	race text,
	ethnicity text,
	gender text,
	birthplace text,
	address text,
	city text,
	state text,
	contry text,
	zip text,
	lat numeric,
	lon numeric,
	healthcare_expenses text,
	healthcare_coverage text
);

CREATE TABLE organizations (
	id uuid primary key,
	name text,
	address text,
	city text,
	state text,
	zip text,
	lat numeric,
	lon numeric,
	phone text,
	revenue numeric,
	utilization numeric
);

CREATE TABLE providers (
	id uuid primary key,
	organization uuid references organizations(id),
	name text,
	gender text,
	speciality text,
	address text,
	city text,
	state text,
	zip text,
	lat numeric,
	lon numeric,
	utilization numeric
);

CREATE TABLE payers (
	id uuid primary key,
	name text,
	address text,
	city text,
	state_headquartered text,
	zip text,
	phone text,
	amount_covered numeric,
	amount_uncovered numeric,
	revenue numeric,
	covered_encounters numeric,
	uncovered_encounters numeric,
	covered_medications numeric,
	uncovered_medications numeric,
	covered_procedures numeric,
	uncovered_procedures numeric,
	covered_immunizations numeric,
	uncovered_immunizations numeric,
	unique_customers numeric,
	qols_avg numeric,
	member_months numeric
);

CREATE TABLE encounters (
	id uuid primary key,
	start Date,
	stop Date,
	patient uuid references patients(id),
	organization uuid references organizations(id),
	provider uuid references providers(id),
	payer uuid references payers(id),
	encounterclass text,
	code text,
	description text,
	base_encounter_cost numeric,
	total_claim_cost numeric,
	payer_coverage numeric,
	reasoncode text,
	reasondescription text
);

CREATE TABLE allergies (
	start date,
	stop date,
	patient uuid references patients(id),
	encounter uuid references encounters(id),
	code text,
	description text
);

CREATE TABLE careplans (
	id uuid,
	start date ,
	stop date ,
	patient uuid references patients(id),
	encounter uuid references encounters(id),
	code text,
	description text,
	reasoncode text,
	reasondescription text
);

CREATE TABLE conditions (
	start date,
	stop date,
	patient uuid references patients(id),
	encounter uuid references encounters(id),
	code text,
	description text
);
	
CREATE TABLE devices (
	start date,
	stop date,
	patient uuid references patients(id),
	encounter uuid references encounters(id),
	code text,
	description text,
	udi text
);

CREATE TABLE imaging_studies (
	id uuid,
	date date ,
	patient uuid references patients(id),
	encounter uuid references encounters(id),
	series_uid text,
	bodysitecode text,
	bodysitedescription text,
	modalitycode text,
	modalitydescription text,
	instanceuid text,
	sopcode text,
	sopdescription text,
	procedurecode text
);

CREATE TABLE immunizations (
	date date,
	patient uuid references patients(id),
	encounter uuid references encounters(id),
	code text,
	description text,
	cost numeric
);

CREATE TABLE medications (
	start date,
	stop date,
	patient uuid references patients(id),
	payer uuid references payers(id),
	encounter uuid references encounters(id),
	code text,
	description text,
	basecost numeric,
	payercoverage numeric,
	dispenses numeric,
	totalcost numeric,
	reasoncode text,
	reasondescription text
);

CREATE TABLE observations (
	date date,
	patient uuid references patients(id),
	encounter uuid references encounters(id),
	code text,
	description text,
	value text,
	units text,
	type text
);

CREATE TABLE payer_transitions (
	patient uuid references patients(id),
	start_year integer,
	end_year integer,
	payer uuid references payers(id),
	ownership text
);

CREATE TABLE procedures (
	date date,
	patient uuid references patients(id),
	encounter uuid references encounters(id),
	code text,
	description text,
	basecost numeric,
	reasoncode text,
	reasondescription text
);

CREATE TABLE supplies (
	date date,
	patient uuid references patients(id),
	encounter uuid references encounters(id),
	code text,
	description text,
	quantity numeric
);

