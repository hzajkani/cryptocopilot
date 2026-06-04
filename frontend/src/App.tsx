import { Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { MarketsPage } from './pages/MarketsPage';
import { CoinDetailPage } from './pages/CoinDetailPage';
import { SignalsPage } from './pages/SignalsPage';
import { AnalystPage } from './pages/AnalystPage';
import { AnalystDetailPage } from './pages/AnalystDetailPage';
import { ChatPage } from './pages/ChatPage';
import { TradePage } from './pages/TradePage';
import { PerformancePage } from './pages/PerformancePage';
import { MlPipelinePage } from './pages/MlPipelinePage';

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<MarketsPage />} />
        <Route path="/coins/:symbol" element={<CoinDetailPage />} />
        <Route path="/signals" element={<SignalsPage />} />
        <Route path="/analyst" element={<AnalystPage />} />
        <Route path="/analyst/:symbol" element={<AnalystDetailPage />} />
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/trade" element={<TradePage />} />
        <Route path="/performance" element={<PerformancePage />} />
        <Route path="/ml" element={<MlPipelinePage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
