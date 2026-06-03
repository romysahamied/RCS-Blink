/**
 * RCS Blink logo — uses unoptimized static asset to avoid stale Next/Image cache.
 */
export default function BrandLogo({
  className = 'h-8 w-8 rounded-lg',
}: {
  className?: string
}) {
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src='/images/rcs-blink-icon.png'
      alt='RCS Blink Logo'
      width={32}
      height={32}
      className={className}
      decoding='async'
    />
  )
}
