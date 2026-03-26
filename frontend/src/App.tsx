import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout';
import GraderWorkspace from './pages/GraderWorkspace';
import TestGenerator from './pages/TestGenerator';

export default function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<Navigate to="/grader" replace />} />
          <Route path="grader" element={<GraderWorkspace />} />
          <Route path="test-generator" element={<TestGenerator />} />
        </Route>
      </Routes>
    </Router>
  );
}
