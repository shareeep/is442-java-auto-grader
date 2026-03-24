import React from 'react';
import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';

const Layout: React.FC = () => {
  return (
    <div className="flex h-screen w-screen bg-background overflow-hidden relative selection:bg-accent/20 selection:text-charcoal">
      {/* Global CSS Noise Overlay */}
      <div className="noise-overlay" />

      {/* Sidebar Command Center */}
      <Sidebar />

      {/* Main Workspace Area */}
      <main className="flex-1 h-full overflow-y-auto relative z-10 p-8 md:p-12">
        <div className="max-w-6xl mx-auto w-full">
          <Outlet />
        </div>
      </main>
    </div>
  );
};

export default Layout;
