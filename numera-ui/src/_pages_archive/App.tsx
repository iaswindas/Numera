import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Layout from './components/Layout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import FileStore from './pages/FileStore'
import CustomerSearch from './pages/CustomerSearch'
import ExistingItems from './pages/ExistingItems'
import SpreadingWorkspace from './pages/SpreadingWorkspace'
import CovenantManagement from './pages/CovenantManagement'
import CovenantItems from './pages/CovenantItems'
import CovenantDashboard from './pages/CovenantDashboard'
import FormulaManagement from './pages/FormulaManagement'
import Reports from './pages/Reports'
import AdminUsers from './pages/AdminUsers'
import AdminTaxonomy from './pages/AdminTaxonomy'
import WorkflowDesigner from './pages/WorkflowDesigner'
import EmailTemplates from './pages/EmailTemplates'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<Layout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="file-store" element={<FileStore />} />
          <Route path="customers" element={<CustomerSearch />} />
          <Route path="customers/:id/items" element={<ExistingItems />} />
          <Route path="covenant-management" element={<CovenantManagement />} />
          <Route path="covenant-items" element={<CovenantItems />} />
          <Route path="covenant-dashboard" element={<CovenantDashboard />} />
          <Route path="formulas" element={<FormulaManagement />} />
          <Route path="email-templates" element={<EmailTemplates />} />
          <Route path="reports" element={<Reports />} />
          <Route path="admin/users" element={<AdminUsers />} />
          <Route path="admin/taxonomy" element={<AdminTaxonomy />} />
          <Route path="admin/workflows" element={<WorkflowDesigner />} />
        </Route>
        <Route path="/workspace" element={<SpreadingWorkspace />} />
      </Routes>
    </BrowserRouter>
  )
}
