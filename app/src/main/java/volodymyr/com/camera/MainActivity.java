package volodymyr.com.camera;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.Surface;
import android.view.TextureView;

import java.util.Arrays;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.PublishSubject;
import volodymyr.com.camera.camera.CameraHelper;
import volodymyr.com.camera.camera.event.CaptureSessionStateEvent;
import volodymyr.com.camera.camera.event.DeviceStateEvent;
import volodymyr.com.camera.camera.pojo.CaptureSessionData;
import volodymyr.com.camera.camera.pojo.Pair;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @BindView(R.id.texture_view)
    AutoFitTextureView textureView;
    private Surface mSurface;

    private CameraHelper cameraHelper;
    private PublishSubject<SurfaceTexture> mOnSurfaceTextureAvailable = PublishSubject.create();
    private final CompositeDisposable mCompositeDisposable = new CompositeDisposable();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        cameraHelper = new CameraHelper(this);
        initTexture();

        Observable<Pair<DeviceStateEvent, CameraDevice>> cameraDeviceObservable = mOnSurfaceTextureAvailable
                .firstElement()
                .doAfterSuccess(this::setupSurface)
                .toObservable()
                .flatMap(__ -> cameraHelper.openCamera())
                .share();

        Observable<CameraDevice> openCameraObservable = cameraDeviceObservable
                .filter(pair -> pair.param1 == DeviceStateEvent.ON_OPENED)
                .map(pair -> pair.param2)
                .share();

        Observable<CameraDevice> closeCameraObservable = cameraDeviceObservable
                .filter(pair -> pair.param1 == DeviceStateEvent.ON_CLOSED)
                .map(pair -> pair.param2)
                .share();


        Observable<Pair<CaptureSessionStateEvent, CameraCaptureSession>> createCaptureSessionObservable = openCameraObservable
                .flatMap(cameraDevice -> cameraHelper.createCaptureSession(cameraDevice, Arrays.asList(mSurface)))
                .share();

        Observable<CameraCaptureSession> captureSessionConfiguredObservable = createCaptureSessionObservable
                .filter(pair -> pair.param1 == CaptureSessionStateEvent.ON_CONFIGURED)
                .map(pair -> pair.param2)
                .share();

        Observable<CameraCaptureSession> captureSessionClosedObservable = createCaptureSessionObservable
                .filter(pair -> pair.param1 == CaptureSessionStateEvent.ON_CLOSED)
                .map(pair -> pair.param2)
                .share();

        Observable<CaptureSessionData> previewObservable = captureSessionConfiguredObservable
                .flatMap(cameraCaptureSession -> {
                    CaptureRequest.Builder previewBuilder = createPreviewBuilder(cameraCaptureSession, mSurface);
                    return cameraHelper.fromSetRepeatingRequest(cameraCaptureSession, previewBuilder.build());
                })
                .share();

        previewObservable.subscribe(captureSessionData -> {

        });
    }

    private CaptureRequest.Builder createPreviewBuilder(CameraCaptureSession captureSession, Surface previewSurface) throws CameraAccessException {
        CaptureRequest.Builder builder = captureSession.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        builder.addTarget(previewSurface);
        setup3Auto(builder);
        return builder;
    }

    private void setup3Auto(CaptureRequest.Builder builder) {
        // Enable auto-magical 3A run by camera device
        CameraHelper.CameraParams cameraParams = cameraHelper.getCameraParams();
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

        Float minFocusDist = cameraParams.cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);

        // If MINIMUM_FOCUS_DISTANCE is 0, lens is fixed-focus and we need to skip the AF run.
        boolean noAFRun = (minFocusDist == null || minFocusDist == 0);

        if (!noAFRun) {
            // If there is a "continuous picture" mode available, use it, otherwise default to AUTO.
            int[] afModes = cameraParams.cameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            if (contains(afModes, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            }
        }

        // If there is an auto-magical flash control mode available, use it, otherwise default to
        // the "on" mode, which is guaranteed to always be available.
        int[] aeModes = cameraParams.cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
        if (contains(aeModes, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        }

        // If there is an auto-magical white balance control mode available, use it.
        int[] awbModes = cameraParams.cameraCharacteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
        if (contains(awbModes, CaptureRequest.CONTROL_AWB_MODE_AUTO)) {
            // Allow AWB to run auto-magically if this device supports this
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
        }
    }

    private static boolean contains(int[] modes, int mode) {
        if (modes == null) {
            return false;
        }
        for (int i : modes) {
            if (i == mode) {
                return true;
            }
        }
        return false;
    }

    private void initTexture() {
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(final SurfaceTexture surface, int width, int height) {
                mOnSurfaceTextureAvailable.onNext(surface);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
        textureView.setAspectRatio(cameraHelper.getCameraParams().previewSize.getHeight(), cameraHelper.getCameraParams().previewSize.getWidth());

    }


    private void setupSurface(@NonNull SurfaceTexture surfaceTexture) {
        surfaceTexture.setDefaultBufferSize(cameraHelper.getCameraParams().previewSize.getWidth(), cameraHelper.getCameraParams().previewSize.getHeight());
        mSurface = new Surface(surfaceTexture);
    }


}
