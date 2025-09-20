-- V2__ist_timezone_and_types.sql
-- Goal:
--   1) Ensure the database uses Asia/Kolkata by default
--   2) Store timestamps as timestamptz (timezone-aware)
--   3) Convert existing naive timestamps as if they were IST

-- Make the app database default timezone IST for future sessions
ALTER DATABASE appdb SET TIMEZONE TO 'Asia/Kolkata';

-- Convert performance_data.timestamp -> timestamptz
ALTER TABLE performance_data
  ALTER COLUMN timestamp TYPE timestamptz
  USING timezone('Asia/Kolkata', timestamp);

-- Ensure the default uses 'now()' (now is timestamptz and will render in IST)
ALTER TABLE performance_data
  ALTER COLUMN timestamp SET DEFAULT now();

-- Convert computation_results.timestamp -> timestamptz
ALTER TABLE computation_results
  ALTER COLUMN timestamp TYPE timestamptz
  USING timezone('Asia/Kolkata', timestamp);

ALTER TABLE computation_results
  ALTER COLUMN timestamp SET DEFAULT now();
