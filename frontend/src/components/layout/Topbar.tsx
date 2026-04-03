import { Link, NavLink } from 'react-router-dom';
import { LayoutDashboard, Beaker } from 'lucide-react';
import React from 'react';
import { useGraderStore } from '@/store/graderStore';

const Topbar: React.FC = () => {
  const phase = useGraderStore((s) => s.phase);
  const resetGrader = useGraderStore((s) => s.reset);

  const handleBrandClick = () => {
    // Force return to the upload/home workspace from persisted results state.
    if (phase === 'results') {
      resetGrader();
    }
  };

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-2 px-3 py-1.5 rounded-md font-sans text-sm font-medium transition-all duration-200 ${isActive
      ? 'bg-primary/15 text-primary ring-1 ring-primary/20'
      : 'text-muted-foreground hover:bg-secondary hover:text-foreground'
    }`;

  return (
    <header className="h-11 shrink-0 flex items-center px-4 border-b border-border bg-card z-20 relative">
      {/* Logo */}
      <Link
        to="/grader"
        onClick={handleBrandClick}
        className="flex items-center gap-2.5 mr-6 select-none rounded-md px-1 py-0.5 transition-colors hover:bg-secondary"
        aria-label="Go to auto-grader home"
      >
        <span className="text-sm font-bold text-foreground tracking-tight">IS442</span>
        <span className="font-mono text-[9px] uppercase tracking-widest text-muted-foreground border border-border rounded px-1 py-0.5">
          Auto Grader
        </span>
      </Link>

      {/* Nav links */}
      <nav className="flex items-center gap-1">
        <NavLink to="/grader" className={linkClass}>
          <LayoutDashboard size={13} />
          Auto-Grader
          {/* Grade & View Results */}
        </NavLink>
        <NavLink to="/test-generator" className={linkClass}>
          <Beaker size={13} />
          AI Test Generator
        </NavLink>
      </nav>

      {/* Status */}
      <div className="ml-auto flex items-center gap-2 bg-secondary border border-border rounded-md px-2.5 py-1">
        <span className="relative flex h-1.5 w-1.5 shrink-0">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-vsc-green opacity-40" />
          <span className="relative inline-flex rounded-full h-1.5 w-1.5 bg-vsc-green" />
        </span>
        <span className="font-sans text-xs text-muted-foreground">Backend operational</span>
      </div>
    </header>
  );
};

export default Topbar;
