import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import { cleanCriteria, type Criteria } from './criteria'
import type {
  City,
  District,
  Feature,
  MapPointsResponse,
  PropertyDetails,
  SearchResponse,
  SourceOption,
  UnreadCount,
} from './types'

export const queryKeys = {
  cities: ['dictionaries', 'cities'] as const,
  districts: ['dictionaries', 'districts'] as const,
  features: ['dictionaries', 'features'] as const,
  sources: ['dictionaries', 'sources'] as const,
  search: (params: string) => ['search', params] as const,
  map: (criteria: Criteria) => ['search-map', criteria] as const,
  property: (id: number) => ['property', id] as const,
  compare: (ids: number[]) => ['compare', ids] as const,
  savedSearches: ['saved-searches'] as const,
  favorites: (page: number) => ['favorites', page] as const,
  notifications: ['notifications'] as const,
  unread: ['notifications', 'unread'] as const,
}

const DICTIONARY_STALE = 60 * 60 * 1000

export function useCities() {
  return useQuery({ queryKey: queryKeys.cities, queryFn: () => api<City[]>('/api/v1/dictionaries/cities'), staleTime: DICTIONARY_STALE })
}

export function useDistricts() {
  return useQuery({ queryKey: queryKeys.districts, queryFn: () => api<District[]>('/api/v1/dictionaries/districts'), staleTime: DICTIONARY_STALE })
}

export function useFeatures() {
  return useQuery({ queryKey: queryKeys.features, queryFn: () => api<Feature[]>('/api/v1/dictionaries/features'), staleTime: DICTIONARY_STALE })
}

export function useSourceOptions() {
  return useQuery({ queryKey: queryKeys.sources, queryFn: () => api<SourceOption[]>('/api/v1/dictionaries/sources'), staleTime: DICTIONARY_STALE })
}

export function searchProperties(criteria: Criteria, page: number, size: number, sort: string[], signal?: AbortSignal) {
  return api<SearchResponse>('/api/v1/properties/search', {
    method: 'POST',
    body: cleanCriteria(criteria),
    query: { page, size, sort, facets: true },
    signal,
  })
}

export function fetchMapPoints(criteria: Criteria, signal?: AbortSignal) {
  return api<MapPointsResponse>('/api/v1/properties/search/map', { method: 'POST', body: cleanCriteria(criteria), signal })
}

export function fetchProperty(id: number) {
  return api<PropertyDetails>(`/api/v1/properties/${id}`)
}

export function fetchUnread() {
  return api<UnreadCount>('/api/v1/me/notifications/unread-count')
}
