type ConnectionStatus = "LIVE" | "RECONNECTING" | "OFFLINE";

interface LiveStatusProps {
  status: ConnectionStatus;
}

const config: Record<ConnectionStatus, { label: string; dot: string; text: string }> = {
  LIVE: { label: "LIVE", dot: "bg-green-400", text: "text-green-400" },
  RECONNECTING: { label: "RECONNECTING", dot: "bg-yellow-400 animate-pulse", text: "text-yellow-400" },
  OFFLINE: { label: "OFFLINE", dot: "bg-surface-500", text: "text-surface-500" },
};

export default function LiveStatus({ status }: LiveStatusProps) {
  const { label, dot, text } = config[status];
  return (
    <div className="flex items-center gap-2" role="status" aria-live="polite">
      <span className={`h-2 w-2 rounded-full ${dot}`} />
      <span className={`text-xs font-semibold uppercase tracking-wider ${text}`}>
        {label}
      </span>
    </div>
  );
}
