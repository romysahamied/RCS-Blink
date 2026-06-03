# RCS Blink branding

## Master icon

`rcs-blink-icon.png` — 1024×1024 source artwork for the app.

## Regenerate all sizes

After replacing the master PNG, run:

```powershell
.\scripts\install-rcs-blink-icon.ps1
```

This updates:

- **Web:** `web/public/images/logo.png`, `web/public/icon.png`, favicons, `web/app/icon.png`, `web/app/apple-icon.png`
- **Android:** `mipmap-*` launcher icons, `drawable/rcs_blink_icon.png`, `drawable/ic_notification.png`
