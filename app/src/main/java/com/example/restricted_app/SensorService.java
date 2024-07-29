package com.example.restricted_app;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import androidx.annotation.Nullable;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SensorService extends Service implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor rotationSensor, linearAccelerationSensor;
    private final int MEASUREMENT_DURATION = 15000; // 15 seconds
    private long startTime;
    private List<Float> roYValues, laZValues, roXValues, roZValues, laXValues, roMagValues, laMagValues, laYValues;
    private Interpreter tflite;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        roYValues = new ArrayList<>();
        laZValues = new ArrayList<>();
        roXValues = new ArrayList<>();
        roZValues = new ArrayList<>();
        laXValues = new ArrayList<>();
        roMagValues = new ArrayList<>();
        laMagValues = new ArrayList<>();
        laYValues = new ArrayList<>();

        try {
            tflite = new Interpreter(loadModelFile("model.tflite"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private MappedByteBuffer loadModelFile(String modelPath) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(getAssets().openFd(modelPath).getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = getAssets().openFd(modelPath).getStartOffset();
        long declaredLength = getAssets().openFd(modelPath).getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        startTime = System.currentTimeMillis();

        new Handler().postDelayed(this::calculateAndSendResult, MEASUREMENT_DURATION);

        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            roYValues.add(event.values[1]);
            roXValues.add(event.values[0]);
            roZValues.add(event.values[2]);
            roMagValues.add(calculateMagnitude(event.values));
        } else if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            laZValues.add(event.values[2]);
            laXValues.add(event.values[0]);
            laYValues.add(event.values[1]);
            laMagValues.add(calculateMagnitude(event.values));
        }
    }

    private void calculateAndSendResult() {
        float roYMax = Collections.max(roYValues);
        float roYMin = Collections.min(roYValues);
        float roYMean = calculateMean(roYValues);
        float laZMean = calculateMean(laZValues);
        float roXMax = Collections.max(roXValues);
        float roXMin = Collections.min(roXValues);
        float roZMax = Collections.max(roZValues);
        float roXMean = calculateMean(roXValues);
        float laXMean = calculateMean(laXValues);
        float roZMin = Collections.min(roZValues);
        float roZMean = calculateMean(roZValues);
        float roMagMean = calculateMean(roMagValues);
        float roMagMin = Collections.min(roMagValues);
        float roMagMax = Collections.max(roMagValues);
        float laMagMean = calculateMean(laMagValues);
        float laYMean = calculateMean(laYValues);
        float laXMin = Collections.min(laXValues);
        float laMagMin = Collections.min(laMagValues);
        float roMagStd = calculateStandardDeviation(roMagValues);
        float roMagRmse = calculateRMSE(roMagValues);

        float[] inputValues = {
                roYMax, roYMin, roYMean, laZMean, roXMax, roXMin, roZMax, roXMean, laXMean, roZMin,
                roZMean, roMagMean, roMagMin, roMagMax, laMagMean, laYMean, laXMin, laMagMin,
                roMagStd, roMagRmse
        };

        // Prepare input for the model
        float[][] input = new float[1][inputValues.length];
        System.arraycopy(inputValues, 0, input[0], 0, inputValues.length);

        // Prepare output for the model
        float[][] output = new float[1][1];

        // Run the model
        tflite.run(input, output);

        boolean isKid = output[0][0] > 0.5;  // Assuming a threshold of 0.5 for binary classification

        // Broadcast the result
        Intent resultIntent = new Intent("com.example.prediction.SENSOR_VALUES");
        resultIntent.putExtra("isKid", isKid);
        sendBroadcast(resultIntent);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private float calculateMean(List<Float> values) {
        float sum = 0;
        for (float value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private float calculateStandardDeviation(List<Float> values) {
        float mean = calculateMean(values);
        float sum = 0;
        for (float value : values) {
            sum += Math.pow(value - mean, 2);
        }
        return (float) Math.sqrt(sum / values.size());
    }

    private float calculateRMSE(List<Float> values) {
        float sum = 0;
        for (float value : values) {
            sum += Math.pow(value, 2);
        }
        return (float) Math.sqrt(sum / values.size());
    }

    private float calculateMagnitude(float[] values) {
        return (float) Math.sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2]);
    }
}
