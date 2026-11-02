import { useState } from 'react';
import { validateLatitude, validateLongitude } from '../utils/validation.js';

// Manual lat/lon form with validation + "Use my location" (PROMPT.md section 7).
export default function LocationForm({ onSubmit }) {
  const [lat, setLat] = useState('');
  const [lon, setLon] = useState('');
  const [error, setError] = useState(null);

  function handleSubmit(event) {
    event.preventDefault();
    const latResult = validateLatitude(lat);
    if (!latResult.valid) {
      setError(latResult.error);
      return;
    }
    const lonResult = validateLongitude(lon);
    if (!lonResult.valid) {
      setError(lonResult.error);
      return;
    }
    setError(null);
    onSubmit({ lat: latResult.value, lon: lonResult.value });
  }

  function handleUseMyLocation() {
    if (!navigator.geolocation) {
      setError('Geolocation is not available in this browser.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setError(null);
        // FIXED: Now populates the form fields instead of immediately submitting
        const latitude = position.coords.latitude.toFixed(4);
        const longitude = position.coords.longitude.toFixed(4);
        setLat(latitude);
        setLon(longitude);
        // Auto-submit after populating (you can remove this line if you want manual submit)
        onSubmit({ lat: parseFloat(latitude), lon: parseFloat(longitude) });
      },
      () => {
        setError('Could not determine your location.');
      }
    );
  }

  return (
    <form onSubmit={handleSubmit} className="location-form" aria-label="Observer location">
      <div className="form-row">
        <label htmlFor="latitude-input">Latitude</label>
        <input
          id="latitude-input"
          type="text"
          inputMode="decimal"
          value={lat}
          onChange={(e) => setLat(e.target.value)}
          placeholder="-90 to 90"
        />
      </div>
      <div className="form-row">
        <label htmlFor="longitude-input">Longitude</label>
        <input
          id="longitude-input"
          type="text"
          inputMode="decimal"
          value={lon}
          onChange={(e) => setLon(e.target.value)}
          placeholder="-180 to 180"
        />
      </div>
      {error && <p role="alert" className="form-error">{error}</p>}
      <div className="form-actions">
        <button type="submit">Use these coordinates</button>
        <button type="button" onClick={handleUseMyLocation}>Use my location</button>
      </div>
    </form>
  );
}
