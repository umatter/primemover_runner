package primemover.multilogin.mlx;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Multilogin workspace folder is account-specific. It used to be a hardcoded
 * constant in ProfileService, so a new account needed a code change; it is now
 * injected from multilogin.defaultFolderId (MlxConfiguration passes it to the
 * constructor, and startProfile sends `folderId` to startBrowserProfile). These
 * pin the config->field wiring and the blank-fallback; the one-line call-site
 * use is verified by inspection + the build.
 */
class ProfileServiceFolderIdTest {

    private static ProfileService withFolder(String folderId) {
        return new ProfileService(null, null, null, null, null, folderId);
    }

    @Test
    @DisplayName("a configured folder id is stored and used, not the old hardcoded constant")
    void configuredFolderIsStored() {
        assertEquals("new-account-folder-xyz", withFolder("new-account-folder-xyz").folderId());
    }

    @Test
    @DisplayName("a blank or null configured folder falls back, never empty")
    void blankFallsBack() {
        assertFalse(withFolder("   ").folderId().isBlank());
        assertFalse(withFolder(null).folderId().isBlank());
    }

    @Test
    @DisplayName("the legacy constructors still work (default folder)")
    void legacyConstructorKeepsDefault() {
        ProfileService legacy = new ProfileService(null, null, null, null);
        assertFalse(legacy.folderId().isBlank());
    }
}
