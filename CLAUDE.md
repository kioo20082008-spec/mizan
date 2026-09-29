# Mizan (ميزان) — Claude working guide

Android personal-finance app (Kotlin, Jetpack Compose, Room). Reads bank SMS → transactions → budget/goals/debts/bills/advisor.
Arabic-first (RTL) with English; Samsung One UI look. Package: `com.mizan.money`. Build = GitHub Actions only (`.github/workflows/build.yml`).

**Rule: read only the files listed for the task below. Don't scan the whole repo.**
Paths are under `app/src/main/java/com/mizan/money/` unless stated.

## Task → files
| If the task is about… | Open |
|---|---|
| Advisor tips / analysis logic (trends, debt load, salary detection, budget plan math, recurring-bill suggestions) | `advisor/FinancialAdvisor.kt` (+ `ui/AdvisorScreen.kt` for display, strings `adv_*` in `res/values*/strings.xml`) |
| Advisor screen look | `ui/AdvisorScreen.kt` |
| Home/dashboard | `ui/DashboardScreen.kt` |
| Transactions list, search, filters, multi-select | `ui/TransactionsScreen.kt`; add/edit/detail sheets + filter sheet: `ui/TransactionDialogs.kt` |
| Budget tab (limits, categories, rollover) | `ui/BudgetScreen.kt` |
| Goals & debts | `ui/GoalsAndDebtsScreen.kt` |
| Bill reminders + recurring-bill suggestion banner | `ui/RemindersScreen.kt`; notifications: `notify/BillReminderWorker.kt`, `notify/NotificationHelper.kt` |
| Planning tab shared widgets (chips, section headers, sheet buttons) | `ui/PlanningComponents.kt` |
| Reports/charts/month comparison | `ui/ReportsScreen.kt`; export CSV/PDF: `ui/ExportHelper.kt`, `ui/PdfBuilder.kt` |
| Settings screen (language, theme, salary, categories, rules, backup, rates) | `ui/SettingsDialog.kt` |
| First-run / SMS permission screen | `ui/PermissionScreen.kt` |
| Navigation, tabs, bottom bar, app shell | `ui/AppRoot.kt` (+ `MainActivity.kt`) |
| Colors, fonts, radii, shared components (TransactionCard, FormSheet, IconBadge, fmt(), Arabic digits) | `ui/DesignSystem.kt`; theme tokens `ui/theme/MizanColors.kt`, `MizanTheme.kt` |
| Category icons/colors | `ui/CategoryIcons.kt` (+ `catColor/catIcon` in DesignSystem.kt) |
| Dark mode / language switching | `ui/theme/ThemePreference.kt`, `ui/theme/LanguagePreference.kt` |
| App state, all user actions (add/update/delete, prefs, bulk actions, suggestions) | `ui/MainViewModel.kt` (single ViewModel; also holds `Dates` month-range helper) |
| SMS parsing (amount, merchant, bank, type) | `sms/SmsParser.kt` (test: `src/test/.../sms/SmsParserTest.kt`) |
| Auto-categorisation | `sms/CategoryClassifier.kt`, user keyword rules `sms/CustomCategoryRules.kt` |
| Reading old SMS on first launch / live SMS | `sms/InboxScanner.kt`, `sms/SmsReceiver.kt` |
| DB schema, tables | `data/Entities.kt`, `data/AppDatabase.kt` (schemas in `app/schemas/`; bump version + migration when changing), queries `data/Daos.kt` |
| DB access layer used by ViewModel | `data/TransactionRepository.kt` |
| Currency conversion (SAR rates) | `data/ExchangeRates.kt` |
| Local backup/restore (JSON) | `data/BackupManager.kt`, `data/BackupDao.kt` |
| Cloud backup (Firebase/Firestore, sign-in) | `cloud/CloudBackup.kt`, `cloud/CloudBackupMapper.kt`, `/firestore.rules` |
| Home-screen widget | `widget/MizanWidget.kt` (UI), `widget/WidgetTheme.kt` (color choice), `widget/WidgetUpdater.kt` (refresh), `widget/MizanWidgetReceiver.kt`, `res/xml/mizan_widget_info.xml` |
| App start / global init | `MoneyApp.kt`, `AndroidManifest.xml` |
| Build/deps/CI/signing | `app/build.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.github/workflows/build.yml`, `app/proguard-rules.pro` |
| Launcher/notification icons | `res/drawable/`, `res/mipmap-anydpi-v26/` |
| Tests | `app/src/test/java/com/mizan/money/**` (advisor, cloud mapper, db, backup, repository, classifier, parser, dates) |

## Strings (always edit BOTH languages)
- `res/values/*` = Arabic (default), `res/values-en/*` = English. Same file name and same key in both.
- `strings.xml` general/advisor/transactions/reminders · `strings_home.xml` dashboard · `strings_planning.xml` budget/goals/debts/bills · `strings_insights.xml` reports/advisor header.
- Use `%1$s` style args; English percent literal is `٪` by existing convention.

## Conventions
- Data flow: Room DAO → `TransactionRepository` → `MainViewModel` StateFlows → Compose screens `collectAsState()`.
- Screens never touch DAOs. New setting = pref key in `MainViewModel` (`mizan_prefs`) following the existing `_x/x/setX` pattern.
- Amounts on screen: use composable `fmt()` (Arabic-Indic digits when UI is Arabic). `FinancialAdvisor.fmt` (Latin digits) is for exports/notifications/advice args.
- Category keys stored in DB are Arabic strings; show with `categoryDisplay()`.
- `FinancialAdvisor` is pure Kotlin (no Android/Compose) — keep it that way so it stays unit-testable; it returns string-resource ids + args.
- Bottom sheets use `FormSheet` (has a keyboard-dismiss fix — don't replace with a raw ModalBottomSheet).

## Working rules
- No local Gradle build is possible in the cloud sandbox; verify syntax by careful review / kotlinc syntax pass, real check = GitHub Actions after `git push`. If Actions fails, paste the log to Claude.
- Repo files use LF but Windows checkouts may show CRLF noise: never `git add -A`; stage exact files (`git add -- path1 path2`) after checking `git diff --stat`.
- Claude in the cloud cannot push; commit locally, then the user runs `git push` in Git Bash from `C:\Users\kioo2\mizan`.
