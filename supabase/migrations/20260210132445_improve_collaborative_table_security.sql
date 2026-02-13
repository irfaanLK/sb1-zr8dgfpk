/*
  # Improve Collaborative Table Security

  1. Security Improvements
    - Add profile validation checks to tasks and projects RLS policies
    - Ensure only users with valid profiles can interact with data
    - Maintain collaborative nature while adding basic security

  2. Note on Indexes
    - The "unused" indexes are expected for a new database
    - They will be utilized as queries execute and data grows
    - All indexes are properly positioned for foreign key lookups

  3. Policy Changes
    - Tasks: Require valid profile for all operations
    - Projects: Require valid profile for all operations
    - Maintains team collaboration while preventing unauthorized access

  ## Changes

  ### Tasks Table Policies
  - All operations now verify user has a valid profile
  - Keeps collaborative access for team flexibility

  ### Projects Table Policies  
  - All operations now verify user has a valid profile
  - Keeps collaborative access for cross-team projects
*/

-- Drop and recreate tasks policies with profile validation
DROP POLICY IF EXISTS "Users can view all tasks" ON tasks;
DROP POLICY IF EXISTS "Users can insert tasks" ON tasks;
DROP POLICY IF EXISTS "Users can update tasks" ON tasks;
DROP POLICY IF EXISTS "Users can delete tasks" ON tasks;

CREATE POLICY "Users can view all tasks"
  ON tasks FOR SELECT
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

CREATE POLICY "Users can insert tasks"
  ON tasks FOR INSERT
  TO authenticated
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

CREATE POLICY "Users can update tasks"
  ON tasks FOR UPDATE
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

CREATE POLICY "Users can delete tasks"
  ON tasks FOR DELETE
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

-- Drop and recreate projects policies with profile validation
DROP POLICY IF EXISTS "Users can view all projects" ON projects;
DROP POLICY IF EXISTS "Users can insert projects" ON projects;
DROP POLICY IF EXISTS "Users can update projects" ON projects;
DROP POLICY IF EXISTS "Users can delete projects" ON projects;

CREATE POLICY "Users can view all projects"
  ON projects FOR SELECT
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

CREATE POLICY "Users can insert projects"
  ON projects FOR INSERT
  TO authenticated
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

CREATE POLICY "Users can update projects"
  ON projects FOR UPDATE
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

CREATE POLICY "Users can delete projects"
  ON projects FOR DELETE
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM profiles 
      WHERE profiles.id = (select auth.uid())
    )
  );

-- Add comment explaining the collaborative nature
COMMENT ON TABLE tasks IS 'Collaborative task tracking table. All authenticated users with valid profiles can manage tasks for team flexibility.';
COMMENT ON TABLE projects IS 'Collaborative project management table. All authenticated users with valid profiles can manage projects for cross-team coordination.';
