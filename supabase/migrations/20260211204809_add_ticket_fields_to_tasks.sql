/*
  # Add Ticket Fields to Tasks

  1. Changes
    - Add `ticket_name` column to tasks table for ticket identification
    - Add `ticket_url` column to tasks table for hyperlinked ticket tracking
    
  2. Notes
    - ticket_name: Text field for the ticket name/title
    - ticket_url: Text field for storing ticket hyperlink URLs
    - Both fields are optional and can be null
*/

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'tasks' AND column_name = 'ticket_name'
  ) THEN
    ALTER TABLE tasks ADD COLUMN ticket_name text;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'tasks' AND column_name = 'ticket_url'
  ) THEN
    ALTER TABLE tasks ADD COLUMN ticket_url text;
  END IF;
END $$;
