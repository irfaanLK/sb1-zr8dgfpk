/*
  # Fix Security and Performance Issues

  1. Performance Improvements
    - Add missing indexes on foreign key columns for better query performance
    - Optimize RLS policies to use (select auth.uid()) to avoid re-evaluation per row

  2. Security Improvements
    - Fix overly permissive RLS policies on attendance table
    - Restrict attendance management to user's own records only
    - Keep collaborative access for tasks and projects (internal team tool)

  ## Changes

  ### Indexes Added
  - announcements.created_by
  - profiles.team_id
  - projects.market_id, team_id
  - tasks.market_id, team_id
  - useful_links.created_by

  ### RLS Policies Updated
  - Optimized auth function calls with SELECT wrapper
  - Restricted attendance policies to own records only
*/

-- Add missing indexes on foreign keys
CREATE INDEX IF NOT EXISTS idx_announcements_created_by ON announcements(created_by);
CREATE INDEX IF NOT EXISTS idx_profiles_team_id ON profiles(team_id);
CREATE INDEX IF NOT EXISTS idx_projects_market_id ON projects(market_id);
CREATE INDEX IF NOT EXISTS idx_projects_team_id ON projects(team_id);
CREATE INDEX IF NOT EXISTS idx_tasks_market_id ON tasks(market_id);
CREATE INDEX IF NOT EXISTS idx_tasks_team_id ON tasks(team_id);
CREATE INDEX IF NOT EXISTS idx_useful_links_created_by ON useful_links(created_by);

-- Drop and recreate profiles policies with optimized auth calls
DROP POLICY IF EXISTS "Users can update own profile" ON profiles;
DROP POLICY IF EXISTS "Users can insert own profile" ON profiles;

CREATE POLICY "Users can update own profile"
  ON profiles FOR UPDATE
  TO authenticated
  USING ((select auth.uid()) = id)
  WITH CHECK ((select auth.uid()) = id);

CREATE POLICY "Users can insert own profile"
  ON profiles FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = id);

-- Drop and recreate announcements policies with optimized auth calls
DROP POLICY IF EXISTS "Users can view active announcements" ON announcements;
DROP POLICY IF EXISTS "Users can insert announcements" ON announcements;
DROP POLICY IF EXISTS "Users can update own announcements" ON announcements;
DROP POLICY IF EXISTS "Users can delete own announcements" ON announcements;

CREATE POLICY "Users can view active announcements"
  ON announcements FOR SELECT
  TO authenticated
  USING (is_active = true OR created_by = (select auth.uid()));

CREATE POLICY "Users can insert announcements"
  ON announcements FOR INSERT
  TO authenticated
  WITH CHECK (created_by = (select auth.uid()));

CREATE POLICY "Users can update own announcements"
  ON announcements FOR UPDATE
  TO authenticated
  USING (created_by = (select auth.uid()))
  WITH CHECK (created_by = (select auth.uid()));

CREATE POLICY "Users can delete own announcements"
  ON announcements FOR DELETE
  TO authenticated
  USING (created_by = (select auth.uid()));

-- Drop and recreate useful_links policies with optimized auth calls
DROP POLICY IF EXISTS "Users can insert links" ON useful_links;
DROP POLICY IF EXISTS "Users can update own links" ON useful_links;
DROP POLICY IF EXISTS "Users can delete own links" ON useful_links;

CREATE POLICY "Users can insert links"
  ON useful_links FOR INSERT
  TO authenticated
  WITH CHECK (created_by = (select auth.uid()));

CREATE POLICY "Users can update own links"
  ON useful_links FOR UPDATE
  TO authenticated
  USING (created_by = (select auth.uid()))
  WITH CHECK (created_by = (select auth.uid()));

CREATE POLICY "Users can delete own links"
  ON useful_links FOR DELETE
  TO authenticated
  USING (created_by = (select auth.uid()));

-- Fix attendance policies - restrict to own records only
DROP POLICY IF EXISTS "Users can view all attendance" ON attendance;
DROP POLICY IF EXISTS "Users can insert attendance" ON attendance;
DROP POLICY IF EXISTS "Users can update attendance" ON attendance;
DROP POLICY IF EXISTS "Users can delete attendance" ON attendance;

CREATE POLICY "Users can view all attendance"
  ON attendance FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Users can insert own attendance"
  ON attendance FOR INSERT
  TO authenticated
  WITH CHECK (user_id = (select auth.uid()));

CREATE POLICY "Users can update own attendance"
  ON attendance FOR UPDATE
  TO authenticated
  USING (user_id = (select auth.uid()))
  WITH CHECK (user_id = (select auth.uid()));

CREATE POLICY "Users can delete own attendance"
  ON attendance FOR DELETE
  TO authenticated
  USING (user_id = (select auth.uid()));
