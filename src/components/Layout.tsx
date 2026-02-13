import { ReactNode, useState } from 'react';
import { Home, ClipboardList, BarChart3, Calendar, Clipboard, LogOut, Menu, X, Shield, CheckCircle } from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';

interface LayoutProps {
  children: ReactNode;
  currentPage: string;
  onNavigate: (page: string) => void;
}

export default function Layout({ children, currentPage, onNavigate }: LayoutProps) {
  const { signOut, profile } = useAuth();
  const [sidebarOpen, setSidebarOpen] = useState(true);

  const baseNavItems = [
    { id: 'home', label: 'Homepage', icon: Home },
    { id: 'tracker', label: 'Daily Tracker', icon: ClipboardList },
    { id: 'qa-tracker', label: 'QA Tracker', icon: CheckCircle },
    { id: 'dashboard', label: 'Dashboard', icon: BarChart3 },
    { id: 'attendance', label: 'Attendance', icon: Calendar },
    { id: 'planning', label: 'Planning', icon: Clipboard },
  ];

  const adminNavItem = { id: 'admin', label: 'Admin Panel', icon: Shield };

  const navItems = profile?.role === 'admin'
    ? [...baseNavItems, adminNavItem]
    : baseNavItems;

  return (
    <div className="flex h-screen bg-gray-50">
      <aside
        className={`${
          sidebarOpen ? 'w-64' : 'w-20'
        } bg-white border-r border-gray-200 transition-all duration-300 flex flex-col`}
      >
        <div className="p-4 border-b border-gray-200 flex items-center justify-between">
          {sidebarOpen && <h1 className="text-2xl font-bold text-gray-900">BlueBlue</h1>}
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className="p-2 hover:bg-gray-100 rounded-md"
          >
            {sidebarOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>

        <nav className="flex-1 p-4 space-y-2">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.id}
                onClick={() => onNavigate(item.id)}
                className={`w-full flex items-center ${
                  sidebarOpen ? 'px-4' : 'justify-center'
                } py-3 rounded-md transition-colors ${
                  currentPage === item.id
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <Icon size={20} />
                {sidebarOpen && <span className="ml-3 font-medium">{item.label}</span>}
              </button>
            );
          })}
        </nav>

        <div className="p-4 border-t border-gray-200">
          {sidebarOpen && profile && (
            <div className="mb-3 px-2">
              <div className="flex items-center gap-2">
                <p className="text-sm font-medium text-gray-900">{profile.full_name}</p>
                {profile.role === 'admin' && (
                  <span className="px-2 py-0.5 bg-purple-100 text-purple-800 text-xs font-medium rounded">
                    Admin
                  </span>
                )}
              </div>
              <p className="text-xs text-gray-500">{profile.email}</p>
            </div>
          )}
          <button
            onClick={() => signOut()}
            className={`w-full flex items-center ${
              sidebarOpen ? 'px-4' : 'justify-center'
            } py-3 text-red-600 hover:bg-red-50 rounded-md transition-colors`}
          >
            <LogOut size={20} />
            {sidebarOpen && <span className="ml-3 font-medium">Sign Out</span>}
          </button>
        </div>
      </aside>

      <main className="flex-1 overflow-auto">
        {children}
      </main>
    </div>
  );
}
