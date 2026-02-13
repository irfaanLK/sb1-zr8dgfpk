import { createClient } from '@supabase/supabase-js';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY;

if (!supabaseUrl || !supabaseAnonKey) {
  throw new Error('Missing Supabase environment variables');
}

export const supabase = createClient(supabaseUrl, supabaseAnonKey);

export interface Profile {
  id: string;
  full_name: string;
  email: string;
  team_id: string | null;
  role: string;
  created_at: string;
  updated_at: string;
}

export interface Team {
  id: string;
  name: string;
  description: string;
  created_at: string;
}

export interface Market {
  id: string;
  name: string;
  created_at: string;
}

export interface Task {
  id: string;
  task_date: string;
  project_name: string;
  priority: string;
  market_id: string | null;
  status: string;
  assignee_id: string | null;
  team_id: string | null;
  hub_effort: string;
  start_time: string | null;
  end_time: string | null;
  total_hours: number;
  comments: string;
  ticket_name: string | null;
  ticket_url: string | null;
  created_at: string;
  updated_at: string;
}

export interface Attendance {
  id: string;
  user_id: string;
  attendance_date: string;
  status: string;
  created_at: string;
  updated_at: string;
}

export interface Announcement {
  id: string;
  title: string;
  content: string;
  priority: string;
  created_by: string | null;
  is_active: boolean;
  created_at: string;
  updated_at: string;
}

export interface UsefulLink {
  id: string;
  title: string;
  url: string;
  description: string;
  category: string;
  created_by: string | null;
  created_at: string;
}

export interface Project {
  id: string;
  name: string;
  description: string;
  start_date: string | null;
  end_date: string | null;
  team_id: string | null;
  market_id: string | null;
  status: string;
  assigned_to: string[];
  milestones: any;
  created_at: string;
  updated_at: string;
}

export interface HomepageTimezone {
  id: string;
  label: string;
  timezone: string;
  display_order: number;
  is_active: boolean;
  created_at: string;
  updated_at: string;
}

export interface AuditLog {
  id: string;
  user_id: string | null;
  action: string;
  table_name: string;
  record_id: string | null;
  changes: any;
  created_at: string;
}

export async function logAudit(
  action: string,
  tableName: string,
  recordId?: string,
  changes?: any
) {
  const { data: { user } } = await supabase.auth.getUser();

  if (user) {
    await supabase.from('audit_logs').insert({
      user_id: user.id,
      action,
      table_name: tableName,
      record_id: recordId,
      changes: changes || {}
    });
  }
}
