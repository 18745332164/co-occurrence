/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2017-2018 Nikita Shakarun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.LruCache;

import java.io.IOException;
import java.io.InputStream;

import javax.microedition.lcdui.game.Sprite;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.util.PNGUtils;

public class Image {

	private static final int CACHE_SIZE = (int) (Runtime.getRuntime().maxMemory() >> 2); // 1/4 heap max
	private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(CACHE_SIZE) {
		@Override
		protected int sizeOf(String key, Bitmap value) {
			return value.getByteCount();
		}
	};

	private Bitmap bitmap;
	private Canvas canvas;
	private Graphics graphics;
	private int save;
	private Rect bounds;
	private boolean isBlackWhiteAlpha;

	private Image(Bitmap bitmap) {
		if (bitmap == null) {
			throw new NullPointerException();
		}
		this.bitmap = bitmap;
		bounds = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
	}

	public static Image createTransparentImage(int width, int height) {
		return new Image(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888));
	}

	public Bitmap getBitmap() {
		return bitmap;
	}

	public Canvas getCanvas() {
		if (canvas == null) {
			canvas = new Canvas(bitmap);
			save = canvas.save();
		}

		return canvas;
	}

	public static Image createImage(int width, int height) {
		Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		b.eraseColor(Color.WHITE);
		return new Image(b);
	}

	public static Image createImage(String resname) throws IOException {
		synchronized (CACHE) {
			Bitmap b = CACHE.get(resname);
			if (b != null) {
				return new Image(b);
			}
			InputStream stream = ContextHolder.getResourceAsStream(null, resname);
			if (stream == null) {
				throw new IOException("Can't read image: " + resname);
			}
			b = PNGUtils.getFixedBitmap(stream);
			stream.close();
			if (b == null) {
				throw new IOException("Can't decode image: " + resname);
			}
			CACHE.put(resname, b);
			return new Image(b);
		}
	}

	public static Image createImage(InputStream stream) throws IOException {
		Bitmap b = PNGUtils.getFixedBitmap(stream);
		if (b == null) {
			throw new IOException("Can't decode image");
		}
		return new Image(b);
	}

	public static Image createImage(byte[] imageData, int imageOffset, int imageLength) {
		Bitmap b = PNGUtils.getFixedBitmap(imageData, imageOffset, imageLength);
		if (b == null) {
			throw new IllegalArgumentException("Can't decode image");
		}
		return new Image(b);
	}

	public static Image createImage(Image image, int x, int y, int width, int height, int transform) {
		return new Image(Bitmap.createBitmap(image.bitmap, x, y, width, height, Sprite.transformMatrix(transform, width / 2f, height / 2f), false));
	}

	public static Image createImage(Image image) {
		return new Image(Bitmap.createBitmap(image.bitmap));
	}

	public static Image createRGBImage(int[] rgb, int width, int height, boolean processAlpha) {
		if (!processAlpha) {
			final int length = width * height;
			int[] rgbCopy = new int[length];
			System.arraycopy(rgb, 0, rgbCopy, 0, length);
			for (int i = 0; i < length; i++) {
				rgbCopy[i] |= 0xFF << 24;
			}
			rgb = rgbCopy;
		}
		return new Image(Bitmap.createBitmap(rgb, width, height, Bitmap.Config.ARGB_8888));
	}

	public Graphics getGraphics() {
		return new Graphics(this);
	}

	public boolean isMutable() {
		return bitmap.isMutable();
	}

	public int getWidth() {
		return bounds.right;
	}

	public int getHeight() {
		return bounds.bottom;
	}

	public void getRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width, int height) {
		bitmap.getPixels(rgbData, offset, scanlength, x, y, width, height);
	}

	void copyTo(Image dst) {
		dst.getCanvas().drawBitmap(bitmap, bounds, bounds, null);
	}

	void copyTo(Image dst, int x, int y) {
		Rect r = new Rect(x, y, x + bounds.right, y + bounds.bottom);
		dst.getCanvas().drawBitmap(bitmap, bounds, r, null);
	}

	public Graphics getSingleGraphics() {
		if (graphics == null) {
			graphics = getGraphics();
		}
		return graphics;
	}

	void setSize(int width, int height) {
		bounds.right = width;
		bounds.bottom = height;
	}

	public Rect getBounds() {
		return bounds;
	}

	public boolean isBlackWhiteAlpha() {
		return isBlackWhiteAlpha;
	}

	public void setBlackWhiteAlpha(boolean blackWhiteAlpha) {
		isBlackWhiteAlpha = blackWhiteAlpha;
	}
}