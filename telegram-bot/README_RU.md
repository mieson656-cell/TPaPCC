# Telegram-бот TPaPCC

Бот используется как интерфейс уведомлений и управления доверенными подключениями.

Токен бота **не хранить в GitHub**. Он должен быть секретом Supabase с именем `TELEGRAM_BOT_TOKEN`.

Webhook:
`https://xblfnpiarlhbntfkxwgq.supabase.co/functions/v1/tpaPCC-telegram`

Команды:
- /start
- /help
- /id
- /privacy

Скрытое управление телефоном не реализуется: функции телефона остаются за приложением TPaPCC и разрешениями владельца Android.
