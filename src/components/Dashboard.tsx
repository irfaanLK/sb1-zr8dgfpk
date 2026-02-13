import { useState, useEffect } from 'react';
import { Download, TrendingUp, Users, Clock, AlertCircle } from 'lucide-react';
import { supabase, Task, Profile, Team, Market } from '../lib/supabase';

interface TaskWithDetails extends Task {
  assignee?: Profile;
  team?: Team;
  market?: Market;
}

export default function Dashboard() {
  const [period, setPeriod] = useState<'daily' | 'weekly' | 'monthly'>('daily');
  const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split('T')[0]);
  const [tasks, setTasks] = useState<TaskWithDetails[]>([]);
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [markets, setMarkets] = useState<Market[]>([]);

  useEffect(() => {
    loadData();
  }, [period, selectedDate]);

  async function loadData() {
    const dateRange = getDateRange();
    const { data: tasksData, error } = await supabase
      .from('tasks')
      .select(`
        *,
        assignee:profiles!assignee_id(*),
        team:teams(*),
        market:markets(*)
      `)
      .gte('task_date', dateRange.start)
      .lte('task_date', dateRange.end)
      .order('task_date', { ascending: false });

    if (!error && tasksData) {
      setTasks(tasksData);
    }

    const { data: profilesData } = await supabase.from('profiles').select('*');
    const { data: teamsData } = await supabase.from('teams').select('*');
    const { data: marketsData } = await supabase.from('markets').select('*');

    if (profilesData) setProfiles(profilesData);
    if (teamsData) setTeams(teamsData);
    if (marketsData) setMarkets(marketsData);
  }

  function getDateRange() {
    const date = new Date(selectedDate);
    let start = selectedDate;
    let end = selectedDate;

    if (period === 'weekly') {
      const dayOfWeek = date.getDay();
      const startOfWeek = new Date(date);
      startOfWeek.setDate(date.getDate() - dayOfWeek);
      const endOfWeek = new Date(startOfWeek);
      endOfWeek.setDate(startOfWeek.getDate() + 6);
      start = startOfWeek.toISOString().split('T')[0];
      end = endOfWeek.toISOString().split('T')[0];
    } else if (period === 'monthly') {
      const startOfMonth = new Date(date.getFullYear(), date.getMonth(), 1);
      const endOfMonth = new Date(date.getFullYear(), date.getMonth() + 1, 0);
      start = startOfMonth.toISOString().split('T')[0];
      end = endOfMonth.toISOString().split('T')[0];
    }

    return { start, end };
  }

  function getHoursByAssignee() {
    const hoursByAssignee: Record<string, { name: string; hours: number }> = {};

    tasks.forEach((task) => {
      if (task.assignee_id && task.assignee) {
        if (!hoursByAssignee[task.assignee_id]) {
          hoursByAssignee[task.assignee_id] = {
            name: task.assignee.full_name,
            hours: 0,
          };
        }
        hoursByAssignee[task.assignee_id].hours += task.total_hours || 0;
      }
    });

    return Object.values(hoursByAssignee).sort((a, b) => b.hours - a.hours);
  }

  function getTasksByPriority() {
    const priorities = { P1: 0, P2: 0, P3: 0 };
    tasks.forEach((task) => {
      if (task.priority in priorities) {
        priorities[task.priority as keyof typeof priorities]++;
      }
    });
    return priorities;
  }

  function getTasksByMarket() {
    const marketCounts: Record<string, { name: string; count: number }> = {};
    tasks.forEach((task) => {
      if (task.market && task.market_id) {
        if (!marketCounts[task.market_id]) {
          marketCounts[task.market_id] = {
            name: task.market.name,
            count: 0,
          };
        }
        marketCounts[task.market_id].count++;
      }
    });
    return Object.values(marketCounts).sort((a, b) => b.count - a.count);
  }

  function getTasksByTeam() {
    const teamData: Record<string, { name: string; hours: number; tasks: number }> = {};
    tasks.forEach((task) => {
      if (task.team && task.team_id) {
        if (!teamData[task.team_id]) {
          teamData[task.team_id] = {
            name: task.team.name,
            hours: 0,
            tasks: 0,
          };
        }
        teamData[task.team_id].hours += task.total_hours || 0;
        teamData[task.team_id].tasks++;
      }
    });
    return Object.values(teamData).sort((a, b) => b.hours - a.hours);
  }

  function exportToExcel() {
    const hoursByAssignee = getHoursByAssignee();
    const teamData = getTasksByTeam();

    let csv = 'Daily Hours Report\n\n';
    csv += 'Period:,' + period + '\n';
    csv += 'Date Range:,' + getDateRange().start + ' to ' + getDateRange().end + '\n\n';

    csv += 'Hours by Assignee\n';
    csv += 'Name,Total Hours\n';
    hoursByAssignee.forEach((item) => {
      csv += `${item.name},${item.hours.toFixed(2)}\n`;
    });

    csv += '\nHours by Team\n';
    csv += 'Team,Total Hours,Task Count\n';
    teamData.forEach((item) => {
      csv += `${item.name},${item.hours.toFixed(2)},${item.tasks}\n`;
    });

    const totalHours = tasks.reduce((sum, task) => sum + (task.total_hours || 0), 0);
    csv += '\nTotal Hours,' + totalHours.toFixed(2) + '\n';

    const blob = new Blob([csv], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `hours-report-${period}-${selectedDate}.csv`;
    a.click();
    window.URL.revokeObjectURL(url);
  }

  const hoursByAssignee = getHoursByAssignee();
  const tasksByPriority = getTasksByPriority();
  const tasksByMarket = getTasksByMarket();
  const teamData = getTasksByTeam();
  const totalHours = tasks.reduce((sum, task) => sum + (task.total_hours || 0), 0);

  return (
    <div className="p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-3xl font-bold text-gray-900">Dashboard</h1>
        <div className="flex gap-4">
          <select
            value={period}
            onChange={(e) => setPeriod(e.target.value as any)}
            className="px-3 py-2 border border-gray-300 rounded-md"
          >
            <option value="daily">Daily</option>
            <option value="weekly">Weekly</option>
            <option value="monthly">Monthly</option>
          </select>
          <input
            type="date"
            value={selectedDate}
            onChange={(e) => setSelectedDate(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-md"
          />
          <button
            onClick={exportToExcel}
            className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700"
          >
            <Download size={20} />
            Export to Excel
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <div className="flex items-center gap-3 mb-2">
            <Clock className="text-blue-600" size={24} />
            <h3 className="text-sm font-medium text-gray-600">Total Hours</h3>
          </div>
          <p className="text-3xl font-bold text-gray-900">{totalHours.toFixed(1)}h</p>
        </div>

        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <div className="flex items-center gap-3 mb-2">
            <TrendingUp className="text-green-600" size={24} />
            <h3 className="text-sm font-medium text-gray-600">Total Tasks</h3>
          </div>
          <p className="text-3xl font-bold text-gray-900">{tasks.length}</p>
        </div>

        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <div className="flex items-center gap-3 mb-2">
            <Users className="text-purple-600" size={24} />
            <h3 className="text-sm font-medium text-gray-600">Active Members</h3>
          </div>
          <p className="text-3xl font-bold text-gray-900">{hoursByAssignee.length}</p>
        </div>

        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <div className="flex items-center gap-3 mb-2">
            <AlertCircle className="text-red-600" size={24} />
            <h3 className="text-sm font-medium text-gray-600">High Priority</h3>
          </div>
          <p className="text-3xl font-bold text-gray-900">{tasksByPriority.P1}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-8">
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Hours by Assignee</h2>
          <div className="space-y-3">
            {hoursByAssignee.length === 0 ? (
              <p className="text-gray-500 text-center py-4">No data available</p>
            ) : (
              hoursByAssignee.map((item, index) => (
                <div key={index} className="flex items-center justify-between">
                  <span className="text-sm font-medium text-gray-700">{item.name}</span>
                  <div className="flex items-center gap-3 flex-1 ml-4">
                    <div className="flex-1 bg-gray-200 rounded-full h-2">
                      <div
                        className="bg-blue-600 h-2 rounded-full"
                        style={{
                          width: `${Math.min((item.hours / Math.max(...hoursByAssignee.map((a) => a.hours))) * 100, 100)}%`,
                        }}
                      />
                    </div>
                    <span className="text-sm font-semibold text-gray-900 min-w-[60px] text-right">
                      {item.hours.toFixed(1)}h
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Tasks by Priority</h2>
          <div className="space-y-4">
            <div className="flex items-center justify-between p-4 bg-red-50 rounded-lg border border-red-200">
              <div>
                <p className="text-sm font-medium text-gray-700">P1 - High Priority</p>
                <p className="text-2xl font-bold text-red-600">{tasksByPriority.P1}</p>
              </div>
            </div>
            <div className="flex items-center justify-between p-4 bg-yellow-50 rounded-lg border border-yellow-200">
              <div>
                <p className="text-sm font-medium text-gray-700">P2 - Medium Priority</p>
                <p className="text-2xl font-bold text-yellow-600">{tasksByPriority.P2}</p>
              </div>
            </div>
            <div className="flex items-center justify-between p-4 bg-green-50 rounded-lg border border-green-200">
              <div>
                <p className="text-sm font-medium text-gray-700">P3 - Low Priority</p>
                <p className="text-2xl font-bold text-green-600">{tasksByPriority.P3}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Team Performance</h2>
          <div className="space-y-3">
            {teamData.length === 0 ? (
              <p className="text-gray-500 text-center py-4">No data available</p>
            ) : (
              teamData.map((item, index) => (
                <div key={index} className="p-4 border border-gray-200 rounded-lg">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-medium text-gray-900">{item.name}</span>
                    <span className="text-sm text-gray-600">{item.tasks} tasks</span>
                  </div>
                  <div className="flex items-center gap-3">
                    <div className="flex-1 bg-gray-200 rounded-full h-2">
                      <div
                        className="bg-blue-600 h-2 rounded-full"
                        style={{
                          width: `${Math.min((item.hours / Math.max(...teamData.map((t) => t.hours))) * 100, 100)}%`,
                        }}
                      />
                    </div>
                    <span className="text-sm font-semibold text-gray-900 min-w-[60px] text-right">
                      {item.hours.toFixed(1)}h
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Tasks by Market</h2>
          <div className="space-y-3">
            {tasksByMarket.length === 0 ? (
              <p className="text-gray-500 text-center py-4">No data available</p>
            ) : (
              tasksByMarket.map((item, index) => (
                <div key={index} className="flex items-center justify-between p-3 border border-gray-200 rounded-lg">
                  <span className="font-medium text-gray-700">{item.name}</span>
                  <span className="text-lg font-bold text-gray-900">{item.count}</span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
