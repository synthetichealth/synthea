-- CUSTOM REPORT QUERIES
-- This file contains the queries that will be run, one at a time, to generate custom CSV reports.
-- Each query will produce 1 report.
-- Blank lines and lines starting with -- (single line comments) will be ignored.
-- In this initial implementation, all queries must start with SELECT 
--   (ie, no creation of views or modification of data allowed)
-- and each query must be on its own line, entirely on a single line.
-- See the DataStore class for details on what tables are available.


-- Sample Query 1: select everything from the "person" table.
-- Note that column ordering is not guaranteed.

-- select * from person;



-- Sample Query 2: select the number of living people.

-- select count(*) from person where person.DATE_OF_DEATH is null;



-- Sample Query 3: select the people that have an active diagnosis of diabetes, along with the age of diagnosis

-- select p.name, p.gender, DATEADD('MILLISECOND', p.DATE_OF_BIRTH, DATE '1970-01-01') DOB, DATEADD('MILLISECOND', c.start , DATE '1970-01-01') onset_date, DATEDIFF('YEAR', DATEADD('MILLISECOND', p.DATE_OF_BIRTH, DATE '1970-01-01'), DATEADD('MILLISECOND', c.start, DATE '1970-01-01')) age_at_diagnosis   FROM PERSON p, CONDITION c WHERE p.ID = c.PERSON_ID AND c.code = '44054006';
