package ca.yyx.hu.aap;

import java.util.ArrayList;

import ca.yyx.hu.aap.protocol.AudioConfigs;
import ca.yyx.hu.aap.protocol.Channel;
import ca.yyx.hu.aap.protocol.MsgType;
import ca.yyx.hu.aap.protocol.nano.Protocol;
import ca.yyx.hu.aap.protocol.nano.Protocol.Service;
import ca.yyx.hu.aap.protocol.nano.Protocol.Service.InputSourceService.TouchConfig;
import ca.yyx.hu.aap.protocol.nano.Protocol.Service.MediaSinkService.VideoConfiguration;
import ca.yyx.hu.aap.protocol.nano.Protocol.Service.SensorSourceService;
import ca.yyx.hu.utils.AppLog;
import ca.yyx.hu.utils.Utils;

/**
 * @author algavris
 * @date 08/06/2016.
 */

public class Messages {
    static final int DEF_BUFFER_LENGTH = 1024 * 256;

    static byte[] createRawMessage(int chan, int flags, int type, byte[] data, int size) {

        int total = 6 + size;
        byte[] buffer = new byte[total];

        buffer[0] = (byte) chan;
        buffer[1] = (byte) flags;
        Utils.intToBytes(size + 2, 2, buffer);
        Utils.intToBytes(type, 4, buffer);

        System.arraycopy(data, 0, buffer, 6, size);
        return buffer;
    }

    static AapMessage createVideoFocus(int mode, boolean unsolicited) {
        Protocol.VideoFocusNotification videoFocus = new Protocol.VideoFocusNotification();
        videoFocus.mode = mode;
        videoFocus.unsolicited = unsolicited;

        return new AapMessage(Channel.ID_VID, MsgType.Media.VIDEOFOCUSNOTIFICATION, videoFocus);
    }

    static AapMessage createKeyEvent(long timeStamp, int keycode, boolean isPress) {
        Protocol.InputReport inputReport = new Protocol.InputReport();
        Protocol.KeyEvent keyEvent = new Protocol.KeyEvent();
        // Timestamp in nanoseconds = microseconds x 1,000,000
        inputReport.timestamp = timeStamp * 1000000L;
        inputReport.keyEvent = keyEvent;

        keyEvent.keys = new Protocol.Key[1];
        keyEvent.keys[0] = new Protocol.Key();
        keyEvent.keys[0].keycode = keycode;
        keyEvent.keys[0].down = isPress;

        return new AapMessage(Channel.ID_INP, MsgType.Input.EVENT, inputReport);
    }

    static AapMessage createScrollEvent(long timeStamp, int delta) {
        Protocol.InputReport inputReport = new Protocol.InputReport();
        Protocol.KeyEvent keyEvent = new Protocol.KeyEvent();
        // Timestamp in nanoseconds = microseconds x 1,000,000
        inputReport.timestamp = timeStamp * 1000000L;
        inputReport.keyEvent = keyEvent;

        Protocol.RelativeEvent relativeEvent = new Protocol.RelativeEvent();
        relativeEvent.data = new Protocol.RelativeEvent_Rel[1];
        relativeEvent.data[0] = new Protocol.RelativeEvent_Rel();
        relativeEvent.data[0].delta = delta;
        relativeEvent.data[0].keycode = KeyCode.SCROLL_WHEEL;
        inputReport.relativeEvent = relativeEvent;

        return new AapMessage(Channel.ID_INP, MsgType.Input.EVENT, inputReport);
    }

    static AapMessage createTouchEvent(long timeStamp, int action, int x, int y) {

        Protocol.InputReport inputReport = new Protocol.InputReport();
        Protocol.TouchEvent touchEvent = new Protocol.TouchEvent();
        inputReport.timestamp = timeStamp * 1000000L;
        inputReport.touchEvent = touchEvent;

        touchEvent.pointerData = new Protocol.TouchEvent.Pointer[1];
        Protocol.TouchEvent.Pointer pointer = new Protocol.TouchEvent.Pointer();
        pointer.x = x;
        pointer.y = y;
        touchEvent.pointerData[0] = pointer;
        touchEvent.actionIndex = 0;
        touchEvent.action = action;

        return new AapMessage(Channel.ID_INP, MsgType.Input.EVENT, inputReport);
    }

    static AapMessage createNightModeEvent(boolean enabled) {
        Protocol.SensorBatch sensorBatch = new Protocol.SensorBatch();
        sensorBatch.nightMode = new Protocol.SensorBatch.NightModeData[1];
        sensorBatch.nightMode[0] = new Protocol.SensorBatch.NightModeData();
        sensorBatch.nightMode[0].isNight = enabled;

        return new AapMessage(Channel.ID_SEN, MsgType.Sensor.EVENT, sensorBatch);
    }

    static AapMessage createDrivingStatusEvent(int status) {
        Protocol.SensorBatch sensorBatch = new Protocol.SensorBatch();
        sensorBatch.drivingStatus = new Protocol.SensorBatch.DrivingStatusData[1];
        sensorBatch.drivingStatus[0] = new Protocol.SensorBatch.DrivingStatusData();
        sensorBatch.drivingStatus[0].status = status;

        return new AapMessage(Channel.ID_SEN, MsgType.Sensor.EVENT, sensorBatch);
    }

    static byte[] VERSION_REQUEST = {0, 1, 0, 1};

    static AapMessage createServiceDiscoveryResponse(String btAddress) {
        Protocol.ServiceDiscoveryResponse carInfo = new Protocol.ServiceDiscoveryResponse();
        carInfo.make = "AACar";
        carInfo.model = "0001";
        carInfo.year = "2016";
        carInfo.headUnitModel = "ChangAn S";
        carInfo.headUnitMake = "Roadrover";
        carInfo.headUnitSoftwareBuild = "SWB1";
        carInfo.headUnitSoftwareVersion = "SWV1";
        carInfo.driverPosition = true;

        ArrayList<Service> services = new ArrayList<>();

        Service sensors = new Service();
        sensors.id = Channel.ID_SEN;
        sensors.sensorSourceService = new SensorSourceService();
        sensors.sensorSourceService.sensors = new SensorSourceService.Sensor[2];
        sensors.sensorSourceService.sensors[0] = new SensorSourceService.Sensor();
        sensors.sensorSourceService.sensors[0].type = Protocol.SENSOR_TYPE_DRIVING_STATUS;
        sensors.sensorSourceService.sensors[1] = new SensorSourceService.Sensor();
        sensors.sensorSourceService.sensors[1].type = Protocol.SENSOR_TYPE_NIGHT;

        services.add(sensors);

        Service video = new Service();
        video.id = Channel.ID_VID;
        video.mediaSinkService = new Service.MediaSinkService();
        video.mediaSinkService.availableType = Protocol.MEDIA_CODEC_VIDEO;
        video.mediaSinkService.availableWhileInCall = true;
        video.mediaSinkService.videoConfigs = new VideoConfiguration[1];
        VideoConfiguration videoConfig = new VideoConfiguration();
        videoConfig.codecResolution = VideoConfiguration.VIDEO_RESOLUTION_800x480;
        videoConfig.frameRate = VideoConfiguration.VIDEO_FPS_60;
        videoConfig.density = 140;
        video.mediaSinkService.videoConfigs[0] = videoConfig;
        services.add(video);

        Service input = new Service();
        input.id = Channel.ID_INP;
        input.inputSourceService = new Service.InputSourceService();
        input.inputSourceService.touchscreen = new TouchConfig();
        input.inputSourceService.touchscreen.width = 800;
        input.inputSourceService.touchscreen.height = 480;
        input.inputSourceService.keycodesSupported = KeyCode.supported();
        services.add(input);

        Service audio1 = new Service();
        audio1.id = Channel.ID_AU1;
        audio1.mediaSinkService = new Service.MediaSinkService();
        audio1.mediaSinkService.availableType = Protocol.MEDIA_CODEC_AUDIO;
        audio1.mediaSinkService.audioType = Protocol.CAR_STREAM_SYSTEM;
        audio1.mediaSinkService.audioConfigs = new Protocol.AudioConfiguration[1];
        audio1.mediaSinkService.audioConfigs[0] = AudioConfigs.get(Channel.ID_AU1);
        services.add(audio1);

        Service audio2 = new Service();
        audio2.id = Channel.ID_AU2;
        audio2.mediaSinkService = new Service.MediaSinkService();
        audio2.mediaSinkService.availableType = Protocol.MEDIA_CODEC_AUDIO;
        audio2.mediaSinkService.audioType = Protocol.CAR_STREAM_VOICE;
        audio2.mediaSinkService.audioConfigs = new Protocol.AudioConfiguration[1];
        audio2.mediaSinkService.audioConfigs[0] = AudioConfigs.get(Channel.ID_AU2);
        services.add(audio2);

        Service audio0 = new Service();
        audio0.id = Channel.ID_AUD;
        audio0.mediaSinkService = new Service.MediaSinkService();
        audio0.mediaSinkService.availableType = Protocol.MEDIA_CODEC_AUDIO;
        audio0.mediaSinkService.audioType = Protocol.CAR_STREAM_MEDIA;
        audio0.mediaSinkService.audioConfigs = new Protocol.AudioConfiguration[1];
        audio0.mediaSinkService.audioConfigs[0] = AudioConfigs.get(Channel.ID_AUD);
        services.add(audio0);

        Service mic = new Service();
        mic.id = Channel.ID_MIC;
        mic.mediaSourceService = new Service.MediaSourceService();
        mic.mediaSourceService.type = Protocol.MEDIA_CODEC_AUDIO;
        Protocol.AudioConfiguration micConfig = new Protocol.AudioConfiguration();
        micConfig.sampleRate = 16000;
        micConfig.numberOfBits = 16;
        micConfig.numberOfChannels = 1;
        mic.mediaSourceService.audioConfig = micConfig;
        services.add(mic);

        if (btAddress != null) {
            Service bluetooth = new Service();
            bluetooth.id = Channel.ID_BTH;
            bluetooth.bluetoothService = new Service.BluetoothService();
            bluetooth.bluetoothService.carAddress = btAddress;
            bluetooth.bluetoothService.supportedPairingMethods = new int[]{4};
            services.add(bluetooth);
        } else {
            AppLog.d("BT MAC Address is null. Skip bluetooth service");
        }

        carInfo.services = services.toArray(new Service[0]);

        return new AapMessage(Channel.ID_CTR, MsgType.Control.SERVICEDISCOVERYRESPONSE, carInfo);
    }


    private final static Protocol.Ack mediaAck = new Protocol.Ack();
    private final static byte[] ackBuf = new byte[10];

    static AapMessage createMediaAck(int channel, int sessionId) {
        mediaAck.clear();
        mediaAck.sessionId = sessionId;
        mediaAck.ack = 1;

        return new AapMessage(channel, MsgType.Media.ACK, mediaAck, ackBuf);
    }
}
