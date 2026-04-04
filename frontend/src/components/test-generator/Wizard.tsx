import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { CheckCircle2, GraduationCap, LayoutPanelLeft, ListChecks, FileCheck, RotateCcw } from 'lucide-react';

import ProjectSetup from './Upload';
import InferenceReview from './Review';
import GenerationHub from './Generate';
import FinalizeExport from './Export';
import { useWizardStore } from '../../store/wizardStore';

const STEPS = [
  { id: 1, title: 'Upload', icon: GraduationCap },
  { id: 2, title: 'Review', icon: LayoutPanelLeft },
  { id: 3, title: 'Generate', icon: ListChecks },
  { id: 4, title: 'Export', icon: FileCheck },
];

const Wizard: React.FC = () => {
  const currentStep = useWizardStore((s) => s.currentStep);
  const setStep = useWizardStore((s) => s.setStep);
  const reset = useWizardStore((s) => s.reset);
  const examId = useWizardStore((s) => s.examId);
  const templateId = useWizardStore((s) => s.templateId);
  const testerId = useWizardStore((s) => s.testerId);

  const [confirmingReset, setConfirmingReset] = useState(false);

  const hasNoData = !examId && !templateId && !testerId;

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
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-5 pb-16 sm:px-6 sm:py-6 lg:px-8">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-3xl font-outfit font-bold text-foreground tracking-tight">
            AI Test Generator
          </h1>
          <p className="text-muted-foreground mt-1">
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
              disabled={hasNoData}
              className="text-muted-foreground hover:text-foreground gap-1.5 disabled:opacity-30 disabled:cursor-not-allowed"
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
            <button
              key={step.id}
              onClick={() => isCompleted && setStep(step.id)}
              disabled={!isCompleted && !isActive}
              className={`w-full text-left flex items-center gap-3 p-3 rounded-md transition-all duration-200 border ${
                isActive
                  ? 'bg-primary/15 text-primary border-primary/30 glow-blue -translate-y-0.5'
                  : isCompleted
                    ? 'bg-vsc-green/10 text-vsc-green border-vsc-green/20 hover:bg-vsc-green/20 cursor-pointer'
                    : 'bg-card text-muted-foreground border-border cursor-default'
              }`}
            >
              <div className={`p-1.5 rounded ${isActive ? 'bg-primary/20' : isCompleted ? 'bg-vsc-green/10' : 'bg-secondary'}`}>
                {isCompleted ? <CheckCircle2 size={16} /> : <Icon size={16} />}
              </div>
              <div className="hidden md:block">
                <p className="text-[10px] uppercase tracking-wider font-mono opacity-60">Phase {step.id}</p>
                <p className="text-xs font-bold whitespace-nowrap">{step.title}</p>
              </div>
            </button>
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
