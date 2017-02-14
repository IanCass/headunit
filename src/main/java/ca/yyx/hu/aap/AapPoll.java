package ca.yyx.hu.aap;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import ca.yyx.hu.aap.protocol.Channel;
import ca.yyx.hu.aap.protocol.MsgType;
import ca.yyx.hu.connection.AccessoryConnection;
import ca.yyx.hu.decoder.MicRecorder;
import ca.yyx.hu.utils.AppLog;
import ca.yyx.hu.utils.Utils;


/**
 * @author algavris
 * @date 01/10/2016.
 */

class AapPoll {

    private final AccessoryConnection mConnection;
    private final AapTransport mTransport;

    private byte[] recv_buffer;
    private ByteBuffer fifo = ByteBuffer.allocate(1024 * 256); //256k
    byte[] buf = new byte[Short.MAX_VALUE];

    private final Header recv_header = new Header();

    private final AapAudio mAapAudio;
    private final AapVideo mAapVideo;
    private final AapControl mAapControl;

    AapPoll(AccessoryConnection connection, AapTransport transport, MicRecorder recorder, AapAudio aapAudio, AapVideo aapVideo, String btMacAddress) {
        mConnection = connection;
        mAapAudio = aapAudio;
        mAapVideo = aapVideo;
        mTransport = transport;
        mAapControl = new AapControl(transport, recorder, mAapAudio, btMacAddress);
        recv_buffer = new byte[mConnection.bufferSize()];
    }

    int poll() {

        byte[] header = new byte[Header.SIZE];

        if (mConnection == null) {
            AppLog.e("Error: No connection.");
            return -1;
        }

        // receive bulk data
        int size = mConnection.recv(recv_buffer, recv_buffer.length, 150);
        if (size <= 0) {
            AppLog.d("recv <= zero %d", size);
            return 0;
        }

        // move the data we've read into the bytebuffer
        fifo.put(recv_buffer, fifo.position(), size);
        fifo.flip();

        try {
            while (fifo.hasRemaining()) {

                // Parse the header
                fifo.get(header, 0, 4);
                recv_header.decode(header);

                // hack because these message types have 8 byte headers
                if (recv_header.chan == Channel.ID_VID && recv_header.flags == 9) {
                    fifo.position(fifo.position() + 4);
                }

                // Retrieve the entire message now we know the length
                fifo.get(buf, 0, recv_header.enc_len);

                // Decrypt & Process 1 received encrypted message
                AapMessage msg = decryptMessage(recv_header, buf);
                if (msg == null) {
                    // If error...
                    AppLog.e("Error iaap_recv_dec_process: enc_len: %d chan: %d %s flags: %01x", buf.length, recv_header.chan, Channel.name(recv_header.chan), recv_header.flags);
                    return -1;
                }

                // process the message
                iaap_msg_process(msg);
            }
        } catch (BufferUnderflowException e) {
            // Not enough bytes. ignore.
            AppLog.e(e);
        } catch (InvalidProtocolBufferNanoException e) {
            // erk!
            AppLog.e(e);
            return -1;
        }
        // Prepare for next write
        fifo.compact();

        return 0;
    }

    private AapMessage decryptMessage(Header header, byte[] buf) {
        // Decrypt & Process 1 received encrypted message
        int offset = 0;

        if ((header.flags & 0x08) != 0x08) {
            AppLog.e("WRONG FLAG: enc_len: %d  chan: %d %s flags: 0x%02x",
                    header.enc_len, header.chan, Channel.name(header.chan), header.flags);
            return null;
        }

        ByteArray ba = AapSsl.decrypt(offset, header.enc_len, buf);
        if (ba == null) {
            return null;
        }

        int msg_type = Utils.bytesToInt(ba.data, 0, true);

        AapMessage msg = new AapMessage(header.chan, (byte) header.flags, msg_type, 2, ba.length, ba.data);

        if (AppLog.LOG_VERBOSE) {
            AppLog.d("RECV: ", msg.toString());
        }
        return msg;
    }

    private int iaap_msg_process(AapMessage message) throws InvalidProtocolBufferNanoException {

        int msg_type = message.type;
        byte flags = message.flags;

        if (message.isAudio() && (msg_type == 0 || msg_type == 1)) {
            mTransport.sendMediaAck(message.channel);
            return mAapAudio.process(message);
            // 300 ms @ 48000/sec   samples = 14400     stereo 16 bit results in bytes = 57600
        } else if (message.isVideo() && msg_type == 0 || msg_type == 1 || flags == 8 || flags == 9 || flags == 10) {
            mTransport.sendMediaAck(message.channel);
            return mAapVideo.process(message);
        } else if ((msg_type >= 0 && msg_type <= 31) || (msg_type >= 32768 && msg_type <= 32799) || (msg_type >= 65504 && msg_type <= 65535)) {
            mAapControl.execute(message);
        } else {
            AppLog.e("Unknown msg_type: %d", msg_type);
        }

        return 0;
    }

    private static class Header {
        final static int SIZE = 4;

        int chan;
        int flags;
        int enc_len;

        void decode(byte[] buf) {

            this.chan = (int) buf[0];
            this.flags = buf[1];
            this.enc_len = Utils.bytesToInt(buf, 2, true); // Encoded length of bytes to be decrypted (minus 4/8 byte headers)
        }

    }

    private static class RecvProcessResult {
        static final RecvProcessResult Error = new RecvProcessResult(-1);
        static final RecvProcessResult Ok = new RecvProcessResult(0);
        static final RecvProcessResult NeedMore = new RecvProcessResult(1);

        int result;
        int have_length;
        int need_length;
        int start;

        RecvProcessResult(int result) {
            this.result = result;
        }

        RecvProcessResult setNeedMore(int start, int have_length, int need_length) {
            this.start = start;
            this.need_length = need_length;
            this.have_length = have_length;
            return this;
        }
    }
}
