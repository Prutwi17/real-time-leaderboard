export type SportCode = "FOOTBALL" | "CRICKET" | "F1";

export type ScoreType = "POINTS" | "GOALS" | "RUNS" | "LAP_TIME" | "POSITION";

export type UserRole = "USER" | "ADMIN";

// --- Auth DTOs ---

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface LogoutRequest {
  refreshToken: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: number;
  username: string;
  role: UserRole;
}

export interface RegistrationResponse {
  message: string;
  userId: number;
  username: string;
  role: string;
}

export interface MeResponse {
  userId: number;
  username: string;
  email: string;
  role: UserRole;
}

// --- Player DTOs ---

export interface CreatePlayerRequest {
  displayName: string;
  email: string;
  bio?: string;
  profileImageUrl?: string;
}

export interface UpdatePlayerRequest {
  displayName?: string;
  email?: string;
  bio?: string;
  profileImageUrl?: string;
}

export interface PlayerResponse {
  id: number;
  displayName: string;
  email: string;
  bio: string;
  profileImageUrl: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

// --- Sport DTOs ---

export interface CreateSportRequest {
  code: SportCode;
  name: string;
  description?: string;
}

export interface UpdateSportRequest {
  name: string;
  description?: string;
}

export interface SportResponse {
  id: number;
  code: string;
  name: string;
  description: string;
  active: boolean;
}

// --- Competition DTOs ---

export interface CreateCompetitionRequest {
  name: string;
  code: string;
  description?: string;
  startDate?: string;
  endDate?: string;
}

export interface CompetitionResponse {
  id: number;
  name: string;
  code: string;
  sportId: number;
  sportCode: string;
  description: string;
  active: boolean;
  startDate: string;
  endDate: string;
}

export interface StatusUpdateRequest {
  active: boolean;
}

// --- Score DTOs ---

export interface CreateScoreRequest {
  sportId: number;
  value: number;
  eventName?: string;
  eventId?: string;
  scoreType: ScoreType;
  submissionId?: string;
}

export interface ScoreResponse {
  id: number;
  userId: number;
  sportId: number;
  value: number;
  eventName: string;
  eventId: string;
  scoreType: ScoreType;
  submissionId: string;
  recordedAt: string;
  createdAt: string;
}

// --- Leaderboard DTOs ---

export interface LeaderboardEntry {
  rank: number;
  userId: number;
  score: number;
}

export interface LeaderboardResponse {
  sport: string;
  entries: LeaderboardEntry[];
  page: number;
  size: number;
  totalPlayers: number;
}

export interface PlayerRankResponse {
  sport: string;
  userId: number;
  rank: number;
  score: number;
}

export interface SizeResponse {
  sport: string;
  totalPlayers: number;
}

// --- WebSocket ---

export interface LeaderboardSnapshot {
  sport: string;
  entries: LeaderboardEntry[];
  totalPlayers: number;
}

export interface LeaderboardUpdateMessage {
  eventId: string;
  eventType: string;
  sport: string;
  timestamp: string;
  leaderboard: LeaderboardSnapshot;
}

// --- Pagination ---

export interface PageResponse<T> {
  content?: T[];
  items?: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// --- Errors ---

export interface FieldError {
  field: string;
  message: string;
}

export interface ApiError {
  timestamp?: string;
  status: number;
  error: string;
  message: string;
  path?: string;
  fieldErrors?: FieldError[];
}

// --- Union ---

export type MessageResponse = { message: string };
