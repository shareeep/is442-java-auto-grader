import * as React from 'react';
import * as ToastPrimitives from '@radix-ui/react-toast';
import { X } from 'lucide-react';

const ToastProvider = ToastPrimitives.Provider;
const ToastViewport = React.forwardRef<
  React.ElementRef<typeof ToastPrimitives.Viewport>,
  React.ComponentPropsWithoutRef<typeof ToastPrimitives.Viewport>
>(({ className, ...props }, ref) => (
  <ToastPrimitives.Viewport
    ref={ref}
    className={`fixed bottom-4 right-4 z-[100] flex flex-col gap-2 w-[380px] max-w-[100vw] ${className ?? ''}`}
    {...props}
  />
));
ToastViewport.displayName = ToastPrimitives.Viewport.displayName;

const Toast = React.forwardRef<
  React.ElementRef<typeof ToastPrimitives.Root>,
  React.ComponentPropsWithoutRef<typeof ToastPrimitives.Root> & { variant?: 'default' | 'destructive' }
>(({ className, variant = 'default', ...props }, ref) => (
  <ToastPrimitives.Root
    ref={ref}
    className={`
      group pointer-events-auto relative flex w-full items-start gap-3 overflow-hidden rounded-md border p-4 shadow-lg
      animate-in slide-in-from-bottom-2 data-[swipe=end]:animate-out data-[swipe=end]:slide-out-to-right-full
      ${variant === 'destructive'
        ? 'border-destructive/30 bg-destructive/10 text-destructive'
        : 'border-border bg-card text-foreground'}
      ${className ?? ''}
    `}
    {...props}
  />
));
Toast.displayName = ToastPrimitives.Root.displayName;

const ToastClose = React.forwardRef<
  React.ElementRef<typeof ToastPrimitives.Close>,
  React.ComponentPropsWithoutRef<typeof ToastPrimitives.Close>
>(({ className, ...props }, ref) => (
  <ToastPrimitives.Close
    ref={ref}
    className={`shrink-0 opacity-50 hover:opacity-100 transition-opacity ${className ?? ''}`}
    toast-close=""
    {...props}
  >
    <X size={14} />
  </ToastPrimitives.Close>
));
ToastClose.displayName = ToastPrimitives.Close.displayName;

const ToastTitle = React.forwardRef<
  React.ElementRef<typeof ToastPrimitives.Title>,
  React.ComponentPropsWithoutRef<typeof ToastPrimitives.Title>
>(({ className, ...props }, ref) => (
  <ToastPrimitives.Title
    ref={ref}
    className={`text-sm font-semibold font-outfit ${className ?? ''}`}
    {...props}
  />
));
ToastTitle.displayName = ToastPrimitives.Title.displayName;

const ToastDescription = React.forwardRef<
  React.ElementRef<typeof ToastPrimitives.Description>,
  React.ComponentPropsWithoutRef<typeof ToastPrimitives.Description>
>(({ className, ...props }, ref) => (
  <ToastPrimitives.Description
    ref={ref}
    className={`text-xs text-muted-foreground ${className ?? ''}`}
    {...props}
  />
));
ToastDescription.displayName = ToastPrimitives.Description.displayName;

// ── Toast hook ────────────────────────────────────────────────────────────────

type ToastData = {
  id: string;
  title?: string;
  description?: string;
  variant?: 'default' | 'destructive';
  open: boolean;
};

type ToastInput = Omit<ToastData, 'id' | 'open'>;

let listeners: Array<(toasts: ToastData[]) => void> = [];
let toasts: ToastData[] = [];

function dispatch(toast: ToastData) {
  toasts = [...toasts, toast];
  listeners.forEach((l) => l(toasts));
}

function dismiss(id: string) {
  toasts = toasts.map((t) => (t.id === id ? { ...t, open: false } : t));
  listeners.forEach((l) => l(toasts));
}

export function toast(input: ToastInput) {
  const id = Math.random().toString(36).slice(2);
  dispatch({ ...input, id, open: true });
  setTimeout(() => dismiss(id), 4000);
}

export function useToast() {
  const [toastList, setToastList] = React.useState<ToastData[]>(toasts);

  React.useEffect(() => {
    listeners.push(setToastList);
    return () => {
      listeners = listeners.filter((l) => l !== setToastList);
    };
  }, []);

  return { toasts: toastList, dismiss };
}

// ── Toaster component ─────────────────────────────────────────────────────────

export function Toaster() {
  const { toasts: toastList, dismiss } = useToast();

  return (
    <ToastProvider>
      {toastList.map(({ id, title, description, variant, open }) => (
        <Toast key={id} open={open} onOpenChange={(o) => { if (!o) dismiss(id); }} variant={variant}>
          <div className="flex-1 min-w-0">
            {title && <ToastTitle>{title}</ToastTitle>}
            {description && <ToastDescription>{description}</ToastDescription>}
          </div>
          <ToastClose />
        </Toast>
      ))}
      <ToastViewport />
    </ToastProvider>
  );
}
