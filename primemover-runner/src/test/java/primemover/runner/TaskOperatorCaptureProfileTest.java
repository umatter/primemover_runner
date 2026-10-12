package primemover.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import primemover.capture.CaptureProfile;
import primemover.capture.SessionStorageDeduplicator;
import primemover.capture.SessionStorageRef;
import primemover.model.jobs.WaitJob;
import primemover.runner.context.IJobDataRecorder;
import primemover.service.datawriter.JobDataWriter;
import primemover.service.datawriter.PageSourceWriter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The per-job capture decisions TaskOperator makes from a crawler's capture
 * profile. Extracted as static functions for the same reason as
 * {@code challengeWarning}: through {@code runJobInContext} they would need a
 * browser and a Spring context. FULL must reproduce today's capture exactly.
 */
class TaskOperatorCaptureProfileTest {

	private static WaitJob job(long id) {
		WaitJob job = new WaitJob();
		job.setId(id);
		return job;
	}

	private static byte[] png() throws IOException {
		BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	@Test
	@DisplayName("HAR: the job type decides under FULL; COMPACT never records")
	void har() {
		assertThat(TaskOperator.recordsHar(CaptureProfile.FULL, true)).isTrue();
		assertThat(TaskOperator.recordsHar(CaptureProfile.FULL, false)).isFalse();
		assertThat(TaskOperator.recordsHar(CaptureProfile.COMPACT, true)).isFalse();
	}

	@Test
	@DisplayName("writers: FULL keeps the separate page-source object, COMPACT drops only that one")
	void writers() {
		RecordedJobData data = new RecordedJobDataBuilder(job(1L)).setPageSource("<html/>").build();

		assertThat(TaskOperator.keepsWriter(CaptureProfile.FULL, new PageSourceWriter(data))).isTrue();
		assertThat(TaskOperator.keepsWriter(CaptureProfile.COMPACT, new PageSourceWriter(data))).isFalse();
		assertThat(TaskOperator.keepsWriter(CaptureProfile.COMPACT, new JobDataWriter(data))).isTrue();
	}

	@Test
	@DisplayName("screenshot: FULL stores the PNG as it came; COMPACT stores a JPEG")
	void screenshot() throws IOException {
		byte[] png = png();

		IJobDataRecorder full = mock(IJobDataRecorder.class);
		TaskOperator.recordScreenshot(full, png, CaptureProfile.FULL, 80);
		verify(full).setScreenshot(png);

		IJobDataRecorder compact = mock(IJobDataRecorder.class);
		TaskOperator.recordScreenshot(compact, png, CaptureProfile.COMPACT, 80);
		ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
		verify(compact).setScreenshot(stored.capture(), eq("jpg"));
		assertThat(stored.getValue()[0]).isEqualTo((byte) 0xFF);
		assertThat(stored.getValue()[1]).isEqualTo((byte) 0xD8);
	}

	@Test
	@DisplayName("screenshot: COMPACT keeps the PNG when it cannot be re-encoded")
	void screenshotFallback() {
		byte[] notAnImage = "broken".getBytes();
		IJobDataRecorder recorder = mock(IJobDataRecorder.class);

		TaskOperator.recordScreenshot(recorder, notAnImage, CaptureProfile.COMPACT, 80);

		verify(recorder).setScreenshot(notAnImage);
		verify(recorder, never()).setScreenshot(any(), eq("jpg"));
	}

	@Test
	@DisplayName("session storage: FULL stores it every time; COMPACT points an unchanged one at its full copy")
	void sessionStorage() {
		String storage = "{\"SerpCache\":\"blob\"}";

		IJobDataRecorder full = mock(IJobDataRecorder.class);
		SessionStorageDeduplicator fullDedup = new SessionStorageDeduplicator();
		TaskOperator.recordSessionStorage(full, job(1L), storage, CaptureProfile.FULL, fullDedup);
		fullDedup.written(1L, true);
		TaskOperator.recordSessionStorage(full, job(2L), storage, CaptureProfile.FULL, fullDedup);
		verify(full, never()).setSessionStorageRef(any());

		SessionStorageDeduplicator dedup = new SessionStorageDeduplicator();
		IJobDataRecorder first = mock(IJobDataRecorder.class);
		TaskOperator.recordSessionStorage(first, job(1L), storage, CaptureProfile.COMPACT, dedup);
		verify(first).setSessionStorage(storage);
		verify(first, never()).setSessionStorageRef(any());
		dedup.written(1L, true);

		IJobDataRecorder second = mock(IJobDataRecorder.class);
		TaskOperator.recordSessionStorage(second, job(2L), storage, CaptureProfile.COMPACT, dedup);
		ArgumentCaptor<SessionStorageRef> ref = ArgumentCaptor.forClass(SessionStorageRef.class);
		verify(second).setSessionStorageRef(ref.capture());
		assertThat(ref.getValue().getJobId()).isEqualTo(1L);
		assertThat(ref.getValue().getObject()).isEqualTo("static-jobdata-waitjob-NULL-1");
	}
}
