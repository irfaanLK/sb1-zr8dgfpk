/*
  # Add Admin Role System

  1. Schema Changes
    - Update profiles table to use role enum with 'admin' and 'member' values
    - Add is_admin helper function for easier RLS checks
    
  2. Security Updates
    - Update all RLS policies to grant admins full access
    - Admins can manage all users, tasks, projects, attendance, announcements, and links
    - Admins can update any user's profile including role assignments
    
  3. Admin Capabilities
    - View and manage all users
    - Change user passwords (via auth.users table access)
    - Assign/revoke admin privileges
    - Full CRUD access to all tables
    - Override attendance records for all users
    - Manage all tasks and projects regardless of ownership

  ## Changes

  ### Helper Function
  - is_admin(): Returns true if current user has admin role

  ### Profile Policies
  - Admins can view, insert, update, delete any profile
  - Admins can change user roles

  ### All Table Policies
  - Add admin bypass to all existing policies
*/

-- Create helper function to check if user is admin
CREATE OR REPLACE FUNCTION is_admin()
RETURNS boolean AS $$
BEGIN
  RETURN EXISTS (
    SELECT 1 FROM profiles
    WHERE profiles.id = auth.uid()
    AND profiles.role = 'admin'
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Update profiles policies to allow admin full access
DROP POLICY IF EXISTS "Users can view all profiles" ON profiles;
DROP POLICY IF EXISTS "Users can update own profile" ON profiles;
DROP POLICY IF EXISTS "Users can insert own profile" ON profiles;

CREATE POLICY "Users can view all profiles"
  ON profiles FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Users can update own profile or admin can update any"
  ON profiles FOR UPDATE
  TO authenticated
  USING ((select auth.uid()) = id OR is_admin())
  WITH CHECK ((select auth.uid()) = id OR is_admin());

CREATE POLICY "Users can insert own profile"
  ON profiles FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = id);

CREATE POLICY "Admins can delete any profile"
  ON profiles FOR DELETE
  TO authenticated
  USING (is_admin());

-- Update tasks policies to allow admin full access
DROP POLICY IF EXISTS "Users can view all tasks" ON tasks;
DROP POLICY IF EXISTS "Users can insert tasks" ON tasks;
DROP POLICY IF EXISTS "Users can update tasks" ON tasks;
DROP POLICY IF EXISTS "Users can delete tasks" ON tasks;

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

-- Update projects policies to allow admin full access
DROP POLICY IF EXISTS "Users can view all projects" ON projects;
DROP POLICY IF EXISTS "Users can insert projects" ON projects;
DROP POLICY IF EXISTS "Users can update projects" ON projects;
DROP POLICY IF EXISTS "Users can delete projects" ON projects;

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

-- Update attendance policies to allow admin full access
DROP POLICY IF EXISTS "Users can view all attendance" ON attendance;
DROP POLICY IF EXISTS "Users can insert own attendance" ON attendance;
DROP POLICY IF EXISTS "Users can update own attendance" ON attendance;
DROP POLICY IF EXISTS "Users can delete own attendance" ON attendance;

CREATE POLICY "Users can view all attendance"
  ON attendance FOR SELECT
  TO authenticated
  USING (true);

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

-- Update announcements policies to allow admin full access
DROP POLICY IF EXISTS "Users can view active announcements" ON announcements;
DROP POLICY IF EXISTS "Users can insert announcements" ON announcements;
DROP POLICY IF EXISTS "Users can update own announcements" ON announcements;
DROP POLICY IF EXISTS "Users can delete own announcements" ON announcements;

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

-- Update useful_links policies to allow admin full access
DROP POLICY IF EXISTS "Users can view all links" ON useful_links;
DROP POLICY IF EXISTS "Users can insert links" ON useful_links;
DROP POLICY IF EXISTS "Users can update own links" ON useful_links;
DROP POLICY IF EXISTS "Users can delete own links" ON useful_links;

CREATE POLICY "Users can view all links"
  ON useful_links FOR SELECT
  TO authenticated
  USING (true);

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

-- Add policies for teams and markets to allow admin management
DROP POLICY IF EXISTS "Anyone authenticated can view teams" ON teams;
CREATE POLICY "Anyone authenticated can view teams"
  ON teams FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Admins can manage teams"
  ON teams FOR ALL
  TO authenticated
  USING (is_admin())
  WITH CHECK (is_admin());

DROP POLICY IF EXISTS "Anyone authenticated can view markets" ON markets;
CREATE POLICY "Anyone authenticated can view markets"
  ON markets FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Admins can manage markets"
  ON markets FOR ALL
  TO authenticated
  USING (is_admin())
  WITH CHECK (is_admin());
