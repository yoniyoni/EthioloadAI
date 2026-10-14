import { useState, useRef, useEffect } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/contexts/auth-context'
import { useLanguage } from '@/lib/i18n/language-context'
import {
  Loader2,
  MessageSquare,
  X,
  Send,
  Bot,
  User,
  Sparkles,
  Zap,
} from 'lucide-react'

interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  intent?: string
  timestamp: Date
}

export function AIChatWidget() {
  const { user } = useAuth()
  const { t } = useLanguage()
  const [isOpen, setIsOpen] = useState(false)
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      role: 'assistant',
      text: t('chat.welcome'),
      timestamp: new Date(),
    },
  ])
  const [input, setInput] = useState('')
  const scrollRef = useRef<HTMLDivElement>(null)

  const assistantMutation = useMutation({
    mutationFn: (message: string) =>
      api.post<any>('/ai/freight-assistant', {
        message,
        context: {
          userRole: user?.role,
          userId: user?.id,
          language: localStorage.getItem('freightlink_lang') ?? 'en',
        },
      }),
    onSuccess: (data) => {
      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          text: data.response?.text || t('chat.error'),
          intent: data.response?.intent,
          timestamp: new Date(),
        },
      ])
    },
    onError: () => {
      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          text: t('chat.error'),
          timestamp: new Date(),
        },
      ])
    },
  })

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [messages])

  const handleSend = () => {
    if (!input.trim() || assistantMutation.isPending) return
    const userMsg = input.trim()
    setMessages((prev) => [
      ...prev,
      { role: 'user', text: userMsg, timestamp: new Date() },
    ])
    setInput('')
    assistantMutation.mutate(userMsg)
  }

  const quickChips = [t('chat.quick1'), t('chat.quick2'), t('chat.quick3')]

  return (
    <>
      {/* Floating button */}
      {!isOpen && (
        <button
          onClick={() => setIsOpen(true)}
          className='fixed bottom-6 right-6 z-[100] h-14 w-14 rounded-full bg-gradient-to-br from-primary to-[#1a3a6e] text-white shadow-xl hover:scale-105 transition-all flex items-center justify-center animate-in fade-in slide-in-from-bottom-4 duration-500'
          aria-label='Open AI assistant'
          style={{ animation: 'pulse-glow 2s infinite' }}
        >
          <div className='absolute inset-0 rounded-full bg-primary/30 animate-ping' />
          <Sparkles className='h-5 w-5 relative z-10' />
        </button>
      )}

      {/* Chat panel */}
      {isOpen && (
        <div className='fixed bottom-6 right-6 z-[100] w-[360px] max-w-[calc(100vw-2rem)] h-[480px] max-h-[calc(100vh-4rem)] bg-background rounded-2xl shadow-2xl border border-border/60 flex flex-col overflow-hidden'>
          {/* Header */}
          <div className='flex items-center justify-between px-4 py-3 border-b bg-gradient-to-r from-[#0c1e4a] to-[#1a3a6e] text-white'>
            <div className='flex items-center gap-2'>
              <div className='h-8 w-8 rounded-full bg-white/10 flex items-center justify-center'>
                <Bot className='h-4 w-4 text-emerald-400' />
              </div>
              <div>
                <p className='text-sm font-semibold'>{t('chat.title')}</p>
                <p className='text-xs text-slate-300'>{t('chat.subtitle')}</p>
              </div>
            </div>
            <button
              onClick={() => setIsOpen(false)}
              className='p-1 rounded-md hover:bg-white/10 transition-colors'
              aria-label='Close chat'
            >
              <X className='h-4 w-4 text-white' />
            </button>
          </div>

          {/* Messages */}
          <div ref={scrollRef} className='flex-1 overflow-y-auto p-4 space-y-3'>
            {messages.map((msg, i) => (
              <div
                key={i}
                className={`flex gap-2 ${msg.role === 'user' ? 'flex-row-reverse' : ''}`}
              >
                <div
                  className={`h-7 w-7 rounded-full flex items-center justify-center shrink-0 ${
                    msg.role === 'assistant' ? 'bg-primary/10' : 'bg-emerald-50'
                  }`}
                >
                  {msg.role === 'assistant' ? (
                    <Bot className='h-3.5 w-3.5 text-primary' />
                  ) : (
                    <User className='h-3.5 w-3.5 text-emerald-600' />
                  )}
                </div>
                <div
                  className={`max-w-[80%] rounded-2xl px-3 py-2 text-sm whitespace-pre-wrap ${
                    msg.role === 'assistant'
                      ? 'bg-muted text-foreground rounded-tl-sm'
                      : 'bg-primary text-primary-foreground rounded-tr-sm'
                  }`}
                >
                  {msg.text}
                </div>
              </div>
            ))}
            {assistantMutation.isPending && (
              <div className='flex gap-2'>
                <div className='h-7 w-7 rounded-full bg-primary/10 flex items-center justify-center'>
                  <Bot className='h-3.5 w-3.5 text-primary' />
                </div>
                <div className='bg-muted rounded-2xl rounded-tl-sm px-3 py-2 flex items-center gap-1'>
                  <Loader2 className='h-3 w-3 animate-spin text-muted-foreground' />
                  <span className='text-xs text-muted-foreground'>
                    {t('chat.thinking')}
                  </span>
                </div>
              </div>
            )}
          </div>

          {/* Quick chips */}
          <div className='px-3 pt-2 flex flex-wrap gap-1'>
            {quickChips.map((q) => (
              <button
                key={q}
                onClick={() => {
                  setMessages((prev) => [
                    ...prev,
                    { role: 'user', text: q, timestamp: new Date() },
                  ])
                  assistantMutation.mutate(q)
                }}
                className='text-xs bg-muted hover:bg-primary/10 hover:text-primary text-muted-foreground px-2 py-1 rounded-full transition-colors'
              >
                {q}
              </button>
            ))}
          </div>

          {/* Input */}
          <div className='p-3 border-t flex gap-2'>
            <Input
              placeholder={t('chat.placeholder')}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSend()}
              className='text-sm rounded-lg'
            />
            <Button
              size='icon'
              onClick={handleSend}
              disabled={!input.trim() || assistantMutation.isPending}
              className='shrink-0 rounded-lg bg-primary hover:bg-primary/90'
            >
              <Send className='h-4 w-4' />
            </Button>
          </div>
        </div>
      )}
    </>
  )
}
