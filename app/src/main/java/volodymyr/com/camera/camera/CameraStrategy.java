package volodymyr.com.camera.camera;

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Size;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.reactivex.Observable;

@TargetApi(21)
class CameraStrategy {
    private static final String TAG = CameraStrategy.class.getSimpleName();
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1920;
    private static final int MAX_STILL_IMAGE_WIDTH = 1920;
    private static final int MAX_STILL_IMAGE_HEIGHT = 1920;



    static Size getPreviewSize(@NonNull CameraCharacteristics characteristics) {
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);

        if (outputSizes.length == 0) {
            throw new IllegalStateException("No supported sizes for SurfaceTexture");
        }
        List<Size> filteredOutputSizes = Observable.fromArray(outputSizes)
                .filter(size -> size.getWidth() <= MAX_PREVIEW_WIDTH && size.getHeight() <= MAX_PREVIEW_HEIGHT)
                .toList()
                .blockingGet();

        if (filteredOutputSizes.size() == 0) {
            return outputSizes[0];
        }

        return Collections.max(filteredOutputSizes, new CompareSizesByArea());
    }

    /**
     * Please note that aspect ratios should be the same for {@link #getPreviewSize(CameraCharacteristics)} and {@link #getStillImageSize(CameraCharacteristics, Size)}
     */
    static Size getStillImageSize(@NonNull CameraCharacteristics characteristics, @NonNull Size previewSize) {
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] outputSizes = map.getOutputSizes(ImageFormat.JPEG);
        if (outputSizes.length == 0) {
            throw new IllegalStateException("No supported sizes for JPEG");
        }
        List<Size> filteredOutputSizes = Observable.fromArray(outputSizes)
                .filter(size -> size.getWidth() == size.getHeight() * previewSize.getWidth() / previewSize.getHeight())
                .filter(size -> size.getWidth() <= MAX_STILL_IMAGE_WIDTH && size.getHeight() <= MAX_STILL_IMAGE_HEIGHT)
                .toList()
                .blockingGet();

        if (filteredOutputSizes.size() == 0) {
            return outputSizes[0];
        }

        return Collections.max(filteredOutputSizes, new CompareSizesByArea());
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    private static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
}