import { CircleMarker, MapContainer, TileLayer } from 'react-leaflet'

export function MiniMap({ lat, lon, className }: { lat?: number; lon?: number; className?: string }) {
  if (lat === undefined || lon === undefined) return null
  return (
    <div className={`overflow-hidden rounded-xl border ${className ?? 'h-56'}`}>
      <MapContainer center={[lat, lon]} zoom={14} className="h-full w-full" scrollWheelZoom={false}>
        <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                   url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
        <CircleMarker center={[lat, lon]} radius={10} pathOptions={{ color: '#0f766e', fillColor: '#14b8a6', fillOpacity: 0.7 }} />
      </MapContainer>
    </div>
  )
}
