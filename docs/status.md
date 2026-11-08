# Status

Tested on an Android 15 emulator with invented data, then on a real phone with a real export:
every bill imported, and all-time totals, every account balance, net assets and sampled month headers
match the reference app. The reference app is still installed, so nothing has been lost if this
turns out not to be a replacement.

## Tested and working

- First run: a fresh install opens on the welcome page with only the Cash account and no records, tags
  or rules. Its five rows open their pages and return, and the ticks follow the data. Updating over an
  older build with records in it doesn't show the page. Checked in dark, light and 1.3 times text, on
  an emulator only. The app is portrait only.
- Double taps: on older builds, two taps under about 75 ms apart recorded a bill twice, and closed Home
  too, leaving a blank app. Slower than that, the page had already slid out from under the finger.
  From 19 to 136 ms apart, one bill is now recorded and the page stays. Emulator timings.
- Bills: add, edit, delete, duplicate, keypad sums, tags, photos, future dates, transfers with fees.
- Home: ledgers, All ledgers, period picker, card styles, budget card, checklists, Calendar, search.
- Reports: Week, Month, Year, Custom, filter, category ranking, ranking row into bill list, top bills.
- Accounts: balances, foreign currency accounts, net assets, hide amounts, reorder.
- Planning: budgets, recurring (skip or record past dates), all three savings plan types, deposits.
- Management: ledgers, categories, subcategories, Not used, reorder, tags, checklists.
- Security: PIN, pattern, wrong attempts, relock, nothing reachable or readable behind the lock.
- Files: CSV export by share and to a file, CSV import by share and by picker, repeat import adds
  nothing, template, backup and restore including photos.
- Settings: Light and Dark, accents, start of month, number formats, base currency change, reminders.
- Sync: first connection, wrong password and bad address refused, automatic upload about 6 seconds
  after a change, a change from another device with its photo, photo upload, real conflicts with both
  choices, an upload whose answer was dropped, a server outage recovered by the background retry,
  earlier copies, disconnect and reconnect, and a wiped phone restored completely from the server.
  The server copy was checked to hold no PIN hash, and a restore keeps this phone's lock.

- App lock on an Android 15 emulator: with a PIN set, widgets turned to dots on their own and the
  Recents card went blank, while screenshots still worked. The widget's + opened the lock screen
  first from a cold start, then a new bill. Removing the PIN brought both back. Recents on HyperOS
  is not confirmed.
- Widgets: all six placed on a launcher, live figures matching the Home header, an update on the
  home screen after adding a bill, and the Today add button opening a new bill.
- Database upgrade: a phone on the earlier database version keeps every bill and rule, and rules
  carry on recording afterwards.
- App icons: all eight, the restart after choosing one, and the choice surviving an app update.

Biometric unlock has not been tested, because the emulator has no enrolled fingerprint.

Sync was tested from the emulator to the real server program running on the computer, over plain
HTTP. The phone has also synced to the home server over HTTPS since 2026-11-06, and the server's log
shows every upload accepted. Restoring a phone from that server copy hasn't been tried.

- Paging, on the release build on an emulator with animations on: a page following a slow drag, flicks
  over bills both ways deleting nothing, arrows adding up on quick presses, picker jumps, weeks, Reports,
  Calendar, date pickers, the picker's years, and income saved after swiping the add page. Also the hold
  menu, Undo and logos in both themes. All 23 Mine pages were looked at, and the main ones at 1.3 times
  system text size.

## Builds

Everyday use needs the release build. A debug build is about three times slower to respond, because
Android can't precompile it, and Compose relies heavily on that. On an emulator with several hundred bills, the
app's own work per press fell from 223 ms to 76 ms on a first open and from 123 ms to 46 ms after
that, and a cold start from 3.3 s to 1.6 s. Those are emulator figures and only the comparison
between them carries over to a phone.

The release build uses the same signing key as the debug build, so either one updates over the
other and keeps the app's data. Using a separate key later would mean a reinstall, so the data has
to be on the server first. A release build can't be read over adb, and it only accepts https://
server addresses.

Code shrinking can break anything that saves or loads data, so before release on an emulator: a
release build with only the address check relaxed uploaded several hundred bills to a local copy of the
server, was wiped, and restored them all with the same totals.

## Missing from the reference app

- More than one language. The reference has 13.
- The camera on bills. The button opens the photo picker.
- The mascot, and everything drawn around it.
- Voice entry, if there is any. The reference asks for the microphone, but no screen that uses it
  was found, including a long press on the add button, and the phone studied had never granted it.
- Google Drive sync, replaced by your own server. Backup and restore to a file remain.
- Rate, Share, Feedback, More Apps and Restore Purchase, which a personal app has no use for.

- Whole-app themes. The reference's Appearance page offers Theme1, Theme2 and more, each a set of
  icons with light and dark looks. This app has five accent colours. How many themes exist was not
  recorded.
- The Import Data page's Data source choice and help page. This app detects the columns by name.

## Different from the reference app

- Sync adds Earlier copies and Disconnect, and asks before overwriting anything. The app lock is not
  synced, so a new phone starts unlocked until one is set.
- With an app lock set, widgets hide amounts and Recents shows no preview. Whether the reference
  does either was not tested.
- One font, MiSans, with no font picker.
- Which gestures the reference has on its bill rows was not tested, because a swipe there could delete
  a real bill. Here swipes always turn the page, and a bill is deleted by holding it.
- Own artwork. Category and account icons are the app's own drawings in four styles, and an emoji
  tab keeps emoji as a choice. App icons are the wallet logo in eight colours.
- Widgets are six original designs. Only one of the reference's six was seen, so their contents
  follow its sizes and that one layout: today's figures, or the month's with balance.
- Reports averages use calendar days. The reference counts hours, so a month containing the
  daylight saving change comes out a day short. This is kept on purpose, because it is the correct
  figure.
- A zero amount shows as `$0`, where the reference shows `-$0`. Also kept on purpose.
- Savings deposits raise the plan's receive account. How the reference treats them was not tested,
  because that would have meant saving into a real plan. They show as their own line on the account
  page and do not count as income.
- A currency cannot be removed while an account, savings plan or recurring rule uses it, because
  each unit would then count as one of the base currency. The reference's behaviour here is unknown.
- Filter lists open under the row of chips and push the list down, rather than covering it.
- Profile photos are 16 emoji in a dialog, where the reference has a page of 20 illustrated ones.
- Reminders edit the title and body directly, where the reference ticks either the preset text or
  custom content.

## How far the comparison goes

Screens were compared by looking at them and by matching their recorded labels against this code. A
matching label shows the word exists here, not that the control behaves the same. Most controls in
the reference were read, not operated, because operating them means saving into real data.

## Reference screens not opened

These could not be compared directly, so the rebuild follows the closest screen that was seen: PIN
and gesture setup, because trying them means setting a lock on a real phone; the bill and account
edit pages; the Tags filter chip, whose page looks like the other chips; and the Rate, Share,
Restore Purchase and Feedback pages. Forward and Backward under On weekends are read as moving to
the Friday before and the Monday after, without changing the setting to confirm it.

## Still open

- Paging against the build before it, on an emulator with both fully compiled: opening a bill and going
  back cost the same. Turning a page takes about 1 ms of app work per frame, except one frame of about
  42 ms when the next page is first built. Not yet measured on a phone.
- The logos in `res/drawable-nodpi/logo_*` belong to their owners, apart from Investment. They are here
  to identify the owner's own accounts, so check each brand's terms before publishing a build.
- On gesture navigation a right swipe from the very edge of the screen is the system's Back, which
  leaves the app from Home.
- Restoring a real phone from the home server.

## Importing from the reference app

Its CSV export has one column set: Date, Category, Subcategory, Bill type, Amount, Currency, Notes,
Account, Ledger, Tags, Included in budget, Id, Image. What that means when moving across:

- A transfer has no receiving-account column. Its Category holds both accounts, one per line, and
  its Ledger is blank. The importer handles both.
- Exchange rates are not exported, though the reference keeps one per bill. A foreign currency with
  no rate on the phone and none in the file blocks the import, because otherwise each unit would
  count as one of the base currency. Current rates give totals that are close but not equal. To
  match exactly, add a Rate column worked out from the reference's own month and day totals.
- An account's currency is not exported either, and a bill's currency doesn't settle it. An account
  takes a foreign currency only if all its bills use it, so any other foreign account has to be
  changed by hand afterwards.
- Opening balances are not exported. After importing, enter each account's real balance in its
  editor, which works out the opening balance. Accounts that only received transfers come out
  right on their own.
- Budgets, recurring rules and unused tags are not exported, so they have to be entered by hand.
- Bills recorded by the reference's recurring rules arrive as ordinary bills. Enter each rule with its
  real start date and repeat times, finished ones included, and saving finds those bills and links them
  to it. It must match on category, amount, currency, ledger and note, within a day of the rule's date,
  because the reference counts 24-hour days and drifts a day across daylight saving. The account may
  differ, except on transfers. An ongoing rule that stopped being recorded offers to add only the dates
  with no bill.
- The Image column was empty for every bill in the real export, so bill photos were not carried
  over, if there were any.
