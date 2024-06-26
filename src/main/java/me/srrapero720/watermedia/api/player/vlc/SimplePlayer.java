package me.srrapero720.watermedia.api.player.vlc;

import me.srrapero720.watermedia.api.network.DynamicURL;
import me.srrapero720.watermedia.api.player.PlayerAPI;
import me.srrapero720.watermedia.tools.ThreadCore;
import me.srrapero720.watermedia.tools.Tools;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import uk.co.caprica.vlcj.binding.RuntimeUtil;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.media.InfoApi;
import uk.co.caprica.vlcj.media.MediaType;
import uk.co.caprica.vlcj.player.base.EmbededMediaPlayerEventListener;
import uk.co.caprica.vlcj.player.base.State;
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.SimpleBufferFormatCallback;

import static me.srrapero720.watermedia.WaterMedia.LOGGER;

public abstract class SimplePlayer {
    protected static final Marker IT = MarkerManager.getMarker("SyncMediaPlayer");
    protected static final WaterMediaPlayerEventListener LISTENER = new WaterMediaPlayerEventListener();

    // PLAYER
    protected String url;
    private volatile CallbackMediaPlayerComponent raw;
    public CallbackMediaPlayerComponent raw() { return raw; }

    // PLAYER THREAD
    protected volatile boolean live = false;
    protected volatile boolean started = false;

    /**
     * Submit async task, specially when you want to run vlc task on a vlc callback
     * @param r runnable to run
     */
    public void submit(Runnable r) { if (raw != null) raw.mediaPlayer().submit(r); }

    protected SimplePlayer(MediaPlayerFactory factory, RenderCallback renderCallback, SimpleBufferFormatCallback bufferFormatCallback) {
        this.init(factory, renderCallback, bufferFormatCallback);
    }

    /**
     * This constructor skips raw player creation, instead waits for {@link #init(MediaPlayerFactory, RenderCallback, SimpleBufferFormatCallback)} to create raw player
     * Intended to be used just in case you need to do some special implementations of {@link RenderCallback} or {@link SimpleBufferFormatCallback}
     */
    protected SimplePlayer() {}

    /**
     * Creates a raw player and makes this works normally
     * @param factory MediaPlayerFactory to create raw player, cannot be null
     * @param renderCallback this is executed when buffer loads media info (first time)
     * @param bufferFormatCallback creates a buffer for the frame
     */
    protected void init(MediaPlayerFactory factory, RenderCallback renderCallback, SimpleBufferFormatCallback bufferFormatCallback) {
        if (PlayerAPI.isReady() && raw == null) {
            this.raw = new CallbackMediaPlayerComponent(factory, false, renderCallback, bufferFormatCallback);
            raw.mediaPlayer().events().addMediaPlayerEventListener(LISTENER);
        } else {
            LOGGER.error(IT, "Failed to create raw player because VLC is not loaded");
            this.raw = null;
        }
    }

    private boolean rpa(DynamicURL url) {
        if (raw == null) return false;
        return Tools.withReturn(defaultVar -> {

            this.url = url.getSource();
            this.live = url.isVideo();
            return true;
        }, false);
    }

    public void start(DynamicURL url) { this.start(url, new String[0]); }
    public void start(DynamicURL url, String[] vlcArgs) {
        ThreadCore.thread(3, () -> {
            if (rpa(url)) raw.mediaPlayer().media().start(this.url, vlcArgs);
            started = true;
        });
    }

    public void startPaused(DynamicURL url) { this.startPaused(url, new String[0]); }
    public void startPaused(DynamicURL url, String[] vlcArgs) {
        ThreadCore.thread(3, () -> {
            if (rpa(url)) raw.mediaPlayer().media().startPaused(this.url, vlcArgs);
            started = true;
        });
    }

    public State getRawPlayerState() {
        if (raw == null) return State.ERROR;
        return raw.mediaPlayer().status().state();
    }

    public void play() {
        if (raw == null) return;
        raw.mediaPlayer().controls().play();
    }

    public void pause() {
        if (raw == null) return;
        if (raw.mediaPlayer().status().canPause()) raw.mediaPlayer().controls().pause();
    }

    public void togglePlayback() {
        if (raw == null) return;
        if (isPaused()) {
            raw.mediaPlayer().controls().play();
        } else if (isPlaying()) {
            raw.mediaPlayer().controls().pause();
        }
    }

    public void setPauseMode(boolean pauseMode) {
        if (raw == null) return;
        if (raw.mediaPlayer().status().canPause()) raw.mediaPlayer().controls().setPause(pauseMode);
    }

    public void stop() {
        if (raw == null) return;
        raw.mediaPlayer().controls().stop();
    }

    /**
     * Check if any async task was active<br>
     * method {@link #release()} is also async, but it deletes raw player,
     * basically makes player instance useless.
     * @return true if any async task was active
     */
    public boolean isSafeUse() { return started; }

    public boolean isBuffering() {
        if (raw == null) return false;
        return raw.mediaPlayer().status().state().equals(State.BUFFERING);
    }
    public boolean isReady() {
        if (raw == null) return false;
        return raw.mediaPlayer().status().isPlayable();
    }
    public boolean isPaused() {
        if (raw == null) return false;
        return raw.mediaPlayer().status().state().equals(State.PAUSED);
    }
    public boolean isStopped() {
        if (raw == null) return false;
        return raw.mediaPlayer().status().state().equals(State.STOPPED);
    }
    public boolean isEnded() {
        if (raw == null) return false;
        return raw.mediaPlayer().status().state().equals(State.ENDED);
    }
    public boolean isMuted() {
        if (raw == null) return false;
        return raw.mediaPlayer().audio().isMute();
    }
    public boolean isBroken() {
        if (raw == null) return true;
        return raw.mediaPlayer().status().state().equals(State.ERROR);
    }

    public boolean isValid() {
        if (raw == null) return false;
        return raw.mediaPlayer().media().isValid();
    }
    public boolean isPlaying() {
        if (raw == null) return false;
        return raw.mediaPlayer().status().isPlaying();
    }

    /**
     * Method is currently incomplete
     * it cannot be distingué if was a stream after get media information
     * that is supplied with our API but isn't enough, because another type of streams cannot be handled
     * @return if mrl was a livestream
     */
    public boolean isLive() {
        if (live) return true;

        if (url.endsWith(".m3u8") || url.endsWith(".m3u")) {
            if (getMediaInfoDuration() == -1) return true;
            if (getTime() > getDuration()) return true;
        }

        InfoApi info = raw.mediaPlayer().media().info();
        if (info != null) {
            return info.type().equals(MediaType.STREAM);
        }

        return false;
    }

    public boolean isSeekAble() {
        if (raw == null) return false;
        return raw.mediaPlayer().status().isSeekable();
    }

    public void seekTo(long time) {
        if (raw == null) return;
        raw.mediaPlayer().controls().setTime(time);
    }

    public void seekFastTo(long ticks) {
        if (raw == null) return;
        raw.mediaPlayer().controls().setTime(ticks);
    }

    public void foward() {
        if (raw == null) return;
        raw.mediaPlayer().controls().skipTime(5000L);
    }

    public void rewind() {
        if (raw == null) return;
        raw.mediaPlayer().controls().skipTime(-5000L);
    }

    public void setSpeed(float rate) {
        if (raw == null) return;
        raw.mediaPlayer().controls().setRate(rate);
    }

    public int getVolume() {
        if (raw == null) return 0;
        return raw.mediaPlayer().audio().volume();
    }

    public void setVolume(int volume) {
        if (raw == null) return;
        raw.mediaPlayer().audio().setVolume(volume);

        if (volume == 0 && !raw.mediaPlayer().audio().isMute()) raw.mediaPlayer().audio().setMute(true);
        else if (volume > 0 && raw.mediaPlayer().audio().isMute()) raw.mediaPlayer().audio().setMute(false);
    }

    public void mute() {
        if (raw == null) return;
        raw.mediaPlayer().audio().setMute(true);
    }

    public void unmute() {
        if (raw == null) return;
        raw.mediaPlayer().audio().setMute(false);
    }

    public void setMuteMode(boolean mode) {
        if (raw == null) return;
        raw.mediaPlayer().audio().setMute(mode);
    }

    /**
     * Equals to <pre>player.mediaPlayer().status().length()</pre>
     * @return Player duration
     */
    public long getDuration() {
        if (raw == null) return 0L;
        if (!isValid() || (RuntimeUtil.isNix() && getRawPlayerState().equals(State.STOPPED))) return 0L;
        return raw.mediaPlayer().status().length();
    }

    public long getMediaInfoDuration() {
        if (raw == null) return 0L;
        InfoApi info = raw.mediaPlayer().media().info();
        if (info != null) return info.duration();
        return 0L;
    }

    public long getTime() {
        if (raw == null) return 0L;
        return raw.mediaPlayer().status().time();
    }

    public boolean getRepeatMode() {
        if (raw == null) return false;
        return raw.mediaPlayer().controls().getRepeat();
    }

    public void setRepeatMode(boolean repeatMode) {
        if (raw == null) return;
        raw.mediaPlayer().controls().setRepeat(repeatMode);
    }

    public void release() {
        if (raw == null) return;
        ThreadCore.thread(3, () -> {
            while (!started); // WAIT FOR PLAYER START WAS FINISHED

            synchronized (this) {
                CallbackMediaPlayerComponent rawRef = raw;
                raw = null;

                if (rawRef == null) return; // If for some reason is triggered 2 times.
                rawRef.mediaPlayer().release();
            }
        });
    }

    protected static final class WaterMediaPlayerEventListener extends EmbededMediaPlayerEventListener {
        @Override
        public void buffering(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer, float newCache) {
            if (newCache >= 100) mediaPlayer.submit(() -> {
                mediaPlayer.audio().enforceVolume();

                int volume = mediaPlayer.audio().masterVolume();
                if (volume == 0 && !mediaPlayer.audio().isMute()) mediaPlayer.audio().setMute(true);
                else if (volume > 0 && mediaPlayer.audio().isMute()) mediaPlayer.audio().setMute(false);
            });
        }

        @Override
        public void playing(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
            mediaPlayer.submit(() -> {
                mediaPlayer.audio().enforceVolume();

                int volume = mediaPlayer.audio().masterVolume();
                if (volume == 0 && !mediaPlayer.audio().isMute()) mediaPlayer.audio().setMute(true);
                else if (volume > 0 && mediaPlayer.audio().isMute()) mediaPlayer.audio().setMute(false);
            });
        }

        @Override
        public void paused(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
            mediaPlayer.submit(() -> {
                mediaPlayer.audio().enforceVolume();

                int volume = mediaPlayer.audio().masterVolume();
                if (volume == 0 && !mediaPlayer.audio().isMute()) mediaPlayer.audio().setMute(true);
                else if (volume > 0 && mediaPlayer.audio().isMute()) mediaPlayer.audio().setMute(false);
            });
        }

        @Override
        public void mediaPlayerReady(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
            mediaPlayer.submit(() -> {
                mediaPlayer.audio().enforceVolume();

                int volume = mediaPlayer.audio().masterVolume();
                if (volume == 0 && !mediaPlayer.audio().isMute()) mediaPlayer.audio().setMute(true);
                else if (volume > 0 && mediaPlayer.audio().isMute()) mediaPlayer.audio().setMute(false);
            });
        }
    }
}