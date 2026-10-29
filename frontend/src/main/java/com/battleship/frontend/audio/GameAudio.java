package com.battleship.frontend.audio;

import com.battleship.frontend.settings.ClientPreferences;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import java.io.BufferedInputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import javax.sound.sampled.*;

/** Java Sound runs entirely off the render thread; works independently of the OpenGL renderer. */
public final class GameAudio implements AutoCloseable {
    public enum Cue {
        CLICK("click"), PLACE("place"), READY("ready"), CANNON("cannon"), HIT("hit"), MISS("splash"),
        SUNK("sunk"), VICTORY("victory"), DEFEAT("defeat"), BACK("ui_back"), OPEN("ui_panel_open"),
        CLOSE("ui_panel_close"), ERROR("ui_error"), HOVER("ui_hover"), ROTATE("ship_rotate"),
        REMOVE("ship_remove"), RANDOM("fleet_random"), MATCH_START("match_start"),
        YOUR_TURN("turn_yours"), ENEMY_TURN("turn_enemy"), TARGET("target_select"),
        TIMER_WARNING("timer_warning"), TIMEOUT("timer_timeout"), CHAT("chat_receive"),
        INVITE("invite_receive"), JOIN("room_join"), LEAVE("room_leave"),
        DISCONNECTED("connection_lost"), REMATCH("rematch_request");
        final String file;
        Cue(String file) { this.file=file; }
    }
    public enum Music { MENU("bgm_menu"), PLACEMENT("bgm_placement"), BATTLE("bgm_battle"), SILENT(null);
        final String file;
        Music(String file) { this.file = file; }
    }
    private record Sample(AudioFormat format,byte[] pcm) { }
    private static final Logger LOG=Logger.getLogger(GameAudio.class.getName());
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofVirtual().name("game-audio").factory());
    private final Map<Cue,Sample> samples=new EnumMap<>(Cue.class);
    private final Map<Cue,Clip> clips=new EnumMap<>(Cue.class);
    private final Map<Cue,Float> clipVolumes=new EnumMap<>(Cue.class);
    private final CompletableFuture<Void> loaded=new CompletableFuture<>();
    private final AudioLoop musicLoop, oceanLoop, windLoop;
    private volatile Music music = Music.SILENT;
    private final Map<Cue,Long> lastPlayed = new EnumMap<>(Cue.class);
    private volatile float volume=.75f;
    private volatile boolean muted,closed,available;
    private long lastClick;
    public GameAudio(AssetManager assets) {
        musicLoop = new AudioLoop(assets, () -> muted || closed ? 0 : volume * .22f, "music");
        oceanLoop = new AudioLoop(assets, () -> muted || closed ? 0 : volume * .10f, "ocean");
        windLoop = new AudioLoop(assets, () -> muted || closed ? 0 : volume * .025f, "wind");
        worker.execute(() -> {
            try {
                for(Cue cue:Cue.values()) {
                    var asset=assets.locateAsset(new AssetKey<>("sounds/"+cue.file+".wav"));
                    if(asset==null) throw new IllegalStateException("Missing sound "+cue.file);
                    try(var stream=AudioSystem.getAudioInputStream(new BufferedInputStream(asset.openStream()))) {
                        samples.put(cue,new Sample(stream.getFormat(),stream.readAllBytes()));
                    }
                }
                reopen(); loaded.complete(null);
            } catch(Exception e) { LOG.warning("Sound effects unavailable: "+e.getMessage()); loaded.completeExceptionally(e); }
        });
    }
    public CompletableFuture<Void> loaded() { return loaded; }
    public boolean available() { return available; }
    /** Idempotent per-frame selection. Settings overlays retain the game's current score. */
    public void scene(Music next, boolean sea) {
        if (closed) return;
        Music previous = music;
        music = next;
        if (previous != next && (next == Music.MENU || next == Music.PLACEMENT)) {
            worker.execute(() -> {
                for (Cue cue : new Cue[]{Cue.CANNON, Cue.HIT, Cue.MISS, Cue.SUNK, Cue.VICTORY,
                        Cue.DEFEAT, Cue.MATCH_START, Cue.YOUR_TURN, Cue.ENEMY_TURN,
                        Cue.TIMER_WARNING, Cue.TIMEOUT}) {
                    Clip clip = clips.get(cue);
                    if (clip != null) clip.stop();
                }
            });
        }
        musicLoop.select(next.file);
        oceanLoop.select(sea ? "amb_ocean" : null);
        windLoop.select(sea ? "amb_wind" : null);
    }
    public Music music() { return music; }
    public void configure(ClientPreferences settings) {
        boolean gainChanged=Math.abs(volume-settings.volume())>.0001f;
        volume=settings.volume(); muted=settings.muted();
        musicLoop.wake(); oceanLoop.wake(); windLoop.wake();
        if(closed) return;
        worker.execute(() -> {
            if(muted) clips.values().forEach(Clip::stop);
            else if(gainChanged) {
                // Only running effects need an immediate gain change. Idle clips rebuild lazily.
                for(Cue cue:new ArrayList<>(clips.keySet())) {
                    Clip old=clips.get(cue);
                    if(old.isRunning()) {
                        int frame=old.getFramePosition();
                        Clip next=clipFor(cue);
                        if(next!=null) { next.setFramePosition(Math.min(frame,next.getFrameLength()-1)); next.start(); }
                    }
                }
            }
        });
    }
    public void play(Cue cue) {
        if(closed||muted||volume<=0) return;
        worker.execute(() -> {
            if(closed||muted||volume<=0) return;
            long now=System.nanoTime();
            if(cue==Cue.CLICK && now-lastClick<35_000_000L) return;
            if(cue==Cue.CLICK) lastClick=now;
            long cooldown = cue == Cue.CHAT || cue == Cue.INVITE ? 700_000_000L
                    : cue == Cue.ERROR || cue == Cue.TIMER_WARNING ? 250_000_000L : 50_000_000L;
            Long previous = lastPlayed.get(cue);
            if (previous != null && now - previous < cooldown) return;
            lastPlayed.put(cue, now);
            retirePreviousCue(cue);
            Clip clip=clipFor(cue);
            if(clip!=null) { clip.stop(); clip.setFramePosition(0); clip.start(); }
        });
    }
    private static boolean combat(Cue cue) {
        return cue==Cue.CANNON || cue==Cue.HIT || cue==Cue.MISS || cue==Cue.SUNK;
    }
    private void retirePreviousCue(Cue next) {
        // Imported combat samples include long tails; never carry them into the next impact/result.
        boolean result=next==Cue.VICTORY || next==Cue.DEFEAT;
        if(!combat(next) && !result && next!=Cue.TIMEOUT) return;
        for(var entry:clips.entrySet()) {
            Cue previous=entry.getKey();
            if(combat(previous) || result && (previous==Cue.VICTORY || previous==Cue.DEFEAT
                    || previous==Cue.TIMEOUT || previous==Cue.YOUR_TURN || previous==Cue.ENEMY_TURN
                    || previous==Cue.TIMER_WARNING)) entry.getValue().stop();
        }
    }
    private void reopen() {
        clips.values().forEach(clip->{clip.stop();clip.close();}); clips.clear();
        clipVolumes.clear();
        if(closed) return;
        for(Cue cue:samples.keySet()) clipFor(cue);
        available=!clips.isEmpty();
        if(!available) LOG.warning("No supported audio output device. Game continues without sound.");
    }
    private Clip clipFor(Cue cue) {
        Clip existing=clips.get(cue);
        if(existing!=null && Objects.equals(clipVolumes.get(cue),volume)) return existing;
        if(existing!=null) { existing.stop(); existing.close(); clips.remove(cue); }
        Clip clip=null;
        try {
                float balance = switch (cue) {
                    case CANNON, HIT, MISS, SUNK -> .85f;
                    case VICTORY, DEFEAT, MATCH_START -> .75f;
                    default -> .5f;
                };
                clip=AudioSystem.getClip(); Sample sample=samples.get(cue);
                if(sample==null) { clip.close(); return null; }
                byte[] data=scaledPcm(sample.pcm,volume * balance);
                clip.open(sample.format,data,0,data.length); clips.put(cue,clip); clipVolumes.put(cue,volume);
                return clip;
            } catch(Exception e) {
                if(clip!=null) clip.close();
                LOG.fine("No audio output for "+cue+": "+e.getMessage());
                return null;
            }
    }
    static byte[] scaledPcm(byte[] original,float volume) {
        byte[] result=new byte[original.length];
        for(int i=0;i+1<original.length;i+=2) {
            int sample=(short)((original[i]&255)|(original[i+1]<<8));
            int adjusted=Math.round(sample*volume); result[i]=(byte)adjusted; result[i+1]=(byte)(adjusted>>8);
        }
        return result;
    }
    public CompletableFuture<Integer> playingVoices() {
        return CompletableFuture.supplyAsync(()->(int)clips.values().stream().filter(Clip::isRunning).count()
                + musicLoop.playing() + oceanLoop.playing() + windLoop.playing(),worker);
    }
    int loopingVoices() { return musicLoop.playing() + oceanLoop.playing() + windLoop.playing(); }
    @Override public void close() {
        closed=true;
        musicLoop.close(); oceanLoop.close(); windLoop.close();
        worker.execute(()->{clips.values().forEach(clip->{clip.stop();clip.close();});clips.clear();});
        worker.shutdown();
    }
}
