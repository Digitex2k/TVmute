# TV Mute Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Заменить Button Mapper крошечным приложением, которое по короткому нажатию кнопки Mi глушит звук, а по удержанию открывает PatchWall, не влияя на остальные кнопки.

**Architecture:** Один AccessibilityService с фильтрацией клавиш. Логика нажатия вынесена в чистый Java-класс `PressStateMachine`, который тестируется на ПК без Android. Сервис только связывает события клавиш с действиями (mute через AudioManager, запуск PatchWall через explicit intent).

**Tech Stack:** Java 8, Android SDK (build-tools 35.0.0, platform android-35), сборка без Gradle: aapt2 → javac → d8 → jar → zipalign → apksigner. Тесты логики — plain Java `main` с assert. E2E — adb (установка, инъекция `sendevent`, проверка logcat/`dumpsys`).

**Spec:** `docs/superpowers/specs/2026-11-21-tv-mute-button-design.md`

## Global Constraints

- Пакет: `ru.digit.tvmute`; приложение: «TV Mute».
- minSdk 28, targetSdk 28 (телевизор на Android 9, API 28).
- Кнопка Mi: Android keyCode **191**; Linux `KEY_YELLOW` (`type=1 code=400`).
- Порог удержания: **500 мс**; долгое действие срабатывает по таймеру, не по отпусканию.
- Другие клавиши: `onKeyEvent` немедленно `return false`, никаких записей и таймеров.
- PatchWall: `com.mitv.tvhome.atv/.app.BannerLogoB`, `ACTION_MAIN` + `CATEGORY_LAUNCHER` + `FLAG_ACTIVITY_NEW_TASK`.
- Mute: глушить набор потоков, доступных приложению (цель — как системный mute: 0–5, 7–10), состояние брать из `isStreamMute(STREAM_MUSIC)`; `SecurityException` по потоку — пропустить.
- Никаких сторонних библиотек и Gradle.
- Button Mapper после проверки отключается (`pm disable-user --user 0 flar2.homebutton`), не удаляется.
- Телевизор: `192.168.10.180:5555`, adb: `C:\platform-tools\adb.exe`.

## Review Focus

- **Чужие клавиши.** Навигация (стрелки/OK/назад/домой) должна проходить без задержки и без записей `TVMute` — сервис не должен их даже логировать.
- **Однократность долгого нажатия.** Удержание даёт ровно один запуск PatchWall и ни одного mute, в том числе если клавиша повторяется (`repeatCount > 0`).
- **Повторные короткие нажатия.** Два быстрых тапа подряд = два mute (туда-обратно), без «двойного нажатия».
- **Реальный звук.** Mute должен глушить то, что реально звучит (STREAM_MUSIC и системные), а не менять только флаг.
- **Отключённая служба.** Если службу выключить, кнопка возвращается к штатному PatchWall — приложение не должно ломать устройство.

---

### Task 1: Каркас проекта и сборка APK

**Files:**
- Create: `C:\Default Project\TVmute\app\AndroidManifest.xml`
- Create: `C:\Default Project\TVmute\app\res\values\strings.xml`
- Create: `C:\Default Project\TVmute\app\res\drawable\ic_launcher.xml`
- Create: `C:\Default Project\TVmute\app\java\ru\digit\tvmute\SetupActivity.java`
- Create: `C:\Default Project\TVmute\build.ps1`
- Create: `C:\Default Project\TVmute\.gitignore`
- Create: `C:\Default Project\TVmute\README.md`

**Interfaces:**
- Produces: `build.ps1` с параметром `-Target {test|apk|all}` (по умолчанию `all`) собирает `out\TVMute.apk`; переменные `$Jdk`, `$BuildTools`, `$AndroidJar` в начале скрипта; keystore `keystore\tvmute.keystore` (alias `tvmute`, пароль `tvmute123`).
- Produces: manifest объявляет activity `ru.digit.tvmute.SetupActivity` как launcher.

- [ ] **Step 1: Инициализировать git-репозиторий**

Run:
```
git init; git add docs; git commit -m "docs: design and plan for TV Mute"
```

- [ ] **Step 2: Скачать портативный JDK 17**

`build.ps1` при отсутствии `tools\jdk-17\bin\javac.exe` скачивает
`https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse`
в `tools\jdk17.zip` и распаковывает в `tools\jdk-17` (Expand-Archive).

- [ ] **Step 3: Написать манифест и минимальный SetupActivity**

`AndroidManifest.xml`: `package="ru.digit.tvmute"`, `uses-sdk minSdkVersion=28 targetSdkVersion=28`,
разрешение `android.permission.MODIFY_AUDIO_SETTINGS`, activity `.SetupActivity` с MAIN/LAUNCHER.
`SetupActivity` на этом шаге — `TextView` «TV Mute» по центру.

- [ ] **Step 4: Написать build.ps1**

Пайплайн (пути через переменные вверху скрипта):
```
aapt2 compile --dir app\res -o build\res.zip
aapt2 link -o build\base.apk -I $AndroidJar --manifest app\AndroidManifest.xml
           -R build\res.zip --java build\gen --min-sdk-version 28 --target-sdk-version 28
javac -source 8 -target 8 -classpath "$AndroidJar;build\gen" -d build\classes @sources
d8 --lib $AndroidJar --min-api 28 --output build\dex build\classes
jar --update --file build\base.apk -C build\dex classes.dex
zipalign -f 4 build\base.apk build\aligned.apk
apksigner sign --ks keystore\tvmute.keystore --ks-pass pass:tvmute123
          --key-pass pass:tvmute123 --out out\TVMute.apk build\aligned.apk
```
Keystore создаётся один раз через `keytool -genkeypair ... -dname "CN=TVMute"`.
`$BuildTools = "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0"`,
`$AndroidJar = "$env:LOCALAPPDATA\Android\Sdk\platforms\android-35\android.jar"`.

- [ ] **Step 5: Собрать и установить**

Run: `powershell -ExecutionPolicy Bypass -File build.ps1`
Expected: `Build OK: out\TVMute.apk`
Run: `adb -s 192.168.10.180:5555 install -r out\TVMute.apk`
Expected: `Success`

- [ ] **Step 6: Проверить, что приложение запускается**

Run: `adb -s 192.168.10.180:5555 shell am start -n ru.digit.tvmute/.SetupActivity`
Expected: на экране ТВ «TV Mute», в `dumpsys activity activities` — `ru.digit.tvmute/.SetupActivity`.

- [ ] **Step 7: Commit**

```
git add app build.ps1 .gitignore README.md
git commit -m "feat: project skeleton and build pipeline"
```

---

### Task 2: PressStateMachine + тесты на ПК

**Files:**
- Create: `C:\Default Project\TVmute\app\java\ru\digit\tvmute\PressStateMachine.java`
- Create: `C:\Default Project\TVmute\tests\java\ru\digit\tvmute\PressStateMachineTest.java`
- Modify: `build.ps1` (шаг `test`: компиляция и запуск тестов до сборки APK)

**Interfaces:**
- Produces:
  ```java
  public final class PressStateMachine {
      public enum Action { NONE, MUTE, OPEN_PATCHWALL }
      public static final long LONG_PRESS_MS = 500L;
      public Action onDown(long eventTimeMs, boolean repeat);
      public Action onUp(long eventTimeMs);
      public Action onTimeout();
      public void reset();
  }
  ```
- Consumes: ничего.

- [ ] **Step 1: Написать падающий тест**

`PressStateMachineTest` — plain `main`, счётчик ошибок, в конце `System.exit(bad == 0 ? 0 : 1)`. Кейсы:
1. tap 150 мс → `onDown(1000,false)=NONE`, `onUp(1150)=MUTE`.
2. hold: `onDown(0,false)=NONE`, `onTimeout()=OPEN_PATCHWALL`, `onUp(700)=NONE`.
3. два тапа: (0,150)→MUTE, (160,300)→MUTE.
4. repeat: повторный `onDown(…,true)` не меняет состояние; `onTimeout()` без DOWN → `NONE`.
5. cancel/`reset()` после DOWN: `onTimeout()` → `NONE`, `onUp` → `NONE`.
6. `onTimeout()` дважды → второй `NONE`.

- [ ] **Step 2: Убедиться, что тест падает**

Run: `powershell -ExecutionPolicy Bypass -File build.ps1 -Target test`
Expected: FAIL — класс `PressStateMachine` не найден.

- [ ] **Step 3: Реализовать класс**

Поля: `down`, `downAt`, `longFired`. `onDown(repeat=true)` → `NONE` без изменения состояния.
`onUp`: если `!down` → `NONE`; если `longFired` → сброс + `NONE`; иначе сброс + `MUTE`.
`onTimeout`: если `down && !longFired` → `longFired=true`, `OPEN_PATCHWALL`, иначе `NONE`.

- [ ] **Step 4: Тесты зелёные**

Run: `powershell -ExecutionPolicy Bypass -File build.ps1 -Target test`
Expected: `PressStateMachineTest: 6/6 OK`

- [ ] **Step 5: Commit**

```
git add app/java/ru/digit/tvmute/PressStateMachine.java tests/java build.ps1
git commit -m "feat: press state machine with host tests"
```

---

### Task 3: AccessibilityService — перехват только кнопки Mi

**Files:**
- Create: `C:\Default Project\TVmute\app\java\ru\digit\tvmute\MuteAccessibilityService.java`
- Create: `C:\Default Project\TVmute\app\java\ru\digit\tvmute\Actions.java`
- Create: `C:\Default Project\TVmute\app\res\xml\accessibility_service.xml`
- Modify: `C:\Default Project\TVmute\app\AndroidManifest.xml`

**Interfaces:**
- Consumes: `PressStateMachine.Action`.
- Produces: service `ru.digit.tvmute/.MuteAccessibilityService`, тег logcat `TVMute`.

- [ ] **Step 1: Манифест и конфиг службы**

Manifest: `<service android:name=".MuteAccessibilityService" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" android:exported="true">`
с `<intent-filter><action android:name="android.accessibilityservice.AccessibilityService"/></intent-filter>`
и `<meta-data android:name="android.accessibilityservice" android:resource="@xml/accessibility_service"/>`.

`accessibility_service.xml`: `accessibilityEventTypes="typeWindowStateChanged"`,
`accessibilityFeedbackType="feedbackGeneric"`, `canRetrieveWindowContent="false"`,
`canRequestFilterKeyEvents="true"`, `accessibilityFlags="flagRequestFilterKeyEvents"`,
`notificationTimeout="0"`, description «Кнопка Mi: коротко — звук, долго — PatchWall».

- [ ] **Step 2: Реализовать onKeyEvent**

Псевдокод с точными константами:
```java
private static final int KEYCODE_MI = 191;
private final Handler handler = new Handler(Looper.getMainLooper());
private final Runnable timeout = this::onTimeout;
private final PressStateMachine machine = new PressStateMachine();

public boolean onKeyEvent(KeyEvent e) {
    if (e.getKeyCode() != KEYCODE_MI) return false; // чужие клавиши не трогаем
    switch (e.getAction()) {
        case ACTION_DOWN:
            if (e.getRepeatCount() == 0) {
                machine.onDown(SystemClock.uptimeMillis(), false);
                handler.postDelayed(timeout, PressStateMachine.LONG_PRESS_MS);
            }
            return true;
        case ACTION_UP:
            handler.removeCallbacks(timeout);
            run(machine.onUp(SystemClock.uptimeMillis()));
            return true;
        case ACTION_CANCEL:
            handler.removeCallbacks(timeout);
            machine.reset();
            return true;
    }
    return true;
}
```
`onServiceConnected` добавляет `FLAG_REQUEST_FILTER_KEY_EVENTS` в `serviceInfo.flags` и вызывает `setServiceInfo`.
`run(action)`: `MUTE` → `Actions.toggleMute(this)`; `OPEN_PATCHWALL` → `Actions.openPatchWall(this)`; `NONE` → ничего.
Логировать только для кода 191: `Log.i("TVMute", "key=191 action=... dur=...")`.

- [ ] **Step 3: Реализовать Actions**

`Actions.toggleMute(Context)`:
```java
AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
boolean mute = !am.isStreamMute(AudioManager.STREAM_MUSIC);
int[] streams = {0,1,2,3,4,5,7,8,9,10};
for (int s : streams) {
    try { am.adjustStreamVolume(s, mute ? ADJUST_MUTE : ADJUST_UNMUTE, 0); }
    catch (SecurityException ignored) {}
}
```
`Actions.openPatchWall(Context)`:
```java
Intent i = new Intent(Intent.ACTION_MAIN)
    .addCategory(Intent.CATEGORY_LAUNCHER)
    .setComponent(new ComponentName("com.mitv.tvhome.atv", "com.mitv.tvhome.atv.app.BannerLogoB"))
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
try { ctx.startActivity(i); } catch (ActivityNotFoundException e) { Log.e("TVMute", "PatchWall not found", e); }
```

- [ ] **Step 4: Собрать, установить, включить службу**

Run: `build.ps1`, затем
`adb shell settings put secure enabled_accessibility_services ru.digit.tvmute/ru.digit.tvmute.MuteAccessibilityService`
`adb shell settings put secure accessibility_enabled 1`
Expected: в `dumpsys accessibility` есть `Service[label=TV Mute ...]`.

- [ ] **Step 5: E2E — короткое нажатие**

Run: `adb logcat -c`, затем инъекция
```
sendevent /dev/input/event3 4 4 786540; sendevent /dev/input/event3 1 400 1; sendevent /dev/input/event3 0 0 0;
sleep 0.15;
sendevent /dev/input/event3 4 4 786540; sendevent /dev/input/event3 1 400 0; sendevent /dev/input/event3 0 0 0
```
Expected: `dumpsys audio` → `Muted: true`; logcat `TVMute key=191 dur≈150 action=MUTE`; в логе нет `startPatchWall`.
Повторить инъекцию → `Muted: false`.

- [ ] **Step 6: E2E — удержание**

Аналогично, `sleep 1.2` между DOWN и UP.
Expected: открыт PatchWall (`dumpsys activity activities | grep BannerLogoB`), `Muted` не изменился,
в logcat `action=OPEN_PATCHWALL` ровно один раз.

- [ ] **Step 7: E2E — чужие клавиши**

Инъекция `KEY_HOMEPAGE` (`type=1 code=172`) и `KEY_BACK` (`code=158`): работает навигация,
в logcat нет строк `TVMute`.

- [ ] **Step 8: Commit**

```
git add app build.ps1
git commit -m "feat: accessibility service for Mi button"
```

---

### Task 4: Экран настройки, интеграция и проверка

**Files:**
- Modify: `C:\Default Project\TVmute\app\java\ru\digit\tvmute\SetupActivity.java`
- Create: `C:\Default Project\TVmute\tests\e2e.ps1`
- Modify: `C:\Default Project\TVmute\README.md`

**Interfaces:**
- Consumes: `ru.digit.tvmute/.MuteAccessibilityService`.
- Produces: `tests\e2e.ps1 -Adb <path> -Serial <ip:port> [-SkipButtonMapper]`, exit code 0 = все проверки прошли.

- [ ] **Step 1: SetupActivity**

Показывает: «Служба: включена/выключена» (проверка через `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`),
кнопку «Открыть настройки доступности» (`Settings.ACTION_ACCESSIBILITY_SETTINGS`), кнопку «Проверить»,
которая запускает `View`-подсказку «Нажмите Mi: коротко — звук, долго — PatchWall».
Текст на русском.

- [ ] **Step 2: tests\e2e.ps1**

Скрипт последовательно: установка APK → включение службы → очистка logcat → инъекции (короткая, длинная,
чужая клавиша) → проверки `Muted`, `BannerLogoB`, отсутствие `TVMute` для чужой клавиши → выключение службы →
инъекция ключа 191 → проверка, что открылся штатный PatchWall (т.е. поведение вернулось системе).
Печатает `PASS/FAIL` по каждому пункту и итог.

- [ ] **Step 3: Отключить Button Mapper и прогнать полный E2E**

Run: `adb shell pm disable-user --user 0 flar2.homebutton`
Run: `powershell -ExecutionPolicy Bypass -File tests\e2e.ps1`
Expected: все пункты PASS.

- [ ] **Step 4: Физическая проверка с владельцем**

Попросить нажать Mi коротко 3 раза (звук вкл/выкл/вкл) и удержать 2 раза (PatchWall),
затем походить по меню стрелками.
Expected: без задержек; жалоб на навигацию нет.

- [ ] **Step 5: README**

Описать установку, включение службы, как откатиться (`pm enable flar2.homebutton`, удалить `ru.digit.tvmute`),
пути сборки и требования (JDK 17, build-tools 35).

- [ ] **Step 6: Commit**

```
git add app tests README.md
git commit -m "feat: setup screen, e2e script, docs"
```

---

## Проверка после выполнения

1. `powershell -File build.ps1 -Target test` → unit-тесты 6/6.
2. `powershell -File tests\e2e.ps1` → PASS.
3. Button Mapper отключён, `dumpsys accessibility` содержит только `TV Mute`.
4. Владелец подтвердил: короткое — mute, долгое — PatchWall, навигация не тормозит.
