/*
 * Copyright (C) 2017 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ch.blinkenlights.android.vanilla;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;
import android.widget.Scroller;

import java.lang.IllegalStateException;
import java.lang.IllegalArgumentException;

import android.util.Log;



/**
 * Displays a flingable/draggable View of cover art/song info images
 * generated by CoverBitmap.
 */
public final class CoverView extends View implements Handler.Callback {
	/**
	 * If >= 0, perform the switch after this delay
	 */
	private final static int ASYNC_SWITCH = -1;
	/**
	 * Maximum amount of pixels we are allowed to scroll to consider
	 * touch events to be normal touches.
	 */
	private final static double TOUCH_MAX_SCROLL_PX = 10;
	/**
	 * The system provided display density
	 */
	private static double sDensity = -1;
	/**
	 * The minimum velocity to move to the next song
	 */
	private static double sSnapVelocity = -1;
	/**
	 * Computes scroll velocity to detect flings.
	 */
	private VelocityTracker mVelocityTracker;
	/**
	 * The context.
	 */
	private final Context mContext;
	/**
	 * The scroller instance we are using.
	 */
	private final CoverScroller mScroller;
	/**
	 * Our bitmap cache helper
	 */
	private BitmapBucket mBitmapBucket;
	/**
	 * Our callback to dispatch song events.
	 */
	private CoverView.Callback mCallback;
	/**
	 * Indicates that we attempted to query and update our songs but couldn't as the
	 * view was not yet ready.
	 */
	private boolean mPendingQuery;
	/**
	 * The x coordinate of the initial touch event.
	 */
	private float mInitialMotionX;
	/**
	 * The y coordinate of the initial touch event.
	 */
	private float mInitialMotionY;
	/**
	 * The x coordinate of the last touch down or move event.
	 */
	private float mLastMotionX;
	/**
	 * The y coordinate of the last touch down or move event.
	 */
	private float mLastMotionY;
	/**
	 * The message handler used by this class.
	 */
	private Handler mHandler;
	/**
	 * Handler thread only used for UI updates
	 */
	private Handler mUiHandler;
	/**
	 * Our current scroll position.
	 * Setting this to '0' means that we will display bitmap[0].
	 */
	private int mScrollX = -1;
	/**
	 * The style to use for the cover.
	 */
	private int mCoverStyle = -1;
	/**
	 * Our public callback interface
	 */
	public interface Callback {
		void shiftCurrentSong(int delta);
		void upSwipe();
		void downSwipe();
	}

	/**
	 * Constructs a new CoverView class, note that setup() must be called
	 * before the view becomes useable.
	 */
	public CoverView(Context context, AttributeSet attributes) {
		super(context, attributes);
		if (sDensity == -1) {
			sDensity = context.getResources().getDisplayMetrics().density;
			sSnapVelocity = ViewConfiguration.get(context).getScaledMinimumFlingVelocity() * 5;
		}
		mContext = context;
		mBitmapBucket = new BitmapBucket();
		mScroller = new CoverScroller(context);
	}

	/**
	 * Configures and sets up this view
	 */
	public void setup(Looper looper, Callback callback, int style) {
		mUiHandler = new Handler(this);
		mHandler = new Handler(looper, this);
		mCallback = callback;
		mCoverStyle = style;
	}

	/**
	 * Sent if the songs timeline changed and we should check if
	 * mCacheBitmap is stale.
	 * Just calls querySongsInternal() via handler to ensure
	 * that we do this in a background thread.
	 */
	public void querySongs() {
		mHandler.removeMessages(MSG_QUERY_SONGS);
		mHandler.sendEmptyMessage(MSG_QUERY_SONGS);
	}

	/**
	 * Called if a specific song got replaced.
	 * The current implementation does not take this hint into
	 * account as querySongsInternal() already tries to be efficient.
	 */
	public void replaceSong(int delta, Song song) {
		querySongs();
	}

	/**
	 * Called by querySongs() - this runs in a background thread.
	 */
	private void querySongsInternal() {
		DEBUG("querySongsInternal");

		if (getWidth() < 1 || getHeight() < 1) {
			mPendingQuery = true;
			return;
		}

		if (mScrollX < 0) { // initialize mScrollX to show cover '1' by default.
			mScrollX = getWidth();
		}

		mHandler.removeMessages(MSG_SET_BITMAP);
		PlaybackService service = PlaybackService.get(mContext);

		final Song[] songs = { service.getSong(-1), service.getSong(0), service.getSong(1) };
		final int len = songs.length;

		for (int i = 0; i < len; i++) {
			Song song = songs[i];
			if (mBitmapBucket.getSong(i) != song) {
				mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_BITMAP, i, 0, song));
			}
		}
	}

	/**
	 * Updates the cover in bitmap bucket for given index.
	 *
	 * @param i the index to modify
	 * @param song the source of the cover
	 */
	private void setSongBitmap(int i, Song song) {
		Bitmap bitmap = mBitmapBucket.grepBitmap(song);

		if (bitmap == null && song != null)
			bitmap = generateBitmap(song);

		mBitmapBucket.setSongBitmap(i, song, bitmap);
		postInvalidate();
	}

	/**
	 * Returns a correctly sized cover bitmap for given song
	 */
	private Bitmap generateBitmap(Song song) {
		int style = mCoverStyle;
		Bitmap cover = song == null ? null : song.getCover(mContext);

		if (cover == null && style != CoverBitmap.STYLE_OVERLAPPING_BOX) {
			cover = CoverBitmap.generateDefaultCover(mContext, getWidth(), getHeight());
		}

		return CoverBitmap.createBitmap(mContext, style, cover, song, getWidth(), getHeight());
	}


	private final static int MSG_QUERY_SONGS = 1;
	private final static int MSG_SHIFT_SONG = 2;
	private final static int MSG_SET_BITMAP = 3;
	private final static int MSG_UI_LONG_CLICK = 4; // should only be used with mUiHandler

	@Override
	public boolean handleMessage(Message message) {
		switch (message.what) {
			case MSG_QUERY_SONGS:
				querySongsInternal();
				break;
			case MSG_SHIFT_SONG:
				DEBUG("Shifting to song: "+message.arg1);
				mCallback.shiftCurrentSong(message.arg1);
				break;
			case MSG_SET_BITMAP:
				setSongBitmap(message.arg1, (Song)message.obj);
				break;
			case MSG_UI_LONG_CLICK:
				if (!Looper.getMainLooper().equals(Looper.myLooper())) {
					throw new IllegalStateException("MSG_UI_LONG_CLICK must be run from the UI thread");
				}
				if (scrollIsNotSignificant()) {
					performLongClick();
				}
				break;
			default:
				throw new IllegalArgumentException("Unknown message received: "+message.what);
		}
		return true;
	}

	/**
	 * Triggers if the view changes its size, may call querySongs() if it was called
	 * previously but had to be aborted as the view was not yet laid out.
	 */
	@Override
	protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
		if (mPendingQuery && width != 0 && height != 0) {
			mPendingQuery = false;
			querySongs();
		}
	}

	/**
	 * Lays out this view - only handles our specific use cases
	 */
	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		// This implementation only tries to handle two cases: use in the
		// FullPlaybackActivity, where we want to fill the whole screen,
		// and use in the  MiniPlaybackActivity, where we want to be square.

		int width = View.MeasureSpec.getSize(widthSpec);
		int height = View.MeasureSpec.getSize(heightSpec);
		if  (View.MeasureSpec.getMode(widthSpec) == View.MeasureSpec.EXACTLY && View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.EXACTLY) {
			// FullPlaybackActivity: fill screen
			setMeasuredDimension(width, height);
		} else {
			// MiniPlaybackActivity: be square
			int size = Math.min(width, height);
			setMeasuredDimension(size, size);
		}
	}

	/**
	 * Paint the cover art views to the canvas.
	 */
	@Override
	protected void onDraw(Canvas canvas) {
		int width = getWidth();
		int height = getHeight();
		int x = 0;
		int scrollX = mScrollX;
		boolean snapshot = !mScroller.isFinished();
		Bitmap bitmap;

		for (int i=0; i <= 2 ; i++) {
			bitmap = snapshot ? mBitmapBucket.getSnapshot(i) : mBitmapBucket.getBitmap(i);
			if (bitmap != null && scrollX + width > x && scrollX < x + width) {
				final int xOffset = (width - bitmap.getWidth()) / 2;
				final int yOffset = (int)(height - bitmap.getHeight()) / 2;
				canvas.drawBitmap(bitmap, x + xOffset - scrollX, yOffset, null);
			}
			x += width;
		}
		advanceScroll();
	}

	/**
	 * Advances to the next frame of the animation.
	 */
	private void advanceScroll() {
		boolean running = mScroller.computeScrollOffset();
		if (running) {
			mScrollX = mScroller.getCurrX();
			if (mScroller.isFinished()) {
				// just hit the end!
				mBitmapBucket.finalizeScroll();
				mScrollX = getWidth();

				int coverIntent = mScroller.getCoverIntent();
				if (coverIntent != 0 && ASYNC_SWITCH < 0)
					mHandler.sendMessage(mHandler.obtainMessage(MSG_SHIFT_SONG, coverIntent, 0));

				DEBUG("Scroll finished, invalidating all snapshot bitmaps!");
			}
			invalidate();
		}
	}

	/**
	 * Handles all touch events received by this view.
	 */
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		float x = ev.getX();
		float y = ev.getY();
		int scrollX = mScrollX;
		int width = getWidth();
		boolean invalidate = false;

		if (mVelocityTracker == null)
			mVelocityTracker = VelocityTracker.obtain();
		mVelocityTracker.addMovement(ev);

		switch (ev.getAction()) {
			case MotionEvent.ACTION_DOWN: {

				if (mScroller.isFinished()) {
					mUiHandler.sendEmptyMessageDelayed(MSG_UI_LONG_CLICK, ViewConfiguration.getLongPressTimeout());
				} else {
					// Animation was still running while we got a new down event
					// Abort the current animation!

					final int coverIntent = mScroller.getCoverIntent();
					mBitmapBucket.abortScroll();
					mScroller.abortAnimation();

					if (coverIntent != 0) {
						// The running animation was actually supposed to switch to a new song.
						// Do this right now as the animation is canceled:
						// First, set our non-cached covers to a sane version
						mBitmapBucket.prepareScroll(coverIntent);
						// ..and fix up the scrolling position.
						mScrollX -= coverIntent * getWidth();
						// all done: we can now trigger the song jump
						if (ASYNC_SWITCH < 0 || mHandler.hasMessages(MSG_SHIFT_SONG)) {
							mHandler.removeMessages(MSG_SHIFT_SONG);
							mHandler.sendMessage(mHandler.obtainMessage(MSG_SHIFT_SONG, coverIntent, 0));
						}
					}

					// There is no running animation (anymore?), so we can drop the cache.
					mBitmapBucket.finalizeScroll();
				}

				mLastMotionX = mInitialMotionX = x;
				mLastMotionY = mInitialMotionY = y;

				break;
			}
			case MotionEvent.ACTION_MOVE: {
				final float deltaX = mLastMotionX - x;
				final float deltaY = mLastMotionY - y;

				if (Math.abs(deltaX) > Math.abs(deltaY)) { // only change X if the fling is horizontal
					if (deltaX < 0) {
						int availableToScroll = scrollX - (mBitmapBucket.getSong(0) == null ? width : 0);
						if (availableToScroll > 0) {
							mScrollX += Math.max(-availableToScroll, (int)deltaX);
							invalidate = true;
						}
					} else if (deltaX > 0) {
						int availableToScroll = width * 2 - scrollX;
						if (availableToScroll > 0) {
							mScrollX += Math.min(availableToScroll, (int)deltaX);
							invalidate = true;
						}
					}
				}

				mLastMotionX = x;
				mLastMotionY = y;
				break;
			}
			case MotionEvent.ACTION_UP: {
				VelocityTracker velocityTracker = mVelocityTracker;
				velocityTracker.computeCurrentVelocity(1000); // report velocity in pixels-per-second, as assumed by snap-velocity and co.
				final int velocityX = (int) velocityTracker.getXVelocity();
				final int velocityY = (int) velocityTracker.getYVelocity();
				mVelocityTracker.recycle();
				mVelocityTracker = null;

				final int mvx = Math.abs(velocityX);
				final int mvy = Math.abs(velocityY);
				final float distanceX = mLastMotionX - mInitialMotionX;
				int whichCover = 0;

				if (scrollIsNotSignificant()) {
					if (mUiHandler.hasMessages(MSG_UI_LONG_CLICK)) {
						// long click didn't fire yet -> consider this to be a normal click
						performClick();
					}
				} else if (Math.abs(distanceX) > width/2) {
					whichCover = distanceX < 0 ? 1 : -1;
				} else if (mvx > sSnapVelocity || mvy > sSnapVelocity) {
					if (mvy > mvx) {
						if (velocityY > 0)
							mCallback.downSwipe();
						else
							mCallback.upSwipe();
					} else {
						whichCover = velocityX < 0 ? 1 : -1;
					}
				}

				// Ensure that the target song actually exists.
				// Eg: We may not have song 0 in random mode.
				if (mBitmapBucket.getSong(1+whichCover) == null)
					whichCover = 0;

				final int scrollTargetX = width + whichCover*width;

				mBitmapBucket.prepareScroll(whichCover);
				mScroller.handleFling(velocityX, mScrollX, scrollTargetX, whichCover);
				if (ASYNC_SWITCH >= 0)
					mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SHIFT_SONG, whichCover, 0), ASYNC_SWITCH);
				mUiHandler.removeMessages(MSG_UI_LONG_CLICK);

				invalidate = true;
				break;
			}
		}

		if (invalidate)
			postInvalidate();

		return true;
	}

	/**
	 * Returns true if the scroll traveled a significant distance
	 * and is therefore not considered to be random noise during a click.
	 */
	private boolean scrollIsNotSignificant() {
		final float distanceX = mLastMotionX - mInitialMotionX;
		final float distanceY = mLastMotionY - mInitialMotionY;
		return Math.abs(distanceX) + Math.abs(distanceY) < TOUCH_MAX_SCROLL_PX;
	}

	private void DEBUG(String s) {
		// Log.v("VanillaMusicCover", s);
	}


	/**
	 * Class to handle access to our bitmap and song mapping
	 */
	private class BitmapBucket {
		/**
		 * The pre-generated bitmaps for all 3 songs
		 */
		private final Bitmap[] mCacheBitmaps = new Bitmap[3];
		/**
		 * Cached songs, used to check if mCacheBitmaps is still valid
		 */
		private final Song[] mCacheSongs = new Song[3];
		/**
		 * A WIP copy: We use this (iff available) to draw.
		 * This allows us to update mCacheBitmaps while scrolling
		 */
		private final Bitmap[] mSnapshotBitmaps = new Bitmap[3];
		/**
		 * A WIP copy of songs, used if we have to restore
		 * This snapshot is only used internally and copied
		 * to mCacheBitmaps if we restore our bitmap snapshot.
		 */
		private final Song[] mSnapshotSongs = new Song[3];
		/**
		 * Constructor for BitmapBucket
		 */
		public BitmapBucket() {
		}

		public Song getSong(int i) {
			return mCacheSongs[i];
		}

		public Bitmap getBitmap(int i) {
			return mCacheBitmaps[i];
		}

		public Bitmap getSnapshot(int i) {
			return mSnapshotBitmaps[i];
		}

		public void setSongBitmap(int i, Song song, Bitmap bitmap) {
			mCacheSongs[i] = song;
			mCacheBitmaps[i] = bitmap;
		}

		public Bitmap grepBitmap(Song song) {
			final int len = mCacheSongs.length;
			for (int i = 0; i < len ; i++) {
				if (song != null && song.equals(mCacheSongs[i])) {
					return mCacheBitmaps[i];
				}
			}
			return null;
		}

		/**
		 * Hint that we are going to scroll.
		 * This causes us to populate our bitmap snapshot
		 * and will cause us to modify the current cache with a guess.
		 *
		 * @param futureCover the cover we are going to scroll to
		 */
		public void prepareScroll(int futureCover) {
			// Grab a snapshot of the bitmaps which will be used
			// while the animation is running, so that we can concurrently
			// modify mCacheBitmaps.
			System.arraycopy(mCacheBitmaps, 0, mSnapshotBitmaps, 0, 3);
			System.arraycopy(mCacheSongs, 0, mSnapshotSongs, 0, 3);

			// we are going to scroll, so most likely we can save 2 bitmaps by guessing the
			// new situation. This doesn't have to be 100% correct as the next querySongs()
			// call would fix up wrong guesses.
			// FIXME: This may be under-locked: cache checks via getSong() and prepareScroll() may race.
			if (futureCover > 0) {
				mCacheBitmaps[0] = mCacheBitmaps[1];
				mCacheSongs[0] = mCacheSongs[1];
				mCacheBitmaps[1] = mCacheBitmaps[2];
				mCacheSongs[1] = mCacheSongs[2];
				mCacheBitmaps[2] = null;
				mCacheSongs[2] = new Song(-1);
			} else if (futureCover < 0) {
				mCacheBitmaps[2] = mCacheBitmaps[1];
				mCacheSongs[2] = mCacheSongs[1];
				mCacheBitmaps[1] = mCacheBitmaps[0];
				mCacheSongs[1] = mCacheSongs[0];
				mCacheBitmaps[0] = null;
				mCacheSongs[0] = new Song(-1);
			}
		}

		/**
		 * Abort a scroll initiated by prepareScroll.
		 */
		public void abortScroll() {
			// undo our guess we did in prepareScroll
			System.arraycopy(mSnapshotBitmaps, 0, mCacheBitmaps, 0, 3);
			System.arraycopy(mSnapshotSongs, 0, mCacheSongs, 0, 3);
			finalizeScroll();
		}

		public void finalizeScroll() {
			for (int i=0; i <= 2; i++) {
				mSnapshotBitmaps[i] = null;
				mSnapshotSongs[i] = null;
			}
		}

	}

	/**
	 * The scroller class helps to keep track
	 * of the current scroll progress.
	 */
	private class CoverScroller extends Scroller {
		/**
		 * The cover we are scrolling to
		 */
		private int mCoverIntent;
		/**
		 * Returns a new scroller instance
		 */
		public CoverScroller(Context context) {
			super(context, new LinearInterpolator(), false);
		}

		/**
		 * Returns the cover set by the last handleFling
		 * call.
		 *
		 * @return int the cover set by handleFling.
		 */
		public int getCoverIntent() {
			return mCoverIntent;
		}

		@Override
		public void abortAnimation() {
			mCoverIntent = 0;
			super.abortAnimation();
		}
		/**
		 * Starts a fling operation
		 *
		 * @param velocity the current velocity
		 * @param from the current x coordinate
		 * @param to the target x coordinate
		 * @param coverIntent the cover we are scrolling to, returned by getCoverIntent()
		 */
		public void handleFling(int velocity, int from, int to, int coverIntent) {
			if (!isFinished()) {
				abortAnimation();
			}

			mCoverIntent = coverIntent;

			final int distance = to - from;
			int duration = (int)(Math.abs(distance) / sDensity);
			if (duration > 200)
				duration = 200;

			startScroll(from, 0, distance, 0, duration);
		}
	}

}