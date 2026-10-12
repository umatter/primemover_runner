package primemover.capture;

import javax.annotation.Nullable;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Optional;

/**
 * The browser's PNG screenshot as a JPEG, for {@link CaptureProfile#COMPACT}.
 *
 * <p>
 * A PNG is already compressed, so the job-data ZIP stores it at full size: a
 * Bing page's screenshot was 1.19 MB of a 1.78 MB job archive on 2026-10-12.
 * JPEG at quality 80 is a fraction of that and keeps the page legible.
 * The browser's PNG carries an alpha channel, which the JPEG writer refuses, so
 * the image is first drawn onto an opaque RGB canvas. Plain {@code javax.imageio},
 * no extra library.
 */
public final class ScreenshotEncoder {

	private ScreenshotEncoder() {
	}

	/**
	 * @param png     the screenshot as the driver returned it
	 * @param quality JPEG quality, 1..100 (clamped)
	 * @return the JPEG bytes, or empty when the input is not a readable image --
	 *         the caller then keeps the PNG it has
	 */
	public static Optional<byte[]> toJpeg(@Nullable byte[] png, int quality) {
		if (png == null || png.length == 0) {
			return Optional.empty();
		}

		try {
			BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
			if (source == null) {
				return Optional.empty();
			}

			BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D graphics = rgb.createGraphics();
			try {
				graphics.drawImage(source, 0, 0, java.awt.Color.WHITE, null);
			} finally {
				graphics.dispose();
			}

			Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
			if (!writers.hasNext()) {
				return Optional.empty();
			}
			ImageWriter writer = writers.next();
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(Math.max(1, Math.min(100, quality)) / 100f);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
				writer.setOutput(stream);
				writer.write(null, new IIOImage(rgb, null, null), param);
			} finally {
				writer.dispose();
			}
			return Optional.of(out.toByteArray());
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
