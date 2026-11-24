import type { SVGProps } from 'react'

/** 16px 외곽선 아이콘 한 벌 (1.5px 선). 색은 currentColor를 따른다 */
type IconProps = SVGProps<SVGSVGElement> & { size?: number }

function Svg({ size = 16, children, ...rest }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {children}
    </svg>
  )
}

export const IconEye = (p: IconProps) => (
  <Svg {...p}>
    <path d="M1.5 8S4 3.5 8 3.5 14.5 8 14.5 8 12 12.5 8 12.5 1.5 8 1.5 8Z" />
    <circle cx="8" cy="8" r="2" />
  </Svg>
)

export const IconEyeOff = (p: IconProps) => (
  <Svg {...p}>
    <path d="M6.5 3.7A6.6 6.6 0 0 1 8 3.5c4 0 6.5 4.5 6.5 4.5a11 11 0 0 1-1.7 2.2M10.5 11.9A6.3 6.3 0 0 1 8 12.5C4 12.5 1.5 8 1.5 8a11 11 0 0 1 2.6-3M2 2l12 12" />
  </Svg>
)

export const IconPlus = (p: IconProps) => (
  <Svg {...p}>
    <path d="M8 3.5v9M3.5 8h9" />
  </Svg>
)

export const IconCalendar = (p: IconProps) => (
  <Svg {...p}>
    <rect x="2.5" y="3.5" width="11" height="10" rx="1.5" />
    <path d="M5.5 2v3M10.5 2v3M2.5 7h11" />
  </Svg>
)

export const IconEdit = (p: IconProps) => (
  <Svg {...p}>
    <path d="M10.5 3 13 5.5 6 12.5H3.5V10z" />
  </Svg>
)

export const IconTrash = (p: IconProps) => (
  <Svg {...p}>
    <path d="M3 4.5h10M6.5 4.5V3h3v1.5M4.5 4.5l.6 8.5h5.8l.6-8.5" />
  </Svg>
)

export const IconClose = (p: IconProps) => (
  <Svg {...p}>
    <path d="m4 4 8 8M12 4l-8 8" />
  </Svg>
)

export const IconChevron = (p: IconProps) => (
  <Svg {...p}>
    <path d="m6 4 4 4-4 4" />
  </Svg>
)

export const IconBack = (p: IconProps) => (
  <Svg {...p}>
    <path d="M10 3.5 5.5 8l4.5 4.5" />
  </Svg>
)

export const IconUpload = (p: IconProps) => (
  <Svg {...p}>
    <path d="M8 10.5V3M5 6l3-3 3 3M3 10.5v2h10v-2" />
  </Svg>
)

export const IconDownload = (p: IconProps) => (
  <Svg {...p}>
    <path d="M8 3v7.5M5 7.5l3 3 3-3M3 10.5v2h10v-2" />
  </Svg>
)

export const IconMenu = (p: IconProps) => (
  <Svg {...p}>
    <path d="M2.5 4.5h11M2.5 8h11M2.5 11.5h11" />
  </Svg>
)

export const IconStack = (p: IconProps) => (
  <Svg {...p}>
    <path d="M3.5 2.5h6l3 3v8h-9z" />
    <path d="M9.5 2.5v3h3" />
  </Svg>
)

export const IconInbox = (p: IconProps) => (
  <Svg {...p}>
    <path d="M2.5 9 4 3.5h8L13.5 9v3.5h-11z" />
    <path d="M2.5 9h3l.8 1.5h3.4L10.5 9h3" />
  </Svg>
)

export const IconArrow = (p: IconProps) => (
  <Svg {...p}>
    <path d="M3 8h10M9 4l4 4-4 4" />
  </Svg>
)

export const IconSearch = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="7" cy="7" r="4" />
    <path d="m10 10 3.5 3.5" />
  </Svg>
)

export const IconAlert = (p: IconProps) => (
  <Svg {...p}>
    <path d="M8 2.5 14 13H2z" />
    <path d="M8 6.5v3M8 11.2v.1" />
  </Svg>
)

export const IconCheck = (p: IconProps) => (
  <Svg {...p}>
    <path d="m3.5 8.5 3 3 6-7" />
  </Svg>
)

export const IconFolder = (p: IconProps) => (
  <Svg {...p}>
    <path d="M2.5 4h4l1.5 1.5h5.5v7h-11z" />
  </Svg>
)

/** 제품 표식: 접힌 귀퉁이가 있는 종이 한 장과 발송 기록 두 줄 */
export function BrandMark({ size = 22 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 22 22" aria-hidden="true" focusable="false">
      <path d="M4 2h9.5L18 6.5V20H4z" fill="var(--text)" />
      <path d="M13.5 2v4.5H18" fill="var(--ground)" />
      <path d="M7 11h8M7 14.5h5" stroke="var(--ground)" strokeWidth="1.6" strokeLinecap="round" />
      <rect x="12.5" y="15.5" width="4" height="2.5" rx="0.5" fill="var(--hue)" />
    </svg>
  )
}
