/*
MIT License

Copyright (c) 2020 Who Write Code

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/


package voiceClient;

import Utils.Utils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 *
 * @author gg
 */
public class AudioChannel extends Thread{
    private long chId; //an id unique for each user. generated by IP and port
    private ArrayList<VoiceMessage> queue = new ArrayList<VoiceMessage>(); //queue of messages to be played
    private int lastSoundPacketLen = SoundPacket.defaultDataLenght;
    private long lastPacketTime = System.nanoTime();

    public boolean canKill() { //returns true if it's been a long time since last received packet
        if (System.nanoTime() - lastPacketTime > 5000000000L) {
            return true; //5 seconds with no data
        } else {
            return false;
        }
    }

    public void closeAndKill() {
        if (speaker != null) {
            speaker.close();
        }
        stop();
    }

    public AudioChannel(long chId) {
        this.chId = chId;
    }

    public long getChId() {
        return chId;
    }

    public void addToQueue(VoiceMessage m) { //adds a message to the play queue
        queue.add(m);
    }
    private SourceDataLine speaker = null; //speaker

    @Override
    public void run() {
        try {
            //open channel to sound card
            AudioFormat af = SoundPacket.defaultFormat;
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, af);
            speaker = (SourceDataLine) AudioSystem.getLine(info);
            speaker.open(af);
            speaker.start();
            //sound card ready
            for (;;) { //this infinite cycle checks for new packets to be played in the queue, and plays them. to avoid busy wait, a sleep(10) is executed at the beginning of each iteration
                if (queue.isEmpty()) { //nothing to play, wait
                    Utils.sleep(10);
                    continue;
                } else {//we got something to play
                    lastPacketTime = System.nanoTime();
                    VoiceMessage in = queue.get(0);
                    queue.remove(in);
                    if (in.getData() instanceof SoundPacket) { //it's a sound packet, send it to sound card
                        SoundPacket m = (SoundPacket) (in.getData());
                        if (m.getData() == null) {//sender skipped a packet, play comfort noise
                            byte[] noise = new byte[lastSoundPacketLen];
                            for (int i = 0; i < noise.length; i++) {
                                noise[i] = (byte) ((Math.random() * 3) - 1);
                            }
                            speaker.write(noise, 0, noise.length);
                        } else {
                            //decompress data
                            GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(m.getData()));
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            for (;;) {
                                int b = gis.read();
                                if (b == -1) {
                                    break;
                                } else {
                                    baos.write((byte) b);
                                }
                            }
                            //play decompressed data
                            byte[] toPlay=baos.toByteArray();
                            speaker.write(toPlay, 0, toPlay.length);
                            lastSoundPacketLen = m.getData().length;
                        }
                    } else { //not a sound packet, trash
                        continue; //invalid message
                    }
                }
            }
        } catch (Exception e) { //sound card error or connection error, stop
            System.out.println("receiverThread " + chId + " error: " + e.toString());
            if (speaker != null) {
                speaker.close();
            }
            stop();
        }
    }    
}
