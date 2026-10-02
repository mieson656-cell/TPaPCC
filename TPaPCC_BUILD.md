# TPaPCC build/test

## Android app
The repository contains the Android application foundation. GitHub Actions builds a debug APK automatically on every push to main.

The app uses Android's user-visible MediaProjection flow for screen capture and foreground services for visible screen/microphone sessions. Android requires the user to approve screen capture before MediaProjection can be used. See Android documentation.

## What the owner must do on the phone
1. Install the APK.
2. Grant microphone/notification permissions when needed.
3. For screen sharing, approve Android's system screen-capture dialog.
4. For UI control, explicitly enable TPaPCC in Accessibility settings.
5. Never enable permissions that are not needed for the current session.

## Telegram
Set the Supabase secret TELEGRAM_BOT_TOKEN after creating the bot with BotFather. Never commit the token.
