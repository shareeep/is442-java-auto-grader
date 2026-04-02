import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { CheckCircle2, GraduationCap, LayoutPanelLeft, ListChecks, FileCheck, RotateCcw } from 'lucide-react';

import ProjectSetup from './ProjectSetup';
import InferenceReview from './InferenceReview';
import GenerationHub from './GenerationHub';
import FinalizeExport from './FinalizeExport';
import { useWizardStore } from '../../store/wizardStore';

const STEPS = [
  { id: 1, title: 'Project Setup', icon: GraduationCap },
  { id: 2, title: 'Inference & Review', icon: LayoutPanelLeft },
  { id: 3, title: 'Generation Hub', icon: ListChecks },
  { id: 4, title: 'Finalize & Export', icon: FileCheck },
];

const Wizard: React.FC = () => {
  const currentStep = useWizardStore((s) => s.currentStep);
  const setStep = useWizardStore((s) => s.setStep);
  const reset = useWizardStore((s) => s.reset);

  const [confirmingReset, setConfirmingReset] = useState(false);

  const nextStep = () => setStep(Math.min(currentStep + 1, STEPS.length));
  const prevStep = () => setStep(Math.max(currentStep - 1, 1));

  const handleReset = () => {
    reset();
    setConfirmingReset(false);
  };

  const renderStep = () => {
    switch (currentStep) {
      case 1: return <ProjectSetup onNext={nextStep} />;
      case 2: return <InferenceReview onNext={nextStep} onBack={prevStep} />;
      case 3: return <GenerationHub onNext={nextStep} onBack={prevStep} />;
      case 4: return <FinalizeExport onBack={prevStep} />;
      default: return null;
    }
  };

  return (
    <div className="flex flex-col gap-6 max-w-5xl mx-auto w-full pb-16">
      <header className="flex flex-col md:flex-row justify-between items-start md:items-end gap-4">
        <div>
          <h1 className="text-3xl font-outfit font-bold text-foreground tracking-tight">
            AI Test Generator
          </h1>
          <p className="text-muted-foreground font-sans">
            Generalized grading pipeline with automated inference.
          </p>
        </div>
        <div className="flex items-center gap-2">
          {confirmingReset ? (
            <div className="flex items-center gap-2 bg-destructive/10 border border-destructive/30 rounded-md px-3 py-1.5">
              <span className="text-xs text-destructive font-medium">All progress will be lost. Are you sure?</span>
              <Button
                variant="ghost"
                size="sm"
                onClick={handleReset}
                className="h-6 px-2 text-xs text-destructive hover:text-destructive hover:bg-destructive/20"
              >
                Yes, reset
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setConfirmingReset(false)}
                className="h-6 px-2 text-xs text-muted-foreground hover:text-foreground"
              >
                Cancel
              </Button>
            </div>
          ) : (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setConfirmingReset(true)}
              className="text-muted-foreground hover:text-foreground gap-1.5"
            >
              <RotateCcw size={13} /> Start Over
            </Button>
          )}
          <div className="flex items-center gap-2 text-xs font-mono text-primary bg-primary/10 px-3 py-1.5 rounded-md border border-primary/20">
            Step {currentStep} of {STEPS.length}
          </div>
        </div>
      </header>

      {/* Stepper UI */}
      <div className="grid grid-cols-4 gap-3 w-full">
        {STEPS.map((step) => {
          const Icon = step.icon;
          const isActive = currentStep === step.id;
          const isCompleted = currentStep > step.id;

          return (
            <div key={step.id}>
              <div className={`flex items-center gap-3 p-3 rounded-md transition-all duration-200 border ${
                isActive
                  ? 'bg-primary/15 text-primary border-primary/30 glow-blue -translate-y-0.5'
                  : isCompleted
                    ? 'bg-vsc-green/10 text-vsc-green border-vsc-green/20'
                    : 'bg-card text-muted-foreground border-border'
              }`}>
                <div className={`p-1.5 rounded ${isActive ? 'bg-primary/20' : isCompleted ? 'bg-vsc-green/10' : 'bg-secondary'}`}>
                  {isCompleted ? <CheckCircle2 size={16} /> : <Icon size={16} />}
                </div>
                <div className="hidden md:block">
                  <p className="text-[9px] uppercase tracking-wider font-mono opacity-60">Phase 0{step.id}</p>
                  <p className="text-xs font-bold whitespace-nowrap">{step.title}</p>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <div>
        {renderStep()}
      </div>
    </div>
  );
};

export default Wizard;
