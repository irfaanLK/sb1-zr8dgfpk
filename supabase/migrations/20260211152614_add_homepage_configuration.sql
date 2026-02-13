/*
  # Add Homepage Configuration Tables

  1. New Tables
    - `homepage_timezones`
      - `id` (uuid, primary key)
      - `label` (text) - Display name for the timezone
      - `timezone` (text) - IANA timezone identifier
      - `display_order` (integer) - Order in which to display the timezone
      - `is_active` (boolean) - Whether the timezone should be shown
      - `created_at` (timestamptz)
      - `updated_at` (timestamptz)

  2. Security
    - Enable RLS on `homepage_timezones` table
    - Add policy for all authenticated users to read timezones
    - Add policy for admin users to manage timezones

  3. Initial Data
    - Insert default timezones (Mauritius, BST, CET)
*/

CREATE TABLE IF NOT EXISTS homepage_timezones (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  label text NOT NULL,
  timezone text NOT NULL,
  display_order integer NOT NULL DEFAULT 0,
  is_active boolean DEFAULT true,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now()
);

ALTER TABLE homepage_timezones ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Anyone can view active timezones"
  ON homepage_timezones
  FOR SELECT
  TO authenticated
  USING (is_active = true);

CREATE POLICY "Admins can insert timezones"
  ON homepage_timezones
  FOR INSERT
  TO authenticated
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role = 'admin'
    )
  );

CREATE POLICY "Admins can update timezones"
  ON homepage_timezones
  FOR UPDATE
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role = 'admin'
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role = 'admin'
    )
  );

CREATE POLICY "Admins can delete timezones"
  ON homepage_timezones
  FOR DELETE
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role = 'admin'
    )
  );

INSERT INTO homepage_timezones (label, timezone, display_order, is_active)
VALUES
  ('Mauritius', 'Indian/Mauritius', 1, true),
  ('BST', 'Europe/London', 2, true),
  ('CET', 'Europe/Paris', 3, true)
ON CONFLICT DO NOTHING;
