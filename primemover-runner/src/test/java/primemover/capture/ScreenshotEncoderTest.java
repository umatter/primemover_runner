package primemover.capture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenshotEncoderTest {

	/** A screenshot-sized PNG with an alpha channel and photo-like noise, as a browser returns. */
	private static byte[] png(int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Random random = new Random(42);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int shade = (x * 255 / width + random.nextInt(40)) & 0xFF;
				image.setRGB(x, y, 0xFF000000 | shade << 16 | (y * 255 / height) << 8 | (255 - shade));
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	@Test
	@DisplayName("a PNG with alpha becomes a smaller JPEG of the same size")
	void convertsToJpeg() throws IOException {
		byte[] png = png(1280, 720);

		Optional<byte[]> jpeg = ScreenshotEncoder.toJpeg(png, 80);

		assertThat(jpeg).isPresent();
		assertThat(jpeg.get()[0]).isEqualTo((byte) 0xFF);
		assertThat(jpeg.get()[1]).isEqualTo((byte) 0xD8);
		assertThat(jpeg.get().length).isLessThan(png.length / 2);
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg.get()));
		assertThat(decoded.getWidth()).isEqualTo(1280);
		assertThat(decoded.getHeight()).isEqualTo(720);
	}

	@Test
	@DisplayName("anything that is not an image yields nothing, so the caller keeps the PNG")
	void notAnImage() {
		assertThat(ScreenshotEncoder.toJpeg(null, 80)).isEmpty();
		assertThat(ScreenshotEncoder.toJpeg(new byte[0], 80)).isEmpty();
		assertThat(ScreenshotEncoder.toJpeg("not a png".getBytes(), 80)).isEmpty();
	}

	@Test
	@DisplayName("quality is clamped to 1..100")
	void qualityIsClamped() throws IOException {
		byte[] png = png(64, 64);

		assertThat(ScreenshotEncoder.toJpeg(png, 0)).isPresent();
		assertThat(ScreenshotEncoder.toJpeg(png, 250)).isPresent();
	}
}
