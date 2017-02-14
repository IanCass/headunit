package ca.yyx.hu.connection;

import android.support.annotation.NonNull;

/**
 * @author algavris
 * @date 05/11/2016.
 *
 * @author iancass
 * @date 14/02/2017
 *
 */

public interface AccessoryConnection {

    interface Listener {
        void onConnectionResult(boolean success);
    }

    boolean isSingleMessage();
    int send(byte[] buf, int length, int timeout);
    int recv(byte[] buf, int length, int timeout);
    boolean isConnected();
    void connect(@NonNull final Listener listener);
    void disconnect();
    int bufferSize();
}
