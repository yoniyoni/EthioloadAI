import { useLanguage } from "@/lib/i18n/language-context";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Globe } from "lucide-react";

const langs = [
  { code: "en" as const, label: "English" },
  { code: "am" as const, label: "Amharic" },
  { code: "om" as const, label: "Afaan Oromo" },
  { code: "ti" as const, label: "Tigrinya" },
] as const;

export function LanguageSwitcher() {
  const { lang, setLang } = useLanguage();
  const current = langs.find((l) => l.code === lang);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-1 text-xs">
          <Globe className="h-3.5 w-3.5" />
          <span className="hidden sm:inline">{current?.label}</span>
          <span className="sm:hidden">{current?.code.toUpperCase()}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {langs.map((l) => (
          <DropdownMenuItem
            key={l.code}
            onClick={() => setLang(l.code)}
            className={l.code === lang ? "font-semibold" : ""}
          >
            {l.label}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
