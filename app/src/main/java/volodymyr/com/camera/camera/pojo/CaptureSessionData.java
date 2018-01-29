package volodymyr.com.camera.camera.pojo;


import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;

import volodymyr.com.camera.camera.event.CaptureSessionEvents;

public class CaptureSessionData {
    final CaptureSessionEvents event;
    final CameraCaptureSession session;
    final CaptureRequest request;
    final CaptureResult result;

    public CaptureSessionData(CaptureSessionEvents event, CameraCaptureSession session, CaptureRequest request, CaptureResult result) {
        this.event = event;
        this.session = session;
        this.request = request;
        this.result = result;
    }
}
