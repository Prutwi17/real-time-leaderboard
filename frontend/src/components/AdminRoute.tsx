import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import LoadingState from "./LoadingState";
import type { ReactNode } from "react";

export default function AdminRoute({ children }: { children: ReactNode }) {
  const { user, isAuthenticated, isLoading } = useAuth();

  if (isLoading) return <LoadingState message="Checking session..." />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (user?.role !== "ADMIN") return <Navigate to="/" replace />;

  return <>{children}</>;
}
