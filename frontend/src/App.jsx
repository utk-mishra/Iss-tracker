import { useState } from 'react';
import LocationForm from './components/LocationForm.jsx';
import OfflineToggle from './components/OfflineToggle.jsx';
import DegradedBanner from './components/DegradedBanner.jsx';
import TleStaleWarning from './components/TleStaleWarning.jsx';
import PositionCard from './components/PositionCard.jsx';
import AlertList from './components/AlertList.jsx';
import PassTable from './components/PassTable.jsx';
import CompassDial from './components/CompassDial.jsx';
import Countdown from './components/Countdown.jsx';
import ErrorBanner from './components/ErrorBanner.jsx';
import LoadingSkeleton from './components/LoadingSkeleton.jsx';
import useOverview from './hooks/useOverview.js';
import usePositionPolling from './hooks/usePositionPolling.js';
import useOfflineToggle from './hooks/useOfflineToggle.js';

const DEFAULT_LOCATION = { lat: 28.6139, lon: 77.209 };

export default function App() {
  const [location, setLocation] = useState(DEFAULT_LOCATION);
  const { offline, setOffline } = useOfflineToggle();

  const overview = useOverview({ lat: location.lat, lon: location.lon, offline });
  const livePosition = usePositionPolling({ offline });

  const hasLoaded = overview.data !== null;
  const nextPass = overview.data?.nextVisiblePass ?? null;
  const position = livePosition.data?.position ?? overview.data?.position ?? null;
  const positionMeta = livePosition.data?.meta ?? overview.data?.meta?.position ?? null;

  return (
    <div className="app">
      <header className="app-header">
        <h1>ISS Location &amp; Pass Predictor</h1>
        <OfflineToggle offline={offline} onChange={setOffline} />
      </header>

      <LocationForm onSubmit={setLocation} />

      {overview.data && <DegradedBanner meta={overview.data.meta} />}
      {overview.data && <TleStaleWarning tleStale={overview.data.meta?.passes?.tleStale} />}
      <ErrorBanner error={overview.error || livePosition.error} />

      {!hasLoaded && <LoadingSkeleton />}

      {hasLoaded && (
        <>
          <PositionCard position={position} meta={positionMeta} />

          <section aria-label="Next visible pass">
            <h2>Next visible pass</h2>
            {nextPass ? (
              <>
                <Countdown targetIso={nextPass.visibleStart} />
                <CompassDial
                  riseAzimuthDeg={nextPass.riseAzimuthDeg}
                  setAzimuthDeg={nextPass.setAzimuthDeg}
                />
                <AlertList alerts={overview.data.alerts} />
              </>
            ) : (
              <p>{overview.data.message}</p>
            )}
          </section>

          <section aria-label="All passes">
            <h2>Upcoming passes</h2>
            <PassTable passes={overview.data.passes} />
          </section>
        </>
      )}
    </div>
  );
}
