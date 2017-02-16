package ca.yyx.hu.aap;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import ca.yyx.hu.aap.protocol.Channel;
import ca.yyx.hu.aap.protocol.MsgType;
import ca.yyx.hu.connection.AccessoryConnection;
import ca.yyx.hu.decoder.MicRecorder;
import ca.yyx.hu.utils.AppLog;
import ca.yyx.hu.utils.Utils;


/**
 * @author algavris
 * @date 01/10/2016.
 *
 * @author iancass
 * @date 14/02/2017
 */

class AapPoll {

    private final AccessoryConnection mConnection;
    private final AapTransport mTransport;

    private byte[] recv_buffer;
    private ByteBuffer fifo = ByteBuffer.allocate(65535 * 2);
    byte[] buf = new byte[65535]; // unsigned short max

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

        Header recv_header = new Header();
        byte[] header = new byte[Header.SIZE];

        if (mConnection == null) {
            AppLog.e("Error: No connection.");
            return -1;
        }

        // receive bulk data
        //FIXME modify AccesoryConnection to read/write ByteBuffers instead of byte arrays
        int size = mConnection.recv(recv_buffer, recv_buffer.length, 500);
        //AppLog.i("Size %d", size);
        if (size <= 0) {
            AppLog.d("recv <= zero %d", size);
            return 0;
        }

        // move the data we've read into the bytebuffer
        fifo.put(recv_buffer, 0, size);
        fifo.flip();

        try {
            while (fifo.hasRemaining()) {

                // Parse the header
                fifo.mark();
                try {
                    fifo.get(header, 0, 4);
                } catch (BufferUnderflowException e) {
                    // we'll come back later for more data
                    AppLog.v("BufferUnderflowException whilst trying to read 4 bytes capacity = %d, position = %d", fifo.capacity(), fifo.position());
                    return 0;
                }
                recv_header.decode(header);

                // hack because these message types have 8 byte headers
                if (recv_header.chan == Channel.ID_VID && recv_header.flags == 9) {
                    //FIXME - check we CAN move forward 4 places!

                    byte[] skipped = new byte[4];
                    fifo.get(skipped, 0, 4);
                    AppLog.e("Skipping! chan %d, flags %d, length %d, data %s", recv_header.chan, recv_header.flags, recv_header.chan, byteArrayToHex(skipped));
                }

                // Retrieve the entire message now we know the length
                try {
                    fifo.get(buf, 0, recv_header.enc_len);
                } catch (BufferUnderflowException e) {
                    // rewind so we process the header again next time
                    AppLog.e("BufferUnderflowException whilst trying to read %d bytes limit = %d, position = %d", recv_header.enc_len, fifo.limit(), fifo.position());
                    fifo.reset();
                    break;
                }

                // Decrypt & Process 1 received encrypted message
                AapMessage msg = decryptMessage(recv_header, buf);
                if (msg == null) {
                    // If error...
                    AppLog.e("Message Decryption Error: enc_len: %d chan: %d %s flags: %01x", recv_header.enc_len, recv_header.chan, Channel.name(recv_header.chan), recv_header.flags);
                    return -1;
                }

                // process the message
                iaap_msg_process(msg);


            }
        } catch (InvalidProtocolBufferNanoException e) {
            // erk!
            AppLog.e(e);
            return -1;
        }

        // consume
        fifo.compact();

        return 0;
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(int b = 0; b < a.length && b < 20; b++)
            sb.append(String.format("%02x", a[b]));
        return sb.toString();
    }

    private AapMessage decryptMessage(Header header, byte[] buf) {
        // Decrypt & Process 1 received encrypted message
        int offset = 0;

        if ((header.flags & 0x08) != 0x08) {
            //FIXME sometimes we get the wrong flag. Why? Corrupted read?
            AppLog.e("WRONG FLAG: enc_len: %d,  chan: %d %s, flags: 0x%02x, buf len: %d, Array: %s ...",
                    header.enc_len, header.chan, Channel.name(header.chan), header.flags, buf.length, byteArrayToHex(buf));
            return null;
        }

        ByteArray ba = AapSsl.decrypt(offset, header.enc_len, buf);
        if (ba == null) {
            AppLog.e("Could not decrypt offset %d, enc_len %d, chan: %d %s, flags 0x%02x", offset, header.enc_len, header.chan, Channel.name(header.chan), header.flags);
            return null;
        }

        // First 2 bytes are Message Type msg_type
        int msg_type = Utils.bytesToInt(ba.data, 0, true);

        // Remaining bytes are protobufs data, usually starting with 0x08 for integer or 0x0a for array
        AapMessage msg = new AapMessage(header.chan, (byte) header.flags, msg_type, 2, ba.length, ba.data);

        if (AppLog.LOG_VERBOSE) {
            AppLog.d("RECV: ", msg.toString());
        }
        return msg;
    }

    private int iaap_msg_process(AapMessage message) throws InvalidProtocolBufferNanoException {

        int msg_type = message.type;
        byte flags = message.flags;

        //TODO thread handoff for video & audio? Currently both on the same thread!
        if (message.isAudio() && (msg_type == MsgType.Control.MEDIADATA || msg_type == MsgType.Control.CODECDATA)) {
            mTransport.sendMediaAck(message.channel);
            return mAapAudio.process(message);
            // 300 ms @ 48000/sec   samples = 14400     stereo 16 bit results in bytes = 57600
        } else if (message.isVideo() && msg_type == MsgType.Control.MEDIADATA || msg_type == MsgType.Control.CODECDATA || flags == 8 || flags == 9 || flags == 10) {
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

            this.chan = (int) buf[0] & 0xff;                       // 1 byte channel
            this.flags = (int) buf[1] & 0xff;                            // 1 byte flags
            this.enc_len = Utils.bytesToInt(buf, 2, true);  // 2 bytes body length (unsigned short)
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
