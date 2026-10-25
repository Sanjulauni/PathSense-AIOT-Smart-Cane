package com.example.smart_cane;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class OutdoorNavigationActivity extends FragmentActivity implements OnMapReadyCallback {

    // Map & Location
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private Location lastKnownLocation;

    // UI Elements
    private EditText etSearchPlace;
    private Button btnSearch;
    private TextView tvCurrentLocation;
    private TextView tvNavigationInstruction;
    private TextView tvDistance;
    private CardView navigationCard;
    private ProgressBar progressBar;

    // TTS
    private TextToSpeech textToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outdoor_navigation);

        // 1. Initialize Views
        etSearchPlace = findViewById(R.id.etSearchPlace);
        btnSearch = findViewById(R.id.btnSearch);
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation);
        tvNavigationInstruction = findViewById(R.id.tvNavigationInstruction);
        tvDistance = findViewById(R.id.tvDistance);
        navigationCard = findViewById(R.id.navigationCard);
        progressBar = findViewById(R.id.progressBar);

        // 2. Initialize Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 3. Initialize Map Fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // 4. Setup Text To Speech
        setupTextToSpeech();

        // 5. Search Button Listener
        btnSearch.setOnClickListener(v -> {
            String location = etSearchPlace.getText().toString();
            if (!location.isEmpty()) {
                searchLocation(location);
            } else {
                speak("Please enter a place name");
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Check Permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);

        // Get User Location
        getDeviceLocation();
    }

    private void getDeviceLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                lastKnownLocation = location;
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

                // Move Camera
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));

                // Update the Text View with Address
                updateCurrentLocationText(location);
                speak("Location found.");
            } else {
                tvCurrentLocation.setText("Location unavailable. Make sure GPS is on.");
            }
        });
    }

    private void updateCurrentLocationText(Location location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                String address = addresses.get(0).getAddressLine(0);
                // Cut string if too long for UI
                if (address.length() > 50) address = address.substring(0, 50) + "...";
                tvCurrentLocation.setText(address);
            } else {
                tvCurrentLocation.setText("Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude());
            }
        } catch (IOException e) {
            tvCurrentLocation.setText("Address unavailable (Network error)");
        }
    }

    private void searchLocation(String locationName) {
        progressBar.setVisibility(View.VISIBLE);
        speak("Searching for " + locationName);

        Geocoder geocoder = new Geocoder(OutdoorNavigationActivity.this, Locale.getDefault());
        try {
            List<Address> addressList = geocoder.getFromLocationName(locationName, 1);

            if (addressList != null && !addressList.isEmpty()) {
                Address address = addressList.get(0);
                LatLng destinationLatLng = new LatLng(address.getLatitude(), address.getLongitude());

                // 1. Update Map
                mMap.clear(); // Remove old markers
                mMap.addMarker(new MarkerOptions().position(destinationLatLng).title(locationName));
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destinationLatLng, 15));

                // 2. Calculate Distance
                float distanceInMeters = 0;
                if (lastKnownLocation != null) {
                    Location destLocation = new Location("Destination");
                    destLocation.setLatitude(address.getLatitude());
                    destLocation.setLongitude(address.getLongitude());
                    distanceInMeters = lastKnownLocation.distanceTo(destLocation);
                }

                // 3. Update UI Card
                navigationCard.setVisibility(View.VISIBLE);
                tvNavigationInstruction.setText("Destination: " + locationName);
                tvDistance.setText(String.format(Locale.US, "Distance: %.0f meters", distanceInMeters));

                speak("Found " + locationName + ". Distance is " + (int)distanceInMeters + " meters.");

            } else {
                speak("Could not find " + locationName);
                Toast.makeText(this, "Place not found", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e("MapSearch", "Geocoder failed", e);
            speak("Network error. Please check internet.");
        } finally {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });
    }

    // --- THIS IS THE MISSING METHOD THAT CAUSED THE ERROR ---
    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation();
        } else {
            speak("Permission denied. Cannot show location.");
        }
    }

    private void enableUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            getDeviceLocation();
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}