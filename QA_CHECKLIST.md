# 🧪 PSWD MNGR — QA Checklist

> Run through each test on a **physical device** (or emulator for items that work there).
> Mark each step ✅ / ❌ / ➖ (N/A) as you go.

---

## 1. Argon2id Timing

**Where:** More tab → "Argon2id benchmark"

| Step | Action | Expected Result | Result |
|------|--------|----------------|--------|
| 1.1 | Open the benchmark screen | Parameters display: `ARGON_M_KIB=49152`, `ARGON_T=3`, `ARGON_P=2` | ☐ |
| 1.2 | Tap "Run benchmark" | Wait ~0.5-1s, result appears in ms | ☐ |
| 1.3 | Record the result | `_____ ms` | ☐ |
| 1.4 | Repeat 3 times, record each | Run 1: `_____` Run 2: `_____` Run 3: `_____` Average: `_____` | ☐ |
| 1.5 | Check color indicator | If **Mint**: 400-600ms (target hit, no action needed) | ☐ |
| | | If **Amber** (250-400ms or 600-900ms): borderline — consider tuning | ☐ |
| | | If **Coral** (<250ms or >900ms): must tune before shipping | ☐ |
| 1.6 | If out of range, adjust `ARGON_M_KIB` in `VaultCrypto.kt` | **Too slow (>600ms → decrease)** — try 32768 (32 MiB) | ☐ |
| | | **Too fast (<400ms → increase)** — try 65536 (64 MiB) | ☐ |
| | | Rebuild, re-run benchmark, repeat until target is green | ☐ |

**Pass criteria:** Average of 3 runs is in 400-600ms range.

---

## 2. Migration v1→v2

**Prerequisite:** A v1-format vault. To simulate one:
  - Install an older build (pre-recovery-key) OR
  - Clear app data, install current build, create a vault, then edit `vault_meta` SharedPreferences to remove v2 keys OR
  - Create a fresh vault and manually delete `pw_wrapped_mk`, `recovery_wrapped_mk`, etc. from `vault_meta` and set `vault_data_version=1`

| Step | Action | Expected Result | Result |
|------|--------|----------------|--------|
| 2.1 | Set up a v1 vault (or simulate) | Vault unlocks with master password only | ☐ |
| 2.2 | Force close and reopen the app | Goes to unlock screen | ☐ |
| 2.3 | Enter the master password and unlock | Vault opens | ☐ |
| 2.4 | **Migration dialog appears** | A non-dismissible dialog titled "Your Recovery Key" with a recovery key displayed | ☐ |
| 2.5 | **Test non-dismissible** | Tap back button / tap outside the dialog — nothing happens | ☐ |
| 2.6 | **Retype challenge appears** | Dialog shows "Type group N:" with a text field (where N is a random group number) | ☐ |
| 2.7 | **Confirm button is disabled** | Button "I've saved it" is grayed out / unclickable | ☐ |
| 2.8 | Type the wrong group value | Error message "That doesn't match group N" appears | ☐ |
| 2.9 | Type the correct group value from the displayed key | Confirm button becomes enabled | ☐ |
| 2.10 | Tap "I've saved it" | Dialog closes, vault screen is visible | ☐ |
| 2.11 | Go to More → Settings → View Recovery Key | Requires biometric, then shows the **same** recovery key that was in the dialog | ☐ |
| 2.12 | Lock the vault, then tap "Forgot master password?" | Recovery key entry screen opens | ☐ |
| 2.13 | Enter the recovery key → verify → set a new password | Unlocks successfully with new password | ☐ |

**Pass criteria:** All steps 2.4-2.13 pass without errors.

---

## 3. Process Kill During Migration

| Step | Action | Expected Result | Result |
|------|--------|----------------|--------|
| 3.1 | Set up v1 vault again (or re-simulate) | v1 state | ☐ |
| 3.2 | Unlock with master password | Migration fires (recovery key generated internally) | ☐ |
| 3.3 | **Before dismissing the dialog**, force-kill the app | Swipe app away from recents or Settings → Apps → Force Stop | ☐ |
| 3.4 | Reopen the app | Goes to unlock screen | ☐ |
| 3.5 | Enter the master password and unlock | **Migration dialog reappears** with the same recovery key (not a newly generated one) | ☐ |
| 3.6 | Confirm the challenge is the same style (random group, typed confirmation) | Works as in 2.6-2.10 | ☐ |
| 3.7 | Dismiss the dialog, verify recovery key in Settings | Same key as before | ☐ |

**Pass criteria:** Recovery key dialog survives process kill with the **same** key. The key is the one generated during migration, not a freshly generated one.

---

## 4. Backup / Restore

### 4a. Full Export

| Step | Action | Expected Result | Result |
|------|--------|----------------|--------|
| 4a.1 | Create a handful of test entries across categories (a login, a card, a bank, a doc with an attachment, a note, a task) | At least one entry in every category | ☐ |
| 4a.2 | Go to More → Settings → "Export full backup" | File picker opens | ☐ |
| 4a.3 | Choose a location and set backup password (e.g. `test-backup-pw-2026`) | "Encrypted backup exported" snackbar | ☐ |
| 4a.4 | Verify the file was created at the chosen location | File exists, size > 0 bytes | ☐ |

### 4b. Selective Export

| Step | Action | Expected Result | Result |
|------|--------|----------------|--------|
| 4b.1 | Go to More → Settings → "Selective export" | File picker opens, then dialog shows category checkboxes | ☐ |
| 4b.2 | Uncheck all categories except "Cards" | OK button enabled if at least one checked | ☐ |
| 4b.3 | Tap OK, enter password, confirm | "Encrypted backup exported" | ☐ |
| 4b.4 | Examine the file (optional: hex dump header) | Starts with `PSWDMGR1` magic bytes | ☐ |

### 4c. Full Restore

| Step | Action | Expected Result | Result |
|------|--------|----------------|--------|
| 4c.1 | Clear app data or reinstall | Fresh app state | ☐ |
| 4c.2 | Launch app, create a new vault with a master password (e.g. `new-vault-pw`) | Vault created | ☐ |
| 4c.3 | Go to More → Settings → "Import backup" | File picker opens | ☐ |
| 4c.4 | Select the full backup file from 4a, enter backup password `test-backup-pw-2026` | "Imported N records" snackbar (N includes logins + cards + banks + docs + notes + tasks) | ☐ |
| 4c.5 | Navigate through each category screen | All imported entries visible with correct data | ☐ |
| 4c.6 | Verify document attachments open correctly | Photos/PDFs decrypt and display in the attachment viewer | ☐ |

### 4d. Selective Restore

| Step | Action | Expected Result | Result |
|------|--------|----------------|--------|
| 4d.1 | Reinstall / clear app data again | Fresh state | ☐ |
| 4d.2 | Create a vault, import the selective backup from 4b | "Imported N records" shows only card entries | ☐ |
| 4d.3 | Check each category | Only cards are present; logins/banks/docs/notes/tasks are empty | ☐ |

### 4e. Key Integrity After Restore

| Step | Action | Expected Result | Result |
|------|--------|----------------|--------|
| 4e.1 | After a full restore, lock the vault | Goes to unlock screen | ☐ |
| 4e.2 | Unlock with the **vault's own** master password (from 4c.2, `new-vault-pw`) | Opens successfully | ☐ |
| 4e.3 | Lock again, tap "Forgot master password?" | Recovery key screen | ☐ |
| 4e.4 | Enter the **vault's** recovery key (viewable in Settings) | Unlocks and allows password change | ☐ |

**Pass criteria:** All sub-steps 4a-4e pass. Full and selective backup/restore work. Vault key (password and recovery key) remain intact after restore.

---

## 5. Manifest Permission Check

Run this ADB command with the app installed:

```bash
adb shell dumpsys package com.family.pswdmngr | grep -i "permission\|INTERNET"
```

| Step | Action | Expected Result | Result |
|------|--------|----------------|--------|
| 5.1 | Connect device via USB, ensure app is installed | `adb devices` shows the device | ☐ |
| 5.2 | Run the command above | Output shows `CAMERA`, `USE_BIOMETRIC`, `BIND_AUTOFILL_SERVICE`, etc. | ☐ |
| 5.3 | Manually scan output for `INTERNET` | **Must NOT appear** anywhere in the output | ☐ |

**Pass criteria:** INTERNET permission is absent.

---

## Summary

| Section | Pass/Fail |
|---------|-----------|
| 1. Argon2id Timing | ☐ |
| 2. Migration v1→v2 | ☐ |
| 3. Process Kill During Migration | ☐ |
| 4. Backup / Restore | ☐ |
| 5. Manifest Permission Check | ☐ |
| **Overall** | ☐ Sign-off |

**Date tested:** `____________`
**Device:** `____________`
**Build version:** `____________`
**Tester:** `____________`
