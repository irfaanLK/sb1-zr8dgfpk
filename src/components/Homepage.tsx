import { useState, useEffect } from 'react';
import { Clock, Plus, X, Edit2, ExternalLink, AlertCircle } from 'lucide-react';
import { supabase, Announcement, UsefulLink, HomepageTimezone } from '../lib/supabase';
import { useAuth } from '../contexts/AuthContext';
import ConfirmDialog from './ConfirmDialog';

export default function Homepage() {
  const [time, setTime] = useState(new Date());
  const [announcements, setAnnouncements] = useState<Announcement[]>([]);
  const [links, setLinks] = useState<UsefulLink[]>([]);
  const [timezones, setTimezones] = useState<HomepageTimezone[]>([]);
  const [showAnnouncementForm, setShowAnnouncementForm] = useState(false);
  const [showLinkForm, setShowLinkForm] = useState(false);
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
  const isAdmin = profile?.role === 'admin';

  useEffect(() => {
    const timer = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    loadAnnouncements();
    loadLinks();
    loadTimezones();
  }, []);

  async function loadTimezones() {
    const { data, error } = await supabase
      .from('homepage_timezones')
      .select('*')
      .eq('is_active', true)
      .order('display_order');

    if (!error && data) {
      setTimezones(data);
    }
  }

  async function loadAnnouncements() {
    const { data, error } = await supabase
      .from('announcements')
      .select('*')
      .eq('is_active', true)
      .order('created_at', { ascending: false });

    if (!error && data) {
      setAnnouncements(data);
    }
  }

  async function loadLinks() {
    const { data, error } = await supabase
      .from('useful_links')
      .select('*')
      .order('created_at', { ascending: false });

    if (!error && data) {
      setLinks(data);
    }
  }

  async function addAnnouncement(title: string, content: string, priority: string) {
    const { error } = await supabase
      .from('announcements')
      .insert([{ title, content, priority, created_by: profile?.id }]);

    if (!error) {
      loadAnnouncements();
      setShowAnnouncementForm(false);
    }
  }

  async function addLink(title: string, url: string, description: string, category: string) {
    const { error } = await supabase
      .from('useful_links')
      .insert([{ title, url, description, category, created_by: profile?.id }]);

    if (!error) {
      loadLinks();
      setShowLinkForm(false);
    }
  }

  async function deleteAnnouncement(id: string) {
    setConfirmDialog({
      isOpen: true,
      title: 'Delete Announcement',
      message: 'Are you sure you want to delete this announcement? This action cannot be undone.',
      onConfirm: async () => {
        const { error } = await supabase
          .from('announcements')
          .delete()
          .eq('id', id);

        if (!error) {
          loadAnnouncements();
        }
        setConfirmDialog({ ...confirmDialog, isOpen: false });
      },
    });
  }

  async function deleteLink(id: string) {
    setConfirmDialog({
      isOpen: true,
      title: 'Delete Link',
      message: 'Are you sure you want to delete this link? This action cannot be undone.',
      onConfirm: async () => {
        const { error } = await supabase
          .from('useful_links')
          .delete()
          .eq('id', id);

        if (!error) {
          loadLinks();
        }
        setConfirmDialog({ ...confirmDialog, isOpen: false });
      },
    });
  }

  function getTimeZoneTime(timezone: string) {
    return new Intl.DateTimeFormat('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
      timeZone: timezone,
    }).format(time);
  }

  return (
    <div className="p-8 max-w-7xl mx-auto">
      <div className="mb-8 flex items-center justify-between">
        <h1 className="text-4xl font-bold text-gray-900">BlueBlue</h1>

        <div className="flex gap-6">
          {timezones.map((tz) => (
            <div key={tz.id} className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 min-w-[160px]">
              <div className="flex items-center gap-2 mb-2">
                <Clock size={16} className="text-gray-500" />
                <span className="text-sm font-medium text-gray-600">{tz.label}</span>
              </div>
              <div className="text-2xl font-mono font-semibold text-gray-900">
                {getTimeZoneTime(tz.timezone)}
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-semibold text-gray-900">Announcements</h2>
            {isAdmin && (
              <button
                onClick={() => setShowAnnouncementForm(true)}
                className="flex items-center gap-2 px-3 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors text-sm"
              >
                <Plus size={16} />
                Add
              </button>
            )}
          </div>

          {showAnnouncementForm && (
            <AnnouncementForm
              onSubmit={addAnnouncement}
              onCancel={() => setShowAnnouncementForm(false)}
            />
          )}

          <div className="space-y-4 mt-4">
            {announcements.length === 0 ? (
              <p className="text-gray-500 text-center py-8">No announcements yet</p>
            ) : (
              announcements.map((announcement) => (
                <div
                  key={announcement.id}
                  className={`p-4 rounded-md border-l-4 ${
                    announcement.priority === 'High'
                      ? 'bg-red-50 border-red-500'
                      : announcement.priority === 'Medium'
                      ? 'bg-yellow-50 border-yellow-500'
                      : 'bg-blue-50 border-blue-500'
                  }`}
                >
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <AlertCircle size={16} />
                        <h3 className="font-semibold text-gray-900">{announcement.title}</h3>
                      </div>
                      <p className="text-gray-700 text-sm">{announcement.content}</p>
                      <p className="text-xs text-gray-500 mt-2">
                        {new Date(announcement.created_at).toLocaleDateString()}
                      </p>
                    </div>
                    {(announcement.created_by === profile?.id || isAdmin) && (
                      <button
                        onClick={() => deleteAnnouncement(announcement.id)}
                        className="text-gray-400 hover:text-red-600"
                      >
                        <X size={16} />
                      </button>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-semibold text-gray-900">Useful Links</h2>
            {isAdmin && (
              <button
                onClick={() => setShowLinkForm(true)}
                className="flex items-center gap-2 px-3 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors text-sm"
              >
                <Plus size={16} />
                Add
              </button>
            )}
          </div>

          {showLinkForm && (
            <LinkForm
              onSubmit={addLink}
              onCancel={() => setShowLinkForm(false)}
            />
          )}

          <div className="space-y-3 mt-4">
            {links.length === 0 ? (
              <p className="text-gray-500 text-center py-8">No links yet</p>
            ) : (
              links.map((link) => (
                <div
                  key={link.id}
                  className="p-3 rounded-md border border-gray-200 hover:border-blue-300 transition-colors"
                >
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <a
                        href={link.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center gap-2 text-blue-600 hover:text-blue-700 font-medium"
                      >
                        <ExternalLink size={16} />
                        {link.title}
                      </a>
                      {link.description && (
                        <p className="text-sm text-gray-600 mt-1">{link.description}</p>
                      )}
                      <span className="inline-block mt-2 px-2 py-1 bg-gray-100 text-gray-600 text-xs rounded">
                        {link.category}
                      </span>
                    </div>
                    {(link.created_by === profile?.id || isAdmin) && (
                      <button
                        onClick={() => deleteLink(link.id)}
                        className="text-gray-400 hover:text-red-600"
                      >
                        <X size={16} />
                      </button>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

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

function AnnouncementForm({
  onSubmit,
  onCancel,
}: {
  onSubmit: (title: string, content: string, priority: string) => void;
  onCancel: () => void;
}) {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [priority, setPriority] = useState('Medium');

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    onSubmit(title, content, priority);
    setTitle('');
    setContent('');
    setPriority('Medium');
  }

  return (
    <form onSubmit={handleSubmit} className="bg-gray-50 p-4 rounded-md mb-4">
      <input
        type="text"
        placeholder="Title"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        className="w-full px-3 py-2 border border-gray-300 rounded-md mb-3"
        required
      />
      <textarea
        placeholder="Content"
        value={content}
        onChange={(e) => setContent(e.target.value)}
        className="w-full px-3 py-2 border border-gray-300 rounded-md mb-3"
        rows={3}
        required
      />
      <select
        value={priority}
        onChange={(e) => setPriority(e.target.value)}
        className="w-full px-3 py-2 border border-gray-300 rounded-md mb-3"
      >
        <option value="Low">Low</option>
        <option value="Medium">Medium</option>
        <option value="High">High</option>
      </select>
      <div className="flex gap-2">
        <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700">
          Add
        </button>
        <button type="button" onClick={onCancel} className="px-4 py-2 bg-gray-300 text-gray-700 rounded-md hover:bg-gray-400">
          Cancel
        </button>
      </div>
    </form>
  );
}

function LinkForm({
  onSubmit,
  onCancel,
}: {
  onSubmit: (title: string, url: string, description: string, category: string) => void;
  onCancel: () => void;
}) {
  const [title, setTitle] = useState('');
  const [url, setUrl] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('General');

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    onSubmit(title, url, description, category);
    setTitle('');
    setUrl('');
    setDescription('');
    setCategory('General');
  }

  return (
    <form onSubmit={handleSubmit} className="bg-gray-50 p-4 rounded-md mb-4">
      <input
        type="text"
        placeholder="Title"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        className="w-full px-3 py-2 border border-gray-300 rounded-md mb-3"
        required
      />
      <input
        type="url"
        placeholder="URL"
        value={url}
        onChange={(e) => setUrl(e.target.value)}
        className="w-full px-3 py-2 border border-gray-300 rounded-md mb-3"
        required
      />
      <textarea
        placeholder="Description (optional)"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        className="w-full px-3 py-2 border border-gray-300 rounded-md mb-3"
        rows={2}
      />
      <input
        type="text"
        placeholder="Category"
        value={category}
        onChange={(e) => setCategory(e.target.value)}
        className="w-full px-3 py-2 border border-gray-300 rounded-md mb-3"
      />
      <div className="flex gap-2">
        <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700">
          Add
        </button>
        <button type="button" onClick={onCancel} className="px-4 py-2 bg-gray-300 text-gray-700 rounded-md hover:bg-gray-400">
          Cancel
        </button>
      </div>
    </form>
  );
}
