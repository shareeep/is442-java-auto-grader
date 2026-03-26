import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Beaker, CheckCircle2 } from 'lucide-react';
import React from 'react';

const Sidebar: React.FC = () => {
  return (
    <aside className="w-72 h-full bg-primary flex flex-col justify-between text-background border-r border-primary/20 shrink-0">
      
      {/* Top Section */}
      <div className="p-8 pb-4">
        <div className="mb-12">
          <h1 className="text-2xl font-outfit font-bold text-background tracking-tight">IS442 Studio</h1>
          <p className="font-mono text-[10px] uppercase tracking-widest bg-accent text-background inline-block px-2 py-0.5 rounded-sm mt-2 font-semibold">
            Auto Grader
          </p>
        </div>

        <nav className="flex flex-col gap-2">
          <h2 className="font-mono text-xs uppercase tracking-widest text-background/40 mb-3 px-3">
            Modules
          </h2>

          <NavLink
            to="/grader"
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-3 rounded-xl font-sans text-sm font-medium transition-all duration-300 ${
                isActive
                  ? 'bg-accent/10 text-accent ring-1 ring-accent/30'
                  : 'text-background/70 hover:bg-background/5 hover:text-background'
              }`
            }
          >
            <LayoutDashboard size={18} />
            Auto-Grader
          </NavLink>

          <NavLink
            to="/test-generator"
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-3 rounded-xl font-sans text-sm font-medium transition-all duration-300 ${
                isActive
                  ? 'bg-accent/10 text-accent ring-1 ring-accent/30'
                  : 'text-background/70 hover:bg-background/5 hover:text-background'
              }`
            }
          >
            <Beaker size={18} />
            AI Test Generator
          </NavLink>
        </nav>
      </div>

      {/* Bottom Section */}
      <div className="p-8 pt-4">
        <div className="flex items-center gap-3 bg-background/5 border border-background/10 rounded-xl p-4">
          <div className="relative flex h-3 w-3 shrink-0">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-40"></span>
            <span className="relative inline-flex rounded-full h-3 w-3 bg-green-500"></span>
          </div>
          <div className="flex flex-col">
            <span className="font-mono text-[10px] uppercase tracking-widest text-background/60">System Status</span>
            <span className="font-sans text-xs font-semibold text-background/90 flex items-center gap-1">
              Operational <CheckCircle2 size={12} className="text-green-500" />
            </span>
          </div>
        </div>
      </div>

    </aside>
  );
};

export default Sidebar;
