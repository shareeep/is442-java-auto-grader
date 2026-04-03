import React from 'react';
import { Outlet } from 'react-router-dom';
import Topbar from './Topbar';
import { Toaster } from '@/components/ui/toast';

const Layout: React.FC = () => {
  return (
    <div className="relative flex min-h-screen w-full flex-col overflow-x-hidden bg-background selection:bg-primary/20 selection:text-foreground">
      <div className="noise-overlay" />
      <Topbar />
      <main className="relative z-10 flex-1 overflow-y-auto custom-scrollbar">
        <Outlet />
      </main>
      <Toaster />
    </div>
  );
};

export default Layout;
