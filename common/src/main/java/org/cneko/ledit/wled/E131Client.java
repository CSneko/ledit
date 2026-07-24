package org.cneko.ledit.wled;

import org.cneko.ledit.config.LedItConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Sends per-LED color data via E1.31 (sACN) over UDP.
 * <p>
 * Implements the ANSI E1.31-2018 protocol. Each universe carries a full 512-channel
 * DMX frame (638 bytes total per packet). LEDs beyond the configured count are
 * zero-filled. Multi-universe spanning is automatic for >170 LEDs.
 */
public final class E131Client {
    private static final Logger LOGGER = LoggerFactory.getLogger("ledit");

    // --- Protocol Constants ---
    private static final int DMX_CHANNELS = 512;
    private static final int LEDS_PER_UNIVERSE = DMX_CHANNELS / 3; // 170
    private static final int E131_DEFAULT_PORT = 5568;

    /** ACN Packet Identifier "ASC-E1.17" + 3 null bytes */
    private static final byte[] ACN_PACKET_ID = {
            'A', 'S', 'C', '-', 'E', '1', '.', '1', '7', 0, 0, 0
    };

    // --- Layer sizes (bytes) ---
    // Root: preamble(4) + acnId(12) + flags(2) + vector(4) + cid(16) = 38
    private static final int ROOT_PREAMBLE_SIZE = 16; // offset of root PDU
    private static final int ROOT_BODY_SIZE = 2 + 4 + 16;       // 22
    static final int ROOT_LAYER_SIZE = ROOT_PREAMBLE_SIZE + ROOT_BODY_SIZE; // 38

    // Framing: flags(2) + vector(4) + srcName(64) + prio(1) + sync(2) + seq(1) + opts(1) + univ(2) = 77
    private static final int FRAMING_BODY_START = ROOT_LAYER_SIZE; // 38
    static final int FRAMING_LAYER_SIZE = 2 + 4 + 64 + 1 + 2 + 1 + 1 + 2; // 77

    // DMP header: flags(2) + vector(1) + type(1) + firstAddr(2) + incr(2) + count(2) = 10
    private static final int DMP_HEADER_START = FRAMING_BODY_START + FRAMING_LAYER_SIZE; // 115
    private static final int DMP_HEADER_SIZE = 2 + 1 + 1 + 2 + 2 + 2; // 10

    // DMX data: startCode(1) + 512 channels = 513
    private static final int DMX_DATA_START = DMP_HEADER_START + DMP_HEADER_SIZE; // 125
    private static final int DMX_DATA_SIZE = 1 + DMX_CHANNELS;       // 513
    private static final int DMP_LAYER_SIZE = DMP_HEADER_SIZE + DMX_DATA_SIZE; // 523

    /** Full E1.31 packet: 38 + 77 + 523 = 638 bytes */
    static final int PACKET_SIZE = ROOT_LAYER_SIZE + FRAMING_LAYER_SIZE + DMP_LAYER_SIZE;

    // --- Instance state ---
    private final SecureRandom random = new SecureRandom();
    private final byte[] cid = new byte[16];
    private int sequenceNumber;
    private boolean firstSend = true;
    private DatagramSocket socket;

    public E131Client() {
        random.nextBytes(cid);
    }

    /**
     * Send per-LED colors via E1.31. Universes are auto-split for >170 LEDs.
     */
    public void sendColors(int[][] colors, int brightness) {
        int ledCount = colors.length;
        int universeCount = (ledCount + LEDS_PER_UNIVERSE - 1) / LEDS_PER_UNIVERSE;

        try {
            ensureSocket();
            InetAddress addr = InetAddress.getByName(LedItConfig.wledAddress);
            int port = LedItConfig.e131Port > 0 ? LedItConfig.e131Port : E131_DEFAULT_PORT;

            for (int u = 0; u < universeCount; u++) {
                int startLed = u * LEDS_PER_UNIVERSE;
                int endLed = Math.min(startLed + LEDS_PER_UNIVERSE, ledCount);
                int numLeds = endLed - startLed;
                int universe = LedItConfig.e131Universe + u;

                byte[] packet = buildPacket(colors, startLed, numLeds, brightness, universe);
                socket.send(new DatagramPacket(packet, PACKET_SIZE, addr, port));
            }

            if (firstSend) {
                firstSend = false;
                LOGGER.info("E1.31 streaming to {}:{}, {} LED(s) across {} universe(s)",
                        LedItConfig.wledAddress, port, ledCount, universeCount);
            }
        } catch (Exception e) {
            closeSocket();
            LOGGER.warn("E1.31 send error (will retry): {}", e.toString());
        }
    }

    /**
     * Build a complete ANSI E1.31-2018 sACN data packet.
     * <p>
     * Packet structure (638 bytes total):
     * <pre>
     *   Root    ( 0– 37):  Preamble + ACN ID + Flags+Len(622) + Vector(4) + CID(16)
     *   Framing (38–114):  Flags+Len(600) + Vector(2) + SrcName(64) + Prio + Sync + Seq + Opt + Univ
     *   DMP     (115–637): Flags+Len(523) + Vector(0x02) + Type(0xA1) + Addr + Incr + Count(513) + StartCode(0) + 512 DMX
     * </pre>
     * The Flags+Length value in each layer is the PDU length from <b>that flags field</b>
     * through the end of the entire packet (nested/cumulative).
     */
    byte[] buildPacket(int[][] colors, int startLed, int numLeds, int brightness, int universe) {
        float briScale = brightness / 255f;
        ByteBuffer buf = ByteBuffer.allocate(PACKET_SIZE);
        buf.order(ByteOrder.BIG_ENDIAN);

        // ===== Root Layer (38 bytes, offset 0) =====
        buf.putShort((short) 0x0010);                                // [  0– 1] Preamble Size
        buf.putShort((short) 0x0000);                                // [  2– 3] Post-amble Size
        buf.put(ACN_PACKET_ID);                                      // [  4–15] "ASC-E1.17\0\0\0"

        int rootPduLen = PACKET_SIZE - ROOT_PREAMBLE_SIZE;           // 622 = everything from [16] to end
        buf.putShort((short) (0x7000 | rootPduLen));                 // [ 16–17] Flags(0x7) + Length
        buf.putInt(0x00000004);                                      // [ 18–21] Root Vector
        buf.put(cid);                                                // [ 22–37] CID (16 bytes)

        // ===== Framing Layer (77 bytes, offset 38) =====
        int framePduLen = PACKET_SIZE - FRAMING_BODY_START;          // 600 = everything from [38] to end
        buf.putShort((short) (0x7000 | framePduLen));                // [ 38–39] Flags(0x7) + Length
        buf.putInt(0x00000002);                                      // [ 40–43] Framing Vector

        // Source Name (64 bytes, null-terminated UTF-8)
        byte[] srcName = new byte[64];
        byte[] nameBytes = "LEDIt".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, srcName, 0, Math.min(nameBytes.length, 63));
        buf.put(srcName);                                            // [ 44–107]

        buf.put((byte) 100);                                         // [108]    Priority
        buf.putShort((short) 0x0000);                                // [109–110] Sync Address (unused)
        buf.put((byte) (sequenceNumber & 0xFF));                    // [111]    Sequence Number
        buf.put((byte) 0x00);                                        // [112]    Options
        buf.putShort((short) universe);                              // [113–114] Universe
        sequenceNumber = (sequenceNumber + 1) & 0xFF;

        // ===== DMP Layer (523 bytes, offset 115) =====
        int dmpPduLen = PACKET_SIZE - DMP_HEADER_START;              // 523 = everything from [115] to end
        buf.putShort((short) (0x7000 | dmpPduLen));                  // [115–116] Flags(0x7) + Length
        buf.put((byte) 0x02);                                        // [117]    DMP Vector (1 byte!)
        buf.put((byte) 0xA1);                                        // [118]    Address & Data Type
        buf.putShort((short) 0x0000);                                // [119–120] First Property Address
        buf.putShort((short) 0x0001);                                // [121–122] Address Increment
        buf.putShort((short) (1 + DMX_CHANNELS));                    // [123–124] Property Count = 513 (start code + 512 slots)
        buf.put((byte) 0x00);                                        // [125]    DMX Start Code

        // ===== DMX Data (512 bytes, offset 126) =====
        for (int i = 0; i < numLeds; i++) {
            int[] rgb = colors[startLed + i];
            buf.put((byte) Math.round(rgb[0] * briScale));
            buf.put((byte) Math.round(rgb[1] * briScale));
            buf.put((byte) Math.round(rgb[2] * briScale));
        }
        // Zero-fill remaining channels
        int bytesWritten = numLeds * 3;
        for (int i = bytesWritten; i < DMX_CHANNELS; i++) {
            buf.put((byte) 0x00);
        }

        return buf.array();
    }

    private void ensureSocket() throws Exception {
        if (socket == null || socket.isClosed()) {
            socket = new DatagramSocket();
            socket.setReuseAddress(true);
        }
    }

    private void closeSocket() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        socket = null;
    }
}
