# App Store submission preparation

Draft metadata for App Store Connect. Fill it in under Sophia > App Store **(you)**.

## Listing

| Field | Draft |
|---|---|
| Name | Sophia (alternative if taken: Sophia Study Browser) |
| Subtitle (30) | Browse. Learn. Understand. |
| Category | Education (secondary: Productivity) |
| Age rating | 4+ answers, except "Unrestricted Web Access": **Yes**, which makes the rating 17+. This is required for any app with a general web browser. |
| Price | Free |
| Support URL | https://github.com/kerlbryandomingo-wq/lapsus/issues |
| Privacy policy URL | https://github.com/kerlbryandomingo-wq/lapsus/blob/main/ios/docs/PRIVACY.md (the repository is private, so publish this file somewhere public first, for example GitHub Pages) |
| Copyright | 2026 KRLBRYN |

**Promotional text (170):** A focused browser for your student portal, with a study assistant that
explains lessons, writes study notes and makes flashcards.

**Description:**

> Sophia is an educational browser and AI study companion for AMA Computer College students.
>
> Sophia opens your student portal the moment you launch it, full screen, exactly as the site looks.
> Swipe from the left edge for the address bar and your school bookmarks. Swipe from the right edge
> for Back, Forward, Reload and Settings. Close a panel and the page is yours again.
>
> STUDY ASSISTANT
> Paste a lesson or select text on a page, then ask Sophia to explain it, summarize it, turn it into
> study notes, write practice questions or make flashcards. Use your own OpenAI, Anthropic or
> compatible AI account. You always see exactly what will be sent before it leaves your device, and
> passwords, email addresses and ID numbers are removed automatically.
>
> BUILT FOR IPHONE AND IPAD
> Portrait and landscape, Split View, Slide Over, Stage Manager, keyboard shortcuts and trackpad.
>
> PRIVATE BY DESIGN
> Secure HTTPS pages only. History, downloads and notes stay on your device. API keys are kept in the
> iOS Keychain. No account, no ads, no tracking.
>
> Sophia is a study tool. Follow your school's rules for graded and monitored assessments.

**Keywords (100):** student,portal,browser,study,flashcards,notes,school,college,learning,lms,summary

## Screenshots

Required sizes: 6.9-inch iPhone (1320 x 2868) and 13-inch iPad (2064 x 2752). CI saves simulator
screenshots of the normal page, the left panel, the right panel and the Study Assistant to the
`sophia-ios-screens` branch; take final ones from the largest iPhone and iPad simulators, signed in
to the portal with your account.

## App Privacy ("nutrition label")

Choose **Data Not Collected**. Sophia has no servers and collects nothing. Text sent to an AI
provider is sent directly by the student, to an account they chose, after they review it; it is not
collected by the developer. The privacy manifest (`Sophia/Resources/PrivacyInfo.xcprivacy`) declares
no tracking and the reasons for UserDefaults and file timestamp APIs.

## Review notes (draft)

> Sophia is a web browser for students of AMA Computer College. It opens the school's portal,
> https://discipulusv2.amasystem.net/, which requires a student account; any public site can be
> opened from the address bar (swipe in from the left screen edge). Swipe in from the right edge for
> navigation controls and Settings. The Study Assistant needs the user's own AI API key; to try it
> without one, choose Settings > Study Assistant settings > AI provider > Demo.

Before submitting, consider:

- **Guideline 4.2 (minimum functionality):** Sophia adds native panels, downloads, history,
  settings and the Study Assistant, which is what separates it from a plain web wrapper. Mention them.
- **School names and logos:** Sophia does not use the AMA logo. If App Review asks about the school's
  name in the description, a written OK from the school helps.
- **Demo account:** if you can, give App Review a test student account for the portal.

## Export compliance

`ITSAppUsesNonExemptEncryption` is `NO`: Sophia uses only Apple's standard HTTPS.
