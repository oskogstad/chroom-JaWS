package jaws;

import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;
import java.io.DataInputStream;
import java.io.IOException;


class Frame {

    final OpCode opcode;

    final String message;

    final byte[] messageBytes;

    final int messageLength;

    final byte[] frameBytes;

    private static final Charset utf8 = StandardCharsets.UTF_8;

    final byte[] mask;

    final boolean fin;

    final static byte[] PING_FRAME;
    static {
        PING_FRAME = new byte[3];
        PING_FRAME[0] = (byte)0x89; // Set fin flag (0x80) and opcode PING (0x0A)
        PING_FRAME[1] = (byte)0x01; // Set mask bit to 0 and payload length to 1
        PING_FRAME[2] = (byte)'!';  // Set dummy payload to '!'
    }

    Frame(String message) {
        this.message = message;
        this.mask = null;
        this.fin = true;

        opcode = OpCode.TEXT;
        messageBytes = message.getBytes(utf8);
        messageLength = messageBytes.length;

        this.frameBytes = pack(messageBytes, this.opcode.code, null);
    }

    Frame(DataInputStream input) throws IOException {
            byte[] header = new byte[2];
            input.readFully(header);
            this.fin = ((byte)header[0]&0x80) != 0;
            boolean maskBit = (((byte)header[1])&0x80) != 0;
            int op = (byte)header[0]&0x0F;
            long payloadLen = (byte)(header[1]&0x7F);

            this.opcode = OpCode.getOpcode(op);
            //TODO: Handle opcode = null

            int numPayloadbytes = 0;
            Logger.log("byte1 payload length: "+payloadLen, Logger.WS_PARSE);
            if (payloadLen == 126) {
                numPayloadbytes = 2;
            }
            else if (payloadLen == 127) {
                numPayloadbytes = 8;
            }

            if (numPayloadbytes > 0) {
                byte[] b = new byte[numPayloadbytes];
                input.readFully(b);

                Logger.log("Constructing complex payload length...", Logger.WS_PARSE);

                // Reverse the array
                for(int i=0; i<b.length/2; i++) {
                    byte temp = b[i];
                    b[i] = b[b.length - i - 1];
                    b[b.length - i - 1] = temp;
                }

                payloadLen = 0; // Reset payloadlen
                for(int i=0; i<b.length; i++) {
                    Logger.log("\tbyte "+i+" = 0x"+String.format("%02x", b[i]), Logger.WS_PARSE);
                    Logger.log("Test1: "+(b[i]&0xFF)+", Test2: "+(8*i)+", Test3: "+((b[i]&0xFF) << (8 * i)), Logger.WS_PARSE);
                    payloadLen |= ((b[i]&0xFF) << (8 * i));
                }
            }
            Logger.log("Message length: "+payloadLen, Logger.WS_PARSE);

            if (maskBit) {
                this.mask = new byte[4];
                input.readFully(mask);
            }
            else {
                this.mask = null;
            }

            // This will fail for messages of size bigger than int max val.
            byte[] payload = new byte[(int)payloadLen];
            input.readFully(payload);

            if (maskBit) {
                this.message = decode(payload, this.mask);
            }
            else {
                this.message = new String(payload, utf8);
            }

            this.messageBytes = this.message.getBytes(utf8);
            this.messageLength = messageBytes.length;

            this.frameBytes = pack(messageBytes, op, this.mask);
    }

    private String decode(byte[] payload, byte[] mask) {
       byte[] decoded = new byte[payload.length];
       for (int i=0; i<payload.length; i++) {
           decoded[i] = (byte)((int)payload[i] ^ (int)mask[i%4]);
       }
       return new String(decoded, utf8);
    }

    private static byte[] pack(byte[] messageBytes, int op, byte[] mask) {
        /*
         * 0 for single byte length,
         * 1 for 2 bytes,
         * and 2 for 8 bytes of paylod length
         */
        int length = 0;

        int messageLen = messageBytes.length;
        int framelength = 2+messageLen;

        if (messageLen > 65535) {
            length = 2;
            framelength += 8;
        }
        else if (messageLen > 125) {
            length = 1;
            framelength += 2;
        }

        byte[] bytes = new byte[framelength];
        int pointer = 2; // This will tell us what index in the array we will begin writing the message

        // Writing payload length
        Logger.log("---PACKING PAYLOAD LENGTH "+messageLen+"---", Logger.WS_PARSE);
        bytes[0] = (byte)0x81; // 0x80 is the fin flag, 0x01 is opcode TEXT
        switch(length) {
            case 0:
                bytes[1] = (byte)messageLen;

                Logger.log("byte1: "+(bytes[1]&0xFF), Logger.WS_PARSE);

                pointer = 2;
                break;
            case 1:
                bytes[1] = (byte)126;
                bytes[2] = (byte)(messageLen>>8);
                bytes[3] = (byte)(messageLen&0xFF);

                for (int i=1; i<4; i++) {
                    Logger.log("byte"+i+": "+(bytes[i]&0xFF), Logger.WS_PARSE);
                }

                pointer = 4;
                break;
            case 2: // Due to limitations in java, we cannot fully support 8 bytes of payload length
                bytes[1] = (byte)127;
                bytes[9] = (byte)(messageLen&0xFF);
                bytes[8] = (byte)((messageLen&0xFF00) >> 8);
                bytes[7] = (byte)((messageLen&0xFF0000) >> 16);
                bytes[6] = (byte)((messageLen&0xFF000000) >> 24);

                for (int i=1; i<10; i++) {
                    Logger.log("byte"+i+": "+(bytes[i]&0xFF), Logger.WS_PARSE);
                }

                pointer = 10;
                break;
            default:
                break;
        }

        Logger.log("---END---", Logger.WS_PARSE);

        // Writing the message as payload data
        for (int i=0; i<messageLen; i++) {
            bytes[pointer+i] = messageBytes[i];
        }

        return bytes;
    }

    @Override
    public String toString() {
        return "WEBSOCKET FRAME:\nOpCode: "+opcode+
            "\nmessage: "+message+
            "\nEND";
    }

    static byte[] getCloseFrame(String reason) {
        byte[] messageBytes = reason.getBytes(utf8);

        return pack(messageBytes, OpCode.CONNECTION_CLOSE.code, null);
    }

    static byte[] getPongFrame(byte[] pingBytes) {
        return pack(pingBytes, OpCode.PONG.code, null);        
    }

    enum OpCode {
        CONTINUATION(0),
        TEXT(1),
        BINARY(2),
        FURTHER_NON_CONTROL(3), // 3-7
        CONNECTION_CLOSE(8),
        PING(9),
        PONG(10),
        FURTHER_CONTROL_FRAME(11); // 11-15

         final int code;

        OpCode(int code) {
            this.code = code;
        }

        static OpCode getOpcode(int code) {
            if (code <= 15 && code >= 0) {
                if(code == 0) {
                    return CONTINUATION;
                }
                else if (code == 1) {
                    return TEXT;
                }
                else if (code == 2) {
                    return BINARY;
                }
                else if (code >= 3 && code <= 7) {
                    return FURTHER_NON_CONTROL;
                }
                else if (code == 8) {
                    return CONNECTION_CLOSE;
                }
                else if (code == 9) {
                    return PING;
                }
                else if (code == 10) {
                    return PONG;
                }
                else {
                    return FURTHER_CONTROL_FRAME;
                }
            }
            return null;
        }
    }
}
