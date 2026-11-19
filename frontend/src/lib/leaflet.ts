import L from 'leaflet'

// Wtyczka leaflet.markercluster rozszerza globalny obiekt L — udostępniamy go przed jej importem.
;(window as unknown as { L: typeof L }).L = L

export default L
