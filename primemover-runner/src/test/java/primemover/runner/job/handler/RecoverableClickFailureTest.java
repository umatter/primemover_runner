package primemover.runner.job.handler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;

/**
 * The click loop retries and then falls back to navigate-by-id for two page
 * states, and only those two.
 *
 * <p>
 * Before this, the loop caught only {@link NoSuchElementException} -- the xpath
 * matched nothing. A {@link TimeoutException} from {@code elementToBeClickable}
 * (the element was found but never became clickable, e.g. obscured by YouTube's
 * endscreen video-wall) is not a subtype of that, so it escaped the loop and
 * skipped Layer B entirely -- failing a job whose selected video id was already
 * in hand. Round 8, bot soak8_ch_03: 29 such failures, then recovery, all with
 * a correct xpath.
 *
 * <p>
 * The predicate must recover both, and must NOT quietly swallow an unrelated
 * driver fault -- those still propagate.
 */
class RecoverableClickFailureTest {

    @Test
    @DisplayName("a not-found element is recoverable (the original case)")
    void noSuchElementRecovers() {
        assertTrue(LambdaSelectJobHandler.isRecoverableClickFailure(
                new NoSuchElementException("no element")));
    }

    @Test
    @DisplayName("a found-but-not-clickable element is recoverable (the round-8 case)")
    void timeoutRecovers() {
        // This is the exact failure that escaped the loop and skipped navigate-by-id.
        assertTrue(LambdaSelectJobHandler.isRecoverableClickFailure(
                new TimeoutException("waiting for element to be clickable")));
    }

    @Test
    @DisplayName("an unrelated WebDriverException is NOT swallowed -- it propagates")
    void otherWebDriverExceptionsPropagate() {
        // Widening the catch to WebDriverException must not turn every driver
        // fault into a silent navigate-by-id. A click intercepted by an overlay,
        // a session crash, a generic driver error: none of these are the two
        // states this recovery is for, so they keep their old behaviour.
        assertFalse(LambdaSelectJobHandler.isRecoverableClickFailure(
                new ElementClickInterceptedException("intercepted")));
        assertFalse(LambdaSelectJobHandler.isRecoverableClickFailure(
                new WebDriverException("tab crashed")));
    }

    @Test
    @DisplayName("null is not recoverable")
    void nullIsNotRecoverable() {
        assertFalse(LambdaSelectJobHandler.isRecoverableClickFailure(null));
    }
}
