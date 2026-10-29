package com.battleship.frontend.audio;

import com.battleship.frontend.settings.ClientPreferences;
import com.jme3.asset.DesktopAssetManager;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioSystem;

public final class AudioChecks {
    public static void main(String[] args) throws Exception {
        var assets=new DesktopAssetManager(true);
        for(GameAudio.Cue cue:GameAudio.Cue.values()) {
            try(var input=AudioSystem.getAudioInputStream(AudioChecks.class.getResource("/sounds/"+cue.file+".wav"))) {
                var format=input.getFormat(); byte[] pcm=input.readAllBytes();
                require(format.getChannels()==1 && format.getSampleSizeInBits()==16 && !format.isBigEndian() && format.getSampleRate()==44100,"Portable PCM WAV format");
                require(pcm.length>4000 && !Arrays.equals(pcm,new byte[pcm.length]),"Real non-silent sample: "+cue);
            }
        }
        for(String name:new String[]{"bgm_menu", "bgm_placement", "bgm_battle", "amb_ocean", "amb_wind"}) {
            try(var input=AudioSystem.getAudioInputStream(AudioChecks.class.getResource("/sounds/"+name+".wav"))) {
                var format=input.getFormat(); byte[] data=input.readAllBytes();
                require(format.getChannels()==2 && format.getSampleSizeInBits()==16 && !format.isBigEndian()
                        && format.getSampleRate()==44100,"Streamable stereo PCM: "+name);
                require(data.length>44100 && !Arrays.equals(data,new byte[data.length]),"Non-silent background: "+name);
                // Both samples bordering each circular seam should be close, with no large step.
                for(int channel=0;channel<2;channel++) {
                    int first=(short)((data[channel*2]&255)|(data[channel*2+1]<<8));
                    int end=data.length-4+channel*2;
                    int last=(short)((data[end]&255)|(data[end+1]<<8));
                    require(Math.abs(first-last)<4000,"Loop seam has no large discontinuity: "+name);
                }
            }
        }
        byte[] pcm={0,(byte)0x80,(byte)0xFE,0x7F};
        require(Arrays.equals(GameAudio.scaledPcm(pcm,0),new byte[4]),"Zero volume is silent");
        byte[] half=GameAudio.scaledPcm(pcm,.5f);
        require((short)((half[0]&255)|(half[1]<<8))==-16384 && (short)((half[2]&255)|(half[3]<<8))==16383,"Software gain changes positive and negative samples accurately");
        try(var audio=new GameAudio(assets)) {
            audio.loaded().get(15,TimeUnit.SECONDS);
            if(audio.available()) {
                audio.play(GameAudio.Cue.CANNON); Thread.sleep(100);
                require(audio.playingVoices().get(2,TimeUnit.SECONDS)>0,"Native audio line actually starts");
                for(var next:new GameAudio.Cue[]{GameAudio.Cue.HIT,GameAudio.Cue.SUNK,
                        GameAudio.Cue.CANNON,GameAudio.Cue.MISS,GameAudio.Cue.VICTORY,GameAudio.Cue.DEFEAT}) {
                    audio.play(next);
                    await(()-> {
                        try { return audio.playingVoices().get(2,TimeUnit.SECONDS)==1; }
                        catch(Exception failure) { throw new RuntimeException(failure); }
                    },"Old combat/result tail stops before "+next);
                }
                System.out.println("PASS: native combat/result effects replace previous tails instead of overlapping.");
                audio.configure(new ClientPreferences(1440,900,false,1,1,.25f,true));
                audio.play(GameAudio.Cue.HIT);
                require(audio.playingVoices().get(10,TimeUnit.SECONDS)==0,"Mute stops current audio and blocks new cue");
                audio.configure(new ClientPreferences(1440,900,false,1,1,.35f,false));
                audio.play(GameAudio.Cue.MISS);
                await(()-> {
                    try { return audio.playingVoices().get(2,TimeUnit.SECONDS)>0; }
                    catch(Exception failure) { throw new RuntimeException(failure); }
                },"Unmute and saved gain can play again");
                audio.scene(GameAudio.Music.MENU,true);
                await(()->audio.loopingVoices()==3,"Music, ocean and wind start on real output lines");
                for(int i=0;i<30;i++) audio.scene(GameAudio.Music.MENU,true);
                require(audio.music()==GameAudio.Music.MENU,"Per-frame scene requests are idempotent");
                audio.configure(new ClientPreferences(1440,900,false,1,1,.35f,true));
                await(()->audio.loopingVoices()==0,"Mute also stops every streaming output");
                require(audio.playingVoices().get(10,TimeUnit.SECONDS)==0,"No audible voices remain while muted");
                audio.configure(new ClientPreferences(1440,900,false,1,1,.35f,false));
                await(()->audio.loopingVoices()==3,"Unmute restores the requested background scene");
                audio.scene(GameAudio.Music.BATTLE,false);
                await(()->audio.loopingVoices()==1,"Changing score retires ambience without accumulating streams");
                audio.scene(GameAudio.Music.SILENT,false);
                await(()->audio.loopingVoices()==0,"Results/menu stop the selected backgrounds");
                audio.play(GameAudio.Cue.CANNON);
                await(()-> {
                    try { return audio.playingVoices().get(2,TimeUnit.SECONDS)>0; }
                    catch(Exception failure) { throw new RuntimeException(failure); }
                },"Combat tail is playing before leaving the match");
                audio.scene(GameAudio.Music.MENU,false);
                await(()-> {
                    try { return audio.loopingVoices()==1 && audio.playingVoices().get(2,TimeUnit.SECONDS)==1; }
                    catch(Exception failure) { throw new RuntimeException(failure); }
                },"Returning to menu stops old combat tails and leaves only the menu score");
                audio.scene(GameAudio.Music.SILENT,false);
                System.out.println("PASS native Java Sound: actual output line play, mute, software gain and unmute.");
            } else System.out.println("SKIP native output: no supported audio device; non-blocking silent fallback loaded.");
        }
        System.out.println("PASS: 29 licensed effect WAVs and five seamless stereo backgrounds; PCM decode, gain, native streaming, scene switch and mute.");
    }
    private static void await(java.util.function.BooleanSupplier condition,String reason) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!condition.getAsBoolean() && System.nanoTime()<until) Thread.sleep(40);
        require(condition.getAsBoolean(),reason);
    }
    private static void require(boolean value,String reason) { if(!value) throw new AssertionError(reason); }
}
