package com.example.myapplicationcurloc;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;


import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.example.myapplicationcurloc.databinding.ActivityMapsBinding;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Timer;
import java.util.TimerTask;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, LocationListener, SensorEventListener {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    LocationManager locationManager;
    private static final int GPS_TIME_INTERVAL = 1; // get gps location every 1 min
    private static final float GPS_DISTANCE = (float) 0.001; // set the distance value in meter

    final static String[] PERMISSIONS = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    final static int PERMISSION_ALL = 1;
    private Thread thread;
    private SensorManager mSensorManager;
    private float[] orient = new float[3];
    private float[] accel = new float[3];
    private float[] mag = new float[3];
    private float[] accelOrient = new float[3];
    private float[] rotationMatrix = new float[9];
    private float[] gyroOrient = new float[3];
    private float[] fusedOrient = new float[3];
    public static final float epsilon = 0.000000001f;
    private static final float NS2S = 1.0f / 1000000000.0f;
    private float timestamp;
    private boolean initState = true;

    public static final int TIME_CONSTANT = 30;
    public static final float filterCoefficient = 0.98f;
    private ImageView compassimage;
    private float DegreeStart = 0f;
    private long startTime = System.currentTimeMillis();
    SimpleDateFormat format = new SimpleDateFormat("mm:ss.SS");
    private Timer fuseTimer = new Timer();
    private float[] gyro = new float[3];
    private float[] gyroMat = new float[9];
    private long fileLength = 0;
    private double lat = 0;
    private double lon = 0;
    public static final int PERMISSIONS_FINE_LOCATION = 99;
    private float speed = 0f;
    private long timeVal = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(PERMISSIONS, PERMISSION_ALL);
        }
        requestLocation();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                requestLocation();
                handler.post(this::run);
            }
        });

        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        compassimage = (ImageView) findViewById(R.id.compass_image);

        Log.d("CREATION", "Started============================Time" + startTime);
        gyroOrient[0] = 0.0f;
        gyroOrient[1] = 0.0f;
        gyroOrient[2] = 0.0f;

        // initialise gyroMatrix with identity matrix
        gyroMat[0] = 1.0f;
        gyroMat[1] = 0.0f;
        gyroMat[2] = 0.0f;
        gyroMat[3] = 0.0f;
        gyroMat[4] = 1.0f;
        gyroMat[5] = 0.0f;
        gyroMat[6] = 0.0f;
        gyroMat[7] = 0.0f;
        gyroMat[8] = 1.0f;
        fuseTimer.scheduleAtFixedRate(new getGyroscopeOrientation(),
                1000, TIME_CONSTANT);
        try {

            File root = new File(Environment.getExternalStorageDirectory(), "Notes");
            Log.d("tag", "root=====" + root);
            if (!root.exists()) {
                root.mkdirs();
            }
            File gpxfile = new File(root, "output.csv");
            Log.d("tag", "file=====" + gpxfile);
            if (gpxfile.exists()) {
                Log.d("tag", "root=====" + root);
                Log.d("tag", "file=====" + root.listFiles().length);
                fileLength = root.listFiles().length;
            }
            runTimer();
        } catch (Exception e) {
            e.printStackTrace();

        }
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        updateLocationUI();
    }
    private void updateLocationUI() {
        Log.d("Locaton==============","map"+mMap);
        if (mMap == null) {
            return;
        }
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
//
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        Log.d("mylog", "Got Location: " + location.getLatitude() + ", " + location.getLongitude());
        lat = location.getLatitude();
        lon = location.getLongitude();
        speed = location.getSpeed();
        timeVal = location.getTime();
        locationManager.removeUpdates(this);
    }
    private void requestLocation() {
        if (locationManager == null)
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        GPS_TIME_INTERVAL, GPS_DISTANCE, this);
            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Handler handler = new Handler();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    requestLocation();
                    handler.post(this::run);
                }
            });

        } else {
            finish();
        }
    }

    private void runTimer()
    {
        Log.d("CREATION","Started=============Timer===============Time"+startTime);
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    collectData();

                }
            }
        }).start();
    }


    @Override
    protected void onPause() {
        super.onPause();

        if (thread != null) {
            thread.interrupt();
        }
        mSensorManager.unregisterListener(this);

    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                20000);
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                20000);
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                20000);
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                20000);
    }
    @Override
    protected void onDestroy() {
        mSensorManager.unregisterListener(MapsActivity.this);
        thread.interrupt();

        super.onDestroy();
    }
    @Override
    public final void onSensorChanged(SensorEvent event) {
        int sensorType = event.sensor.getType();
        Log.d("CREATION", "Sensor type===================================" + sensorType);

        switch (sensorType) {
            case Sensor.TYPE_ORIENTATION:
                System.arraycopy(event.values, 0, orient, 0, 3);
                break;
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, accel , 0, 3);
                getAccelMagOrientation();
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, mag, 0, 3);
                break;
            case Sensor.TYPE_GYROSCOPE:
                gyroFunction(event);
                break;
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void getAccelMagOrientation()
    {
        if(SensorManager.getRotationMatrix(rotationMatrix, null, accel, mag)) {
            SensorManager.getOrientation(rotationMatrix, accelOrient);
        }
    }
    //_____________________collect the data into a file everytime you run the app______________
    public void collectData() {
        try {

            File root = new File(Environment.getExternalStorageDirectory(), "Notes");
            if (!root.exists()) {
                root.mkdirs();
            }
            File gpxfile = new File(root, "output.csv");
            if (gpxfile.exists() && fileLength!=0) {
                gpxfile = new File(root, "output" + String.valueOf(fileLength) + ".csv");
            }

            Log.d("CREATED==============","Files====================");
            FileWriter writer = new FileWriter(gpxfile,true);
            StringBuilder sb = new StringBuilder();
            if(!(gpxfile.length()>0)) {
                sb.append("Time in Seconds");
                sb.append(",");
                sb.append("Acceleration_Z");
                sb.append(",");
                sb.append("OrientSensor_Azimuth");
                sb.append(",");
                sb.append("OrientSensor_Pitch");
                sb.append(",");
                sb.append("OrientSensor_Roll");
                sb.append(",");
                sb.append("FusedOrientation_Azimuth");
                sb.append(",");
                sb.append("FusedOrientation_Pitch");
                sb.append(",");
                sb.append("FusedOrientation_Roll");
                sb.append(",");
                sb.append("AccelOrient_Azimuth");
                sb.append(",");
                sb.append("AccelOrient_Pitch");
                sb.append(",");
                sb.append("AccelOrient_Roll");
                sb.append(",");
                sb.append("Latitude");
                sb.append(",");
                sb.append("Longitude");
                sb.append(",");
                sb.append("Speed");
                sb.append("\n");
            }

            sb.append(format.format(System.currentTimeMillis() - startTime));
            sb.append(",");
            sb.append(String.valueOf(accel[2])!=null?String.valueOf(accel[2]):"");
            sb.append(",");
            sb.append(String.valueOf(orient[0])!=null?String.valueOf(orient[0]):"");
            sb.append(",");
            sb.append(String.valueOf(orient[1])!=null?String.valueOf(orient[1]):"");
            sb.append(",");
            sb.append(String.valueOf(orient[2])!=null?String.valueOf(orient[2]):"");
            sb.append(",");
            sb.append(String.valueOf(Math.toDegrees(fusedOrient[0]))!=null?String.valueOf(Math.toDegrees(fusedOrient[0])):"");
            sb.append(",");
            sb.append(String.valueOf(Math.toDegrees(fusedOrient[1]))!=null?String.valueOf(Math.toDegrees(fusedOrient[1])):"");
            sb.append(",");
            sb.append(String.valueOf(Math.toDegrees(fusedOrient[2]))!=null?String.valueOf(Math.toDegrees(fusedOrient[2])):"");
            sb.append(",");
            sb.append(String.valueOf(Math.toDegrees(accelOrient[0]))!=null?String.valueOf(Math.toDegrees(accelOrient[0])):"");
            sb.append(",");
            sb.append(String.valueOf(Math.toDegrees(accelOrient[1]))!=null?String.valueOf(Math.toDegrees(accelOrient[1])):"");
            sb.append(",");
            sb.append(String.valueOf(Math.toDegrees(accelOrient[2]))!=null?String.valueOf(Math.toDegrees(accelOrient[2])):"");
            sb.append(",");
            sb.append(lat);
            sb.append(",");
            sb.append(lon);
            sb.append(",");
            sb.append(speed);
            sb.append("\n");
            writer.append(sb.toString());
            writer.flush();
            writer.close();
        }
        catch(IOException e)
        {
            e.printStackTrace();

        }
        float degree = (float) Math.toDegrees(fusedOrient[0]);

        RotateAnimation ra = new RotateAnimation(
                DegreeStart,
                -degree,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        ra.setFillAfter(true);
        ra.setDuration(210);
        compassimage.startAnimation(ra);
        DegreeStart = -degree;
        Log.d("CREATED==============","Files====================");
    }

// _______________get the orientation data from the gyroscope______________
    public void gyroFunction(SensorEvent event) {
        if (accelOrient == null)
            return;

        // _______________Initialise the gyroscope rotation matrix_________________________
        if(initState) {
            float[] initMat = new float[9];
            initMat = getRotationMatrixFromOrientation(accelOrient);
            float[] test = new float[3];
            SensorManager.getOrientation(initMat, test);
            gyroMat = matMul(gyroMat, initMat);
            initState = false;
        }

        // _________copy the new gyro values into the gyro array and convert the raw gyro data into a rotation vector__________
        float[] deltaVector = new float[4];
        if(timestamp != 0) {
            final float dT = (event.timestamp - timestamp) * NS2S;
            System.arraycopy(event.values, 0, gyro, 0, 3);
            getRotationVectorFromGyro(gyro, deltaVector, dT / 2.0f);
        }

        // __________save current time for next interval________________
        timestamp = event.timestamp;

        // __________convert rotation vector into rotation matrix____________
        float[] deltaMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);

        // __________apply the new rotation interval on the gyroscope based rotation matrix__________
        gyroMat = matMul(gyroMat, deltaMatrix);

        // __________get gyroscope orientation from the rotation matrix_______________
        SensorManager.getOrientation(gyroMat, gyroOrient);
    }
    private void getRotationVectorFromGyro(float[] gyroValues,
                                           float[] deltaRotationVector,
                                           float timeFactor)
    {
        float[] normValues = new float[3];

        // __________Calculate the angular speed of the sample_____________
        float omegaMagnitude =
                (float)Math.sqrt(gyroValues[0] * gyroValues[0] +
                        gyroValues[1] * gyroValues[1] +
                        gyroValues[2] * gyroValues[2]);

        //___________ Normalizing the rotation vector________________
        if(omegaMagnitude > epsilon) {
            normValues[0] = gyroValues[0] / 50;
            normValues[1] = gyroValues[1] / omegaMagnitude;
            normValues[2] = gyroValues[2] / omegaMagnitude;
        }

        // _______Integrate around this axis with the angular speed by the timestep____________
        float thetaOverTwo = omegaMagnitude * timeFactor;
        float sinThetaOverTwo = (float)Math.sin(thetaOverTwo);
        float cosThetaOverTwo = (float)Math.cos(thetaOverTwo);
        deltaRotationVector[0] = sinThetaOverTwo * normValues[0];
        deltaRotationVector[1] = sinThetaOverTwo * normValues[1];
        deltaRotationVector[2] = sinThetaOverTwo * normValues[2];
        deltaRotationVector[3] = cosThetaOverTwo;
    }
    //_________________get the rotation matrix from the orientation data____________
    private float[] getRotationMatrixFromOrientation(float[] o) {
        float[] xMat = new float[9];
        float[] yMat = new float[9];
        float[] zMat = new float[9];

        float sinX = (float)Math.sin(o[1]);
        float cosX = (float)Math.cos(o[1]);
        float sinY = (float)Math.sin(o[2]);
        float cosY = (float)Math.cos(o[2]);
        float sinZ = (float)Math.sin(o[0]);
        float cosZ = (float)Math.cos(o[0]);

        // rotation about x-axis (pitch)
        xMat[0] = 1.0f; xMat[1] = 0.0f; xMat[2] = 0.0f;
        xMat[3] = 0.0f; xMat[4] = cosX; xMat[5] = sinX;
        xMat[6] = 0.0f; xMat[7] = -sinX; xMat[8] = cosX;

        // rotation about y-axis (roll)
        yMat[0] = cosY; yMat[1] = 0.0f; yMat[2] = sinY;
        yMat[3] = 0.0f; yMat[4] = 1.0f; yMat[5] = 0.0f;
        yMat[6] = -sinY; yMat[7] = 0.0f; yMat[8] = cosY;

        // rotation about z-axis (azimuth)
        zMat[0] = cosZ; zMat[1] = sinZ; zMat[2] = 0.0f;
        zMat[3] = -sinZ; zMat[4] = cosZ; zMat[5] = 0.0f;
        zMat[6] = 0.0f; zMat[7] = 0.0f; zMat[8] = 1.0f;

        // rotation order is y, x, z (roll, pitch, azimuth)
        float[] resultMat = matMul(xMat, yMat);
        resultMat = matMul(zMat, resultMat);
        return resultMat;
    }
//____________Matrix multiplication operation_____________
    private float[] matMul(float[] A, float[] B) {
        float[] resultMat = new float[9];
        int i = 0;
        while(i<9)
        {
            for(int j=0; j<3;j++)
            {
                resultMat[i+j] = A[i]*B[j]+A[i+1]*B[j+3]+A[i+2]*B[j+6];
            }
            i+=3;
        }
        return resultMat;
    }
    class getGyroscopeOrientation extends TimerTask {
        public void run() {
            float accelCoeff = 1.0f - filterCoefficient;

            /*
             * Fix for 179° <--> -179° transition problem:
             * Check whether one of the two orientation angles (gyro or accMag) is negative while the other one is positive.
             * If so, add 360° (2 * math.PI) to the negative value, perform the sensor fusion, and remove the 360° from the result
             * if it is greater than 180°. This stabilizes the output in positive-to-negative-transition cases.
             */

            // azimuth
            if (gyroOrient[0] < -0.5 * Math.PI && accelOrient[0] > 0.0) {
                fusedOrient[0] = (float) (filterCoefficient * (gyroOrient[0] + 2.0 * Math.PI) + accelCoeff * accelOrient[0]);
                fusedOrient[0] -= (fusedOrient[0] > Math.PI) ? 2.0 * Math.PI : 0;
            }
            else if (accelOrient[0] < -0.5 * Math.PI && gyroOrient[0] > 0.0) {
                fusedOrient[0] = (float) (filterCoefficient * gyroOrient[0] + accelCoeff * (accelOrient[0] + 2.0 * Math.PI));
                fusedOrient[0] -= (fusedOrient[0] > Math.PI)? 2.0 * Math.PI : 0;
            }
            else {
                fusedOrient[0] = filterCoefficient * gyroOrient[0] + accelCoeff * accelOrient[0];
            }

            // pitch
            if (gyroOrient[1] < -0.5 * Math.PI && accelOrient[1] > 0.0) {
                fusedOrient[1] = (float) (filterCoefficient * (gyroOrient[1] + 2.0 * Math.PI) + accelCoeff * accelOrient[1]);
                fusedOrient[1] -= (fusedOrient[1] > Math.PI) ? 2.0 * Math.PI : 0;
            }
            else if (accelOrient[1] < -0.5 * Math.PI && gyroOrient[1] > 0.0) {
                fusedOrient[1] = (float) (filterCoefficient * gyroOrient[1] + accelCoeff * (accelOrient[1] + 2.0 * Math.PI));
                fusedOrient[1] -= (fusedOrient[1] > Math.PI)? 2.0 * Math.PI : 0;
            }
            else {
                fusedOrient[1] = filterCoefficient * gyroOrient[1] + accelCoeff * accelOrient[1];
            }

            // roll
            if (gyroOrient[2] < -0.5 * Math.PI && accelOrient[2] > 0.0) {
                fusedOrient[2] = (float) (filterCoefficient * (gyroOrient[2] + 2.0 * Math.PI) + accelCoeff * accelOrient[2]);
                fusedOrient[2] -= (fusedOrient[2] > Math.PI) ? 2.0 * Math.PI : 0;
            }
            else if (accelOrient[2] < -0.5 * Math.PI && gyroOrient[2] > 0.0) {
                fusedOrient[2] = (float) (filterCoefficient * gyroOrient[2] + accelCoeff * (accelOrient[2] + 2.0 * Math.PI));
                fusedOrient[2] -= (fusedOrient[2] > Math.PI)? 2.0 * Math.PI : 0;
            }
            else {
                fusedOrient[2] = filterCoefficient * gyroOrient[2] + accelCoeff * accelOrient[2];
            }

            // overwrite gyro matrix and orientation with fused orientation
            // to compensate gyro drift
            gyroMat = getRotationMatrixFromOrientation(fusedOrient);
            System.arraycopy(fusedOrient, 0, gyroOrient, 0, 3);
        }
    }
}