/*
  # Create Audit Logs Table

  1. New Tables
    - `audit_logs`
      - `id` (uuid, primary key) - Unique identifier for the log entry
      - `user_id` (uuid, foreign key to profiles) - User who performed the action
      - `action` (text) - Type of action (created, updated, deleted)
      - `table_name` (text) - Name of the table affected
      - `record_id` (text) - ID of the record affected
      - `changes` (jsonb) - JSON object containing the changes made
      - `created_at` (timestamptz) - When the action occurred
      
  2. Security
    - Enable RLS on `audit_logs` table
    - Only admins can view audit logs
    - Audit logs are immutable (no updates or deletes allowed except by admins)
    
  3. Indexes
    - Index on user_id for fast user activity lookups
    - Index on table_name for fast table-specific queries
    - Index on created_at for time-based queries
*/

CREATE TABLE IF NOT EXISTS audit_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES profiles(id) ON DELETE SET NULL,
  action text NOT NULL,
  table_name text NOT NULL,
  record_id text,
  changes jsonb,
  created_at timestamptz DEFAULT now()
);

ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Admins can view all audit logs"
  ON audit_logs
  FOR SELECT
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role = 'admin'
    )
  );

CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_table_name ON audit_logs(table_name);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at DESC);
