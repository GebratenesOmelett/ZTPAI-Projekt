// Typy DTO zgodne z REST API backendu (/api/v1)

export type Role = 'USER' | 'ADMIN'
export type PropertyType = 'APARTMENT' | 'HOUSE' | 'PLOT' | 'COMMERCIAL' | 'ROOM'
export type TransactionType = 'SALE' | 'RENT'
export type MarketType = 'PRIMARY' | 'SECONDARY'
export type PropertyStatus = 'ACTIVE' | 'ARCHIVED'
export type AlertMode = 'INSTANT' | 'DAILY'
export type ImportStatus = 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED'
export type NotificationType = 'NEW_LISTINGS' | 'PRICE_DROP' | 'DAILY_DIGEST' | 'SYSTEM'

export interface User {
  id: number
  email: string
  displayName: string
  role: Role
  enabled: boolean
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: User
}

export interface FieldError {
  field: string
  message: string
}

export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  errorCode?: string
  fieldErrors?: FieldError[]
  details?: Record<string, unknown>
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface City {
  id: number
  name: string
  slug: string
  lat: number
  lon: number
}

export interface District {
  id: number
  cityId: number
  name: string
  slug: string
  lat: number
  lon: number
}

export interface Feature {
  code: string
  name: string
}

export interface SourceOption {
  code: string
  name: string
}

export interface PropertySummary {
  id: number
  title: string
  propertyType: PropertyType
  transactionType: TransactionType
  marketType?: MarketType
  cityId: number
  city: string
  districtId?: number
  district?: string
  street?: string
  lat?: number
  lon?: number
  area: number
  rooms?: number
  floor?: number
  totalFloors?: number
  yearBuilt?: number
  price?: number
  pricePerM2?: number
  priceDropped: boolean
  priceDropPercent?: number
  lastPriceChangeAt?: string
  sourceCount: number
  firstSeenAt: string
  daysOnMarket: number
  status: PropertyStatus
  mainPhotoUrl?: string
  districtMedianPricePerM2?: number
  vsMedianPercent?: number
  distanceMeters?: number
}

export interface SearchResponse extends Page<PropertySummary> {
  sort: string[]
}

export interface MapPoint {
  id: number
  lat: number
  lon: number
  price?: number
  pricePerM2?: number
  rooms?: number
  area?: number
  title: string
}

export interface MapPointsResponse {
  points: MapPoint[]
  total: number
  truncated: boolean
}

export interface PricePoint {
  price: number
  changedAt: string
}

export interface Offer {
  id: number
  propertyId: number
  sourceId: number
  sourceCode: string
  sourceName: string
  externalId: string
  url?: string
  title: string
  price: number
  active: boolean
  privateSeller: boolean
  agencyName?: string
  firstSeenAt: string
  lastSeenAt: string
  missedImports: number
  priceHistory: PricePoint[]
}

export interface MedianComparison {
  districtMedianPricePerM2?: number
  vsMedianPercent?: number
  sampleSize: number
  label?: string
}

export interface PropertyDetails {
  id: number
  title: string
  description?: string
  propertyType: PropertyType
  transactionType: TransactionType
  marketType?: MarketType
  cityId: number
  city: string
  districtId?: number
  district?: string
  street?: string
  lat?: number
  lon?: number
  area: number
  rooms?: number
  floor?: number
  totalFloors?: number
  yearBuilt?: number
  price?: number
  pricePerM2?: number
  priceDropped: boolean
  priceDropPercent?: number
  lastPriceChangeAt?: string
  lastPriceDropAt?: string
  sourceCount: number
  firstSeenAt: string
  lastSeenAt: string
  daysOnMarket: number
  status: PropertyStatus
  features: Feature[]
  photos: string[]
  offers: Offer[]
  median?: MedianComparison
  version: number
  favoriteId?: number
}

export interface SavedSearch {
  id: number
  name: string
  criteria: import('./criteria').Criteria
  alertEnabled: boolean
  alertMode: AlertMode
  lastNotifiedAt?: string
  createdAt: string
  updatedAt: string
}

export interface Favorite {
  id: number
  propertyId: number
  note?: string
  createdAt: string
  title: string
  propertyType: PropertyType
  transactionType: TransactionType
  city: string
  district?: string
  area: number
  rooms?: number
  price?: number
  pricePerM2?: number
  priceDropped: boolean
  mainPhotoUrl?: string
  status: PropertyStatus
  archived: boolean
}

export interface AppNotification {
  id: number
  type: NotificationType
  title: string
  payload: {
    savedSearchId?: number
    savedSearchName?: string
    newCount?: number
    priceDropCount?: number
    newPropertyIds?: number[]
    priceDropPropertyIds?: number[]
  }
  read: boolean
  createdAt: string
}

export interface Source {
  id: number
  code: string
  name: string
  baseUrl: string
  adapter: string
  enabled: boolean
  importCron?: string
  lastImportAt?: string
  createdAt: string
}

export interface Adapter {
  code: string
  description: string
}

export interface ImportJob {
  id: number
  sourceId: number
  sourceCode: string
  sourceName: string
  status: ImportStatus
  trigger: 'MANUAL' | 'SCHEDULED' | 'BOOTSTRAP'
  requestedBy?: string
  createdAt: string
  startedAt?: string
  finishedAt?: string
  fetched: number
  created: number
  updated: number
  merged: number
  deactivated: number
  errors: number
  expectedBatches?: number
  processedBatches: number
  failedBatches: number
  attempts: number
  errorMessage?: string
}

export interface AdminProperty {
  id: number
  title: string
  propertyType: PropertyType
  transactionType: TransactionType
  city: string
  district?: string
  street?: string
  area: number
  rooms?: number
  floor?: number
  price?: number
  pricePerM2?: number
  status: PropertyStatus
  sourceCount: number
  firstSeenAt: string
  lastSeenAt: string
  version: number
}

export interface UnreadCount {
  unread: number
}

export interface SplitResponse {
  original: PropertyDetails
  created: PropertyDetails
}
