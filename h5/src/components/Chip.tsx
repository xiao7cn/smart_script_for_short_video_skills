type ChipProps = {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
  disabled?: boolean;
};

export default function Chip({ active, onClick, children, disabled }: ChipProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={[
        "px-3.5 py-1.5 text-sm rounded-full border transition-all duration-150 active:scale-95",
        disabled
          ? "border-border text-muted-foreground/40 cursor-not-allowed line-through"
          : active
            ? "border-transparent bg-accent text-accent-foreground shadow-sm shadow-accent/30 font-medium"
            : "border-border bg-card text-foreground hover:border-accent/50 hover:bg-secondary",
      ].join(" ")}
    >
      {children}
    </button>
  );
}
