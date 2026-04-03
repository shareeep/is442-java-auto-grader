import { Link, NavLink } from 'react-router-dom';
import {
  ClipboardCheck, Wand2, History, Menu, X,
} from 'lucide-react';
import React, { useState } from 'react';
import { useGraderStore } from '@/store/graderStore';

const Topbar: React.FC = () => {
  const phase = useGraderStore((s) => s.phase);
  const resetGrader = useGraderStore((s) => s.reset);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  const handleBrandClick = () => {
    // Force return to the upload/home workspace from persisted results state.
    if (phase === 'results') {
      resetGrader();
    }
    setMobileNavOpen(false);
  };

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center rounded-md font-sans text-sm font-medium transition-all duration-200 ${isActive
      ? 'bg-primary/15 text-primary ring-1 ring-primary/20'
      : 'text-muted-foreground hover:bg-secondary hover:text-foreground'
    }`;

  const navItems = [
    { to: '/grader', icon: ClipboardCheck, label: 'Auto-Grader', shortLabel: 'Grader' },
    { to: '/test-generator', icon: Wand2, label: 'AI Test Generator', shortLabel: 'Generator' },
    { to: '/past-runs', icon: History, label: 'Past Runs', shortLabel: 'Runs' },
  ];

  return (
    <header className="shrink-0 border-b border-border bg-card z-20 relative">
      <div className="flex items-center gap-3 px-4 py-3 sm:px-6">
        <Link
          to="/grader"
          onClick={handleBrandClick}
          className="flex min-w-0 items-center gap-2.5 select-none rounded-md px-1 py-0.5 transition-colors hover:bg-secondary"
          aria-label="Go to auto-grader home"
        >
          <span className="text-sm font-bold text-foreground tracking-tight">IS442</span>
          <span className="hidden xl:inline font-mono text-[9px] uppercase tracking-widest text-muted-foreground border border-border rounded px-1 py-0.5">
            Auto Grader
          </span>
        </Link>

        <nav className="hidden xl:flex items-center gap-1">
          {navItems.map(({ to, icon: Icon, label }) => (
            <NavLink key={to} to={to} className={({ isActive }) => `${linkClass({ isActive })} gap-2 px-3 py-1.5`}>
              <Icon size={13} />
              {label}
            </NavLink>
          ))}
        </nav>

        <nav className="hidden md:flex xl:hidden items-center gap-1">
          {navItems.map(({ to, icon: Icon, shortLabel }) => (
            <NavLink key={to} to={to} className={({ isActive }) => `${linkClass({ isActive })} gap-1.5 px-2.5 py-1.5`}>
              <Icon size={13} />
              <span>{shortLabel}</span>
            </NavLink>
          ))}
        </nav>

        <div className="ml-auto hidden 2xl:flex items-center gap-2 bg-secondary border border-border rounded-md px-2.5 py-1">
          <span className="relative flex h-1.5 w-1.5 shrink-0">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-vsc-green opacity-40" />
            <span className="relative inline-flex rounded-full h-1.5 w-1.5 bg-vsc-green" />
          </span>
          <span className="font-sans text-xs text-muted-foreground">Backend operational</span>
        </div>

        <button
          type="button"
          aria-label={mobileNavOpen ? 'Close navigation menu' : 'Open navigation menu'}
          aria-expanded={mobileNavOpen}
          onClick={() => setMobileNavOpen((open) => !open)}
          className="ml-auto inline-flex items-center justify-center rounded-md border border-border bg-card p-2 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground md:hidden"
        >
          {mobileNavOpen ? <X size={16} /> : <Menu size={16} />}
        </button>
      </div>

      {mobileNavOpen && (
        <div className="border-t border-border bg-card px-4 py-3 md:hidden">
          <nav className="flex flex-col gap-2">
            {navItems.map(({ to, icon: Icon, label }) => (
              <NavLink
                key={to}
                to={to}
                onClick={() => setMobileNavOpen(false)}
                className={({ isActive }) => `${linkClass({ isActive })} w-full px-3 py-2.5`}
              >
                <Icon size={14} />
                {label}
              </NavLink>
            ))}
          </nav>
          <div className="mt-3 flex items-center gap-2 rounded-md border border-border bg-secondary px-3 py-2">
            <span className="relative flex h-1.5 w-1.5 shrink-0">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-vsc-green opacity-40" />
              <span className="relative inline-flex rounded-full h-1.5 w-1.5 bg-vsc-green" />
            </span>
            <span className="font-sans text-xs text-muted-foreground">Backend operational</span>
          </div>
        </div>
      )}
    </header>
  );
};

export default Topbar;
