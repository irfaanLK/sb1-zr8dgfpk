import { useState, useEffect } from 'react';
import { Plus, Edit2, Trash2, Users, Calendar, AlertCircle, CheckCircle2 } from 'lucide-react';
import { supabase, Project, Profile, Team, Market, Attendance as AttendanceType } from '../lib/supabase';

export default function Planning() {
  const [projects, setProjects] = useState<(Project & { team?: Team; market?: Market })[]>([]);
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [markets, setMarkets] = useState<Market[]>([]);
  const [attendance, setAttendance] = useState<AttendanceType[]>([]);
  const [showAddForm, setShowAddForm] = useState(false);
  const [editingProject, setEditingProject] = useState<string | null>(null);
  const [view, setView] = useState<'projects' | 'resources'>('projects');

  useEffect(() => {
    loadProjects();
    loadProfiles();
    loadTeams();
    loadMarkets();
    loadAttendance();
  }, []);

  async function loadProjects() {
    const { data, error } = await supabase
      .from('projects')
      .select(`
        *,
        team:teams(*),
        market:markets(*)
      `)
      .order('created_at', { ascending: false });

    if (!error && data) {
      setProjects(data);
    }
  }

  async function loadProfiles() {
    const { data, error } = await supabase
      .from('profiles')
      .select('*')
      .order('full_name');

    if (!error && data) {
      setProfiles(data);
    }
  }

  async function loadTeams() {
    const { data, error } = await supabase
      .from('teams')
      .select('*')
      .order('name');

    if (!error && data) {
      setTeams(data);
    }
  }

  async function loadMarkets() {
    const { data, error } = await supabase
      .from('markets')
      .select('*')
      .order('name');

    if (!error && data) {
      setMarkets(data);
    }
  }

  async function loadAttendance() {
    const today = new Date();
    const thirtyDaysAgo = new Date(today);
    thirtyDaysAgo.setDate(today.getDate() - 30);

    const { data, error } = await supabase
      .from('attendance')
      .select('*')
      .gte('attendance_date', thirtyDaysAgo.toISOString().split('T')[0])
      .lte('attendance_date', today.toISOString().split('T')[0]);

    if (!error && data) {
      setAttendance(data);
    }
  }

  async function addProject(projectData: Partial<Project>) {
    const { error } = await supabase
      .from('projects')
      .insert([projectData]);

    if (!error) {
      loadProjects();
      setShowAddForm(false);
    }
  }

  async function updateProject(id: string, projectData: Partial<Project>) {
    const { error } = await supabase
      .from('projects')
      .update({ ...projectData, updated_at: new Date().toISOString() })
      .eq('id', id);

    if (!error) {
      loadProjects();
      setEditingProject(null);
    }
  }

  async function deleteProject(id: string) {
    const { error } = await supabase
      .from('projects')
      .delete()
      .eq('id', id);

    if (!error) {
      loadProjects();
    }
  }

  function getResourceAvailability(userId: string): {
    available: boolean;
    reason: string;
    activeProjects: number;
  } {
    const userAttendance = attendance.filter((a) => a.user_id === userId);
    const recentLeave = userAttendance.filter(
      (a) => a.status === 'Sick' || a.status === 'Local Leave'
    );

    const activeProjects = projects.filter(
      (p) => p.assigned_to?.includes(userId) && p.status !== 'Completed'
    );

    if (recentLeave.length > 5) {
      return {
        available: false,
        reason: 'On extended leave',
        activeProjects: activeProjects.length,
      };
    }

    if (activeProjects.length >= 5) {
      return {
        available: false,
        reason: 'Overloaded (5+ projects)',
        activeProjects: activeProjects.length,
      };
    }

    return {
      available: true,
      reason: 'Available',
      activeProjects: activeProjects.length,
    };
  }

  function getUpcomingDeadlines() {
    const today = new Date();
    const thirtyDaysFromNow = new Date(today);
    thirtyDaysFromNow.setDate(today.getDate() + 30);

    return projects
      .filter((p) => {
        if (!p.end_date || p.status === 'Completed') return false;
        const endDate = new Date(p.end_date);
        return endDate >= today && endDate <= thirtyDaysFromNow;
      })
      .sort((a, b) => {
        const dateA = new Date(a.end_date!);
        const dateB = new Date(b.end_date!);
        return dateA.getTime() - dateB.getTime();
      });
  }

  const upcomingDeadlines = getUpcomingDeadlines();

  return (
    <div className="p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-3xl font-bold text-gray-900">Planning</h1>
        <div className="flex gap-4">
          <select
            value={view}
            onChange={(e) => setView(e.target.value as any)}
            className="px-3 py-2 border border-gray-300 rounded-md"
          >
            <option value="projects">Projects</option>
            <option value="resources">Resource Allocation</option>
          </select>
          {view === 'projects' && (
            <button
              onClick={() => setShowAddForm(true)}
              className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
            >
              <Plus size={20} />
              Add Project
            </button>
          )}
        </div>
      </div>

      {upcomingDeadlines.length > 0 && (
        <div className="mb-6 bg-yellow-50 border border-yellow-200 rounded-lg p-4">
          <div className="flex items-center gap-2 mb-3">
            <AlertCircle className="text-yellow-600" size={20} />
            <h3 className="font-semibold text-yellow-900">Upcoming Deadlines</h3>
          </div>
          <div className="space-y-2">
            {upcomingDeadlines.slice(0, 5).map((project) => (
              <div key={project.id} className="flex items-center justify-between text-sm">
                <span className="text-gray-900">{project.name}</span>
                <span className="text-gray-600">
                  Due: {new Date(project.end_date!).toLocaleDateString()}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {view === 'projects' ? (
        <>
          {showAddForm && (
            <div className="mb-6 bg-white rounded-lg shadow-sm border border-gray-200 p-6">
              <ProjectForm
                profiles={profiles}
                teams={teams}
                markets={markets}
                onSubmit={addProject}
                onCancel={() => setShowAddForm(false)}
              />
            </div>
          )}

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {projects.length === 0 ? (
              <div className="col-span-2 bg-white rounded-lg shadow-sm border border-gray-200 p-12 text-center">
                <p className="text-gray-500">No projects yet. Create your first project to get started.</p>
              </div>
            ) : (
              projects.map((project) => (
                <div
                  key={project.id}
                  className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 hover:shadow-md transition-shadow"
                >
                  <div className="flex items-start justify-between mb-4">
                    <div className="flex-1">
                      <h3 className="text-lg font-semibold text-gray-900 mb-1">{project.name}</h3>
                      <p className="text-sm text-gray-600">{project.description}</p>
                    </div>
                    <div className="flex gap-2">
                      <button
                        onClick={() => setEditingProject(project.id)}
                        className="text-blue-600 hover:text-blue-700"
                      >
                        <Edit2 size={16} />
                      </button>
                      <button
                        onClick={() => deleteProject(project.id)}
                        className="text-red-600 hover:text-red-700"
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </div>

                  <div className="space-y-2 mb-4">
                    <div className="flex items-center gap-2 text-sm">
                      <Calendar size={16} className="text-gray-400" />
                      <span className="text-gray-600">
                        {project.start_date
                          ? new Date(project.start_date).toLocaleDateString()
                          : 'Not set'}{' '}
                        -{' '}
                        {project.end_date
                          ? new Date(project.end_date).toLocaleDateString()
                          : 'Not set'}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 text-sm">
                      <Users size={16} className="text-gray-400" />
                      <span className="text-gray-600">
                        Team: {project.team?.name || 'Not assigned'}
                      </span>
                    </div>
                    <div className="text-sm text-gray-600">
                      Market: {project.market?.name || 'Not assigned'}
                    </div>
                  </div>

                  <div className="mb-4">
                    <span
                      className={`px-3 py-1 rounded-full text-xs font-medium ${
                        project.status === 'Completed'
                          ? 'bg-green-100 text-green-800'
                          : project.status === 'In Progress'
                          ? 'bg-blue-100 text-blue-800'
                          : project.status === 'On Hold'
                          ? 'bg-red-100 text-red-800'
                          : 'bg-gray-100 text-gray-800'
                      }`}
                    >
                      {project.status}
                    </span>
                  </div>

                  {project.assigned_to && project.assigned_to.length > 0 && (
                    <div>
                      <p className="text-xs font-medium text-gray-600 mb-2">Assigned to:</p>
                      <div className="flex flex-wrap gap-2">
                        {project.assigned_to.map((userId) => {
                          const user = profiles.find((p) => p.id === userId);
                          return user ? (
                            <span
                              key={userId}
                              className="px-2 py-1 bg-gray-100 text-gray-700 rounded text-xs"
                            >
                              {user.full_name}
                            </span>
                          ) : null;
                        })}
                      </div>
                    </div>
                  )}
                </div>
              ))
            )}
          </div>

          {editingProject && (
            <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
              <div className="bg-white rounded-lg shadow-xl max-w-4xl w-full max-h-[90vh] overflow-y-auto p-6">
                <div className="flex items-center justify-between mb-4">
                  <h2 className="text-xl font-semibold">Edit Project</h2>
                  <button
                    onClick={() => setEditingProject(null)}
                    className="text-gray-400 hover:text-gray-600"
                  >
                    ×
                  </button>
                </div>
                <ProjectForm
                  profiles={profiles}
                  teams={teams}
                  markets={markets}
                  initialData={projects.find((p) => p.id === editingProject)}
                  onSubmit={(data) => updateProject(editingProject, data)}
                  onCancel={() => setEditingProject(null)}
                />
              </div>
            </div>
          )}
        </>
      ) : (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <h2 className="text-xl font-semibold text-gray-900 mb-6">Resource Allocation</h2>
          <div className="space-y-4">
            {profiles.map((profile) => {
              const availability = getResourceAvailability(profile.id);
              return (
                <div
                  key={profile.id}
                  className={`p-4 rounded-lg border-2 ${
                    availability.available
                      ? 'border-green-200 bg-green-50'
                      : 'border-red-200 bg-red-50'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      {availability.available ? (
                        <CheckCircle2 className="text-green-600" size={24} />
                      ) : (
                        <AlertCircle className="text-red-600" size={24} />
                      )}
                      <div>
                        <h3 className="font-semibold text-gray-900">{profile.full_name}</h3>
                        <p className="text-sm text-gray-600">{availability.reason}</p>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className="text-sm text-gray-600">Active Projects</p>
                      <p className="text-2xl font-bold text-gray-900">
                        {availability.activeProjects}
                      </p>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

function ProjectForm({
  profiles,
  teams,
  markets,
  initialData,
  onSubmit,
  onCancel,
}: {
  profiles: Profile[];
  teams: Team[];
  markets: Market[];
  initialData?: Project;
  onSubmit: (data: Partial<Project>) => void;
  onCancel: () => void;
}) {
  const [formData, setFormData] = useState({
    name: initialData?.name || '',
    description: initialData?.description || '',
    start_date: initialData?.start_date || '',
    end_date: initialData?.end_date || '',
    team_id: initialData?.team_id || '',
    market_id: initialData?.market_id || '',
    status: initialData?.status || 'Planning',
    assigned_to: initialData?.assigned_to || [],
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    onSubmit({
      ...formData,
      team_id: formData.team_id || null,
      market_id: formData.market_id || null,
      start_date: formData.start_date || null,
      end_date: formData.end_date || null,
    });
  }

  function toggleAssignee(userId: string) {
    setFormData({
      ...formData,
      assigned_to: formData.assigned_to.includes(userId)
        ? formData.assigned_to.filter((id) => id !== userId)
        : [...formData.assigned_to, userId],
    });
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Project Name</label>
        <input
          type="text"
          value={formData.name}
          onChange={(e) => setFormData({ ...formData, name: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          required
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
        <textarea
          value={formData.description}
          onChange={(e) => setFormData({ ...formData, description: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          rows={3}
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Start Date</label>
          <input
            type="date"
            value={formData.start_date}
            onChange={(e) => setFormData({ ...formData, start_date: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-md"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">End Date</label>
          <input
            type="date"
            value={formData.end_date}
            onChange={(e) => setFormData({ ...formData, end_date: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-md"
          />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Team</label>
          <select
            value={formData.team_id}
            onChange={(e) => setFormData({ ...formData, team_id: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-md"
          >
            <option value="">Select Team</option>
            {teams.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Market</label>
          <select
            value={formData.market_id}
            onChange={(e) => setFormData({ ...formData, market_id: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-md"
          >
            <option value="">Select Market</option>
            {markets.map((m) => (
              <option key={m.id} value={m.id}>
                {m.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
        <select
          value={formData.status}
          onChange={(e) => setFormData({ ...formData, status: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
        >
          <option value="Planning">Planning</option>
          <option value="In Progress">In Progress</option>
          <option value="On Hold">On Hold</option>
          <option value="Completed">Completed</option>
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">Assign Team Members</label>
        <div className="border border-gray-300 rounded-md p-4 max-h-48 overflow-y-auto">
          {profiles.map((profile) => (
            <label key={profile.id} className="flex items-center gap-2 mb-2 cursor-pointer">
              <input
                type="checkbox"
                checked={formData.assigned_to.includes(profile.id)}
                onChange={() => toggleAssignee(profile.id)}
                className="rounded"
              />
              <span className="text-sm text-gray-700">{profile.full_name}</span>
            </label>
          ))}
        </div>
      </div>

      <div className="flex gap-3 justify-end pt-4">
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50"
        >
          Cancel
        </button>
        <button
          type="submit"
          className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
        >
          {initialData ? 'Update' : 'Create'} Project
        </button>
      </div>
    </form>
  );
}
