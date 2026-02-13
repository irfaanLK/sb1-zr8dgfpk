import { useState, useEffect } from 'react';
import { CheckCircle, Clock, Edit2, Save, X } from 'lucide-react';
import { supabase, Task, Profile, Market } from '../lib/supabase';
import { useAuth } from '../contexts/AuthContext';
import ConfirmDialog from './ConfirmDialog';

export default function QATracker() {
  const { profile } = useAuth();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [markets, setMarkets] = useState<Market[]>([]);
  const [editingTask, setEditingTask] = useState<string | null>(null);
  const [filter, setFilter] = useState<'all' | 'qa' | 'live'>('all');
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

  useEffect(() => {
    loadTasks();
    loadProfiles();
    loadMarkets();
  }, []);

  async function loadTasks() {
    const qaStatuses = ['Ready for QA', 'QA WIP', 'Client Review', 'Live QA', 'Live QA WIP', 'Live!'];

    const { data, error } = await supabase
      .from('tasks')
      .select('*')
      .in('status', qaStatuses)
      .order('task_date', { ascending: false });

    if (!error && data) {
      setTasks(data);
    }
  }

  async function loadProfiles() {
    const { data } = await supabase
      .from('profiles')
      .select('*')
      .order('full_name');

    if (data) setProfiles(data);
  }

  async function loadMarkets() {
    const { data } = await supabase
      .from('markets')
      .select('*')
      .order('name');

    if (data) setMarkets(data);
  }

  async function updateTaskStatus(taskId: string, newStatus: string) {
    const { error } = await supabase
      .from('tasks')
      .update({
        status: newStatus,
        updated_at: new Date().toISOString()
      })
      .eq('id', taskId);

    if (!error) {
      loadTasks();
    }
  }

  const filteredTasks = tasks.filter(task => {
    if (filter === 'qa') {
      return ['Ready for QA', 'QA WIP', 'Client Review'].includes(task.status);
    } else if (filter === 'live') {
      return ['Live QA', 'Live QA WIP', 'Live!'].includes(task.status);
    }
    return true;
  });

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'Ready for QA':
        return 'bg-blue-100 text-blue-800';
      case 'QA WIP':
        return 'bg-yellow-100 text-yellow-800';
      case 'Client Review':
        return 'bg-purple-100 text-purple-800';
      case 'Live QA':
        return 'bg-green-100 text-green-800';
      case 'Live QA WIP':
        return 'bg-orange-100 text-orange-800';
      case 'Live!':
        return 'bg-emerald-100 text-emerald-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="p-8">
      <div className="mb-6 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <CheckCircle className="text-green-600" size={32} />
          <h1 className="text-3xl font-bold text-gray-900">QA Tracker</h1>
        </div>
      </div>

      <div className="mb-6 bg-green-50 border border-green-200 rounded-lg p-4">
        <p className="text-sm text-green-900">
          <strong>QA Workflow:</strong> Track tasks through QA stages. Status updates here automatically sync with the Daily Tracker.
        </p>
      </div>

      <div className="mb-6 flex gap-2">
        <button
          onClick={() => setFilter('all')}
          className={`px-4 py-2 rounded-md font-medium ${
            filter === 'all'
              ? 'bg-blue-600 text-white'
              : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
          }`}
        >
          All Tasks
        </button>
        <button
          onClick={() => setFilter('qa')}
          className={`px-4 py-2 rounded-md font-medium ${
            filter === 'qa'
              ? 'bg-blue-600 text-white'
              : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
          }`}
        >
          QA Stage
        </button>
        <button
          onClick={() => setFilter('live')}
          className={`px-4 py-2 rounded-md font-medium ${
            filter === 'live'
              ? 'bg-blue-600 text-white'
              : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
          }`}
        >
          Live Stage
        </button>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Date
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Project Name
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Ticket Name
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Ticket ID
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Market
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Assignee
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Comments
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {filteredTasks.length === 0 ? (
                <tr>
                  <td colSpan={9} className="px-6 py-8 text-center text-gray-500">
                    <div className="flex flex-col items-center gap-2">
                      <Clock size={48} className="text-gray-300" />
                      <p>No tasks in QA workflow</p>
                    </div>
                  </td>
                </tr>
              ) : (
                filteredTasks.map((task) => {
                  const assignee = profiles.find((p) => p.id === task.assignee_id);
                  const market = markets.find((m) => m.id === task.market_id);

                  return (
                    <tr key={task.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        {new Date(task.task_date).toLocaleDateString()}
                      </td>
                      <td className="px-6 py-4 text-sm text-gray-900">
                        <div className="max-w-xs truncate">{task.project_name}</div>
                      </td>
                      <td className="px-6 py-4 text-sm text-gray-500">
                        {task.ticket_name || '-'}
                      </td>
                      <td className="px-6 py-4 text-sm">
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
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {market?.name || '-'}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {assignee?.full_name || '-'}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className={`px-2 py-1 text-xs font-medium rounded-full ${getStatusColor(task.status)}`}>
                          {task.status}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-sm text-gray-500">
                        <div className="max-w-xs truncate">{task.comments || '-'}</div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        <button
                          onClick={() => setEditingTask(task.id)}
                          className="text-blue-600 hover:text-blue-700"
                          title="Update Status"
                        >
                          <Edit2 size={16} />
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {editingTask && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-semibold">Update QA Status</h2>
              <button onClick={() => setEditingTask(null)} className="text-gray-400 hover:text-gray-600">
                <X size={24} />
              </button>
            </div>
            <QAStatusForm
              task={tasks.find((t) => t.id === editingTask)!}
              onSuccess={() => {
                setEditingTask(null);
                loadTasks();
              }}
              onCancel={() => setEditingTask(null)}
              confirmDialog={confirmDialog}
              setConfirmDialog={setConfirmDialog}
            />
          </div>
        </div>
      )}

      <ConfirmDialog
        isOpen={confirmDialog.isOpen}
        title={confirmDialog.title}
        message={confirmDialog.message}
        confirmText="Update"
        cancelText="Cancel"
        variant="info"
        onConfirm={confirmDialog.onConfirm}
        onCancel={() => setConfirmDialog({ ...confirmDialog, isOpen: false })}
      />
    </div>
  );
}

function QAStatusForm({
  task,
  onSuccess,
  onCancel,
  confirmDialog,
  setConfirmDialog,
}: {
  task: Task;
  onSuccess: () => void;
  onCancel: () => void;
  confirmDialog: any;
  setConfirmDialog: any;
}) {
  const [status, setStatus] = useState(task.status);
  const [comments, setComments] = useState(task.comments || '');

  const qaStatuses = ['Ready for QA', 'QA WIP', 'Client Review', 'Live QA', 'Live QA WIP', 'Live!'];

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    setConfirmDialog({
      isOpen: true,
      title: 'Update Task Status',
      message: `Are you sure you want to update the status to "${status}"? This will sync with the Daily Tracker.`,
      onConfirm: async () => {
        const { error } = await supabase
          .from('tasks')
          .update({
            status,
            comments,
            updated_at: new Date().toISOString(),
          })
          .eq('id', task.id);

        if (!error) {
          onSuccess();
        }
        setConfirmDialog({ ...confirmDialog, isOpen: false });
      },
    });
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="bg-gray-50 rounded-lg p-4">
        <h3 className="font-semibold text-gray-900 mb-2">Task Details</h3>
        <div className="grid grid-cols-2 gap-3 text-sm">
          <div>
            <span className="text-gray-600">Project:</span>
            <p className="font-medium">{task.project_name}</p>
          </div>
          <div>
            <span className="text-gray-600">Date:</span>
            <p className="font-medium">{new Date(task.task_date).toLocaleDateString()}</p>
          </div>
          <div>
            <span className="text-gray-600">Priority:</span>
            <p className="font-medium">{task.priority}</p>
          </div>
          <div>
            <span className="text-gray-600">Current Status:</span>
            <p className="font-medium">{task.status}</p>
          </div>
        </div>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">New Status</label>
        <select
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          required
        >
          {qaStatuses.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
        <p className="text-xs text-gray-500 mt-1">
          Status will automatically sync with Daily Tracker
        </p>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">QA Comments</label>
        <textarea
          value={comments}
          onChange={(e) => setComments(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          rows={4}
          placeholder="Add QA notes, test results, or observations..."
        />
      </div>

      <div className="flex gap-3 justify-end pt-2">
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50"
        >
          Cancel
        </button>
        <button
          type="submit"
          className="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700"
        >
          Update Status
        </button>
      </div>
    </form>
  );
}
