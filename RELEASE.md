# Releasing Lapsus

## Build outputs

| File                      | What it is                                                                                                                                                        |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Lapsus-Setup-x64.exe`    | Per-user NSIS installer. No administrator rights; installs to `%LOCALAPPDATA%\Programs\Lapsus`; Start menu and desktop shortcuts; uninstall from Windows Settings |
| `Lapsus-Portable-x64.exe` | Runs without installing. Data still goes to `%APPDATA%\Lapsus`                                                                                                    |
| `SHA256SUMS.txt`          | SHA-256 checksums of both files                                                                                                                                   |

## How to release

1. Update `version` in `package.json` and add `docs/release-notes/vX.Y.Z.md`.
2. Merge to `main` with CI green.
3. Tag and push: `git tag vX.Y.Z && git push origin vX.Y.Z`.
4. The **Release** workflow builds on `windows-latest`, runs all tests and packaging checks, and
   publishes a GitHub release with the two executables and `SHA256SUMS.txt`.

While the repository is private, release downloads work only for people with access to it.

## Code signing

Lapsus 0.1.0 is **unsigned**. The publisher name "KRLBRYN" in the file properties is metadata,
not a signature, and Windows shows "Unknown publisher". Microsoft Defender SmartScreen may show
"Windows protected your PC" until a file builds up reputation. Signing reduces these warnings but
does not guarantee they never appear.

To sign, add a code-signing certificate to the repository secrets:

- `CSC_LINK`: the `.pfx` file, base64-encoded (or an HTTPS URL to it)
- `CSC_KEY_PASSWORD`: its password

and expose them as environment variables in the "Package installer and portable app" step.
electron-builder then signs the app, the installer and the uninstaller automatically. Never commit
certificates or passwords to the repository.

Options, with typical prices at the time of writing (check current offers):

- **Microsoft Azure Artifact Signing** (formerly Trusted Signing): a monthly subscription, no
  hardware token; availability depends on country and identity verification.
- **OV code-signing certificate** from a certificate authority: yearly fee; keys must be on a
  hardware token or cloud HSM since 2023, which may need a custom signing setup in CI.
- **EV certificate**: more expensive; since 2024 it no longer gives instant SmartScreen
  reputation.

## Checks before publishing

The workflow fails the release if any of these fail: lint, typecheck, unit tests, end-to-end
tests, the focus and visibility harness, the artifact secret scan, the Electron fuse check, the
unpacked app smoke test, or silent install, metadata check, smoke test and uninstall of the
installer. Code-signing status is printed for information.
