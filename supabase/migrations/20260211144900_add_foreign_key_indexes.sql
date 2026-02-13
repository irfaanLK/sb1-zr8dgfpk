/*
  # Add Indexes for Foreign Keys

  1. Issue
    - Multiple foreign keys lack covering indexes
    - This causes suboptimal query performance on JOIN operations
    
  2. Solution
    - Add indexes for all foreign key columns:
      - announcements.created_by
      - profiles.team_id
      - projects.market_id & team_id
      - tasks.assignee_id, market_id & team_id
      - useful_links.created_by
      
  3. Performance Impact
    - Improves JOIN performance
    - Speeds up queries filtering by these foreign keys
    - Optimizes referential integrity checks
*/

-- Index for announcements foreign keys
CREATE INDEX IF NOT EXISTS idx_announcements_created_by 
ON announcements(created_by);

-- Index for profiles foreign keys
CREATE INDEX IF NOT EXISTS idx_profiles_team_id 
ON profiles(team_id);

-- Indexes for projects foreign keys
CREATE INDEX IF NOT EXISTS idx_projects_market_id 
ON projects(market_id);

CREATE INDEX IF NOT EXISTS idx_projects_team_id 
ON projects(team_id);

-- Indexes for tasks foreign keys
CREATE INDEX IF NOT EXISTS idx_tasks_assignee_id 
ON tasks(assignee_id);

CREATE INDEX IF NOT EXISTS idx_tasks_market_id 
ON tasks(market_id);

CREATE INDEX IF NOT EXISTS idx_tasks_team_id 
ON tasks(team_id);

-- Index for useful_links foreign keys
CREATE INDEX IF NOT EXISTS idx_useful_links_created_by 
ON useful_links(created_by);
