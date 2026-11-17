# Notification panel design

## 0.11.11 — opt-in transfer from the system shade

Operate mode: existing grouped settings gain “Только в нашей Шторке”. Off by default; enabled explicitly on the user's phone after installation. Eligible ordinary text cards that pass the panel filters are durably copied before NotificationListenerService cancellation. Calls, alarms, navigation, media sessions/transport, full-screen intents, ongoing/foreground/no-clear cards, Android/SystemUI, own service and other profiles remain in the system shade. Group summaries are never cancelled while any active child remains, protecting mixed groups.

LocalNotifications stores bounded JSON in app-private, non-backed-up files using AtomicFile: at most 200 copies, seven-day expiry, no eviction of unexpired cards to make room. Write failure, full store, unsupported custom content/images/attachments and oversized text leave the original in place. No message content is uploaded. Source repost replaces the same key; a live native card takes precedence over its copy. Copies survive process/listener restarts, support local dismiss/snooze, and remain when the mode is switched off (only future transfer stops). Expired copies are purged when the source is read. App data removal clears them.

Copies offer Open using the original contentIntent while valid, then the app launcher. Reply/read action tokens are not retained; opening does not imply marking read. No PendingIntent is serialized. Existing SMS and Spleta sources are unchanged. This is a text-card transfer trial, not replacement of SystemUI; unsupported or filtered cards intentionally remain native.

Validation: build/grouping/update tests, Android instrumentation for protected categories/media/ongoing/work profile, durable reload, actions stripped, snooze/return/dismiss, group keys and failed writes; existing SMS/RCS/opener regression passed. Separate emulator fixture posted email + call in one group and alarm/navigation/music: email left the native shade and appeared in our panel after process restart; all four protected categories and their mixed summary remained. Turning the mode off kept a new email native. Settings rendered and visually reviewed on emulator.

Installed 0.11.11 (37) by USB on CPH2723 and enabled the switch through settings. Native counts confirmed Telegram and T-Bank moved out while Yandex Music and service/system cards remained. Phone locked before final card-level visual verification; persistence/display were verified on emulator. Restored USB stay-awake to false. No public release.


## 0.11.10 — translucent panel trial

PanelActivity and OverlayPanelActivity use a translucent full-screen theme.
PanelBackdrop requests native cross-window blur-behind at 120 px and applies an
80% dark-blue tint; cards retain their prior density. WindowManager capability
listener switches to the opaque ShadeBackground when blur is unavailable and
is removed on destruction. No wallpaper screenshots, copied frames or root.
Settings and other activities retain their existing opaque theme.

Verified build, existing tests/signature, emulator rendering and physical CPH2723
window reporting BLUR_BEHIND with blurBehindRadius=120. Inspected phone screenshot
after side-handle launch from home: OverlayPanelActivity, blurred underlying
wallpaper/icons, readable cards. Settings app blocks overlay taps by design.
Installed 0.11.10 via USB; restored stay-awake to false. No public release.
System wm disable-blur command was rejected by automatic approval review; no
global blur setting was changed. Runtime support was read using the public API.


## 0.11.9 — open direct SMS

Direct SMS cards no longer synthesize a PendingIntent to a provider thread URI.
A visible Activity handles the user click with ACTION_SENDTO and an encoded smsto
sender, explicitly targeting the current default SMS app. No body is prefilled.
If navigation throws ActivityNotFoundException or SecurityException, try that
app’s launcher; report failure only if neither route works. Other notification
actions retain the original PendingIntent behavior. Includes 0.11.8 dedup changes.

Validated build/grouping/UpdateCore/signature and instrumented tests for smsto
construction, no message body, fallback on rejection, failure reporting and all
dedup cases. Installed 0.11.9 via USB on CPH2723; tapped Open in a real SMS card:
foreground switched to Google Messages LaunchConversationActivity, conversation
UI present, no previous error toast. Default SMS app unchanged. Restored screen
timeout. No public release.


## 0.11.8 — conservative SMS deduplication

Filter only local snapshots, never cancel source notifications. A notification
from the current default SMS package and current profile is redundant only when
every current message matches a loaded direct SMS by full body, sender and time
(within one second). MessagingStyle supports tel: Person identity; unknown sender,
attachments, redacted content, inbox-style batches and mismatches are retained.
Remove a summary only if at least one child exists and every child was deduplicated.
Mixed SMS/RCS and orphan summaries remain visible. Hidden/snoozed direct SMS still
participate in matching to prevent their ordinary duplicates from resurfacing.
Disable/revoke/no loaded SMS means the ordinary stream is retained. This cannot
guarantee removal of redacted duplicates without a reliable transport identifier.

Validated: build, grouping and UpdateCore checks; emulator instrumentation covering
exact SMS, missing source, RCS, sender/time mismatch, SMS-only/mixed/orphan summaries,
MessagingStyle, mixed conversations, redaction, attachments and another profile.
Phone install pending: USB device 1fdc15af disconnected. No public release.


## 0.11.7 — direct SMS inbox

Opt-in READ_SMS with a native switch in Settings. Local synthetic SMS group uses
a distinct grouping key from Spleta. Query unread inbox messages from the last
7 days, newest first, at most 100 nonempty messages. No SEND_SMS, RECEIVE_SMS,
SMS role request, provider writes, server upload or persisted message bodies.
Observe provider changes, refresh on resume and notification updates, and while
the panel is visible. Clear the in-memory inbox when the screen turns off; source
snapshots also require an unlocked device and active permission.
Open delegates to the current default SMS app. Dismiss/snooze stores only local
message IDs and expiry; never marks the inbox read or deletes provider rows.
Retain ordinary messaging notifications because RCS is outside the SMS provider.
Source is separate from the OS notification stream; duplicate SMS notifications
can therefore coexist with the direct SMS group.

Validation: grouping and UpdateCore tests, build/signature, emulator instrumented
checks for inbox ingestion, distinct group, stable objects across refresh,
snooze/return, disable/re-enable, local dismiss preserving unread state and default
SMS handler. Emulator visual preview inspected. Installed on CPH2723 via USB;
READ_SMS granted and used, SMS group rendered, default remained
com.google.android.apps.messaging. No public release.


## 0.11.6 — grouped settings

Settings is now a dedicated native Activity instead of a modal action sheet.
Four inset groups: Panel, Hide notifications, Portal, Application. Uniform 52 dp
minimum rows, 16 sp labels, 15 sp trailing values, 13 sp section labels, 14 dp
group radii and inset hairline separators. Preserve the blue dark palette.
Binary preferences use native accessible switches; values and exclusions navigate
to child lists. Selection saves immediately, returns to the parent and refreshes
the value. Back preserves the parent scroll offset; configuration recreation
retains the current page. Dynamic text wraps; value width adapts to font scale.
Spleta has one status line instead of overlapping source/transport diagnostics.
The separate portal login and updater screens retain their existing behavior.

Reference: Apple HIG Settings and Settings bundle controls (toggle, multi-value,
child pane), https://developer.apple.com/design/human-interface-guidelines/settings
and https://developer.apple.com/documentation/Foundation/building-a-settings-bundle-for-your-app .
This is native Android UI informed by grouped settings, not an iOS library port.

Verified local build, grouping/UpdateCore tests and signature; emulator captures at
font scales 1.0 and 1.5, value change 3 → 5 reflected in parent then restored to 3.
Installed 0.11.6 via USB on CPH2723 after unlock; verified SettingsActivity UI,
retained values (2 hour snooze, 5 rows, 7 exclusions), and connected Spleta status.
No public release.


## Pending — simplified settings (not released)

Keep the native Operate surface. Replace edge launcher controls with a single
«Боковая кнопка» switch reflecting actual service state, including permission
round trips. Remove edge diagnostic copy, instructions and stop tile registration;
the foreground notification opens the app and no longer stops the edge service.
Remove snooze help, special-category filter control (retain saved filtering),
auto-collapse behavior, demo entry, quick tile add button and stale footer.
Keep Spleta status and connection only. Its stream restores on app resume,
boot and package replacement, uses START_STICKY, and has no manual stop action.
Android force-stop, OEM background limits and expired portal sessions still apply.
The update screen and retained settings are unchanged.

Validation: local build, existing grouping and UpdateCore tests, signature check;
Android emulator screenshots of both settings scroll positions and edge switch
on/off (service started then absent after off). Spleta emulator session is expired;
real authenticated delivery and reboot recovery still need device validation.
No physical phone install or public release for this change.


## 0.11.5 — correct live sidebar height

The 60 dp default resource used in 0.11.4 was not the actual rendered height.
Live OplusOSEdgeFloatBar window is (1188,267), 28 x 264 px on CPH2723. Match its
vertical extent by drawing our bar 264 px tall (264/3.5 dp), centered in the unchanged
266 px touch region: visible y=267..531, center y=399. Keep our 4 dp thickness,
blue color and 75% opacity. The right bar is omitted from ADB screencap, so live
window bounds, not a claimed screenshot comparison, are the reference.

## 0.11.4 — match right sidebar trigger

Match the reference OnePlus right sidebar region (1143,266)-(1217,532): left trigger
74 x 266 px at density 3.5, top 266 px including the status bar. Width scales as
74/3.5 dp, height 76 dp, screen-space top scales as 266/2640 of screen height.
Visible bar uses the installed SmartSideBar resource dimensions, 4 x 60 dp,
vertically centered, with a 4 dp left inset. Preserve blue #C7E4FF at alpha 191/255.
This matches the current reference position; moving the system sidebar later does
not automatically reposition our trigger. Existing recovery logic is unchanged.
Verified installed 0.11.4 on CPH2723: window 74 x 266 px, y=131 plus 135 px status
inset, screenshot inspected, tap opens OverlayPanelActivity.

## 0.11.3 — recover hidden edge handle

Reconcile the edge handle visibility once per second while the screen is interactive.
Screen-on and user-present events restart a single callback chain; screen-off and
service destruction cancel it. No wake lock, alarm or sleeping-device polling.
The existing lockscreen and in-app visibility gates remain authoritative. This
addresses a live service whose handle stayed hidden until an Activity lifecycle
event; the exact OEM event ordering that originally caused it was not recorded.
Explicit service stop still disables restoration. Protected windows and Android
force-stop remain platform limits; this is not an always-visible lockscreen overlay.

## 0.11.2 — native group motion trial

User-triggered group expansion and “Ещё” reveal the measured content over 260 ms;
collapse takes 190 ms, with Android PathInterpolator(0.2, 0, 0, 1).
Only the affected group changes height. Text is measured at its final width and size,
without scaling. Background updates do not trigger the effect. Rapid reversal starts
from the currently displayed height; detached views reset to natural height.
Android's disabled animator setting skips the transition. Existing swipe behavior is
unchanged. This is an original Android Views implementation inspired by the motion
idea discussed with the user; no Transitions.dev code or dependency is included.

Mode: Operate. Native Android, phone-first. Preserve the user-selected dark, layered notification cards and quiet clock/weather header. No redundant heading or empty-state counters.

Semantic palette: PanelColors.java. Dark theme is intentional in both system theme modes; automatic light theme is not implemented. Clock uses responsive sizing; text and actions follow system font scaling. Interactive targets are at least 48 dp. Menu and disclosure use vector drawables.

Group ListView has stable IDs, viewport anchoring and a bounded 24-card cache. App icons and labels have bounded caches. Header remains attached on notification updates. Expanded groups initially show the configured row count and reveal ten more per tap. Accessibility focus is restored by source notification key where the corresponding element remains visible; validate with TalkBack on device before claiming full screen-reader coverage.

Notification actions (0.7.1): Open and Mark read are primary. Read uses source semantic action or exact supported localized label, never substitutes cancellation. Reply remains in overflow. Compact action background is inset 7 dp vertically within a minimum 48 dp touch area.

0.7.2: compact notification text uses 1 dp vertical padding, no font padding; card inset 4 dp vertically. Preview body is capped at two lines, notification title at one. Full text remains available in overflow. Font sizes and 48 dp action targets are unchanged.

0.7.4: six-group compact layout. Collapsed header 28 dp, whole card is the interactive target; title and body each one line. Additional chat-name row removed; count badge retained. Verified six complete grouped cards of 93.33 dp including stack/gap within 620.57 dp list viewport at normal font scale. Expanded action targets unchanged.

0.7.5: collapsed body supports up to two lines. Other sizing unchanged. Six real test packages with long messages measured 109.71 dp per group including stack/gap; five complete groups fit in the 620.57 dp viewport at normal font scale. Short one-line messages retain smaller intrinsic height.

0.7.6: collapsed groups support animated left swipe. Batch cancellation uses captured source objects and excludes non-clearable entries. Summaries are cancelled only when no unselected child remains. Expanded rows keep individual swipe. No layout-density changes.

0.7.7: remove disclosure arrows, count badge fill and overflow-button fill. Keep transient touch ripple and target sizes. Date moves 6 dp toward clock.

0.8.0: collapsed stack swipe defaults to Android system snoozing for 15 minutes. Settings offers 1/15/30/60/120 minutes or permanent dismissal. No stored notification copies or local timer. Existing snoozes retain their original deadline. Expanded individual swipe still dismisses.

0.9.0: shared PanelDialog bottom sheet replaces app AlertDialog and PopupMenu. Opaque slate surface, 24 dp corner radius, capped 82% screen height and 560 dp width, scrolling body and fixed footer. Consistent 52/56 dp rows, native input semantics, styled switches and fields. System permission and installation UI remains OS-owned.

0.10.0: opt-in LEFT edge trigger for left-handed use. A 24 x 88 dp overlay input region contains a 4 dp visible handle; swipe right or tap opens a dedicated panel task. Back returns to prior task. Long press, notification Stop action, settings and separate QS stop tile disable the service. Foreground specialUse service is started only by explicit user action from visible Activity. No boot restart, no lockscreen overlay, no accessibility service, no SystemUI suppression. Protected windows may hide overlays.


## 0.10.4 — stack gestures
Successful stack snooze is silent. Expanded groups support whole-stack left swipe from the app header; individual rows retain their own swipe. Gesture arbitration reserves horizontal intent before ListView can intercept, releases vertical drags, uses screen coordinates for velocity, and accepts either a 24% drag (minimum 56dp) or a left flick above 700dp/s after 32dp. Existing fly-out and height-collapse animation is preserved.


## 0.10.5 — stable reading state
While a group is expanded, keep existing application order; append newly appearing apps. Resume chronological ordering when all groups are collapsed or the panel is reopened. Preserve expansion and viewport anchor across a source cancel/repost gap of up to five seconds without retaining old notification content or PendingIntents. Intentional whole-stack swipe clears expansion immediately. Reading resets the optional auto-collapse timer.


## 0.11.0 candidate — Spleta
Use the existing Spleta mark from public/spleta-login-logo.svg, converted to Android vector paths with original gradients. Native integration reuses the personal portal API and existing compact notification group. Settings contain connection status, sign-in, refresh, explicit background reception and disconnect. Login uses an origin-restricted WebView without a JavaScript bridge. No Google push providers. Server SSE sends private invalidation signals; native client reconciles all unread pages. Not deployed or released yet.

## 0.11.13 — управление музыкой

Над уведомлениями добавлена компактная карточка активного медиасеанса: обложка (если плеер передаёт bitmap), трек, исполнитель, приложение, предыдущий/пауза/продолжить/следующий. Нажатие на обложку открывает плеер. Неподдерживаемые команды отключены. Играющий сеанс имеет приоритет перед приостановленным; при равном приоритете сохраняется выбранный сеанс. На паузе карточка остаётся, остановленные и завершённые сеансы скрываются. Наблюдение работает только при открытой панели, callbacks снимаются при уходе. Новых разрешений нет, используется существующий доступ к уведомлениям. Медиа остаются защищены от переноса/отмены системных уведомлений.

Проверка: сборка, GroupingTest, UpdateCoreTest и подпись APK прошли. Android instrumentation с настоящим MediaSession проверила видимость, передачу pause/play/next, удержание на паузе, отсутствие неподдерживаемой команды, скрытие после stop и очистку. Внешний вид проверен на снимке эмулятора work/music-tests/music.png. Физический телефон недоступен в ADB (offline); установка на телефон и проверка конкретного музыкального приложения не выполнены. Публичного выпуска нет.

OTA 0.11.13 subsequently published with explicit user approval on 2026-11-05. Public latest manifest and APK download verified; SHA-256 matches tested local APK. User will install over the air.

## 0.11.14 — привязка музыкальной карточки к уведомлению

Карточка видна только при активном медиасеансе playing/paused и живом уведомлении
того же пакета/профиля с совпадающим Notification.EXTRA_MEDIA_SESSION. Обычная
пауза с живым уведомлением сохраняет карточку; удаление уведомления скрывает её,
возобновление публикации возвращает. Никаких команд остановки плееру не отправляется.
Исторические карточки SystemUI для возобновления без живого уведомления не
воспроизводятся. Не считать совпадение с Яндекс Музыкой на OnePlus доказанным до
проверки устройства: OEM и плеер могут вести себя иначе. Linux-сборка SDK 36/JDK 17,
GroupingTest, UpdateCoreTest и подпись v3 прошли; Android instrumentation и тест
на телефоне не запускались. OTA опубликовано 17 ноября 2026 по явному запросу
пользователя без проверки на телефоне. Публичные latest.json и APK скачаны без
авторизации: versionCode 40, APK и манифест побайтно совпали с локальными,
сертификат подписи совпадает с опубликованным APK 0.11.13. Факт установки
пользователем и реальное поведение Яндекс Музыки пока неизвестны.

