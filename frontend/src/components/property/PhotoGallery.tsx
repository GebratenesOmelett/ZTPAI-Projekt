import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { PhotoOrPlaceholder } from '@/components/search/PropertyCard'
import { cn } from '@/lib/utils'

export function PhotoGallery({ photos, title }: { photos: string[]; title: string }) {
  const [index, setIndex] = useState(0)
  if (photos.length === 0) {
    return <div className="aspect-[16/10] overflow-hidden rounded-xl"><PhotoOrPlaceholder alt={title} /></div>
  }
  const go = (delta: number) => setIndex((i) => (i + delta + photos.length) % photos.length)
  return (
    <div className="space-y-2">
      <div className="relative aspect-[16/10] overflow-hidden rounded-xl bg-muted">
        <PhotoOrPlaceholder src={photos[index]} alt={`${title} — zdjęcie ${index + 1}`} />
        {photos.length > 1 && (
          <>
            <Button size="icon" variant="secondary" className="absolute top-1/2 left-2 -translate-y-1/2 opacity-90" onClick={() => go(-1)} aria-label="Poprzednie zdjęcie"><ChevronLeft /></Button>
            <Button size="icon" variant="secondary" className="absolute top-1/2 right-2 -translate-y-1/2 opacity-90" onClick={() => go(1)} aria-label="Następne zdjęcie"><ChevronRight /></Button>
            <span className="absolute right-2 bottom-2 rounded bg-black/60 px-2 py-0.5 text-xs text-white">{index + 1} / {photos.length}</span>
          </>
        )}
      </div>
      {photos.length > 1 && (
        <div className="flex gap-2 overflow-x-auto pb-1">
          {photos.map((src, i) => (
            <button key={src} type="button" onClick={() => setIndex(i)} aria-label={`Zdjęcie ${i + 1}`}
                    className={cn('h-16 w-24 shrink-0 overflow-hidden rounded-md border-2', i === index ? 'border-teal-600' : 'border-transparent opacity-70 hover:opacity-100')}>
              <img src={src} alt="" loading="lazy" className="h-full w-full object-cover" />
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
