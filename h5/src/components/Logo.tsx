type LogoProps = {
  className?: string;
};

/**
 * 闪创工厂 品牌标识 —— 一道锐角闪电，呼应「闪」的即时生成之感。
 * 单色填充，剪影在小尺寸(favicon / 32px)下依旧清晰可辨。
 */
export default function Logo({ className }: LogoProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
      className={className}
    >
      <path
        d="M13.4 2.2 5.2 12.9c-.4.5-.05 1.25.6 1.25H10l-1.5 7.2c-.13.63.68 1 1.08.49l8.3-10.8c.4-.52.04-1.28-.61-1.28H12.9l1.6-6.86c.15-.63-.66-1.02-1.1-.5Z"
        fill="currentColor"
      />
    </svg>
  );
}
