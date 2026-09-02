package primemover.multilogin.mlx;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The H.264 codec-override extension path used to be hardcoded to the ex63 home
 * in updateProxy. It is host-specific: on a GPU-less VM (software decode) a
 * wrong or absent path makes Chrome drop the extension silently and the renderer
 * dies under VP9/AV1. It is now injected from multilogin.h264ExtensionPath
 * (MlxConfiguration passes it to the constructor). These pin the config->field
 * wiring and the blank-fallback; the one-line call-site use in updateProxy is
 * verified by inspection + the build.
 */
class ProfileServiceH264ExtensionTest {

    private static ProfileService withExtension(String path) {
        return new ProfileService(null, null, null, null, null, "folder", path);
    }

    @Test
    @DisplayName("a configured extension path is stored, not the old hardcoded ex63 path")
    void configuredPathIsStored() {
        assertEquals("/opt/other/h264-override-extension",
                withExtension("/opt/other/h264-override-extension").h264ExtensionPath());
    }

    @Test
    @DisplayName("a blank or null configured path falls back, never empty")
    void blankFallsBack() {
        assertFalse(withExtension("   ").h264ExtensionPath().isBlank());
        assertFalse(withExtension(null).h264ExtensionPath().isBlank());
    }

    @Test
    @DisplayName("the legacy constructors still work (default extension path)")
    void legacyConstructorKeepsDefault() {
        ProfileService legacy = new ProfileService(null, null, null, null);
        assertFalse(legacy.h264ExtensionPath().isBlank());
    }
}
