import { createBrowserRouter, RouterProvider, Navigate } from 'react-router-dom';
import Layout from './components/layout/Layout';
import GraderWorkspace from './pages/GraderWorkspace';
import TestGenerator from './pages/TestGenerator';
import PastRuns from './pages/PastRuns';
import RunResults from './pages/RunResults';

const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <Navigate to="/grader" replace /> },
      { path: 'grader', element: <GraderWorkspace /> },
      { path: 'test-generator', element: <TestGenerator /> },
      { path: 'past-runs', element: <PastRuns /> },
      { path: 'past-runs/:runId', element: <RunResults /> },
    ],
  },
]);

export default function App() {
  return <RouterProvider router={router} />;
}
