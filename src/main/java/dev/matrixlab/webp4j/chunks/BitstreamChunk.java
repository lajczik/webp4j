package xyz.lychee.discordbot.webp;

public class BitstreamChunk extends RawDataChunk {

    public BitstreamChunk(byte[] fourCC, byte[] data) {
        super(fourCC, data);
    }
}
