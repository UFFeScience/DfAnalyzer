import type { SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

function Icon({ size = 20, children, ...rest }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {children}
    </svg>
  );
}

export const LogoMark = (props: IconProps) => (
  <Icon strokeWidth={2.1} {...props}>
    <circle cx="6" cy="6" r="2.6" />
    <circle cx="18" cy="12" r="2.6" />
    <circle cx="6" cy="18" r="2.6" />
    <path d="M8.3 7.3 15.7 11M15.7 13 8.3 16.7" />
  </Icon>
);

export const DataflowsIcon = (props: IconProps) => (
  <Icon {...props}>
    <rect x="3" y="4" width="7" height="6" rx="1.5" />
    <rect x="14" y="14" width="7" height="6" rx="1.5" />
    <path d="M6.5 10v4.5a2 2 0 0 0 2 2H14" />
  </Icon>
);

export const GraphIcon = (props: IconProps) => (
  <Icon {...props}>
    <circle cx="5" cy="6" r="2.4" />
    <circle cx="19" cy="6" r="2.4" />
    <circle cx="12" cy="18" r="2.4" />
    <path d="M7 7.5 10.4 16M17 7.5 13.6 16M7.4 6h9.2" />
  </Icon>
);

export const QueryIcon = (props: IconProps) => (
  <Icon {...props}>
    <ellipse cx="12" cy="6" rx="7" ry="3" />
    <path d="M5 6v12c0 1.7 3.1 3 7 3s7-1.3 7-3V6M5 12c0 1.7 3.1 3 7 3s7-1.3 7-3" />
  </Icon>
);

export const AnalyticsIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M4 20V4" />
    <path d="M4 20h16" />
    <rect x="7.5" y="12" width="3" height="5" rx="1" />
    <rect x="13" y="8" width="3" height="9" rx="1" />
    <rect x="18" y="14" width="3" height="3" rx="1" />
  </Icon>
);

export const GalleryIcon = (props: IconProps) => (
  <Icon {...props}>
    <rect x="3" y="3" width="7" height="7" rx="1.5" />
    <rect x="14" y="3" width="7" height="7" rx="1.5" />
    <rect x="3" y="14" width="7" height="7" rx="1.5" />
    <rect x="14" y="14" width="7" height="7" rx="1.5" />
  </Icon>
);

export const FileIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M6 3h9l5 5v13a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z" />
    <path d="M15 3v5h5" />
  </Icon>
);

export const SearchIcon = (props: IconProps) => (
  <Icon strokeWidth={2} {...props}>
    <circle cx="11" cy="11" r="6.5" />
    <path d="m16 16 4 4" />
  </Icon>
);

export const CheckIcon = (props: IconProps) => (
  <Icon strokeWidth={2.4} {...props}>
    <path d="m4 12.5 5 5L20 6.5" />
  </Icon>
);

export const ChevronIcon = (props: IconProps) => (
  <Icon strokeWidth={2.2} {...props}>
    <path d="m9 18 6-6-6-6" />
  </Icon>
);

export const ExportIcon = (props: IconProps) => (
  <Icon strokeWidth={1.9} {...props}>
    <path d="M12 15V3.5" />
    <path d="m8 7.5 4-4 4 4" />
    <path d="M4 15v3.5a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V15" />
  </Icon>
);

export const SunIcon = (props: IconProps) => (
  <Icon strokeWidth={1.9} {...props}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
  </Icon>
);

export const MoonIcon = (props: IconProps) => (
  <Icon strokeWidth={1.9} {...props}>
    <path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z" />
  </Icon>
);

export const RefreshIcon = (props: IconProps) => (
  <Icon strokeWidth={1.9} {...props}>
    <path d="M20 11.5A8 8 0 0 0 6.3 6.3L4 8.5" />
    <path d="M4 4.5v4h4" />
    <path d="M4 12.5a8 8 0 0 0 13.7 5.2L20 15.5" />
    <path d="M20 19.5v-4h-4" />
  </Icon>
);

export const CloseIcon = (props: IconProps) => (
  <Icon strokeWidth={2.2} {...props}>
    <path d="M18 6 6 18M6 6l12 12" />
  </Icon>
);

export const WarningIcon = (props: IconProps) => (
  <Icon strokeWidth={2} {...props}>
    <path d="M12 3.5 22 20H2z" />
    <path d="M12 10v4M12 17h.01" />
  </Icon>
);
