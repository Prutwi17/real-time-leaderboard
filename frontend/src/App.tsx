import { Routes, Route } from "react-router-dom";
import MainLayout from "./layouts/MainLayout";
import Home from "./pages/Home";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Leaderboard from "./pages/Leaderboard";
import Players from "./pages/Players";
import PlayerDetail from "./pages/PlayerDetail";
import Scores from "./pages/Scores";
import Admin from "./pages/Admin";
import ProtectedRoute from "./components/ProtectedRoute";
import AdminRoute from "./components/AdminRoute";

export default function App() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/leaderboards/:sport" element={<Leaderboard />} />
        <Route path="/players" element={<Players />} />
        <Route path="/players/:id" element={<PlayerDetail />} />
        <Route
          path="/scores"
          element={
            <ProtectedRoute>
              <Scores />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin"
          element={
            <AdminRoute>
              <Admin />
            </AdminRoute>
          }
        />
      </Route>
    </Routes>
  );
}
