/*
  # Remove Duplicate SELECT Policies

  1. Issue
    - Multiple permissive SELECT policies exist on teams and markets tables
    - Old policies "Anyone authenticated can view teams/markets" still exist
    - New policies "Users can view all teams/markets" were created but old ones not dropped
    
  2. Fix
    - Drop old duplicate SELECT policies
    - Keep only the consolidated policy for each table
*/

-- Remove old duplicate policies on teams table
DROP POLICY IF EXISTS "Anyone authenticated can view teams" ON teams;

-- Remove old duplicate policies on markets table  
DROP POLICY IF EXISTS "Anyone authenticated can view markets" ON markets;
