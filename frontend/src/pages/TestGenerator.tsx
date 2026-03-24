import React from 'react';
import { Hammer } from 'lucide-react';

const TestGenerator: React.FC = () => {
  return (
    <div className="flex flex-col gap-8 h-full">
      <header>
        <h1 className="text-4xl md:text-5xl font-outfit font-bold text-primary mb-2 tracking-tight">
          AI Test Generator
        </h1>
        <p className="text-charcoal/60 font-sans text-lg max-w-2xl">
          Algorithmic test case generation mapped to internal Spring Boot DTOs.
        </p>
      </header>

      <div className="flex flex-col items-center justify-center flex-1 min-h-[400px] glass-card border border-primary/10 bg-primary/5 rounded-[2rem] p-12 text-center">
        <div className="w-16 h-16 bg-accent/10 rounded-2xl flex items-center justify-center mb-6 text-accent">
          <Hammer size={32} />
        </div>
        <h2 className="text-2xl font-outfit font-bold text-primary mb-2">Development in Progress</h2>
        <p className="text-charcoal/50 font-sans max-w-md mx-auto">
          This UI route is securely reserved for external framework injection. Wiring ready for Spring Boot API integration.
        </p>
      </div>
    </div>
  );
};

export default TestGenerator;
