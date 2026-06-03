import { createContext, useContext, useState, useEffect, ReactNode } from "react";
import { t, type Lang } from "./translations";

interface LanguageContextType {
  lang: Lang;
  setLang: (lang: Lang) => void;
  t: (key: string) => string;
}

const LanguageContext = createContext<LanguageContextType | undefined>(undefined);

const LANG_KEY = "freightlink_lang";

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(() => {
    try {
      return (localStorage.getItem(LANG_KEY) as Lang) || "en";
    } catch {
      return "en";
    }
  });

  const setLang = (newLang: Lang) => {
    setLangState(newLang);
    try {
      localStorage.setItem(LANG_KEY, newLang);
    } catch {}
  };

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = "ltr";
  }, [lang]);

  return (
    <LanguageContext.Provider value={{ lang, setLang, t: (key: string) => t(key, lang) }}>
      {children}
    </LanguageContext.Provider>
  );
}

export function useUserLanguage(user: { preferredLanguage?: string | null } | null) {
  const { lang, setLang } = useContext(LanguageContext) || { lang: "en" as Lang, setLang: () => {} };
  useEffect(() => {
    if (user?.preferredLanguage && ["en","am","om","ti"].includes(user.preferredLanguage)) {
      setLang(user.preferredLanguage as Lang);
    }
  }, [user?.preferredLanguage]);
  return lang;
}

export function useLanguage() {
  const context = useContext(LanguageContext);
  if (context === undefined) {
    throw new Error("useLanguage must be used within a LanguageProvider");
  }
  return context;
}
