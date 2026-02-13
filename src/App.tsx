import { useState } from 'react';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import AuthForm from './components/AuthForm';
import Layout from './components/Layout';
import Homepage from './components/Homepage';
import DailyTracker from './components/DailyTracker';
import QATracker from './components/QATracker';
import Dashboard from './components/Dashboard';
import Attendance from './components/Attendance';
import Planning from './components/Planning';
import AdminPanel from './components/AdminPanel';

function AppContent() {
  const { user, loading } = useAuth();
  const [currentPage, setCurrentPage] = useState('home');

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <div className="w-16 h-16 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-600">Loading...</p>
        </div>
      </div>
    );
  }

  if (!user) {
    return <AuthForm />;
  }

  return (
    <Layout currentPage={currentPage} onNavigate={setCurrentPage}>
      {currentPage === 'home' && <Homepage />}
      {currentPage === 'tracker' && <DailyTracker />}
      {currentPage === 'qa-tracker' && <QATracker />}
      {currentPage === 'dashboard' && <Dashboard />}
      {currentPage === 'attendance' && <Attendance />}
      {currentPage === 'planning' && <Planning />}
      {currentPage === 'admin' && <AdminPanel />}
    </Layout>
  );
}

function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}

export default App;
