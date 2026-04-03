import { createBrowserRouter, RouterProvider, Navigate } from 'react-router-dom';
import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getBootIdOptions } from './generated/@tanstack/react-query.gen';
import Layout from './components/layout/Layout';
import GraderWorkspace from './pages/GraderWorkspace';
import TestGenerator from './pages/TestGenerator';
import PastRuns from './pages/PastRuns';
import RunResults from './pages/RunResults';
import { useWizardStore } from './store/wizardStore';

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
  const { data } = useQuery(getBootIdOptions());

  useEffect(() => {
    if (data?.bootId) {
      const store = useWizardStore.getState();
      if (store.bootId && store.bootId !== data.bootId) {
        console.log('[App] Backend boot ID changed, wiping wizard state to prevent stale phantom sessions.');
        store.reset();
      }
      store.setBootId(data.bootId);
    }
  }, [data?.bootId]);

  return <RouterProvider router={router} />;
}
