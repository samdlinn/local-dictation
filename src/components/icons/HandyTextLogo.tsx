import { useTranslation } from "react-i18next";
const HandyTextLogo = ({
  width,
  className,
}: {
  width?: number;
  height?: number;
  className?: string;
}) => {
  const { t } = useTranslation();
  return (
    <span
      style={{ width }}
      className={`text-lg font-semibold tracking-tight leading-tight ${className ?? ""}`}
    >
      {t("product.name")}
    </span>
  );
};
export default HandyTextLogo;
