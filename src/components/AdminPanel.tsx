import { useState, useEffect } from 'react';
import { Shield, Users, Trash2, Edit2, Plus, Key, UserCheck, UserX, Save, X, Clock, Calendar, History } from 'lucide-react';
import { supabase, Profile, Team, Market, HomepageTimezone, Attendance, AuditLog } from '../lib/supabase';
import { useAuth } from '../contexts/AuthContext';
import ConfirmDialog from './ConfirmDialog';

export default function AdminPanel() {
  const { profile } = useAuth();
  const [users, setUsers] = useState<Profile[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [markets, setMarkets] = useState<Market[]>([]);
  const [timezones, setTimezones] = useState<HomepageTimezone[]>([]);
  const [attendance, setAttendance] = useState<Attendance[]>([]);
  const [auditLogs, setAuditLogs] = useState<(AuditLog & { profiles?: Profile })[]>([]);
  const [showAddUser, setShowAddUser] = useState(false);
  const [editingUser, setEditingUser] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'users' | 'teams' | 'markets' | 'homepage' | 'attendance' | 'history'>('users');
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
    loadUsers();
    loadTeams();
    loadMarkets();
    loadTimezones();
    loadAttendance();
    loadAuditLogs();
  }, []);

  async function loadUsers() {
    const { data, error } = await supabase
      .from('profiles')
      .select('*')
      .order('created_at', { ascending: false });

    if (!error && data) {
      setUsers(data);
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

  async function loadTimezones() {
    const { data, error } = await supabase
      .from('homepage_timezones')
      .select('*')
      .order('display_order');

    if (!error && data) {
      setTimezones(data);
    }
  }

  async function loadAttendance() {
    const { data, error } = await supabase
      .from('attendance')
      .select('*, profiles(full_name, email)')
      .order('attendance_date', { ascending: false });

    if (!error && data) {
      setAttendance(data);
    }
  }

  async function loadAuditLogs() {
    const { data, error } = await supabase
      .from('audit_logs')
      .select('*, profiles(full_name, email)')
      .order('created_at', { ascending: false })
      .limit(100);

    if (!error && data) {
      setAuditLogs(data);
    }
  }

  async function deleteUser(userId: string) {
    setConfirmDialog({
      isOpen: true,
      title: 'Delete User',
      message: 'Are you sure you want to delete this user? This action cannot be undone and will remove all their data.',
      onConfirm: async () => {
        const { error } = await supabase.auth.admin.deleteUser(userId);

        if (error) {
          alert('Error deleting user: ' + error.message);
        } else {
          loadUsers();
        }
        setConfirmDialog({ ...confirmDialog, isOpen: false });
      },
    });
  }

  async function toggleAdminRole(userId: string, currentRole: string) {
    const newRole = currentRole === 'admin' ? 'member' : 'admin';

    if (userId === profile?.id) {
      alert('You cannot change your own admin role.');
      return;
    }

    const { error } = await supabase
      .from('profiles')
      .update({ role: newRole })
      .eq('id', userId);

    if (error) {
      alert('Error updating role: ' + error.message);
    } else {
      loadUsers();
    }
  }

  async function resetPassword(email: string) {
    setConfirmDialog({
      isOpen: true,
      title: 'Reset Password',
      message: `Send password reset email to ${email}?`,
      onConfirm: async () => {
        const { error } = await supabase.auth.resetPasswordForEmail(email, {
          redirectTo: window.location.origin,
        });

        if (error) {
          alert('Error sending reset email: ' + error.message);
        } else {
          alert('Password reset email sent successfully!');
        }
        setConfirmDialog({ ...confirmDialog, isOpen: false });
      },
    });
  }

  if (profile?.role !== 'admin') {
    return (
      <div className="p-8">
        <div className="max-w-2xl mx-auto text-center">
          <Shield className="mx-auto mb-4 text-red-600" size={64} />
          <h1 className="text-2xl font-bold text-gray-900 mb-2">Access Denied</h1>
          <p className="text-gray-600">You do not have permission to access the admin panel.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="p-8">
      <div className="mb-6 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Shield className="text-blue-600" size={32} />
          <h1 className="text-3xl font-bold text-gray-900">Admin Panel</h1>
        </div>
      </div>

      <div className="mb-6 bg-blue-50 border border-blue-200 rounded-lg p-4">
        <p className="text-sm text-blue-900">
          <strong>Admin Privileges:</strong> You have full access to manage users, teams, markets, and all platform data.
        </p>
      </div>

      <div className="mb-6">
        <div className="flex gap-2 border-b border-gray-200">
          <button
            onClick={() => setActiveTab('users')}
            className={`px-4 py-2 font-medium ${
              activeTab === 'users'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            Users
          </button>
          <button
            onClick={() => setActiveTab('teams')}
            className={`px-4 py-2 font-medium ${
              activeTab === 'teams'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            Teams
          </button>
          <button
            onClick={() => setActiveTab('markets')}
            className={`px-4 py-2 font-medium ${
              activeTab === 'markets'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            Markets
          </button>
          <button
            onClick={() => setActiveTab('homepage')}
            className={`px-4 py-2 font-medium ${
              activeTab === 'homepage'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            Homepage
          </button>
          <button
            onClick={() => setActiveTab('attendance')}
            className={`px-4 py-2 font-medium ${
              activeTab === 'attendance'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            Attendance
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`px-4 py-2 font-medium ${
              activeTab === 'history'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            History
          </button>
        </div>
      </div>

      {activeTab === 'users' && (
        <>
          <div className="mb-6 flex justify-end">
            <button
              onClick={() => setShowAddUser(true)}
              className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
            >
              <Plus size={20} />
              Add User
            </button>
          </div>

          {showAddUser && (
            <div className="mb-6 bg-white rounded-lg shadow-sm border border-gray-200 p-6">
              <AddUserForm
                teams={teams}
                onSuccess={() => {
                  setShowAddUser(false);
                  loadUsers();
                }}
                onCancel={() => setShowAddUser(false)}
              />
            </div>
          )}

          <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
            <table className="w-full">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-600 uppercase">Name</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-600 uppercase">Email</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-600 uppercase">Role</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-600 uppercase">Team</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-600 uppercase">Created</th>
                  <th className="px-6 py-3 text-right text-xs font-medium text-gray-600 uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {users.map((user) => (
                  <tr key={user.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4 text-sm font-medium text-gray-900">{user.full_name}</td>
                    <td className="px-6 py-4 text-sm text-gray-600">{user.email}</td>
                    <td className="px-6 py-4">
                      <span
                        className={`px-2 py-1 rounded text-xs font-medium ${
                          user.role === 'admin'
                            ? 'bg-purple-100 text-purple-800'
                            : 'bg-gray-100 text-gray-800'
                        }`}
                      >
                        {user.role}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-600">
                      {teams.find((t) => t.id === user.team_id)?.name || '-'}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-600">
                      {new Date(user.created_at).toLocaleDateString()}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex justify-end gap-2">
                        <button
                          onClick={() => setEditingUser(user.id)}
                          className="text-blue-600 hover:text-blue-700"
                          title="Edit user"
                        >
                          <Edit2 size={16} />
                        </button>
                        <button
                          onClick={() => resetPassword(user.email)}
                          className="text-green-600 hover:text-green-700"
                          title="Reset password"
                        >
                          <Key size={16} />
                        </button>
                        <button
                          onClick={() => toggleAdminRole(user.id, user.role)}
                          className={`${
                            user.role === 'admin'
                              ? 'text-orange-600 hover:text-orange-700'
                              : 'text-purple-600 hover:text-purple-700'
                          }`}
                          title={user.role === 'admin' ? 'Revoke admin' : 'Make admin'}
                          disabled={user.id === profile?.id}
                        >
                          {user.role === 'admin' ? <UserX size={16} /> : <UserCheck size={16} />}
                        </button>
                        <button
                          onClick={() => deleteUser(user.id)}
                          className="text-red-600 hover:text-red-700"
                          title="Delete user"
                          disabled={user.id === profile?.id}
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {editingUser && (
            <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
              <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full p-6">
                <div className="flex items-center justify-between mb-4">
                  <h2 className="text-xl font-semibold">Edit User</h2>
                  <button onClick={() => setEditingUser(null)} className="text-gray-400 hover:text-gray-600">
                    <X size={24} />
                  </button>
                </div>
                <EditUserForm
                  user={users.find((u) => u.id === editingUser)!}
                  teams={teams}
                  onSuccess={() => {
                    setEditingUser(null);
                    loadUsers();
                  }}
                  onCancel={() => setEditingUser(null)}
                />
              </div>
            </div>
          )}
        </>
      )}

      {activeTab === 'teams' && (
        <TeamManagement teams={teams} onUpdate={loadTeams} />
      )}

      {activeTab === 'markets' && (
        <MarketManagement markets={markets} onUpdate={loadMarkets} confirmDialog={confirmDialog} setConfirmDialog={setConfirmDialog} />
      )}

      {activeTab === 'homepage' && (
        <HomepageManagement timezones={timezones} onUpdate={loadTimezones} confirmDialog={confirmDialog} setConfirmDialog={setConfirmDialog} />
      )}

      {activeTab === 'attendance' && (
        <AttendanceManagement attendance={attendance} users={users} onUpdate={loadAttendance} confirmDialog={confirmDialog} setConfirmDialog={setConfirmDialog} />
      )}

      {activeTab === 'history' && (
        <HistoryManagement auditLogs={auditLogs} />
      )}

      <ConfirmDialog
        isOpen={confirmDialog.isOpen}
        title={confirmDialog.title}
        message={confirmDialog.message}
        confirmText="Confirm"
        cancelText="Cancel"
        variant="danger"
        onConfirm={confirmDialog.onConfirm}
        onCancel={() => setConfirmDialog({ ...confirmDialog, isOpen: false })}
      />
    </div>
  );
}

function AddUserForm({
  teams,
  onSuccess,
  onCancel,
}: {
  teams: Team[];
  onSuccess: () => void;
  onCancel: () => void;
}) {
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    full_name: '',
    team_id: '',
    role: 'member',
  });
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);

    try {
      const { data: authData, error: authError } = await supabase.auth.admin.createUser({
        email: formData.email,
        password: formData.password,
        email_confirm: true,
      });

      if (authError) throw authError;

      if (authData.user) {
        const { error: profileError } = await supabase
          .from('profiles')
          .insert([{
            id: authData.user.id,
            email: formData.email,
            full_name: formData.full_name,
            team_id: formData.team_id || null,
            role: formData.role,
          }]);

        if (profileError) throw profileError;
      }

      alert('User created successfully!');
      onSuccess();
    } catch (error: any) {
      alert('Error creating user: ' + error.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Full Name</label>
        <input
          type="text"
          value={formData.full_name}
          onChange={(e) => setFormData({ ...formData, full_name: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          required
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
        <input
          type="email"
          value={formData.email}
          onChange={(e) => setFormData({ ...formData, email: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          required
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
        <input
          type="password"
          value={formData.password}
          onChange={(e) => setFormData({ ...formData, password: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          required
          minLength={6}
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Team</label>
        <select
          value={formData.team_id}
          onChange={(e) => setFormData({ ...formData, team_id: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
        >
          <option value="">No Team</option>
          {teams.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Role</label>
        <select
          value={formData.role}
          onChange={(e) => setFormData({ ...formData, role: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
        >
          <option value="member">Member</option>
          <option value="admin">Admin</option>
        </select>
      </div>
      <div className="flex gap-3 justify-end pt-4">
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50"
          disabled={loading}
        >
          Cancel
        </button>
        <button
          type="submit"
          className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
          disabled={loading}
        >
          {loading ? 'Creating...' : 'Create User'}
        </button>
      </div>
    </form>
  );
}

function EditUserForm({
  user,
  teams,
  onSuccess,
  onCancel,
}: {
  user: Profile;
  teams: Team[];
  onSuccess: () => void;
  onCancel: () => void;
}) {
  const [formData, setFormData] = useState({
    full_name: user.full_name,
    team_id: user.team_id || '',
  });
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);

    try {
      const { error } = await supabase
        .from('profiles')
        .update({
          full_name: formData.full_name,
          team_id: formData.team_id || null,
        })
        .eq('id', user.id);

      if (error) throw error;

      alert('User updated successfully!');
      onSuccess();
    } catch (error: any) {
      alert('Error updating user: ' + error.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Full Name</label>
        <input
          type="text"
          value={formData.full_name}
          onChange={(e) => setFormData({ ...formData, full_name: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          required
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
        <input
          type="email"
          value={user.email}
          className="w-full px-3 py-2 border border-gray-300 rounded-md bg-gray-50"
          disabled
        />
        <p className="text-xs text-gray-500 mt-1">Email cannot be changed</p>
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Team</label>
        <select
          value={formData.team_id}
          onChange={(e) => setFormData({ ...formData, team_id: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
        >
          <option value="">No Team</option>
          {teams.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </div>
      <div className="flex gap-3 justify-end pt-4">
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50"
          disabled={loading}
        >
          Cancel
        </button>
        <button
          type="submit"
          className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
          disabled={loading}
        >
          {loading ? 'Updating...' : 'Update User'}
        </button>
      </div>
    </form>
  );
}

function TeamManagement({ teams, onUpdate }: { teams: Team[]; onUpdate: () => void }) {
  const [showAdd, setShowAdd] = useState(false);
  const [editing, setEditing] = useState<string | null>(null);

  return (
    <div>
      <div className="mb-4 flex justify-end">
        <button
          onClick={() => setShowAdd(true)}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
        >
          <Plus size={20} />
          Add Team
        </button>
      </div>

      {showAdd && (
        <TeamForm
          onSuccess={() => {
            setShowAdd(false);
            onUpdate();
          }}
          onCancel={() => setShowAdd(false)}
        />
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {teams.map((team) => (
          <div key={team.id} className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            <div className="flex items-start justify-between mb-2">
              <h3 className="font-semibold text-gray-900">{team.name}</h3>
              <button
                onClick={() => setEditing(team.id)}
                className="text-blue-600 hover:text-blue-700"
              >
                <Edit2 size={16} />
              </button>
            </div>
            <p className="text-sm text-gray-600">{team.description}</p>
          </div>
        ))}
      </div>

      {editing && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
            <TeamForm
              team={teams.find((t) => t.id === editing)}
              onSuccess={() => {
                setEditing(null);
                onUpdate();
              }}
              onCancel={() => setEditing(null)}
            />
          </div>
        </div>
      )}
    </div>
  );
}

function TeamForm({
  team,
  onSuccess,
  onCancel,
}: {
  team?: Team;
  onSuccess: () => void;
  onCancel: () => void;
}) {
  const [formData, setFormData] = useState({
    name: team?.name || '',
    description: team?.description || '',
  });

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (team) {
      const { error } = await supabase
        .from('teams')
        .update(formData)
        .eq('id', team.id);

      if (!error) onSuccess();
    } else {
      const { error } = await supabase
        .from('teams')
        .insert([formData]);

      if (!error) onSuccess();
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Team Name</label>
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
      <div className="flex gap-3 justify-end">
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
          {team ? 'Update' : 'Create'}
        </button>
      </div>
    </form>
  );
}

function MarketManagement({
  markets,
  onUpdate,
  confirmDialog,
  setConfirmDialog
}: {
  markets: Market[];
  onUpdate: () => void;
  confirmDialog: any;
  setConfirmDialog: any;
}) {
  const [showAdd, setShowAdd] = useState(false);
  const [editing, setEditing] = useState<string | null>(null);

  async function deleteMarket(id: string, name: string) {
    setConfirmDialog({
      isOpen: true,
      title: 'Delete Market',
      message: `Are you sure you want to delete "${name}"? This action cannot be undone.`,
      onConfirm: async () => {
        const { error } = await supabase
          .from('markets')
          .delete()
          .eq('id', id);

        if (!error) {
          onUpdate();
        }
        setConfirmDialog({ ...confirmDialog, isOpen: false });
      },
    });
  }

  return (
    <div>
      <div className="mb-4 flex justify-end">
        <button
          onClick={() => setShowAdd(true)}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
        >
          <Plus size={20} />
          Add Market
        </button>
      </div>

      {showAdd && (
        <MarketForm
          onSuccess={() => {
            setShowAdd(false);
            onUpdate();
          }}
          onCancel={() => setShowAdd(false)}
        />
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {markets.map((market) => (
          <div key={market.id} className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            <div className="flex items-start justify-between mb-2">
              <h3 className="font-semibold text-gray-900">{market.name}</h3>
              <div className="flex gap-2">
                <button
                  onClick={() => setEditing(market.id)}
                  className="text-blue-600 hover:text-blue-700"
                >
                  <Edit2 size={16} />
                </button>
                <button
                  onClick={() => deleteMarket(market.id, market.name)}
                  className="text-red-600 hover:text-red-700"
                >
                  <Trash2 size={16} />
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      {editing && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
            <MarketForm
              market={markets.find((m) => m.id === editing)}
              onSuccess={() => {
                setEditing(null);
                onUpdate();
              }}
              onCancel={() => setEditing(null)}
            />
          </div>
        </div>
      )}
    </div>
  );
}

function MarketForm({
  market,
  onSuccess,
  onCancel,
}: {
  market?: Market;
  onSuccess: () => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState(market?.name || '');

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (market) {
      const { error } = await supabase
        .from('markets')
        .update({ name })
        .eq('id', market.id);

      if (!error) onSuccess();
    } else {
      const { error } = await supabase
        .from('markets')
        .insert([{ name }]);

      if (!error) onSuccess();
    }
  }

  return (
    <form onSubmit={handleSubmit} className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-4">
      <h3 className="text-lg font-semibold text-gray-900 mb-4">{market ? 'Edit Market' : 'Add Market'}</h3>
      <div className="mb-4">
        <label className="block text-sm font-medium text-gray-700 mb-1">Market Name</label>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          required
        />
      </div>
      <div className="flex gap-3 justify-end">
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
          {market ? 'Update' : 'Create'}
        </button>
      </div>
    </form>
  );
}

function HomepageManagement({
  timezones,
  onUpdate,
  confirmDialog,
  setConfirmDialog
}: {
  timezones: HomepageTimezone[];
  onUpdate: () => void;
  confirmDialog: any;
  setConfirmDialog: any;
}) {
  const [showAdd, setShowAdd] = useState(false);
  const [editing, setEditing] = useState<string | null>(null);

  async function deleteTimezone(id: string, label: string) {
    setConfirmDialog({
      isOpen: true,
      title: 'Delete Timezone',
      message: `Are you sure you want to delete "${label}"? This action cannot be undone.`,
      onConfirm: async () => {
        const { error } = await supabase
          .from('homepage_timezones')
          .delete()
          .eq('id', id);

        if (!error) {
          onUpdate();
        }
        setConfirmDialog({ ...confirmDialog, isOpen: false });
      },
    });
  }

  return (
    <div>
      <div className="mb-4 flex justify-end">
        <button
          onClick={() => setShowAdd(true)}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
        >
          <Plus size={20} />
          Add Timezone
        </button>
      </div>

      {showAdd && (
        <TimezoneForm
          onSuccess={() => {
            setShowAdd(false);
            onUpdate();
          }}
          onCancel={() => setShowAdd(false)}
        />
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {timezones.map((tz) => (
          <div key={tz.id} className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            <div className="flex items-start justify-between mb-2">
              <div className="flex items-center gap-2">
                <Clock size={20} className="text-blue-600" />
                <h3 className="font-semibold text-gray-900">{tz.label}</h3>
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => setEditing(tz.id)}
                  className="text-blue-600 hover:text-blue-700"
                >
                  <Edit2 size={16} />
                </button>
                <button
                  onClick={() => deleteTimezone(tz.id, tz.label)}
                  className="text-red-600 hover:text-red-700"
                >
                  <Trash2 size={16} />
                </button>
              </div>
            </div>
            <p className="text-sm text-gray-600">{tz.timezone}</p>
            <div className="mt-2 flex items-center gap-2">
              <span className="text-xs text-gray-500">Order: {tz.display_order}</span>
              {tz.is_active ? (
                <span className="px-2 py-0.5 bg-green-100 text-green-800 text-xs rounded">Active</span>
              ) : (
                <span className="px-2 py-0.5 bg-gray-100 text-gray-800 text-xs rounded">Inactive</span>
              )}
            </div>
          </div>
        ))}
      </div>

      {editing && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
            <TimezoneForm
              timezone={timezones.find((t) => t.id === editing)}
              onSuccess={() => {
                setEditing(null);
                onUpdate();
              }}
              onCancel={() => setEditing(null)}
            />
          </div>
        </div>
      )}
    </div>
  );
}

function TimezoneForm({
  timezone,
  onSuccess,
  onCancel,
}: {
  timezone?: HomepageTimezone;
  onSuccess: () => void;
  onCancel: () => void;
}) {
  const [formData, setFormData] = useState({
    label: timezone?.label || '',
    timezone: timezone?.timezone || '',
    display_order: timezone?.display_order || 0,
    is_active: timezone?.is_active ?? true,
  });

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (timezone) {
      const { error } = await supabase
        .from('homepage_timezones')
        .update(formData)
        .eq('id', timezone.id);

      if (!error) onSuccess();
    } else {
      const { error } = await supabase
        .from('homepage_timezones')
        .insert([formData]);

      if (!error) onSuccess();
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <h3 className="text-lg font-semibold text-gray-900">{timezone ? 'Edit Timezone' : 'Add Timezone'}</h3>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Display Label</label>
        <input
          type="text"
          value={formData.label}
          onChange={(e) => setFormData({ ...formData, label: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          required
          placeholder="e.g., New York, Tokyo"
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Timezone (IANA)</label>
        <input
          type="text"
          value={formData.timezone}
          onChange={(e) => setFormData({ ...formData, timezone: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          required
          placeholder="e.g., America/New_York, Asia/Tokyo"
        />
        <p className="text-xs text-gray-500 mt-1">
          Use IANA timezone format (e.g., America/New_York, Europe/London)
        </p>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Display Order</label>
        <input
          type="number"
          value={formData.display_order}
          onChange={(e) => setFormData({ ...formData, display_order: parseInt(e.target.value) })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          required
          min="0"
        />
      </div>

      <div className="flex items-center gap-2">
        <input
          type="checkbox"
          id="is_active"
          checked={formData.is_active}
          onChange={(e) => setFormData({ ...formData, is_active: e.target.checked })}
          className="rounded border-gray-300"
        />
        <label htmlFor="is_active" className="text-sm font-medium text-gray-700">Active</label>
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
          className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
        >
          {timezone ? 'Update' : 'Create'}
        </button>
      </div>
    </form>
  );
}

function AttendanceManagement({
  attendance,
  users,
  onUpdate,
  confirmDialog,
  setConfirmDialog
}: {
  attendance: Attendance[];
  users: Profile[];
  onUpdate: () => void;
  confirmDialog: any;
  setConfirmDialog: any;
}) {
  const [showAdd, setShowAdd] = useState(false);
  const [editingAttendance, setEditingAttendance] = useState<string | null>(null);
  const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split('T')[0]);

  const filteredAttendance = attendance.filter(
    (a) => a.attendance_date === selectedDate
  );

  async function deleteAttendance(id: string, userName: string) {
    setConfirmDialog({
      isOpen: true,
      title: 'Delete Attendance Record',
      message: `Are you sure you want to delete attendance record for ${userName}? This action cannot be undone.`,
      onConfirm: async () => {
        const { error } = await supabase
          .from('attendance')
          .delete()
          .eq('id', id);

        if (!error) {
          onUpdate();
        }
        setConfirmDialog({ ...confirmDialog, isOpen: false });
      },
    });
  }

  return (
    <div>
      <div className="mb-4 flex justify-between items-center">
        <div className="flex items-center gap-3">
          <label className="text-sm font-medium text-gray-700">Date:</label>
          <input
            type="date"
            value={selectedDate}
            onChange={(e) => setSelectedDate(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-md"
          />
        </div>
        <button
          onClick={() => setShowAdd(true)}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
        >
          <Plus size={20} />
          Add Attendance
        </button>
      </div>

      {showAdd && (
        <div className="mb-6 bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <AttendanceForm
            users={users}
            date={selectedDate}
            onSuccess={() => {
              setShowAdd(false);
              onUpdate();
            }}
            onCancel={() => setShowAdd(false)}
          />
        </div>
      )}

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                User
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Email
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Status
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {filteredAttendance.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-6 py-4 text-center text-gray-500">
                  No attendance records for this date
                </td>
              </tr>
            ) : (
              filteredAttendance.map((record: any) => (
                <tr key={record.id}>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm font-medium text-gray-900">
                      {record.profiles?.full_name || 'Unknown'}
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm text-gray-500">
                      {record.profiles?.email || 'N/A'}
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2 py-1 text-xs rounded-full ${
                      record.status === 'Office' ? 'bg-green-100 text-green-800' :
                      record.status === 'Remote' ? 'bg-blue-100 text-blue-800' :
                      record.status === 'Leave' ? 'bg-yellow-100 text-yellow-800' :
                      'bg-red-100 text-red-800'
                    }`}>
                      {record.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    <div className="flex gap-2">
                      <button
                        onClick={() => setEditingAttendance(record.id)}
                        className="text-blue-600 hover:text-blue-700"
                        title="Edit"
                      >
                        <Edit2 size={16} />
                      </button>
                      <button
                        onClick={() => deleteAttendance(record.id, record.profiles?.full_name || 'Unknown')}
                        className="text-red-600 hover:text-red-700"
                        title="Delete"
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

      {editingAttendance && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-semibold">Edit Attendance</h2>
              <button onClick={() => setEditingAttendance(null)} className="text-gray-400 hover:text-gray-600">
                <X size={24} />
              </button>
            </div>
            <AttendanceForm
              users={users}
              date={selectedDate}
              attendance={attendance.find((a) => a.id === editingAttendance)}
              onSuccess={() => {
                setEditingAttendance(null);
                onUpdate();
              }}
              onCancel={() => setEditingAttendance(null)}
            />
          </div>
        </div>
      )}
    </div>
  );
}

function AttendanceForm({
  users,
  date,
  attendance,
  onSuccess,
  onCancel,
}: {
  users: Profile[];
  date: string;
  attendance?: Attendance;
  onSuccess: () => void;
  onCancel: () => void;
}) {
  const [formData, setFormData] = useState({
    user_id: attendance?.user_id || '',
    attendance_date: attendance?.attendance_date || date,
    status: attendance?.status || 'Office',
  });

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (attendance) {
      const { error } = await supabase
        .from('attendance')
        .update({
          status: formData.status,
        })
        .eq('id', attendance.id);

      if (!error) onSuccess();
    } else {
      const { error } = await supabase
        .from('attendance')
        .insert([formData]);

      if (!error) onSuccess();
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">User</label>
        <select
          value={formData.user_id}
          onChange={(e) => setFormData({ ...formData, user_id: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          required
          disabled={!!attendance}
        >
          <option value="">Select user</option>
          {users.map((user) => (
            <option key={user.id} value={user.id}>
              {user.full_name} ({user.email})
            </option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Date</label>
        <input
          type="date"
          value={formData.attendance_date}
          onChange={(e) => setFormData({ ...formData, attendance_date: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          required
          disabled={!!attendance}
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
        <select
          value={formData.status}
          onChange={(e) => setFormData({ ...formData, status: e.target.value })}
          className="w-full px-3 py-2 border border-gray-300 rounded-md"
          required
        >
          <option value="Office">Office</option>
          <option value="Remote">Remote</option>
          <option value="Leave">Leave</option>
          <option value="Absent">Absent</option>
        </select>
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
          className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
        >
          {attendance ? 'Update' : 'Add'}
        </button>
      </div>
    </form>
  );
}

function HistoryManagement({
  auditLogs,
}: {
  auditLogs: (AuditLog & { profiles?: Profile })[];
}) {
  const [filter, setFilter] = useState<'all' | 'created' | 'updated' | 'deleted'>('all');

  const filteredLogs = filter === 'all'
    ? auditLogs
    : auditLogs.filter(log => log.action === filter);

  const getActionColor = (action: string) => {
    switch (action) {
      case 'created':
        return 'bg-green-100 text-green-800';
      case 'updated':
        return 'bg-blue-100 text-blue-800';
      case 'deleted':
        return 'bg-red-100 text-red-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div>
      <div className="mb-4 flex gap-2">
        <button
          onClick={() => setFilter('all')}
          className={`px-4 py-2 rounded-md font-medium ${
            filter === 'all'
              ? 'bg-blue-600 text-white'
              : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
          }`}
        >
          All Actions
        </button>
        <button
          onClick={() => setFilter('created')}
          className={`px-4 py-2 rounded-md font-medium ${
            filter === 'created'
              ? 'bg-blue-600 text-white'
              : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
          }`}
        >
          Created
        </button>
        <button
          onClick={() => setFilter('updated')}
          className={`px-4 py-2 rounded-md font-medium ${
            filter === 'updated'
              ? 'bg-blue-600 text-white'
              : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
          }`}
        >
          Updated
        </button>
        <button
          onClick={() => setFilter('deleted')}
          className={`px-4 py-2 rounded-md font-medium ${
            filter === 'deleted'
              ? 'bg-blue-600 text-white'
              : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
          }`}
        >
          Deleted
        </button>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Timestamp
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                User
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Action
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Table
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Record ID
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Changes
              </th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {filteredLogs.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-6 py-8 text-center text-gray-500">
                  No audit logs found
                </td>
              </tr>
            ) : (
              filteredLogs.map((log: any) => (
                <tr key={log.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {new Date(log.created_at).toLocaleString()}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {log.profiles?.full_name || 'Unknown'}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2 py-1 text-xs font-medium rounded-full ${getActionColor(log.action)}`}>
                      {log.action}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {log.table_name}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500 font-mono">
                    {log.record_id ? log.record_id.substring(0, 8) + '...' : '-'}
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-500">
                    <details className="cursor-pointer">
                      <summary className="text-blue-600 hover:text-blue-700">View Details</summary>
                      <pre className="mt-2 text-xs bg-gray-50 p-2 rounded overflow-x-auto max-w-md">
                        {JSON.stringify(log.changes, null, 2)}
                      </pre>
                    </details>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
