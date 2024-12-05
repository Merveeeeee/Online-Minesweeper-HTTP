import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

// Socket interface to send and receive text messages following the Websocket Data Frames
public class WebSocket {
    private InputStream streamIn;
    private OutputStream streamOut;

    WebSocket(Socket socket) {
        try {
            this.streamIn = socket.getInputStream();
            this.streamOut = socket.getOutputStream();

        } catch (IOException e) {
            System.out.println("Failed to create Worker:" + e);
        }
    }

    // Perform several reads to read up to length bytes, returns the bytes read
    private byte[] read(int length) throws IOException {
        byte[] data = new byte[length];
        int totalBytesRead = 0;
        while (totalBytesRead < length) {
            int bytesRead = streamIn.read(data, totalBytesRead, length - totalBytesRead);
            if (bytesRead == -1) {
                throw new IOException("Data frame error: insufficient read");
            }
            totalBytesRead += bytesRead;
        }
        return data;
    }

    // Receive a text message from a websocket
    String receive() throws IOException {
        ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream();
        boolean isFinal = false;

        while (!isFinal) {

            // Header
            byte[] frameHeader = read(2);
            isFinal = (frameHeader[0] & 0b10000000) != 0;

            // RSV1 RSV2 RSV3 are ignored

            int opcode = frameHeader[0] & 0b00001111;
            if (opcode != 1) {
                throw new IOException("Unsupported opcode, only text data frame");
            }

            boolean isMasked = (frameHeader[1] & 0b10000000) != 0;

            // Payload length
            int payloadLength = frameHeader[1] & 0b01111111;

            // Extended length
            if (payloadLength == 126) {
                byte[] extendedLength = read(2);
                payloadLength = ByteBuffer.wrap(extendedLength).getShort();
            } else if (payloadLength == 127) {
                byte[] extendedLength = read(8);
                payloadLength = (int) ByteBuffer.wrap(extendedLength).getLong();
            }

            // Mask key
            byte[] maskKey = new byte[4];
            if (isMasked) {
                maskKey = read(4);
            }

            // Payload Data
            byte[] payloadData = read(payloadLength);

            // Unmask if needed
            if (isMasked) {
                for (int i = 0; i < payloadLength; i++) {
                    payloadData[i] ^= maskKey[i % 4];
                }
            }
            messageBuffer.write(payloadData);
        }
        return messageBuffer.toString("UTF-8");
    }

    // Send a text message from a websocket
    void send(String message) throws IOException {
        byte[] messageBytes = message.getBytes("UTF-8");
        int messageLength = messageBytes.length;

        ByteArrayOutputStream frame = new ByteArrayOutputStream();

        frame.write(0x81); //Final frame and text opcode

        // payload length
        if (messageLength <= 125) {
            frame.write(messageLength);
        } else if (messageLength <= 65535) {
            frame.write(126);
            frame.write(ByteBuffer.allocate(2).putShort((short) messageLength).array());
        } else {
            // 2^64 - 1 is never reached as int max value is 2^32 - 1
            frame.write(127);
            frame.write(ByteBuffer.allocate(8).putLong(messageLength).array());
        }

        frame.write(messageBytes);
        streamOut.write(frame.toByteArray());
    }
}
