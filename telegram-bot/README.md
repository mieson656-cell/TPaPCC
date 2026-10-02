# TPaPCC Telegram Bot

Telegram Bot API is used as the notification/control interface. Telegram bots communicate with Telegram over HTTPS. See the official Bot API documentation.

## Supabase
The bot webhook is the Edge Function `tpaPCC-telegram`.

Set the Supabase secret:

`TELEGRAM_BOT_TOKEN`

Then configure Telegram to send updates to:

`https://xblfnpiarlhbntfkxwgq.supabase.co/functions/v1/tpaPCC-telegram`

Do not put the bot token in this repository.

## Current commands
- /start
- /help
- /id
- /privacy

The bot deliberately does not implement hidden device control. Device capabilities remain gated by the TPaPCC app and the owner's Android permissions.
