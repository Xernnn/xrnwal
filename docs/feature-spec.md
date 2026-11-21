# xrnwal feature spec

Behavioural spec for xrnwal (xrnwallet until 0.6.0), written from a screen-by-screen walkthrough of
the app it replaces. It records what the app must do. Code, art, icons, mascot and branding are all
our own: features and workflows are matched, assets are not.

## Navigation

Bottom bar: Home, Reports, centre + button (Add bill), Accounts, Mine. Line icons; the chosen one is
filled with the accent under dark lines, with a bold label, and no pill behind it. The + sits in a
notch cut into the bar's top edge.

Touches are ignored while two pages are on screen, so a second tap can't save twice or open a page
twice.

## Welcome

- Shown once, on a phone with no bills. Someone updating the app with records in it never sees it.
  Mine, Getting started opens it again.
- The app's tile, then "Welcome". The tile is drawn in the colour chosen on App Icons, cut to the
  xrn family's rounded square.
- Start here: add a bank or card, add a first bill, set a budget. Each step opens its page and comes
  back, and is ticked once the data shows it's done.
- Already have records?: Import from another app (Import Data) and Restore a backup (Data
  synchronization).
- Get started closes it.

## Home

- Top bar: ledger chip, period picker, search, calendar.
- Ledger switcher: every ledger plus **All ledgers**; + creates a ledger.
- Period picker (shared): drops from just under the title. Month shows month names with the current
  month outlined and empty months faint, and This month jumps back. Week lists week numbers with their
  dates beside them ("Week 45  Nov 2 - 8"), oldest first, opening on the chosen week. Year lists years
  with bills, and Reports adds Custom. One tap chooses and closes.
- Arrows sit either side of the period name, and tapping the name opens the picker.
- Summary card: Expenses, Income, Balance in one of four styles (Preferences). Style 3/4 lead with
  one big figure and tapping cycles Expense/Income/Balance.
- Budget card: ring with % remaining, Budget, Remaining (%), Daily avg. Tapping opens Budget.
- Incomplete checklists, when enabled.
- Bills grouped by day. Day header: date + weekday, day totals with arrows (expense and/or income).
- Today's section with "No record for today" when empty.
- Bill row: category icon, category name, account + note, amount, time, tag chips.

## Add / edit bill

- Close, ledger picker. Editing a bill, also Add again today and Delete (each with Undo on the
  page underneath). Tabs: Expenses, Income, Transfer.
- Category grid of in-use categories; choosing a parent with subcategories offers them. Income has
  an Edit tile that opens the category editor.
- Account chip. Its picker is a page of cards grouped Savings / Credit, each with "Balance" over the
  amount and the current one outlined; + to create, "Do not bind personal accounts" at the foot.
  A new bill starts on the account of the expense or income recorded last (kept across restarts,
  Add again today included), not the newest-dated bill's, which can be next month's rent or a
  recurring bill. Edits, transfers and bills on no account leave that choice alone. Before anything
  has been recorded: the account of the bill entered most recently, then the first open account. An
  archived account is passed over.
- Tag button (multi-select, + to create).
- Budget icon at the right of the row, like the tag button: lit and struck through when a bill is
  left out of the budget, with a short message saying which. Our addition: the reference has this
  switch only on its bill detail page, which a tap no longer opens.
- Keypad and note box at the reference's size, measured on the phone: keys 117 px tall and 15 px
  apart, the note box 159 px, 43 px margins (46.8 dp, 6 dp, 63 dp and 17 dp at the phone's 2.5 px
  per dp). The categories get the rest.
- Photo attachment (tap the thumbnail to see it large, or remove it), note, amount with currency
  switch (billing currencies only). The amount changes in place as digits are typed, with no motion,
  and is grouped in threes as it is typed ("1.234,5+6.789"), each number of a sum on its own, as the
  reference's is. Full size is 24 sp, the reference's digit size ("12.345" is 180 x 47 px there). It
  stays at full size until it is 111 dp wide (the reference's 277 px), then gets smaller to keep that
  width, down to 12 sp.
- Keypad: digits, decimal, backspace, + - x /, date button ("Today"), save. While a sum is pending
  the save key shows =, which evaluates.
- Date and time picker: the month, with the time on hour and minute wheels below it in the same
  card (as the reference), 24-hour or with AM/PM as the phone is set. Each step ticks. Future dates
  are selectable only through recurring items.
- Transfer: one card with Payment account and Receive account side by side, each a 54 dp square
  button (+ until an account is chosen, then its logo with its name below), a swap between them.
  The tag button and a Fee chip (requires an account first, tip: "Please select an account") sit
  above the keypad, the chip reading "Fee $3" once a fee is set.

## Bill detail

The reference opens this on a tap: Category, amount, Notes, Ledger (change), Accounts (change),
Included in budget switch, Date; duplicate, delete, edit. Here a tap opens the edit page instead,
which holds all of it, so changing a bill takes one tap rather than two.

Transfers appear in account history as "From >> To" and count in neither expenses nor income.

## Reports

- Period picker with Custom. Filter sheet: tags, ledgers, accounts; reset and confirm.
- Trend line chart with average line. Switch Expenses/Income.
  - Two looks, switched by the >< button beside Expenses/Income (lit when fitted) and kept across
    restarts. It opens fitted to the card, every point at once, labelled on a pattern of dates that
    fits (1, 4, 7...; 1, 8, 15, 22; 1, 11, 21; month starts; a month's 1st reads as its name). The
    other look scrolls sideways at 38 dp a point, as the reference's does. Tap a point, or hold and
    drag along the line, for its date and figure.
  - Month view: daily points. Year view: monthly points.
  - Average = total / elapsed units (days in month, months so far this year).
  - Use calendar days. The original computes days from hours, so a month containing the DST change
    comes out one day short (October 2026 divides by 30). Do not reproduce that.
- Category ranking: pie, then ranked rows (name, %, amount, bill count, bar), top 5 with expand.
  Categories are coloured by rank in eight hues, stepped for each theme and ordered so neighbours
  stay apart for colour-blind eyes too; from the ninth on they're the grey of Other. A thin line of
  the card's colour separates the slices.
  Switches: Expenses/Income, Top level category/All categories. A row opens that category's bills.
- Bill Amount Ranking TOP 10 with Expenses/Income switch.

Our additions, between them (none is in the reference):

- Spending pace (Week, Month, Year): expenses added up through the period, against the period before
  and, on a month with an overall budget and no filter, an even spread of the budget. Tap a day for
  both totals at that day.
- Daily spending: each day shaded in four steps, set at 40, 70 and 90 in a hundred of the days with
  any spending, so one rent day doesn't pale the rest. Up to 42 days it's a calendar, longer it's
  columns of weeks. Under it, the average per weekday, leaving out bills recorded by recurring rules.
  Tap a day for its date and figure; it opens on the dearest day.
- Income and expenses: twelve accounting months (a year's own, else the twelve up to the period),
  income above the line and expenses below, the net as a line. Tap a month for its three figures.
- Categories by month: the same twelve months stacked by the five largest top-level categories and
  the rest (uncategorised included), the chosen month naming each share.

## Accounts

- Net Assets card: Net Assets, Assets, Debts. Eye hides amounts (shows ****, Net Assets word
  "Prosperous").
- An account named after a known bank or wallet shows its logo: CommBank, Westpac, Revolut, Vietcombank,
  PayPal, Google Wallet, Amazon Pay, Apple. Investment and Cash get their own marks. The name is the only
  setup, so imported and restored accounts show theirs too.
- Accounts grouped by type. Foreign accounts show native balance plus base-currency value.
- Create account: type picker, Current balance, Currency (billing currencies only), Notes, Included in
  total assets.
- Types. Savings: PayPal, Google Wallet, Amazon Pay, Cash, Apple Card, Bank Card (custom name and
  icon), Other (custom). Credit: Apple Card, Debt, Credit Card (custom), Other (custom).
- Detail: icon, name, Included in total assets chip, Current balance, history including transfers
  and balance corrections. Menu: Edit, Archive, Balance Correction, Delete.
- Correcting a balance, by changing Current balance under Edit (as in the reference) or through
  Balance Correction, which asks for the real balance, keeps a correction in the account's history:
  the account's icon with a small up-and-down mark, "After adjust: 250", and the difference (+$200)
  in the income or expense colour. Its Details page shows Before adjust, After adjust and Date, with
  a delete button. The balance is then the latest correction plus what came after it, so bills dated
  before it no longer change the balance, as the reference warns; deleting it undoes it. A correction
  is neither spending nor income: Home, Reports, budgets and the day's totals leave it out. Seen on
  the reference 2026-11-17 on a test account, since deleted.

## Mine

Profile (avatar picker, nickname), all-time Expenses, Income, Records, then:

**Commonly used**: Budget, Savings Plan, Recurring Transactions, Calendar, My Ledgers, Category,
Tags, Checklist.

**Assistive**: Appearance, Reminders, Password Protection, Exchange Rate, Filter, Data
synchronization, Export Data, Import Data, Start of Month, Balance Carryover, Preference Settings.

**Other**: Currencies and exchange rates, Languages, Haptic feedback, App Icons, Getting started, Delete
all data.

### Budget

- Master switch. Off shows a note and hides the Home card.
- Monthly budget amount; Remaining; Daily avg = remaining / days left in the period, today included.
- Category budgets: add by category, edit, delete; each shows Budget, Expenses, Remaining, %.
- Bills with Included in budget off do not count. Budgets are global, not per ledger.

### Savings Plan

Types: Fixed, Increment, Flexible. Fields: icon, name, target, currency, receive account, notes.
- Fixed: start, end (derived), period, repetitions, amount per repetition = target / repetitions.
- Increment: starting amount, each deposit adds the starting amount; target = start x n(n+1)/2.
  Weekly 52 from 10 gives 13780. Schedule screen lists each date and deposit.
- Flexible: target only.
- My Plans list.

### Recurring Transactions

Tabs Income/Expenses and Transfer. Category, note, amount per repetition, currency, start date,
Repeat (a wheel of Every N Days, Weeks, Months or Years, then a weekday or day-of-month grid), time,
Repeat times (a number, or Repeat forever), account, ledger, Included in budget, tags. Transfer has payment and receive accounts,
a swap button and notes. Card: amount/period, Start time, Repeat, Recorded (n bills, total), Remaining
(n bills, total), Account. Detail lists generated bills; delete and edit.
Saving a rule that has passed dates without bills, new or edited, asks what to do with them. Bills
already in the records for those dates are linked to the rule and never recorded again. If some dates
have no bill it offers to add just those, and if none do it offers to record them or skip.

### My Ledgers

Cover, name, currency. Detail: Expenses, Income, Balance, Bills, history; delete, edit. Long press
reorders.

### Category

Expenses/Income tabs, In use and Not used lists, minus badge moves to Not used, long press
reorders. Tapping opens a panel under the row with the category, its subcategories and +
Subcategory. Custom: Expenses/Income, icon, name, then tabs Style1 to Style4 and emoji.

### Tags

Show on bill card switch. Name plus a choice of 12 colours.

### Checklist

Show incomplete lists on homepage switch. Name and items.

### Appearance

Light, Dark, Automatically follow system; colour themes. The font is always MiSans, whose credit
is shown at the bottom of Mine and must stay there.

### Reminders

Name, time, repeat (Daily, Weekly, Monthly), notification content presets or custom title/body.

### Password Protection

Digital (PIN of 4 to 8 digits), Gesture (pattern), Biometric unlock. Hint to set two methods. A PIN
or pattern is kept as PBKDF2-HMAC-SHA256; one kept by an earlier build as a single SHA-256 is kept the
new way the first time it opens the app. After five wrong PINs or patterns in a row the lock screen
waits 30 seconds, doubling with each further miss; closing the app doesn't end the wait, and
biometrics still open it. While any lock is set, widgets print dots instead of amounts and Recents
keeps no picture of the app. Both reach past the lock screen, so the session being unlocked changes
neither. Screenshots stay allowed on Android 13
and later. Older versions have no way to leave out only the Recents picture, so screenshots are
blocked there.

### Exchange Rate

Converter: editing one currency's amount updates all others. Refresh button.

### Filter (also Home search)

Search box: comma-separated terms over categories, amounts, tags, notes. Chips: Bill type, Category,
Ledger, Account, Tags, Amount (range plus presets 0-199, 200-499, 500-999, 1000-1499, 1500-2999,
3000-9999), Time. Summary: Records, Expenses, Income. Results list.

### Export Data

Start time, End time, Ledgers, Accounts, Bill type, Category, Tags, bill count, Export to CSV.

### Import Data

Choose file, Data source (this app's CSV or the general template), Download general template. A file
shared to the app waits on this page until it is imported, even if the app closes
first. A foreign currency with no exchange rate blocks the import until a rate is added.

### Start of Month

Wheels for the start day; On weekends (No change, Forward, Backward); Belonging month (Start date
this month, Start date next month), with a live preview.

### Balance Carryover

When on, Home's Balance is the running balance carried from earlier periods. Calendar's Balance
stays period-only.

### Preference Settings

Show base currency symbol; Thousands Separator ($1,000.01, $1.000,01, Adaptive); Decimal places
(Adaptive, 1, 2); First Day of Week; Income-Expense Card Type (Style 1-4); Enable note
recommendations; Show account on bill card; Income-Expense Colors (arrows, amounts, reset, preview);
Arrow Direction (Default, Reverse); Include future bills. Colors has separate rows for each arrow and
amount, a 12-colour dialog, reset and a preview.

Amounts typed in a field follow the Thousands Separator: with $1.000,01, "500.000" is five hundred
thousand and "500,5" five hundred and a half; with $1,000.01 the other way round. In a currency without
cents, such as the dong, one mark before three digits is always thousands. A CSV file names no
separator, so there one mark before three digits is still a decimal.

### App Icons

Eight colours of the logo in a grid, the current one outlined. Choosing one closes the app and
reopens it under the new icon, because Android closes an activity whose launcher entry is turned off.

### Gestures

Every gesture also has a visible way to do the same thing.

- Pages: Home, Reports, Calendar, date pickers and the picker's months sit on endless sideways pages.
  A page follows the finger with the next one beside it, in both directions, and arrows turn it too.
- The add page's Expenses, Income and Transfer tabs are pages in the same way.
- Other pages cross-fade in about 130 ms, the new one growing the last 2% into place, and go in
  about 90 ms: the reference cross-fades in about 70 ms, and a quarter-second slide read as waiting.
- Hold a bill: Edit, Add again today, Delete. A swipe is never a delete, because it would catch people
  turning pages. Deleting a bill offers Undo for a few seconds, not a confirmation.
- Hold backspace on the keypad: clear the amount.

### Widgets

Six, in Light, Dark and Lime. Today shows the day's expenses and income, and its + opens a new bill.
Month shows the period's expenses, income and balance. Both use the Home header's rules for the
active ledger, future bills, carryover and start of month, and redraw after any change, including
any setting, and hourly. Amounts are hidden while an app lock is set, and the + still works because
the new bill page opens behind the lock screen.

### Currencies

Base currency, billing currencies with rates, full ISO list, long press sorts, swipe deletes.

### Haptic feedback

On/off plus three strengths.

## Calendar

Month grid with arrows; each day shows expense and income totals; month totals; selected day's
bills; + adds a bill on that date.

## Multi-currency rules

- Each bill keeps the rate it was recorded with, so history does not move when rates change.
- Account balances are held natively and converted at the current rate for display and net assets.
- A bill or transfer into an account in another currency keeps the amount it put there, worked out
  at the rates of the moment it was saved or recorded. Bills saved before that are still worked out at
  today's rate.

## Out of scope for the first build

Ads, purchases, restore purchase, rate, share, feedback, more apps, privacy policy.

## Backups

A backup is one JSON file holding every table, the settings and the attached photos. Photos are
stored at most 1600 pixels on the long edge, turned upright, so a backup stays a sensible size.
Restoring replaces everything, and the file is fully read before anything is removed.

## Sync

Replaces the reference's Google Drive sync with a server of your own, keeping the same controls:
automatic syncing, last sync time, sync photo, and sync now. Earlier copies and Disconnect are added.

The phone sends its whole wallet as one document, a few seconds after any change and when the app
opens. If it can't reach the server it retries by itself, and it checks every 6 hours as well. Every
copy the server replaces is kept: the last 20, and the first of each day for 60 days.

- The app lock never leaves the phone. Taking a copy from the server keeps this phone's own lock, so
  a new phone starts with no lock until one is set.
- The login is kept encrypted with a key held in the phone's keystore. Addresses must be `https://`.
- Backup files are separate from sync. They hold everything but the app lock, so restoring one keeps
  this phone's own lock too.

When nothing changed on the phone, a newer server copy simply comes down. Nothing is overwritten
otherwise:

| Situation | What happens |
| --- | --- |
| A new phone connects to a server that has a copy | It asks which to keep |
| This phone was restored from a file, or had all its data deleted | It asks which to keep, as a new phone does |
| Both sides changed | It asks which to keep |
| The server went backwards, restored from a backup say | It asks which to keep |
| The server went backwards and was written to again, to this phone's version or past it | It asks which to keep, as each copy has a tag of its own |
| An upload arrived but its answer was lost | It recognises its own upload by hash and carries on |
| The server lost everything | It sends this phone's copy again |
| A bill is added while a copy is downloading | It asks which to keep, and the bill stays |

Whichever side is chosen, the other is filed on the server first, so the choice can be undone from
Earlier copies. Going back to an earlier copy sends the phone's current data up first for the same
reason.
