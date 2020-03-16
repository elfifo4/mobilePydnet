package unibo.cvlab.pydnet;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Size;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.FloatBuffer;

import timber.log.Timber;
import unibo.cvlab.pydnet.demo.ImageUtils;

/**
 * Created by Elad on 3/16/20.
 */
public class MyActivity extends Activity implements View.OnClickListener {

    protected int previewWidth = 0;
    protected int previewHeight = 0;

    private Matrix frameToCropTransform;

    private ModelFactory modelFactory;
    private Model currentModel;
    private Utils.Resolution resolution = Utils.Resolution.RES4;
    private ColorMapper colorMapper = null;
    private boolean applyColormap = true;
    private static float COLOR_SCALE_FACTOR = 10.5f;
    private static Utils.Scale scale = Utils.Scale.HALF;
    private static int NUMBER_THREADS = Runtime.getRuntime().availableProcessors();
    private Size halfScreenSize = null;
    private Size screenSize = null;
    private Bitmap originalFrame = null;
    private Bitmap croppedFrame = null;
    private Bitmap outputDisp = null;
    private Bitmap outputDispResized = null;
    private Bitmap outputRGB = null;
    private Integer sensorOrientation = 0;
    private static final boolean MAINTAIN_ASPECT = true;

    private ImageView depthImageView;
    private ProgressBar progressBar;
    private TextView durationTextView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pydnet);

        durationTextView = findViewById(R.id.execution_time_label);
        progressBar = findViewById(R.id.progressBar);
        Button buttonDepth = findViewById(R.id.take_photo);
        Button buttonCamera = findViewById(R.id.open_camera);
        buttonDepth.setOnClickListener(this);
        buttonCamera.setOnClickListener(this);

        depthImageView = findViewById(R.id.depth_image);


//        previewWidth = size.getWidth();
//        previewHeight = size.getHeight();

        previewWidth = 1088;
        previewHeight = 1088;


        Display display = getWindowManager().getDefaultDisplay();
        Point pointScreen = new Point();
        display.getSize(pointScreen);
        halfScreenSize = new Size(pointScreen.x, pointScreen.y / 2);

        modelFactory = new ModelFactory(getApplicationContext());
        currentModel = modelFactory.getModel(0);
        currentModel.prepare(resolution);
        colorMapper = new ColorMapper(COLOR_SCALE_FACTOR, applyColormap, MyActivity.this);
        colorMapper.prepare(resolution);

        originalFrame = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);


        croppedFrame = Bitmap.createBitmap(resolution.getWidth(), resolution.getHeight(), Config.ARGB_8888);
        outputDisp = Bitmap.createBitmap(resolution.getWidth(), resolution.getHeight(), Config.ARGB_8888);
        outputDispResized = Bitmap.createBitmap(halfScreenSize.getWidth(), halfScreenSize.getHeight(), Config.ARGB_8888);
        outputRGB = Bitmap.createBitmap(halfScreenSize.getWidth(), halfScreenSize.getHeight(), Config.ARGB_8888);

        frameToCropTransform = ImageUtils.getTransformationMatrix(
                previewWidth, previewHeight,
                resolution.getWidth(), resolution.getHeight(),
                sensorOrientation, MAINTAIN_ASPECT);

    }


    private void renderDepthMap(Canvas canvas) {
        if (outputDispResized != null) {
            canvas.drawBitmap(outputRGB, null, new Rect(0, 0,
                    halfScreenSize.getWidth(), halfScreenSize.getHeight()), null);
            canvas.drawBitmap(outputDispResized, 0, halfScreenSize.getHeight(), null);
        }
    }

    private FloatBuffer doInference() {
        return currentModel.doInference(croppedFrame, resolution, scale);
    }


    private void renderInferredFloatBuffer() {

        FloatBuffer inference = currentModel.doInference(croppedFrame, resolution, scale);

        int[] coloredInference = colorMapper.applyColorMap(inference, NUMBER_THREADS);

        outputDisp.setPixels(coloredInference, 0, resolution.getWidth(), 0, 0, resolution.getWidth(), resolution.getHeight());

        outputDispResized = Bitmap.createScaledBitmap(outputDisp, halfScreenSize.getWidth(), halfScreenSize.getHeight(), false);

        outputRGB = Bitmap.createScaledBitmap(croppedFrame, halfScreenSize.getWidth(), halfScreenSize.getHeight(), false);

        Timber.d("renderInferredFloatBuffer_end");
    }


    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.take_photo) {
            Toast.makeText(this, "load depth map...", Toast.LENGTH_SHORT).show();

            long timeStart = System.currentTimeMillis();

            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.sample_image);

            int[] allPixels = new int[bitmap.getHeight() * bitmap.getWidth()];

            bitmap.getPixels(allPixels, 0, previewWidth, 0, 0, previewWidth, previewHeight);
            originalFrame.setPixels(allPixels, 0, previewWidth, 0, 0, previewWidth, previewHeight);
            final Canvas canvas = new Canvas(croppedFrame);
            canvas.drawBitmap(originalFrame, frameToCropTransform, null);

            progressBar.setVisibility(View.VISIBLE);

            Thread thread = new Thread(() -> {
                Timber.d("thread_run");

                doInference();

                FloatBuffer inferred = doInference();
                Timber.d("inferred\n%s", inferred);

                try {
                    renderInferredFloatBuffer();
                    runOnUiThread(() -> {
                        depthImageView.setImageBitmap(outputDisp);
                        progressBar.setVisibility(View.GONE);

                        long timeEnd = System.currentTimeMillis();
                        long duration = timeEnd - timeStart;
                        Timber.d("duration_milliseconds: %s", duration);
                        String text = "Duration: " + (duration / 1000f) + "sec";
                        durationTextView.setText(text);

                    });
                } catch (Exception e) {
                    Timber.e(e);
                    runOnUiThread(() -> {
                        Toast.makeText(MyActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                    });
                }

            });

            thread.start();


        } else if (v.getId() == R.id.open_camera) {
            Intent intent = new Intent(MyActivity.this, StreamActivity.class);
            startActivity(intent);
        }
    }
}
