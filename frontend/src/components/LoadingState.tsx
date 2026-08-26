export default function LoadingState({ message = "Loading..." }: { message?: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16" role="status">
      <div className="mb-4 h-8 w-8 animate-spin rounded-full border-2 border-surface-600 border-t-brand-400" />
      <p className="text-sm text-surface-400">{message}</p>
    </div>
  );
}
