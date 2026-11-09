import Foundation
import Security
import SophiaKit

/// Stores AI provider API keys in the iOS Keychain. Keys never leave this device: they are
/// `ThisDeviceOnly`, so they are not synced to iCloud or restored to another phone from a backup,
/// and they are readable only while the device is unlocked.
struct KeychainStore {
    var service = LaunchOptions.current.uiTesting ? "com.krlbryn.sophia.uitests" : "com.krlbryn.sophia.ai-keys"

    func key(for provider: AIProviderID) -> String? {
        var query = base(provider)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func hasKey(for provider: AIProviderID) -> Bool { !(key(for: provider) ?? "").isEmpty }

    @discardableResult
    func setKey(_ value: String, for provider: AIProviderID) -> Bool {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        deleteKey(for: provider)
        if trimmed.isEmpty { return true }
        var attrs = base(provider)
        attrs[kSecValueData as String] = Data(trimmed.utf8)
        attrs[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        return SecItemAdd(attrs as CFDictionary, nil) == errSecSuccess
    }

    func deleteKey(for provider: AIProviderID) {
        SecItemDelete(base(provider) as CFDictionary)
    }

    func deleteAll() {
        SecItemDelete([kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service] as CFDictionary)
    }

    private func base(_ provider: AIProviderID) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: provider.rawValue,
        ]
    }
}
