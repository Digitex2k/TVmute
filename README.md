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

Либо открой «TV Mute» на телевизоре: кнопка «Открыть настройки» ведёт в системные настройки ТВ.
Отдельного экрана «Специальные возможности» на этой прошивке нет, поэтому включение и выключение
службы — только командами выше (после изменения настроек может потребоваться перезагрузка ТВ).

## E2E-проверка

```
powershell -ExecutionPolicy Bypass -File tests\e2e.ps1
```

## Известные ограничения

- **Удержание кнопок громкости не даёт повтора.** На Android TV `PhoneWindowManager` забирает кнопки
  громкости себе («volume keys never go to the foreground app»), а пульт Xiaomi RC при удержании
  не присылает repeat-события. Это поведение прошивки/пульта, не приложения: ни Button Mapper,
  ни любое другое приложение не может его изменить без root.

## Откат

```
adb shell pm disable-user --user 0 ru.digit.tvmute
adb shell pm enable flar2.homebutton
```

Для полного удаления: `adb uninstall ru.digit.tvmute`.

> Примечание по прошивке: прямое отключение службы или пакета через adb на этом ТВ
> оставляет залипший фильтр клавиш до перезагрузки. Если выключаешь службу, перезагрузи
> телевизор — тогда кнопка Mi вернётся к штатному PatchWall.
