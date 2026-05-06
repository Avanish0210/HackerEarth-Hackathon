const paths = {
  dashboard: "M3 13h8V3H3v10Zm0 8h8v-6H3v6Zm10 0h8V11h-8v10Zm0-18v6h8V3h-8Z",
  folder: "M3 6.5A2.5 2.5 0 0 1 5.5 4H10l2 2h6.5A2.5 2.5 0 0 1 21 8.5v8A2.5 2.5 0 0 1 18.5 19h-13A2.5 2.5 0 0 1 3 16.5v-10Z",
  brain: "M8 4a3 3 0 0 0-3 3v.4A3.5 3.5 0 0 0 4 14a3 3 0 0 0 3 4.9V21h4V4H8Zm8 0h-3v17h4v-2.1A3 3 0 0 0 20 16a3.5 3.5 0 0 0-1-6.6V7a3 3 0 0 0-3-3Z",
  upload: "M12 3 7 8h3v7h4V8h3l-5-5ZM5 19h14v2H5v-2Z",
  matrix: "M4 4h16v16H4V4Zm2 2v3h3V6H6Zm5 0v3h3V6h-3Zm5 0v3h2V6h-2ZM6 11v3h3v-3H6Zm5 0v3h3v-3h-3Zm5 0v3h2v-3h-2ZM6 16v2h3v-2H6Zm5 0v2h3v-2h-3Zm5 0v2h2v-2h-2Z",
  review: "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Zm1 5v5h4v2h-6V8h2Z",
  report: "M6 3h9l3 3v15H6V3Zm8 1.5V7h2.5L14 4.5ZM8 11h8v2H8v-2Zm0 4h8v2H8v-2Z",
  audit: "M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5l-8-3Zm-1 6h2v5h-2V8Zm0 7h2v2h-2v-2Z",
  search: "M10 4a6 6 0 1 1 0 12 6 6 0 0 1 0-12Zm0 2a4 4 0 1 0 0 8 4 4 0 0 0 0-8Zm5 8 5 5-1.4 1.4-5-5L15 14Z",
  chevron: "M9 6l6 6-6 6",
  close: "M6.4 5 5 6.4 10.6 12 5 17.6 6.4 19 12 13.4 17.6 19 19 17.6 13.4 12 19 6.4 17.6 5 12 10.6 6.4 5Z",
  download: "M11 4h2v9l3-3 1.4 1.4L12 16.8l-5.4-5.4L8 10l3 3V4ZM5 19h14v2H5v-2Z",
  play: "M8 5v14l11-7L8 5Z",
  check: "M9.5 16.2 5.8 12.5 4.4 13.9l5.1 5.1L20 8.5 18.6 7.1 9.5 16.2Z"
};

export default function Icon({ name, size = 18 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path d={paths[name] || paths.dashboard} fill="currentColor" />
    </svg>
  );
}
