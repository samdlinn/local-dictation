const Mark = ({
  width = 96,
  height = 96,
  className = "",
}: {
  width?: number | string;
  height?: number | string;
  className?: string;
}) => (
  <svg
    width={width}
    height={height}
    viewBox="0 0 96 96"
    className={className}
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <rect x="5" y="5" width="86" height="86" rx="24" fill="#123D39" />
    <rect x="35" y="20" width="26" height="40" rx="13" fill="#BDF4DA" />
    <path
      d="M26 47v5a22 22 0 0 0 44 0v-5M48 74v8M36 82h24"
      stroke="#BDF4DA"
      strokeWidth="5"
      strokeLinecap="round"
    />
  </svg>
);
export default Mark;
