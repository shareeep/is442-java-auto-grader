import React from 'react';
import { Outlet } from 'react-router-dom';
import Topbar from './Topbar';
import { Toaster } from '@/components/ui/toast';

const Layout: React.FC = () => {
  return (
    <div className="flex flex-col h-screen w-screen bg-background overflow-hidden relative selection:bg-primary/20 selection:text-foreground">
      <div className="noise-overlay" />
      <Topbar />
      <main className="flex-1 h-0 overflow-y-auto relative z-10 custom-scrollbar">
        <Outlet />
      </main>
      <Toaster />
    </div>
  );
};

export default Layout;
