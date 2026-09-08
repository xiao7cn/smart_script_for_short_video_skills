type SectionCardProps = {
  step: string;
  title: string;
  hint?: string;
  children: React.ReactNode;
};

export default function SectionCard({ step, title, hint, children }: SectionCardProps) {
  return (
    <section className="border border-border bg-card">
      <header className="flex items-baseline gap-3 px-4 pt-4 pb-3 border-b border-border">
        <span className="font-mono text-xs text-primary tracking-widest">{step}</span>
        <div className="min-w-0">
          <h3 className="font-serif font-bold text-[15px] leading-tight text-foreground">
            {title}
          </h3>
          {hint && <p className="text-xs text-muted-foreground mt-0.5">{hint}</p>}
        </div>
      </header>
      <div className="p-4">{children}</div>
    </section>
  );
}
