# Mhorpix v1.7.1 - Important Developer Notes

## Important: Preserving In-App Auto-Update & Notification Features
This repository includes the in-app auto-update and release notification system implemented in v1.7.1.
To ensure none of these changes are overwritten or destroyed:

### 1. Work in a New Feature Branch
Please create and work in a new git branch:
    git checkout -b feature/your-feature-name

Do not force-push or reset the main branch or the 1.7.1 tag.

### 2. Core Update Files (Do Not Remove or Overwrite)
The following files contain the update and notification engine:
- pp/src/main/java/com/music/mhorpix/data/AppUpdateChecker.kt (Release polling, ABI resolution, APK downloader, installer)
- pp/src/main/java/com/music/mhorpix/data/UpdateNotifications.kt (System tray notifications)
- pp/src/main/java/com/music/mhorpix/data/UpdateDeepLink.kt (Deep link handling from notification tray)
- pp/src/main/java/com/music/mhorpix/ui/components/UpdateAvailableDialog.kt (In-app frosted update popup with changelog & progress)
- .github/workflows/release.yml (Automated Android release APK build on tag push)

### 3. Merging Changes
When your feature is complete, merge cleanly with:
    git checkout main
    git merge feature/your-feature-name