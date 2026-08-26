import api from "./client";
import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  RegistrationResponse,
  MeResponse,
  MessageResponse,
  PageResponse,
  PlayerResponse,
  SportResponse,
  CompetitionResponse,
  CreateSportRequest,
  UpdateSportRequest,
  CreateCompetitionRequest,
  CreateScoreRequest,
  ScoreResponse,
  LeaderboardResponse,
  PlayerRankResponse,
  SizeResponse,
  LeaderboardEntry,
  StatusUpdateRequest,
} from "../types";

// --- Auth ---

export const authApi = {
  register: (data: RegisterRequest) =>
    api.post<RegistrationResponse>("/api/auth/register", data),

  login: (data: LoginRequest) =>
    api.post<AuthResponse>("/api/auth/login", data),

  refresh: (refreshToken: string) =>
    api.post<AuthResponse>("/api/auth/refresh", { refreshToken }),

  logout: (refreshToken: string) =>
    api.post<MessageResponse>("/api/auth/logout", { refreshToken }),

  me: () => api.get<MeResponse>("/api/auth/me"),
};

// --- Players ---

export const playerApi = {
  get: (id: number) => api.get<PlayerResponse>(`/api/players/${id}`),

  list: (page = 0, size = 20, search?: string) =>
    api.get<PageResponse<PlayerResponse>>("/api/players", {
      params: { page, size, search },
    }),

  create: (data: { displayName: string; email: string; bio?: string }) =>
    api.post<PlayerResponse>("/api/players", data),

  update: (id: number, data: { displayName?: string; email?: string; bio?: string }) =>
    api.put<PlayerResponse>(`/api/players/${id}`, data),

  deactivate: (id: number) =>
    api.put(`/api/players/${id}/deactivate`),

  activate: (id: number) =>
    api.put(`/api/players/${id}/activate`),

  delete: (id: number) =>
    api.delete(`/api/players/${id}`),
};

// --- Sports ---

export const sportApi = {
  list: () => api.get<SportResponse[]>("/api/sports"),

  get: (id: number) => api.get<SportResponse>(`/api/sports/${id}`),

  getByCode: (code: string) =>
    api.get<SportResponse>(`/api/sports/code/${code}`),

  create: (data: CreateSportRequest) =>
    api.post<SportResponse>("/api/sports", data),

  update: (id: number, data: UpdateSportRequest) =>
    api.put<SportResponse>(`/api/sports/${id}`, data),

  setStatus: (id: number, data: StatusUpdateRequest) =>
    api.patch<SportResponse>(`/api/sports/${id}/status`, data),

  delete: (id: number) =>
    api.delete(`/api/sports/${id}`),

  competitions: (sportId: number) =>
    api.get<CompetitionResponse[]>(`/api/sports/${sportId}/competitions`),

  createCompetition: (sportId: number, data: CreateCompetitionRequest) =>
    api.post<CompetitionResponse>(`/api/sports/${sportId}/competitions`, data),
};

// --- Competitions ---

export const competitionApi = {
  list: () => api.get<CompetitionResponse[]>("/api/competitions"),

  get: (id: number) =>
    api.get<CompetitionResponse>(`/api/competitions/${id}`),

  update: (id: number, data: { name: string; description?: string }) =>
    api.put<CompetitionResponse>(`/api/competitions/${id}`, data),

  setStatus: (id: number, data: StatusUpdateRequest) =>
    api.patch<CompetitionResponse>(`/api/competitions/${id}/status`, data),

  delete: (id: number) =>
    api.delete(`/api/competitions/${id}`),
};

// --- Scores ---

export const scoreApi = {
  submit: (data: CreateScoreRequest) =>
    api.post<ScoreResponse>("/api/scores", data),

  mine: (page = 0, size = 20) =>
    api.get<PageResponse<ScoreResponse>>("/api/scores/me", {
      params: { page, size },
    }),

  get: (id: number) => api.get<ScoreResponse>(`/api/scores/${id}`),

  list: (params: {
    userId?: number;
    sportId?: number;
    page?: number;
    size?: number;
  } = {}) =>
    api.get<PageResponse<ScoreResponse>>("/api/scores", { params }),

  delete: (id: number) =>
    api.delete(`/api/scores/${id}`),
};

// --- Leaderboards ---

export const leaderboardApi = {
  top: (sport: string, limit = 10) =>
    api.get<LeaderboardResponse>(`/api/leaderboards/${sport}/top`, {
      params: { limit },
    }),

  page: (sport: string, page = 0, size = 20) =>
    api.get<LeaderboardResponse>(`/api/leaderboards/${sport}`, {
      params: { page, size },
    }),

  myRank: (sport: string) =>
    api.get<PlayerRankResponse>(`/api/leaderboards/${sport}/me`),

  playerRank: (sport: string, userId: number) =>
    api.get<PlayerRankResponse>(
      `/api/leaderboards/${sport}/players/${userId}/rank`,
    ),

  nearby: (sport: string, userId: number, range = 2) =>
    api.get<LeaderboardEntry[]>(
      `/api/leaderboards/${sport}/players/${userId}/nearby`,
      { params: { range } },
    ),

  size: (sport: string) =>
    api.get<SizeResponse>(`/api/leaderboards/${sport}/size`),
};
