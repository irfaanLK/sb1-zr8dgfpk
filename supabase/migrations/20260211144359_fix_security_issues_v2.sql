/*
  # Fix Security and Performance Issues

  1. Security Fixes
    - Fix is_admin() function search path to prevent SQL injection attacks
    - Consolidate multiple permissive policies on teams and markets tables
    
  2. Performance Optimization
    - Remove unused indexes that waste storage and slow down writes
    
  ## Changes Made

  ### Security
  - Updated is_admin() function with immutable search path
  - Consolidated SELECT policies for teams and markets (removed redundant permissive policies)
  
  ### Performance
  - Dropped 9 unused indexes:
    - idx_tasks_assignee
    - idx_attendance_user
    - idx_announcements_created_by
    - idx_profiles_team_id
    - idx_projects_market_id
    - idx_projects_team_id
    - idx_tasks_market_id
    - idx_tasks_team_id
    - idx_useful_links_created_by

  ### Notes
  - Auth DB Connection Strategy: Must be changed in Supabase Dashboard → Settings → Database → Connection Pooling
  - Leaked Password Protection: Must be enabled in Supabase Dashboard → Authentication → Settings → Enable password breach detection
*/

-- Fix is_admin() function with secure search path (CASCADE will recreate all dependent policies)
DROP FUNCTION IF EXISTS is_admin() CASCADE;

CREATE OR REPLACE FUNCTION is_admin()
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  RETURN EXISTS (
    SELECT 1 FROM profiles
    WHERE profiles.id = auth.uid()
    AND profiles.role = 'admin'
  );
END;
$$;

-- Recreate all policies that depend on is_admin()

-- Profile policies
CREATE POLICY "Users can update own profile or admin can update any"
  ON profiles FOR UPDATE
  TO authenticated
  USING ((select auth.uid()) = id OR is_admin())
  WITH CHECK ((select auth.uid()) = id OR is_admin());

CREATE POLICY "Admins can delete any profile"
  ON profiles FOR DELETE
  TO authenticated
  USING (is_admin());

-- Task policies
CREATE POLICY "Users can view all tasks"
  ON tasks FOR SELECT
  TO authenticated
  USING (
    is_admin() OR
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

CREATE POLICY "Users can insert tasks"
  ON tasks FOR INSERT
  TO authenticated
  WITH CHECK (
    is_admin() OR
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

CREATE POLICY "Users can update tasks"
  ON tasks FOR UPDATE
  TO authenticated
  USING (
    is_admin() OR
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  )
  WITH CHECK (
    is_admin() OR
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

CREATE POLICY "Users can delete tasks"
  ON tasks FOR DELETE
  TO authenticated
  USING (
    is_admin() OR
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

-- Project policies
CREATE POLICY "Users can view all projects"
  ON projects FOR SELECT
  TO authenticated
  USING (
    is_admin() OR
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

CREATE POLICY "Users can insert projects"
  ON projects FOR INSERT
  TO authenticated
  WITH CHECK (
    is_admin() OR
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

CREATE POLICY "Users can update projects"
  ON projects FOR UPDATE
  TO authenticated
  USING (
    is_admin() OR
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  )
  WITH CHECK (
    is_admin() OR
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

CREATE POLICY "Users can delete projects"
  ON projects FOR DELETE
  TO authenticated
  USING (
    is_admin() OR
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

-- Attendance policies
CREATE POLICY "Users can insert own attendance or admin can insert any"
  ON attendance FOR INSERT
  TO authenticated
  WITH CHECK (user_id = (select auth.uid()) OR is_admin());

CREATE POLICY "Users can update own attendance or admin can update any"
  ON attendance FOR UPDATE
  TO authenticated
  USING (user_id = (select auth.uid()) OR is_admin())
  WITH CHECK (user_id = (select auth.uid()) OR is_admin());

CREATE POLICY "Users can delete own attendance or admin can delete any"
  ON attendance FOR DELETE
  TO authenticated
  USING (user_id = (select auth.uid()) OR is_admin());

-- Announcement policies
CREATE POLICY "Users can view active announcements"
  ON announcements FOR SELECT
  TO authenticated
  USING (is_active = true OR created_by = (select auth.uid()) OR is_admin());

CREATE POLICY "Users can insert announcements"
  ON announcements FOR INSERT
  TO authenticated
  WITH CHECK (created_by = (select auth.uid()) OR is_admin());

CREATE POLICY "Users can update own announcements or admin can update any"
  ON announcements FOR UPDATE
  TO authenticated
  USING (created_by = (select auth.uid()) OR is_admin())
  WITH CHECK (created_by = (select auth.uid()) OR is_admin());

CREATE POLICY "Users can delete own announcements or admin can delete any"
  ON announcements FOR DELETE
  TO authenticated
  USING (created_by = (select auth.uid()) OR is_admin());

-- Useful links policies
CREATE POLICY "Users can insert links"
  ON useful_links FOR INSERT
  TO authenticated
  WITH CHECK (created_by = (select auth.uid()) OR is_admin());

CREATE POLICY "Users can update own links or admin can update any"
  ON useful_links FOR UPDATE
  TO authenticated
  USING (created_by = (select auth.uid()) OR is_admin())
  WITH CHECK (created_by = (select auth.uid()) OR is_admin());

CREATE POLICY "Users can delete own links or admin can delete any"
  ON useful_links FOR DELETE
  TO authenticated
  USING (created_by = (select auth.uid()) OR is_admin());

-- Team policies (consolidated - no more multiple permissive policies)
CREATE POLICY "Users can view all teams"
  ON teams FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Admins can insert teams"
  ON teams FOR INSERT
  TO authenticated
  WITH CHECK (is_admin());

CREATE POLICY "Admins can update teams"
  ON teams FOR UPDATE
  TO authenticated
  USING (is_admin())
  WITH CHECK (is_admin());

CREATE POLICY "Admins can delete teams"
  ON teams FOR DELETE
  TO authenticated
  USING (is_admin());

-- Market policies (consolidated - no more multiple permissive policies)
CREATE POLICY "Users can view all markets"
  ON markets FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Admins can insert markets"
  ON markets FOR INSERT
  TO authenticated
  WITH CHECK (is_admin());

CREATE POLICY "Admins can update markets"
  ON markets FOR UPDATE
  TO authenticated
  USING (is_admin())
  WITH CHECK (is_admin());

CREATE POLICY "Admins can delete markets"
  ON markets FOR DELETE
  TO authenticated
  USING (is_admin());

-- Drop unused indexes to improve write performance and reduce storage
DROP INDEX IF EXISTS idx_tasks_assignee;
DROP INDEX IF EXISTS idx_attendance_user;
DROP INDEX IF EXISTS idx_announcements_created_by;
DROP INDEX IF EXISTS idx_profiles_team_id;
DROP INDEX IF EXISTS idx_projects_market_id;
DROP INDEX IF EXISTS idx_projects_team_id;
DROP INDEX IF EXISTS idx_tasks_market_id;
DROP INDEX IF EXISTS idx_tasks_team_id;
DROP INDEX IF EXISTS idx_useful_links_created_by;
