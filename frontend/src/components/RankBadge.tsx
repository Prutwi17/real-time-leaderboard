interface RankBadgeProps {
  rank: number;
}

export default function RankBadge({ rank }: RankBadgeProps) {
  if (rank === 1) {
    return (
      <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-yellow-500/20 text-sm font-bold text-yellow-400">
        1
      </span>
    );
  }
  if (rank === 2) {
    return (
      <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-surface-400/20 text-sm font-bold text-surface-300">
        2
      </span>
    );
  }
  if (rank === 3) {
    return (
      <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-orange-700/20 text-sm font-bold text-orange-400">
        3
      </span>
    );
  }
  return (
    <span className="inline-flex h-8 w-8 items-center justify-center text-sm font-medium text-surface-400">
      {rank}
    </span>
  );
}
