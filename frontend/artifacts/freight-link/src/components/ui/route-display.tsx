import { ArrowRight } from 'lucide-react';

const CITY_AMHARIC: Record<string, string> = {
  'addis ababa': 'አዲስ አበባ',
  'gondar': 'ጎንደር',
  'mekelle': 'መቀሌ',
  'bahir dar': 'ባህር ዳር',
  'hawassa': 'ሐዋሳ',
  'jimma': 'ጅማ',
  'dire dawa': 'ድሬ ዳዋ',
  'humera': 'ሁመራ',
  'metema': 'መተማ',
  'shire': 'ሽሬ',
  'adwa': 'አድዋ',
  'axum': 'አክሱም',
  'dessie': 'ደሴ',
  'kombolcha': 'ኮምቦልቻ',
  'adama': 'አዳማ',
  'nekemte': 'ነቀምት',
  'debre markos': 'ደብረ ማርቆስ',
  'debre tabor': 'ደብረ ታቦር',
  'debre birhan': 'ደብረ ብርሃን',
  'woldia': 'ወልዲያ',
  'addis zemen': 'አዲስ ዘመን',
};

function amharic(city: string) {
  return CITY_AMHARIC[city.toLowerCase().trim()] ?? '';
}

type Props = {
  from: string;
  to: string;
  className?: string;
};

export function RouteDisplay({ from, to, className }: Props) {
  const fromAm = amharic(from);
  const toAm = amharic(to);

  return (
    <div className={className}>
      <div className="flex items-center gap-1.5 font-semibold text-sm" style={{ color: '#0D1F12' }}>
        <span>{from}</span>
        <ArrowRight size={14} color="#F59E0B" />
        <span>{to}</span>
      </div>
      {(fromAm || toAm) && (
        <div
          className="flex items-center gap-1.5 text-xs mt-0.5"
          style={{ color: '#8FA893' }}
        >
          {fromAm && <span>{fromAm}</span>}
          {fromAm && toAm && <ArrowRight size={10} color="#F59E0B" />}
          {toAm && <span>{toAm}</span>}
        </div>
      )}
    </div>
  );
}
