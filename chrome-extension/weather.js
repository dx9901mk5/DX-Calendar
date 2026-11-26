/**
 * DX日曆 Chrome 插件 — 免費 Open-Meteo 7 天天氣預報（3 小時本機快取）
 */
const DXWeather = (() => {
  const CACHE_KEY = "dx_weather_cache_v1";
  const TTL_MS = 3 * 60 * 60 * 1000;

  function resolveCoords(tz) {
    const map = {
      "Asia/Taipei": [25.0330, 121.5654],
      "Asia/Hong_Kong": [22.3193, 114.1694],
      "Asia/Macau": [22.1987, 113.5439],
      "Asia/Shanghai": [31.2304, 121.4737],
      "Asia/Tokyo": [35.6762, 139.6503],
      "Asia/Seoul": [37.5665, 126.9780],
      "Asia/Singapore": [1.3521, 103.8198],
      "America/Los_Angeles": [34.0522, -118.2437],
      "America/New_York": [40.7128, -74.0060],
      "Europe/London": [51.5074, -0.1278]
    };
    return map[tz] || [25.0330, 121.5654];
  }

  function mapWmo(code) {
    if (code === 0) return { icon: "☀️", label: "晴朗" };
    if (code === 1) return { icon: "🌤️", label: "大致晴朗" };
    if (code === 2) return { icon: "⛅", label: "多雲時晴" };
    if (code === 3) return { icon: "☁️", label: "陰天" };
    if (code === 45 || code === 48) return { icon: "🌫️", label: "有霧" };
    if ((code >= 51 && code <= 57) || (code >= 80 && code <= 82)) return { icon: "🌦️", label: "陣雨" };
    if (code >= 61 && code <= 67) return { icon: "🌧️", label: "降雨" };
    if ((code >= 71 && code <= 77) || code === 85 || code === 86) return { icon: "❄️", label: "降雪" };
    if (code >= 95) return { icon: "⛈️", label: "雷陣雨" };
    return { icon: "🌤️", label: "多雲" };
  }

  async function getSevenDayForecast(force = false) {
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Taipei";
    const stored = await chrome.storage.local.get(CACHE_KEY);
    const cache = stored[CACHE_KEY];
    if (!force && cache && cache.tz === tz && (Date.now() - cache.fetchedAt) < TTL_MS && cache.byDate) {
      return cache.byDate;
    }

    const [lat, lon] = resolveCoords(tz);
    const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=7&timezone=auto`;
    try {
      const resp = await fetch(url);
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const json = await resp.json();
      const d = json.daily || {};
      const byDate = {};
      const times = d.time || [];
      for (let i = 0; i < times.length; i++) {
        const meta = mapWmo(d.weather_code[i]);
        byDate[times[i]] = {
          date: times[i],
          icon: meta.icon,
          label: meta.label,
          maxC: Math.round(d.temperature_2m_max[i]),
          minC: Math.round(d.temperature_2m_min[i])
        };
      }
      await chrome.storage.local.set({
        [CACHE_KEY]: { tz, fetchedAt: Date.now(), byDate }
      });
      return byDate;
    } catch (_) {
      return (cache && cache.byDate) || {};
    }
  }

  return { getSevenDayForecast };
})();
