package volodymyr.com.camera.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.support.annotation.NonNull;
import android.util.Size;
import android.view.Surface;

import java.util.Arrays;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.functions.Function;
import volodymyr.com.camera.camera.event.CaptureSessionEvents;
import volodymyr.com.camera.camera.event.CaptureSessionStateEvent;
import volodymyr.com.camera.camera.event.DeviceStateEvent;
import volodymyr.com.camera.camera.pojo.CaptureSessionData;
import volodymyr.com.camera.camera.pojo.Pair;

import static android.content.Context.CAMERA_SERVICE;

public class CameraHelper {

    private static final String TAG = CameraHelper.class.getSimpleName();
    private CameraManager mCameraManager = null;
    private CameraParams mCameraParams;

    public CameraHelper(Context context) {
        mCameraManager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        try {
            getCameraId();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void getCameraId() throws CameraAccessException {
        Observable.fromIterable(Arrays.asList(mCameraManager.getCameraIdList()))
                .map((Function<String, Pair<String, CameraCharacteristics>>) s -> new Pair(s, mCameraManager.getCameraCharacteristics(s)))
                .filter(stringCameraCharacteristicsPair -> {
                    Integer integer = stringCameraCharacteristicsPair.param2.get(CameraCharacteristics.LENS_FACING);
                    if (integer != null && integer == CameraCharacteristics.LENS_FACING_FRONT) {
                        return true;
                    }
                    return false;
                })
                .subscribe(pair -> mCameraParams = getCameraParams(pair));
    }

    private CameraParams getCameraParams(Pair<String, CameraCharacteristics> pair) {
        Size previewSize = CameraStrategy.getPreviewSize(pair.param2);
        return new CameraParams(pair.param1, pair.param2, previewSize);
    }

    public CameraParams getCameraParams() {
        return mCameraParams;
    }

    @SuppressLint("MissingPermission")
    public Observable<Pair<DeviceStateEvent, CameraDevice>> openCamera() {
        return Observable.create(observableEmitter ->
                mCameraManager.openCamera(mCameraParams.cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice cameraDevice) {
                        observableEmitter.onNext(new Pair<>(DeviceStateEvent.ON_OPENED, cameraDevice));
                    }

                    @Override
                    public void onClosed(@NonNull CameraDevice cameraDevice) {
                        observableEmitter.onNext(new Pair<>(DeviceStateEvent.ON_CLOSED, cameraDevice));
                        observableEmitter.onComplete();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                        observableEmitter.onNext(new Pair<>(DeviceStateEvent.ON_DISCONNECTED, cameraDevice));
                        observableEmitter.onComplete();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        observableEmitter.onError(new Exception("Exception " + error));
                    }
                }, null)
        );
    }

    @NonNull
    public static Observable<Pair<CaptureSessionStateEvent, CameraCaptureSession>> createCaptureSession(
            @NonNull CameraDevice cameraDevice,
            @NonNull List<Surface> surfaceList
    ) {
        return Observable.create(observableEmitter -> {
            cameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    observableEmitter.onNext(new Pair<>(CaptureSessionStateEvent.ON_CONFIGURED, session));
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    observableEmitter.onError(new Exception(" Exception " + session.toString()));
                }

                @Override
                public void onReady(@NonNull CameraCaptureSession session) {
                    observableEmitter.onNext(new Pair<>(CaptureSessionStateEvent.ON_READY, session));
                }

                @Override
                public void onActive(@NonNull CameraCaptureSession session) {
                    observableEmitter.onNext(new Pair<>(CaptureSessionStateEvent.ON_ACTIVE, session));
                }

                @Override
                public void onClosed(@NonNull CameraCaptureSession session) {
                    observableEmitter.onNext(new Pair<>(CaptureSessionStateEvent.ON_CLOSED, session));
                    observableEmitter.onComplete();
                }

                @Override
                public void onSurfacePrepared(@NonNull CameraCaptureSession session, @NonNull Surface surface) {
                    observableEmitter.onNext(new Pair<>(CaptureSessionStateEvent.ON_SURFACE_PREPARED, session));
                }
            }, null);
        });
    }

    public Observable<CaptureSessionData> fromSetRepeatingRequest(@NonNull CameraCaptureSession captureSession, @NonNull CaptureRequest request) {
        return Observable.create(observableEmitter -> captureSession.setRepeatingRequest(request, createCaptureCallback(observableEmitter), null));
    }

    @NonNull
    private CameraCaptureSession.CaptureCallback createCaptureCallback(final ObservableEmitter<CaptureSessionData> observableEmitter) {
        return new CameraCaptureSession.CaptureCallback() {

            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            }

            @Override
            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            }

            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                if (!observableEmitter.isDisposed()) {
                    observableEmitter.onNext(new CaptureSessionData(CaptureSessionEvents.ON_COMPLETED, session, request, result));
                }
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                if (!observableEmitter.isDisposed()) {
                    observableEmitter.onError(new Exception("" + failure));
                }
            }

            @Override
            public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            }

            @Override
            public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            }
        };
    }

    @NonNull
    public Observable<ImageReader> createOnImageAvailableObservable(@NonNull ImageReader imageReader) {
        return Observable.create(subscriber -> {
            ImageReader.OnImageAvailableListener listener = reader -> {
                if (!subscriber.isDisposed()) {
                    subscriber.onNext(reader);
                }
            };
            imageReader.setOnImageAvailableListener(listener, null);
            subscriber.setCancellable(() -> imageReader.setOnImageAvailableListener(null, null)); //remove listener on unsubscribe
        });
    }

    public class CameraParams {
        @NonNull
        public final String cameraId;
        @NonNull
        public final CameraCharacteristics cameraCharacteristics;
        @NonNull
        public final Size previewSize;

        private CameraParams(@NonNull String cameraId, @NonNull CameraCharacteristics cameraCharacteristics, @NonNull Size previewSize) {
            this.cameraId = cameraId;
            this.cameraCharacteristics = cameraCharacteristics;
            this.previewSize = previewSize;
        }
    }

}
