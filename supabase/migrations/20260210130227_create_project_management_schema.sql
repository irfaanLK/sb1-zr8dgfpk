/*
  # Create Project Management and Attendance Tracking System Schema

  1. New Tables
    - `profiles`
      - `id` (uuid, references auth.users)
      - `full_name` (text)
      - `email` (text)
      - `team_id` (uuid, references teams)
      - `role` (text)
      - `created_at` (timestamptz)
      - `updated_at` (timestamptz)
    
    - `teams`
      - `id` (uuid, primary key)
      - `name` (text) - e.g., "Team A", "Team B"
      - `description` (text)
      - `created_at` (timestamptz)
    
    - `markets`
      - `id` (uuid, primary key)
      - `name` (text) - e.g., "Benelux", "UK/ES/CH", "CEE", "ROS"
      - `created_at` (timestamptz)
    
    - `tasks`
      - `id` (uuid, primary key)
      - `task_date` (date) - the date this task is logged for
      - `project_name` (text)
      - `priority` (text) - P1, P2, P3
      - `market_id` (uuid, references markets)
      - `status` (text)
      - `assignee_id` (uuid, references profiles)
      - `team_id` (uuid, references teams)
      - `hub_effort` (text) - Low, Medium, High, V.High
      - `start_time` (time)
      - `end_time` (time)
      - `total_hours` (decimal)
      - `comments` (text)
      - `created_at` (timestamptz)
      - `updated_at` (timestamptz)
    
    - `attendance`
      - `id` (uuid, primary key)
      - `user_id` (uuid, references profiles)
      - `attendance_date` (date)
      - `status` (text) - Office, WFH, Sick, Local Leave, Business Trip/Training, Half-Day
      - `created_at` (timestamptz)
      - `updated_at` (timestamptz)
    
    - `announcements`
      - `id` (uuid, primary key)
      - `title` (text)
      - `content` (text)
      - `priority` (text) - High, Medium, Low
      - `created_by` (uuid, references profiles)
      - `is_active` (boolean)
      - `created_at` (timestamptz)
      - `updated_at` (timestamptz)
    
    - `useful_links`
      - `id` (uuid, primary key)
      - `title` (text)
      - `url` (text)
      - `description` (text)
      - `category` (text)
      - `created_by` (uuid, references profiles)
      - `created_at` (timestamptz)
    
    - `projects`
      - `id` (uuid, primary key)
      - `name` (text)
      - `description` (text)
      - `start_date` (date)
      - `end_date` (date)
      - `team_id` (uuid, references teams)
      - `market_id` (uuid, references markets)
      - `status` (text)
      - `assigned_to` (uuid[], array of user ids)
      - `milestones` (jsonb)
      - `created_at` (timestamptz)
      - `updated_at` (timestamptz)

  2. Security
    - Enable RLS on all tables
    - Add policies for authenticated users to manage their own data
    - Add policies for viewing team data
*/

-- Create teams table
CREATE TABLE IF NOT EXISTS teams (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  description text DEFAULT '',
  created_at timestamptz DEFAULT now()
);

-- Create markets table
CREATE TABLE IF NOT EXISTS markets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL UNIQUE,
  created_at timestamptz DEFAULT now()
);

-- Create profiles table
CREATE TABLE IF NOT EXISTS profiles (
  id uuid PRIMARY KEY REFERENCES auth.users ON DELETE CASCADE,
  full_name text NOT NULL,
  email text UNIQUE NOT NULL,
  team_id uuid REFERENCES teams(id),
  role text DEFAULT 'member',
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now()
);

-- Create tasks table
CREATE TABLE IF NOT EXISTS tasks (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  task_date date NOT NULL DEFAULT CURRENT_DATE,
  project_name text NOT NULL,
  priority text NOT NULL DEFAULT 'P3',
  market_id uuid REFERENCES markets(id),
  status text NOT NULL DEFAULT 'Backlog',
  assignee_id uuid REFERENCES profiles(id),
  team_id uuid REFERENCES teams(id),
  hub_effort text DEFAULT 'Medium',
  start_time time,
  end_time time,
  total_hours decimal(5,2) DEFAULT 0,
  comments text DEFAULT '',
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now()
);

-- Create attendance table
CREATE TABLE IF NOT EXISTS attendance (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES profiles(id) NOT NULL,
  attendance_date date NOT NULL,
  status text NOT NULL DEFAULT 'Office',
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now(),
  UNIQUE(user_id, attendance_date)
);

-- Create announcements table
CREATE TABLE IF NOT EXISTS announcements (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  title text NOT NULL,
  content text NOT NULL,
  priority text DEFAULT 'Medium',
  created_by uuid REFERENCES profiles(id),
  is_active boolean DEFAULT true,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now()
);

-- Create useful_links table
CREATE TABLE IF NOT EXISTS useful_links (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  title text NOT NULL,
  url text NOT NULL,
  description text DEFAULT '',
  category text DEFAULT 'General',
  created_by uuid REFERENCES profiles(id),
  created_at timestamptz DEFAULT now()
);

-- Create projects table
CREATE TABLE IF NOT EXISTS projects (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  description text DEFAULT '',
  start_date date,
  end_date date,
  team_id uuid REFERENCES teams(id),
  market_id uuid REFERENCES markets(id),
  status text DEFAULT 'Planning',
  assigned_to uuid[],
  milestones jsonb DEFAULT '[]'::jsonb,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now()
);

-- Enable RLS
ALTER TABLE teams ENABLE ROW LEVEL SECURITY;
ALTER TABLE markets ENABLE ROW LEVEL SECURITY;
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance ENABLE ROW LEVEL SECURITY;
ALTER TABLE announcements ENABLE ROW LEVEL SECURITY;
ALTER TABLE useful_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;

-- Policies for teams
CREATE POLICY "Anyone authenticated can view teams"
  ON teams FOR SELECT
  TO authenticated
  USING (true);

-- Policies for markets
CREATE POLICY "Anyone authenticated can view markets"
  ON markets FOR SELECT
  TO authenticated
  USING (true);

-- Policies for profiles
CREATE POLICY "Users can view all profiles"
  ON profiles FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Users can update own profile"
  ON profiles FOR UPDATE
  TO authenticated
  USING (auth.uid() = id)
  WITH CHECK (auth.uid() = id);

CREATE POLICY "Users can insert own profile"
  ON profiles FOR INSERT
  TO authenticated
  WITH CHECK (auth.uid() = id);

-- Policies for tasks
CREATE POLICY "Users can view all tasks"
  ON tasks FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Users can insert tasks"
  ON tasks FOR INSERT
  TO authenticated
  WITH CHECK (true);

CREATE POLICY "Users can update tasks"
  ON tasks FOR UPDATE
  TO authenticated
  USING (true)
  WITH CHECK (true);

CREATE POLICY "Users can delete tasks"
  ON tasks FOR DELETE
  TO authenticated
  USING (true);

-- Policies for attendance
CREATE POLICY "Users can view all attendance"
  ON attendance FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Users can insert attendance"
  ON attendance FOR INSERT
  TO authenticated
  WITH CHECK (true);

CREATE POLICY "Users can update attendance"
  ON attendance FOR UPDATE
  TO authenticated
  USING (true)
  WITH CHECK (true);

CREATE POLICY "Users can delete attendance"
  ON attendance FOR DELETE
  TO authenticated
  USING (true);

-- Policies for announcements
CREATE POLICY "Users can view active announcements"
  ON announcements FOR SELECT
  TO authenticated
  USING (is_active = true OR created_by = auth.uid());

CREATE POLICY "Users can insert announcements"
  ON announcements FOR INSERT
  TO authenticated
  WITH CHECK (created_by = auth.uid());

CREATE POLICY "Users can update own announcements"
  ON announcements FOR UPDATE
  TO authenticated
  USING (created_by = auth.uid())
  WITH CHECK (created_by = auth.uid());

CREATE POLICY "Users can delete own announcements"
  ON announcements FOR DELETE
  TO authenticated
  USING (created_by = auth.uid());

-- Policies for useful_links
CREATE POLICY "Users can view all links"
  ON useful_links FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Users can insert links"
  ON useful_links FOR INSERT
  TO authenticated
  WITH CHECK (created_by = auth.uid());

CREATE POLICY "Users can update own links"
  ON useful_links FOR UPDATE
  TO authenticated
  USING (created_by = auth.uid())
  WITH CHECK (created_by = auth.uid());

CREATE POLICY "Users can delete own links"
  ON useful_links FOR DELETE
  TO authenticated
  USING (created_by = auth.uid());

-- Policies for projects
CREATE POLICY "Users can view all projects"
  ON projects FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Users can insert projects"
  ON projects FOR INSERT
  TO authenticated
  WITH CHECK (true);

CREATE POLICY "Users can update projects"
  ON projects FOR UPDATE
  TO authenticated
  USING (true)
  WITH CHECK (true);

CREATE POLICY "Users can delete projects"
  ON projects FOR DELETE
  TO authenticated
  USING (true);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_tasks_date ON tasks(task_date);
CREATE INDEX IF NOT EXISTS idx_tasks_assignee ON tasks(assignee_id);
CREATE INDEX IF NOT EXISTS idx_attendance_date ON attendance(attendance_date);
CREATE INDEX IF NOT EXISTS idx_attendance_user ON attendance(user_id);

-- Insert default teams
INSERT INTO teams (name, description) VALUES
  ('Team Alpha', 'Primary development team'),
  ('Team Beta', 'Secondary development team'),
  ('Team Gamma', 'Design and UX team'),
  ('Team Delta', 'QA and Testing team'),
  ('Team Omega', 'Operations team')
ON CONFLICT DO NOTHING;

-- Insert default markets
INSERT INTO markets (name) VALUES
  ('Benelux'),
  ('UK/ES/CH'),
  ('CEE'),
  ('ROS'),
  ('DACH'),
  ('Nordics'),
  ('Southern Europe')
ON CONFLICT (name) DO NOTHING;