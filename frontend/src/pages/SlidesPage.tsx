import { useCallback, useEffect, useState } from 'react';
import { PageHead } from '../components/ui';

// Presentation slides, exported to PNG under frontend/public/slides/ (served at
// /slides/*). Ordered for the demo walkthrough — edit this list to add/reorder.
const SLIDES = ['0.png', '1.png', '1-1.png', '2.png', '3.png', '4.png', '5.png', '6.png'];

// Respect Vite's base URL (BASE_URL ends in '/'), so this works under any base.
const src = (file: string) => `${import.meta.env.BASE_URL}slides/${file}`;

export function SlidesPage() {
  // null = gallery view; a number = that slide open in the lightbox.
  const [active, setActive] = useState<number | null>(null);

  const close = useCallback(() => setActive(null), []);
  const step = useCallback(
    (delta: number) =>
      setActive((i) => (i === null ? i : (i + delta + SLIDES.length) % SLIDES.length)),
    [],
  );

  // Keyboard controls while the lightbox is open: Esc closes, ← / → navigate.
  useEffect(() => {
    if (active === null) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close();
      else if (e.key === 'ArrowRight') step(1);
      else if (e.key === 'ArrowLeft') step(-1);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [active, close, step]);

  return (
    <>
      <PageHead
        title="Slides"
        subtitle="Presentation deck for the CryptoCopilot demo. Click a slide to view it full-size; use ← → to move between slides."
      />

      <div className="slide-gallery">
        {SLIDES.map((file, i) => (
          <button
            key={file}
            type="button"
            className="slide-thumb"
            onClick={() => setActive(i)}
            aria-label={`Open slide ${i + 1}`}
          >
            <img src={src(file)} alt={`Slide ${i + 1}`} loading="lazy" />
            <span className="slide-num">{i + 1}</span>
          </button>
        ))}
      </div>

      {active !== null && (
        <div className="lightbox" role="dialog" aria-modal="true" onClick={close}>
          <button className="lightbox-close" onClick={close} aria-label="Close">
            ×
          </button>
          <button
            className="lightbox-nav prev"
            onClick={(e) => {
              e.stopPropagation();
              step(-1);
            }}
            aria-label="Previous slide"
          >
            ‹
          </button>
          <img
            className="lightbox-img"
            src={src(SLIDES[active])}
            alt={`Slide ${active + 1}`}
            onClick={(e) => e.stopPropagation()}
          />
          <button
            className="lightbox-nav next"
            onClick={(e) => {
              e.stopPropagation();
              step(1);
            }}
            aria-label="Next slide"
          >
            ›
          </button>
          <div className="lightbox-count">
            {active + 1} / {SLIDES.length}
          </div>
        </div>
      )}
    </>
  );
}
