import { useEffect } from 'react';
import { Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import HomePage from './pages/HomePage';
import SearchPage from './pages/SearchPage';
import GenrePage from './pages/GenrePage';
import SongPage from './pages/SongPage';
import PlaylistsPage from './pages/PlaylistsPage';
import PlaylistDetailPage from './pages/PlaylistDetailPage';
import UploadPage from './pages/UploadPage';
import MePage from './pages/MePage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import NowPlayingPage from './pages/NowPlayingPage';
import AdminPage from './pages/AdminPage';
import { useAuthStore } from './store/auth';

/** 全局恢复登录状态（所有页面生效，包括 /play 独立页面） */
function AuthBootstrap() {
  const initialized = useAuthStore((s) => s.initialized);
  const fetchMe = useAuthStore((s) => s.fetchMe);
  useEffect(() => {
    if (!initialized) fetchMe();
  }, [initialized, fetchMe]);
  return null;
}

export default function App() {
  return (
    <>
      <AuthBootstrap />
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/genres" element={<GenrePage />} />
          <Route path="/song/:id" element={<SongPage />} />
          <Route path="/playlists" element={<PlaylistsPage />} />
          <Route path="/playlists/:id" element={<PlaylistDetailPage />} />
          <Route path="/upload" element={<UploadPage />} />
          <Route path="/me" element={<MePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot" element={<ForgotPasswordPage />} />
          <Route path="/admin" element={<AdminPage />} />
          <Route path="*" element={<HomePage />} />
        </Route>
        {/* 全屏播放界面（独立布局，不显示顶栏/侧边栏/播放条） */}
        <Route path="/play" element={<NowPlayingPage />} />
      </Routes>
    </>
  );
}
