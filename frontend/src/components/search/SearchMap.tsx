import L from '@/lib/leaflet'
import 'leaflet.markercluster'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import 'leaflet.markercluster/dist/MarkerCluster.Default.css'
import { useQuery } from '@tanstack/react-query'
import { Crosshair, Eraser, Loader2, PenTool, ScanSearch } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Circle, MapContainer, Polygon, Polyline, Rectangle, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { cleanCriteria, type Criteria, type GeoPolygon } from '@/lib/criteria'
import { formatCompactPrice, formatPrice } from '@/lib/format'
import { fetchMapPoints } from '@/lib/queries'
import type { MapPoint } from '@/lib/types'

type Mode = 'none' | 'polygon' | 'point'

const POLAND_CENTER: [number, number] = [52.0, 19.4]
const MAX_POLYGON_POINTS = 100

function escapeHtml(s: string) {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] ?? c)
}

/** Warstwa klastrów (leaflet.markercluster) z etykietami cen. */
function ClusterLayer({ points, highlightedId }: { points: MapPoint[]; highlightedId?: number | null }) {
  const map = useMap()
  const navigate = useNavigate()
  const groupRef = useRef<L.MarkerClusterGroup | null>(null)
  const markersRef = useRef(new Map<number, L.Marker>())

  useEffect(() => {
    const group = L.markerClusterGroup({ chunkedLoading: true, maxClusterRadius: 50, showCoverageOnHover: false })
    groupRef.current = group
    map.addLayer(group)
    const onPopupOpen = (e: L.PopupEvent) => {
      const link = e.popup.getElement()?.querySelector<HTMLAnchorElement>('a[data-id]')
      link?.addEventListener('click', (ev) => {
        ev.preventDefault()
        navigate(`/nieruchomosci/${link.dataset.id}`)
      }, { once: true })
    }
    map.on('popupopen', onPopupOpen)
    return () => {
      map.off('popupopen', onPopupOpen)
      map.removeLayer(group)
    }
  }, [map, navigate])

  useEffect(() => {
    const group = groupRef.current
    if (!group) return
    group.clearLayers()
    markersRef.current.clear()
    const markers = points.map((p) => {
      const marker = L.marker([p.lat, p.lon], {
        icon: L.divIcon({ className: 'price-marker', html: `<span>${formatCompactPrice(p.price)}</span>`, iconSize: [0, 0] }),
      })
      marker.bindPopup(
        `<div style="min-width:180px"><strong>${escapeHtml(p.title)}</strong><br/>${escapeHtml(formatPrice(p.price))}` +
        `${p.area ? ` · ${p.area} m²` : ''}<br/><a href="/nieruchomosci/${p.id}" data-id="${p.id}">Zobacz szczegóły →</a></div>`)
      markersRef.current.set(p.id, marker)
      return marker
    })
    group.addLayers(markers)
  }, [points])

  useEffect(() => {
    if (highlightedId == null) return
    const marker = markersRef.current.get(highlightedId)
    const group = groupRef.current
    if (marker && group) {
      const visible = group.getVisibleParent(marker)
      if (visible === marker) marker.openPopup()
    }
  }, [highlightedId])

  return null
}

function FitToPoints({ points, enabled }: { points: MapPoint[]; enabled: boolean }) {
  const map = useMap()
  const fitted = useRef(false)
  useEffect(() => {
    if (!enabled || fitted.current || points.length === 0) return
    map.fitBounds(L.latLngBounds(points.map((p) => [p.lat, p.lon] as [number, number])), { padding: [30, 30], maxZoom: 14 })
    fitted.current = true
  }, [map, points, enabled])
  return null
}

function DrawHandler({ mode, onPolygonPoint, onPoint }: {
  mode: Mode
  onPolygonPoint: (latlng: L.LatLng) => void
  onPoint: (latlng: L.LatLng) => void
}) {
  const map = useMapEvents({
    click: (e) => {
      if (mode === 'polygon') onPolygonPoint(e.latlng)
      else if (mode === 'point') onPoint(e.latlng)
    },
  })
  useEffect(() => {
    if (mode === 'none') map.doubleClickZoom.enable()
    else map.doubleClickZoom.disable()
    map.getContainer().style.cursor = mode === 'none' ? '' : 'crosshair'
  }, [map, mode])
  return null
}

/** Pasek narzędzi nad mapą; kliknięcia nie przechodzą do mapy (nie dodają wierzchołków poligonu). */
function MapToolbar({ children }: { children: React.ReactNode }) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (ref.current) {
      L.DomEvent.disableClickPropagation(ref.current)
      L.DomEvent.disableScrollPropagation(ref.current)
    }
  }, [])
  return <div ref={ref} className="absolute top-2 right-2 left-14 z-[1000] flex flex-wrap justify-end gap-1.5">{children}</div>
}

function AreaSearchButton({ onSearch }: { onSearch: (bounds: L.LatLngBounds) => void }) {
  const map = useMap()
  return (
    <Button size="sm" variant="secondary" className="shadow" onClick={() => onSearch(map.getBounds())}>
      <ScanSearch /> Szukaj w tym obszarze
    </Button>
  )
}

/**
 * Mapa wyników: klastrowanie, "szukaj w tym obszarze" (bbox), rysowanie poligonu, punkt z promieniem.
 */
export function SearchMap({ criteria, onChange, highlightedId, className }: {
  criteria: Criteria
  onChange: (patch: Partial<Criteria>) => void
  highlightedId?: number | null
  className?: string
}) {
  const [mode, setMode] = useState<Mode>('none')
  const [draft, setDraft] = useState<[number, number][]>([])
  const cleaned = useMemo(() => cleanCriteria(criteria), [criteria])
  const points = useQuery({
    queryKey: ['search-map', JSON.stringify(cleaned)],
    queryFn: ({ signal }) => fetchMapPoints(cleaned, signal),
    placeholderData: (prev) => prev,
  })
  const data = points.data?.points ?? []

  const finishPolygon = () => {
    if (draft.length < 3) {
      toast.warning('Poligon musi mieć co najmniej 3 punkty')
      return
    }
    const ring = [...draft, draft[0]].map(([lat, lon]) => [Number(lon.toFixed(6)), Number(lat.toFixed(6))])
    const polygon: GeoPolygon = { type: 'Polygon', coordinates: [ring] }
    onChange({ polygon, bbox: undefined })
    setDraft([])
    setMode('none')
  }

  const polygonPositions = criteria.polygon?.coordinates[0]?.map(([lon, lat]) => [lat, lon] as [number, number])

  return (
    <div className={`relative overflow-hidden rounded-xl border ${className ?? ''}`}>
      <MapContainer center={POLAND_CENTER} zoom={6} className="h-full w-full" scrollWheelZoom>
        <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                   url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
        <ClusterLayer points={data} highlightedId={highlightedId} />
        <FitToPoints points={data} enabled={!criteria.bbox} />
        <DrawHandler
          mode={mode}
          onPolygonPoint={(ll) => setDraft((d) => {
            if (d.length >= MAX_POLYGON_POINTS) {
              toast.warning(`Maksymalnie ${MAX_POLYGON_POINTS} punktów`)
              return d
            }
            return [...d, [ll.lat, ll.lng]]
          })}
          onPoint={(ll) => {
            onChange({ lat: Number(ll.lat.toFixed(6)), lon: Number(ll.lng.toFixed(6)), radiusKm: criteria.radiusKm ?? 2 })
            setMode('none')
          }}
        />
        {draft.length > 0 && <Polyline positions={draft} pathOptions={{ color: '#dc2626', dashArray: '4 4' }} />}
        {polygonPositions && <Polygon positions={polygonPositions} pathOptions={{ color: '#0f766e', fillOpacity: 0.08 }} />}
        {criteria.bbox && (
          <Rectangle bounds={[[criteria.bbox.south, criteria.bbox.west], [criteria.bbox.north, criteria.bbox.east]]}
                     pathOptions={{ color: '#2563eb', fillOpacity: 0.04, dashArray: '6 4' }} />
        )}
        {criteria.lat !== undefined && criteria.lon !== undefined && criteria.radiusKm && (
          <Circle center={[criteria.lat, criteria.lon]} radius={criteria.radiusKm * 1000} pathOptions={{ color: '#7c3aed', fillOpacity: 0.06 }} />
        )}
        <MapToolbar>
          <AreaSearchButton onSearch={(b) => onChange({
            bbox: { south: b.getSouth(), west: b.getWest(), north: b.getNorth(), east: b.getEast() },
            polygon: undefined,
          })} />
          {mode === 'polygon' ? (
            <>
              <Button size="sm" className="shadow" onClick={finishPolygon}>Zakończ ({draft.length})</Button>
              <Button size="sm" variant="secondary" className="shadow" onClick={() => { setDraft([]); setMode('none') }}>Anuluj</Button>
            </>
          ) : (
            <Button size="sm" variant="secondary" className="shadow" onClick={() => { setMode('polygon'); setDraft([]) }}>
              <PenTool /> Rysuj obszar
            </Button>
          )}
          <Button size="sm" variant={mode === 'point' ? 'default' : 'secondary'} className="shadow"
                  onClick={() => setMode(mode === 'point' ? 'none' : 'point')}>
            <Crosshair /> Punkt i promień
          </Button>
          {(criteria.polygon || criteria.bbox || criteria.lat !== undefined) && (
            <Button size="sm" variant="secondary" className="shadow"
                    onClick={() => onChange({ polygon: undefined, bbox: undefined, lat: undefined, lon: undefined, radiusKm: undefined })}>
              <Eraser /> Wyczyść obszar
            </Button>
          )}
        </MapToolbar>
      </MapContainer>
      <div className="pointer-events-none absolute bottom-2 left-2 z-[1000] rounded-md bg-background/90 px-2 py-1 text-xs shadow">
        {points.isFetching ? <span className="flex items-center gap-1"><Loader2 className="size-3 animate-spin" />Ładowanie mapy…</span>
          : `${data.length}${points.data?.truncated ? ` z ${points.data.total}` : ''} na mapie`}
        {mode === 'polygon' && ' · klikaj, aby dodać wierzchołki'}
        {mode === 'point' && ' · kliknij, aby wskazać punkt'}
      </div>
    </div>
  )
}
