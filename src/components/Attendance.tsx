import { useState, useEffect } from 'react';
import { ChevronLeft, ChevronRight, Download, AlertTriangle } from 'lucide-react';
import { supabase, Attendance as AttendanceType, Profile } from '../lib/supabase';
import { useAuth } from '../contexts/AuthContext';

const ATTENDANCE_OPTIONS = [
  'Office',
  'WFH',
  'Sick',
  'Local Leave',
  'Business Trip / Training',
  'Half-Day',
];

export default function Attendance() {
  const { user, profile } = useAuth();
  const [currentDate, setCurrentDate] = useState(new Date());
  const [view, setView] = useState<'month' | 'week'>('month');
  const [attendanceData, setAttendanceData] = useState<AttendanceType[]>([]);
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [selectedUser, setSelectedUser] = useState<string>('all');

  const isAdmin = profile?.role === 'admin';

  useEffect(() => {
    loadProfiles();
  }, []);

  useEffect(() => {
    loadAttendance();
  }, [currentDate, view, selectedUser]);

  async function loadProfiles() {
    const { data, error } = await supabase
      .from('profiles')
      .select('*')
      .order('full_name');

    if (!error && data) {
      setProfiles(data);
    }
  }

  async function loadAttendance() {
    const dateRange = getDateRange();
    let query = supabase
      .from('attendance')
      .select('*')
      .gte('attendance_date', dateRange.start)
      .lte('attendance_date', dateRange.end);

    if (selectedUser !== 'all') {
      query = query.eq('user_id', selectedUser);
    }

    const { data, error } = await query;

    if (!error && data) {
      setAttendanceData(data);
    }
  }

  function getDateRange() {
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();

    if (view === 'month') {
      const start = new Date(year, month, 1).toISOString().split('T')[0];
      const end = new Date(year, month + 1, 0).toISOString().split('T')[0];
      return { start, end };
    } else {
      const dayOfWeek = currentDate.getDay();
      const startOfWeek = new Date(currentDate);
      startOfWeek.setDate(currentDate.getDate() - dayOfWeek);
      const endOfWeek = new Date(startOfWeek);
      endOfWeek.setDate(startOfWeek.getDate() + 6);
      return {
        start: startOfWeek.toISOString().split('T')[0],
        end: endOfWeek.toISOString().split('T')[0],
      };
    }
  }

  async function updateAttendance(userId: string, date: string, status: string) {
    const { data: existing } = await supabase
      .from('attendance')
      .select('*')
      .eq('user_id', userId)
      .eq('attendance_date', date)
      .maybeSingle();

    if (existing) {
      await supabase
        .from('attendance')
        .update({ status, updated_at: new Date().toISOString() })
        .eq('id', existing.id);
    } else {
      await supabase
        .from('attendance')
        .insert([{ user_id: userId, attendance_date: date, status }]);
    }

    loadAttendance();
  }

  function getAttendanceForUserAndDate(userId: string, date: string): string | null {
    const record = attendanceData.find(
      (a) => a.user_id === userId && a.attendance_date === date
    );
    return record?.status || null;
  }

  function getOfficeDaysInCycle(userId: string): number {
    const today = new Date();
    const currentDay = today.getDate();

    let cycleStart: Date;
    let cycleEnd: Date;

    if (currentDay >= 15) {
      cycleStart = new Date(today.getFullYear(), today.getMonth(), 15);
      cycleEnd = new Date(today.getFullYear(), today.getMonth() + 1, 14);
    } else {
      cycleStart = new Date(today.getFullYear(), today.getMonth() - 1, 15);
      cycleEnd = new Date(today.getFullYear(), today.getMonth(), 14);
    }

    const officeDays = attendanceData.filter(
      (a) =>
        a.user_id === userId &&
        a.status === 'Office' &&
        new Date(a.attendance_date) >= cycleStart &&
        new Date(a.attendance_date) <= cycleEnd
    );

    return officeDays.length;
  }

  function navigateMonth(direction: number) {
    const newDate = new Date(currentDate);
    if (view === 'month') {
      newDate.setMonth(currentDate.getMonth() + direction);
    } else {
      newDate.setDate(currentDate.getDate() + direction * 7);
    }
    setCurrentDate(newDate);
  }

  function getDaysInMonth(): Date[] {
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const days: Date[] = [];

    const startPadding = firstDay.getDay();
    for (let i = startPadding - 1; i >= 0; i--) {
      const date = new Date(year, month, -i);
      days.push(date);
    }

    for (let day = 1; day <= lastDay.getDate(); day++) {
      days.push(new Date(year, month, day));
    }

    return days;
  }

  function getWeekDays(): Date[] {
    const dayOfWeek = currentDate.getDay();
    const startOfWeek = new Date(currentDate);
    startOfWeek.setDate(currentDate.getDate() - dayOfWeek);

    const days: Date[] = [];
    for (let i = 0; i < 7; i++) {
      const date = new Date(startOfWeek);
      date.setDate(startOfWeek.getDate() + i);
      days.push(date);
    }
    return days;
  }

  function exportAttendance() {
    let csv = 'Attendance Report\n\n';
    csv += 'Name,Office Days in Cycle,Status\n';

    const usersToExport = selectedUser === 'all' ? profiles : profiles.filter((p) => p.id === selectedUser);

    usersToExport.forEach((profile) => {
      const officeDays = getOfficeDaysInCycle(profile.id);
      const status = officeDays >= 12 ? 'Compliant' : 'Below Requirement';
      csv += `${profile.full_name},${officeDays},${status}\n`;
    });

    const blob = new Blob([csv], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `attendance-report-${currentDate.toISOString().split('T')[0]}.csv`;
    a.click();
    window.URL.revokeObjectURL(url);
  }

  const days = view === 'month' ? getDaysInMonth() : getWeekDays();
  const displayUsers = selectedUser === 'all' ? profiles : profiles.filter((p) => p.id === selectedUser);

  return (
    <div className="p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-3xl font-bold text-gray-900">Attendance</h1>
        <div className="flex gap-4">
          <select
            value={view}
            onChange={(e) => setView(e.target.value as any)}
            className="px-3 py-2 border border-gray-300 rounded-md"
          >
            <option value="month">Month View</option>
            <option value="week">Week View</option>
          </select>
          <select
            value={selectedUser}
            onChange={(e) => setSelectedUser(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-md"
          >
            <option value="all">All Users</option>
            {profiles.map((p) => (
              <option key={p.id} value={p.id}>
                {p.full_name}
              </option>
            ))}
          </select>
          <button
            onClick={exportAttendance}
            className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700"
          >
            <Download size={20} />
            Export
          </button>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
        <div className="flex items-center justify-between mb-6">
          <button
            onClick={() => navigateMonth(-1)}
            className="p-2 hover:bg-gray-100 rounded-md"
          >
            <ChevronLeft size={20} />
          </button>
          <h2 className="text-xl font-semibold text-gray-900">
            {currentDate.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })}
          </h2>
          <button
            onClick={() => navigateMonth(1)}
            className="p-2 hover:bg-gray-100 rounded-md"
          >
            <ChevronRight size={20} />
          </button>
        </div>

        <div className="mb-6 p-4 bg-blue-50 border border-blue-200 rounded-lg">
          <p className="text-sm text-blue-900">
            <strong>Note:</strong> Each team member must have 12 mandatory office attendances between the 15th of one month and the 15th of the next month.
            {isAdmin ? ' As an admin, you can edit all attendance records.' : ' You can only edit your own attendance records.'}
          </p>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full border-collapse">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="text-left p-3 font-medium text-gray-700 sticky left-0 bg-white">
                  Team Member
                </th>
                {days.map((day, index) => {
                  const isCurrentMonth = day.getMonth() === currentDate.getMonth();
                  return (
                    <th
                      key={index}
                      className={`p-3 text-center text-xs ${
                        isCurrentMonth ? 'text-gray-700' : 'text-gray-400'
                      }`}
                    >
                      <div>{day.toLocaleDateString('en-US', { weekday: 'short' })}</div>
                      <div className="font-semibold">{day.getDate()}</div>
                    </th>
                  );
                })}
                <th className="text-center p-3 font-medium text-gray-700 bg-gray-50">
                  Office Days
                </th>
              </tr>
            </thead>
            <tbody>
              {displayUsers.map((displayUser) => {
                const officeDays = getOfficeDaysInCycle(displayUser.id);
                const isBelowRequirement = officeDays < 12;

                return (
                  <tr key={displayUser.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="p-3 font-medium text-gray-900 sticky left-0 bg-white">
                      <div className="flex items-center gap-2">
                        {displayUser.full_name}
                        {isBelowRequirement && (
                          <AlertTriangle size={16} className="text-red-500" title="Below 12 office days requirement" />
                        )}
                      </div>
                    </td>
                    {days.map((day, index) => {
                      const dateStr = day.toISOString().split('T')[0];
                      const status = getAttendanceForUserAndDate(displayUser.id, dateStr);
                      const isCurrentMonth = day.getMonth() === currentDate.getMonth();

                      const canEdit = displayUser.id === user?.id || isAdmin;

                      return (
                        <td
                          key={index}
                          className={`p-1 ${!isCurrentMonth ? 'bg-gray-50' : ''}`}
                        >
                          <select
                            value={status || ''}
                            onChange={(e) => updateAttendance(displayUser.id, dateStr, e.target.value)}
                            disabled={!canEdit}
                            className={`w-full px-2 py-1 text-xs border rounded ${
                              !canEdit ? 'cursor-not-allowed opacity-60' : ''
                            } ${
                              status === 'Office'
                                ? 'bg-green-100 border-green-300 text-green-800'
                                : status === 'WFH'
                                ? 'bg-blue-100 border-blue-300 text-blue-800'
                                : status === 'Sick'
                                ? 'bg-red-100 border-red-300 text-red-800'
                                : status === 'Local Leave'
                                ? 'bg-yellow-100 border-yellow-300 text-yellow-800'
                                : status === 'Business Trip / Training'
                                ? 'bg-purple-100 border-purple-300 text-purple-800'
                                : status === 'Half-Day'
                                ? 'bg-orange-100 border-orange-300 text-orange-800'
                                : 'bg-white border-gray-200'
                            }`}
                          >
                            <option value="">-</option>
                            {ATTENDANCE_OPTIONS.map((option) => (
                              <option key={option} value={option}>
                                {option}
                              </option>
                            ))}
                          </select>
                        </td>
                      );
                    })}
                    <td className="p-3 text-center bg-gray-50">
                      <span
                        className={`font-semibold ${
                          isBelowRequirement ? 'text-red-600' : 'text-green-600'
                        }`}
                      >
                        {officeDays}/12
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">Legend</h3>
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
          {ATTENDANCE_OPTIONS.map((option) => (
            <div
              key={option}
              className={`px-3 py-2 rounded text-xs font-medium border ${
                option === 'Office'
                  ? 'bg-green-100 border-green-300 text-green-800'
                  : option === 'WFH'
                  ? 'bg-blue-100 border-blue-300 text-blue-800'
                  : option === 'Sick'
                  ? 'bg-red-100 border-red-300 text-red-800'
                  : option === 'Local Leave'
                  ? 'bg-yellow-100 border-yellow-300 text-yellow-800'
                  : option === 'Business Trip / Training'
                  ? 'bg-purple-100 border-purple-300 text-purple-800'
                  : 'bg-orange-100 border-orange-300 text-orange-800'
              }`}
            >
              {option}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
