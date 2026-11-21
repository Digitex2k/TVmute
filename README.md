# TV Mute

Крошечное приложение для Xiaomi Mi TV (Android 9, MiTV-MSSP1): перехватывает только кнопку Mi
и не влияет на остальные кнопки пульта.

- **Короткое нажатие** — включить/выключить звук.
- **Удержание 500 мс** — штатное действие кнопки: переход в PatchWall.

## Требования

- Windows + PowerShell 5.1+
- Android SDK: `build-tools;35.0.0`, `platforms;android-35`
  (ожидается в `%LOCALAPPDATA%\Android\Sdk`)
- JDK 17 скачивается автоматически в `tools\jdk-17` (около 190 МБ, без установки в систему)

## Сборка

```
powershell -ExecutionPolicy Bypass -File build.ps1
```

Результат: `out\TVMute.apk`. Тесты логики: `build.ps1 -Target test`. Только APK: `build.ps1 -Target apk`.

## Установка и включение

```
adb install -r out\TVMute.apk
adb shell settings put secure enabled_accessibility_services ru.digit.tvmute/ru.digit.tvmute.MuteAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

Либо вручную: открыть «TV Mute» на телевизоре → «Открыть настройки доступности» → включить службу.

## E2E-проверка

```
powershell -ExecutionPolicy Bypass -File tests\e2e.ps1
```

## Откат

```
adb shell pm disable-user --user 0 ru.digit.tvmute
adb shell pm enable flar2.homebutton
```

Для полного удаления: `adb uninstall ru.digit.tvmute`.
