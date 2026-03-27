import React, { useRef } from 'react';
import { CheckCircle2 } from 'lucide-react';

interface FileUploadProps {
  label: string;
  description: React.ReactNode;
  icon: React.ReactNode;
  isRequired: boolean;
  isDirectory?: boolean;
  accept?: string;
  count: number;
  name: string;
  className?: string;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
}

export const FileUploadCard: React.FC<FileUploadProps> = ({ label, description, icon, isRequired, isDirectory, accept, count, name, className = "h-40", onChange }) => {
  const containerRef = useRef<HTMLDivElement>(null);

  const directoryProps = isDirectory ? { webkitdirectory: "true", directory: "true" } as any : {};

  return (
    <div className="flex flex-col w-full">
      <div 
        ref={containerRef}
        className={`relative ${className} flex flex-col items-center justify-center text-center transition-all duration-300 group ${
          count > 0 ? 'bg-accent/5' : 'bg-card hover:bg-secondary'
        }`}
      >
        {/* Absolute dashed border overlay */}
        <div className={`absolute inset-0 rounded-[2rem] border-[3px] border-dashed pointer-events-none transition-colors duration-300 ${count > 0 ? 'border-accent' : 'border-primary/20 group-hover:border-primary/40'}`}></div>

        <input 
          type="file" 
          name={name}
          multiple 
          accept={accept}
          onChange={onChange}
          required={isRequired}
          {...directoryProps}
          className="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10 text-transparent outline-none" 
        />
        
        {/* Text and Icon Content grouped together */}
        <div className="flex flex-col items-center justify-center pointer-events-none px-6">
          <div className={`transition-colors duration-300 ${count > 0 ? 'text-accent scale-110' : 'text-primary/40 group-hover:text-primary/70 group-hover:-translate-y-1'}`}>
            {icon}
          </div>

          <div className="flex items-center gap-3 mt-6 mb-2">
            <h3 className="font-outfit text-2xl tracking-tight text-primary font-bold">{label}</h3>
            {isRequired && <span className="text-[10px] text-accent font-bold uppercase tracking-wider bg-accent/10 px-2 py-1 rounded">Req</span>}
          </div>
          
          <p className="font-sans text-sm text-muted-foreground mb-8 max-w-sm">{description}</p>
          
          {count > 0 ? (
            <span className="font-sans font-bold text-accent px-6 py-2.5 bg-accent/10 rounded-full text-sm flex items-center gap-2 shadow-sm">
              <CheckCircle2 size={18} /> {count} File{count > 1 ? 's' : ''} Selected
            </span>
          ) : (
            <span className="font-sans font-bold text-primary/40 text-sm group-hover:text-primary/70 transition-colors">
              Click or drag here
            </span>
          )}
        </div>
      </div>
    </div>
  );
};
