import { useState, useEffect } from 'react';
import { Plus, Save, X, Edit2, Trash2 } from 'lucide-react';
import { supabase, Task, Profile, Team, Market } from '../lib/supabase';
import { useAuth } from '../contexts/AuthContext';
import ConfirmDialog from './ConfirmDialog';

const PRIORITIES = ['P1', 'P2', 'P3'];
const STATUSES = [
  'Cost Approval',
  'Backlog',
  'In Progress',
  'Ready for QA',
  'QA WIP',
  'Client Review',
  'Feedback',
  'Ready to Publish',
  'Live QA',
  'Live QA WIP',
  'Live!',
  'Completed',
  'Blocked',
  'On-Hold',
  'Cancelled',
  'Brief Clarification',
];
const HUB_EFFORTS = ['Low', 'Medium', 'High', 'V.High'];

export default function DailyTracker() {
  const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split('T')[0]);
  const [tasks, setTasks] = useState<(Task & { assignee?: Profile; team?: Team; market?: Market })[]>([]);
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [markets, setMarkets] = useState<Market[]>([]);
  const [showAddForm, setShowAddForm] = useState(false);
  const [editingTask, setEditingTask] = useState<string | null>(null);
  const [confirmDialog, setConfirmDialog] = useState<{
    isOpen: boolean;
    title: string;
    message: string;
    onConfirm: () => void;
  }>({
    isOpen: false,
    title: '',
    message: '',
    onConfirm: () => {},
  });
  const { profile } = useAuth();

  useEffect(() => {
    loadTasks();
    loadProfiles();
    loadTeams();
    loadMarkets();
  }, [selectedDate]);

  async function loadTasks() {
    const { data, error } = await supabase
      .from('tasks')
      .select(`
        *,
        assignee:profiles!assignee_id(*),
        team:teams(*),
        market:markets(*)
      `)
      .eq('task_date', selectedDate)
      .order('created_at', { ascending: true });

    if (!error && data) {
      setTasks(data);
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

  async function addTask(taskData: Partial<Task>) {
    const { error } = await supabase
      .from('tasks')
      .insert([{ ...taskData, task_date: selectedDate }]);

    if (!error) {
      loadTasks();
      setShowAddForm(false);
    }
  }

  async function updateTask(id: string, taskData: Partial<Task>) {
    const { error } = await supabase
      .from('tasks')
      .update({ ...taskData, updated_at: new Date().toISOString() })
      .eq('id', id);

    if (!error) {
      loadTasks();
      setEditingTask(null);
    }
  }

  async function deleteTask(id: string) {
    setConfirmDialog({
      isOpen: true,
      title: 'Delete Task',
      message: 'Are you sure you want to delete this task? This action cannot be undone.',
      onConfirm: async () => {
        const { error } = await supabase
          .from('tasks')
          .delete()
          .eq('id', id);

        if (!error) {
          loadTasks();
        }
        setConfirmDialog({ ...confirmDialog, isOpen: false });
      },
    });
  }

  function calculateHours(startTime: string, endTime: string): number {
    if (!startTime || !endTime) return 0;
    const start = new Date(`2000-01-01T${startTime}`);
    const end = new Date(`2000-01-01T${endTime}`);
    const diff = (end.getTime() - start.getTime()) / (1000 * 60 * 60);
    return Math.max(0, parseFloat(diff.toFixed(2)));
  }

  function getPriorityColor(priority: string) {
    switch (priority) {
      case 'P1':
        return 'bg-red-100 text-red-800 border-red-300';
      case 'P2':
        return 'bg-yellow-100 text-yellow-800 border-yellow-300';
      case 'P3':
        return 'bg-green-100 text-green-800 border-green-300';
      default:
        return 'bg-gray-100 text-gray-800 border-gray-300';
    }
  }

  return (
    <div className="p-8">
      <div className="mb-6 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <h1 className="text-3xl font-bold text-gray-900">Daily Tracker</h1>
          <input
            type="date"
            value={selectedDate}
            onChange={(e) => setSelectedDate(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-md"
          />
        </div>
        <button
          onClick={() => setShowAddForm(true)}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
        >
          <Plus size={20} />
          Add Task
        </button>
      </div>

      {showAddForm && (
        <div className="mb-6 bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <TaskForm
            profiles={profiles}
            teams={teams}
            markets={markets}
            onSubmit={addTask}
            onCancel={() => setShowAddForm(false)}
          />
        </div>
      )}

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-x-auto">
        <table className="w-full">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Project Name</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Ticket Name</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Ticket ID</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Priority</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Market</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Status</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Assignee</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Team</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Hub Effort</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Start Time</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">End Time</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Total Hours</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Comments</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {tasks.length === 0 ? (
              <tr>
                <td colSpan={14} className="px-4 py-8 text-center text-gray-500">
                  No tasks for this date
                </td>
              </tr>
            ) : (
              tasks.map((task) => (
                <tr key={task.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-sm">{task.project_name}</td>
                  <td className="px-4 py-3 text-sm">{task.ticket_name || '-'}</td>
                  <td className="px-4 py-3 text-sm">
                    {task.ticket_url ? (
                      <a
                        href={task.ticket_url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-blue-600 hover:text-blue-700 underline"
                      >
                        Link
                      </a>
                    ) : '-'}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-1 rounded text-xs font-medium border ${getPriorityColor(task.priority)}`}>
                      {task.priority}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm">{task.market?.name || '-'}</td>
                  <td className="px-4 py-3 text-sm">{task.status}</td>
                  <td className="px-4 py-3 text-sm">{task.assignee?.full_name || '-'}</td>
                  <td className="px-4 py-3 text-sm">{task.team?.name || '-'}</td>
                  <td className="px-4 py-3 text-sm">{task.hub_effort}</td>
                  <td className="px-4 py-3 text-sm font-mono">{task.start_time || '-'}</td>
                  <td className="px-4 py-3 text-sm font-mono">{task.end_time || '-'}</td>
                  <td className="px-4 py-3 text-sm font-semibold">{task.total_hours || 0}h</td>
                  <td className="px-4 py-3 text-sm max-w-xs truncate">{task.comments || '-'}</td>
                  <td className="px-4 py-3">
                    <div className="flex gap-2">
                      <button
                        onClick={() => setEditingTask(task.id)}
                        className="text-blue-600 hover:text-blue-700"
                      >
                        <Edit2 size={16} />
                      </button>
                      <button
                        onClick={() => deleteTask(task.id)}
                        className="text-red-600 hover:text-red-700"
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {editingTask && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg shadow-xl max-w-4xl w-full max-h-[90vh] overflow-y-auto p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-semibold">Edit Task</h2>
              <button onClick={() => setEditingTask(null)} className="text-gray-400 hover:text-gray-600">
                <X size={24} />
              </button>
            </div>
            <TaskForm
              profiles={profiles}
              teams={teams}
              markets={markets}
              initialData={tasks.find((t) => t.id === editingTask)}
              onSubmit={(data) => updateTask(editingTask, data)}
              onCancel={() => setEditingTask(null)}
            />
          </div>
        </div>
      )}

      <ConfirmDialog
        isOpen={confirmDialog.isOpen}
        title={confirmDialog.title}
        message={confirmDialog.message}
        confirmText="Delete"
        cancelText="Cancel"
        variant="danger"
        onConfirm={confirmDialog.onConfirm}
        onCancel={() => setConfirmDialog({ ...confirmDialog, isOpen: false })}
      />
    </div>
  );
}

function TaskForm({
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
  initialData?: Task;
  onSubmit: (data: Partial<Task>) => void;
  onCancel: () => void;
}) {
  const [formData, setFormData] = useState({
    project_name: initialData?.project_name || '',
    ticket_name: initialData?.ticket_name || '',
    ticket_url: initialData?.ticket_url || '',
    priority: initialData?.priority || 'P3',
    market_id: initialData?.market_id || '',
    status: initialData?.status || 'Backlog',
    assignee_id: initialData?.assignee_id || '',
    team_id: initialData?.team_id || '',
    hub_effort: initialData?.hub_effort || 'Medium',
    start_time: initialData?.start_time || '',
    end_time: initialData?.end_time || '',
    comments: initialData?.comments || '',
  });

  function calculateHours(startTime: string, endTime: string): number {
    if (!startTime || !endTime) return 0;
    const start = new Date(`2000-01-01T${startTime}`);
    const end = new Date(`2000-01-01T${endTime}`);
    const diff = (end.getTime() - start.getTime()) / (1000 * 60 * 60);
    return Math.max(0, parseFloat(diff.toFixed(2)));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const totalHours = calculateHours(formData.start_time, formData.end_time);
    onSubmit({
      ...formData,
      total_hours: totalHours,
      market_id: formData.market_id || null,
      assignee_id: formData.assignee_id || null,
      team_id: formData.team_id || null,
    });
  }

  return (
    <form onSubmit={handleSubmit} className="grid grid-cols-2 gap-4">
      <div className="col-span-2">
        <label className="block text-sm font-medium text-gray-700 mb-1">Project Name</label>
        <input
          type="text"
          value={formData.project_name}
          onChange={(e) => setFormData({ ...formData, project_name: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          required
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Ticket Name</label>
        <input
          type="text"
          value={formData.ticket_name}
          onChange={(e) => setFormData({ ...formData, ticket_name: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          placeholder="Optional"
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Ticket URL</label>
        <input
          type="url"
          value={formData.ticket_url}
          onChange={(e) => setFormData({ ...formData, ticket_url: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          placeholder="https://..."
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Priority</label>
        <select
          value={formData.priority}
          onChange={(e) => setFormData({ ...formData, priority: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
        >
          {PRIORITIES.map((p) => (
            <option key={p} value={p}>{p}</option>
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
            <option key={m.id} value={m.id}>{m.name}</option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
        <select
          value={formData.status}
          onChange={(e) => setFormData({ ...formData, status: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
        >
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Assignee</label>
        <select
          value={formData.assignee_id}
          onChange={(e) => setFormData({ ...formData, assignee_id: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
        >
          <option value="">Select Assignee</option>
          {profiles.map((p) => (
            <option key={p.id} value={p.id}>{p.full_name}</option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Team</label>
        <select
          value={formData.team_id}
          onChange={(e) => setFormData({ ...formData, team_id: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
        >
          <option value="">Select Team</option>
          {teams.map((t) => (
            <option key={t.id} value={t.id}>{t.name}</option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Hub Effort</label>
        <select
          value={formData.hub_effort}
          onChange={(e) => setFormData({ ...formData, hub_effort: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
        >
          {HUB_EFFORTS.map((h) => (
            <option key={h} value={h}>{h}</option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Start Time</label>
        <input
          type="time"
          value={formData.start_time}
          onChange={(e) => setFormData({ ...formData, start_time: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">End Time</label>
        <input
          type="time"
          value={formData.end_time}
          onChange={(e) => setFormData({ ...formData, end_time: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Total Hours</label>
        <input
          type="text"
          value={calculateHours(formData.start_time, formData.end_time)}
          className="w-full px-3 py-2 border border-gray-300 rounded-md bg-gray-50"
          disabled
        />
      </div>

      <div className="col-span-2">
        <label className="block text-sm font-medium text-gray-700 mb-1">Comments</label>
        <textarea
          value={formData.comments}
          onChange={(e) => setFormData({ ...formData, comments: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          rows={3}
        />
      </div>

      <div className="col-span-2 flex gap-3 justify-end">
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
          {initialData ? 'Update' : 'Add'} Task
        </button>
      </div>
    </form>
  );
}
