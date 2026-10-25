package com.example.smart_cane;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

public class IndoorNavigationActivity extends AppCompatActivity implements SensorEventListener {

    // UI Components
    private TextView tvSteps, tvDirections, tvMarkerCount;
    private Spinner spinnerSource, spinnerDestination;
    private Button btnNavigate;

    // Sensors & TTS
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private int stepCount = 0;
    private TextToSpeech textToSpeech;

    // Navigation Data Structures
    private Map<Integer, String> locationNames = new HashMap<>();
    private Map<Integer, List<Edge>> adjacencyList = new HashMap<>();

    // State Machine for Active Navigation
    private boolean isNavigating = false;
    private List<RouteStep> currentRoute = new ArrayList<>();
    private int currentRouteStepIndex = 0;
    private int markersLeftForCurrentSegment = 0;

    // Broadcast Receiver for UDP 'track' messages
    private final BroadcastReceiver trackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiUdpService.ACTION_TRACK_DETECTED.equals(intent.getAction()) && isNavigating) {
                handleTrackDetected();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_indoor_navigation);

        // Initialize UI
        tvSteps = findViewById(R.id.tvStepCount);
        tvDirections = findViewById(R.id.tvDirections);
        tvMarkerCount = findViewById(R.id.tvMarkerCount);
        spinnerSource = findViewById(R.id.spinnerSource);
        spinnerDestination = findViewById(R.id.spinnerDestination);
        btnNavigate = findViewById(R.id.btnNavigate);

        setupTextToSpeech();
        initializeMap();
        setupSpinners();

        // Sensor Setup
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        if (stepSensor == null) {
            tvSteps.setText("No Step Sensor");
        } else {
            speak("Indoor Navigation Active.");
        }

        // Button Listener
        btnNavigate.setOnClickListener(v -> calculateAndStartNavigation());
    }

    // --- Graph Map Setup ---

    private void initializeMap() {
        // 1. Define Locations (Nodes)
        locationNames.put(0, "Entrance");
        locationNames.put(1, "Lobby");
        locationNames.put(2, "Corridor A");
        locationNames.put(3, "Corridor B");
        locationNames.put(4, "Office");
        locationNames.put(5, "Toilet");
        locationNames.put(6, "Cafeteria");

        // 2. Define Paths (Edges) with Marker Distances
        // Example: Entrance to Lobby requires passing 3 'track' markers
        addEdge(0, 1, "Walk Forward to the Lobby", 3);
        addEdge(1, 0, "Walk Backwards to the Entrance", 3);

        addEdge(1, 2, "Turn Left and Walk Forward to Corridor A", 2);
        addEdge(2, 1, "Turn Right and Walk Forward to Lobby", 2);

        addEdge(1, 3, "Turn Right and Walk Forward to Corridor B", 4);
        addEdge(3, 1, "Turn Left and Walk Forward to Lobby", 4);

        addEdge(2, 5, "Walk Straight to the end for the Toilet", 5);
        addEdge(5, 2, "Walk Straight back to Corridor A", 5);

        addEdge(2, 4, "Turn Right into the Office", 1);
        addEdge(4, 2, "Turn Left into Corridor A", 1);

        addEdge(3, 6, "Walk Straight into the Cafeteria", 3);
        addEdge(6, 3, "Walk Straight back to Corridor B", 3);
    }

    private void addEdge(int source, int dest, String instruction, int markerDistance) {
        adjacencyList.putIfAbsent(source, new ArrayList<>());
        adjacencyList.get(source).add(new Edge(dest, instruction, markerDistance));
    }

    private void setupSpinners() {
        List<String> orderedLocations = new ArrayList<>();
        for(int i = 0; i < locationNames.size(); i++) {
            orderedLocations.add(locationNames.get(i));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, orderedLocations);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerSource.setAdapter(adapter);
        spinnerDestination.setAdapter(adapter);

        spinnerSource.setSelection(0); // Entrance
        spinnerDestination.setSelection(5); // Toilet
    }

    // --- Navigation & Pathfinding Logic ---

    private void calculateAndStartNavigation() {
        int startId = spinnerSource.getSelectedItemPosition();
        int endId = spinnerDestination.getSelectedItemPosition();

        if (startId == endId) {
            String msg = "You are already at the destination.";
            tvDirections.setText(msg);
            speak(msg);
            return;
        }

        currentRoute = dijkstraFindPath(startId, endId);

        if (currentRoute != null && !currentRoute.isEmpty()) {
            isNavigating = true;
            currentRouteStepIndex = 0;
            tvMarkerCount.setVisibility(View.VISIBLE);

            // Generate full route text for UI
            StringBuilder fullPathText = new StringBuilder();
            fullPathText.append("Route calculated from ").append(locationNames.get(startId)).append(".\n\n");
            for (RouteStep step : currentRoute) {
                fullPathText.append("• ").append(step.instruction)
                        .append(" (").append(step.markerDistance).append(" markers)\n");
            }
            tvDirections.setText(fullPathText.toString());

            // Start first segment
            announceCurrentStep();

        } else {
            isNavigating = false;
            tvMarkerCount.setVisibility(View.GONE);
            String msg = "No path found between these locations.";
            tvDirections.setText(msg);
            speak(msg);
        }
    }

    // Dijkstra's Algorithm based on marker distances
    private List<RouteStep> dijkstraFindPath(int start, int end) {
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1])); // [nodeId, totalDistance]
        Map<Integer, Integer> distances = new HashMap<>();
        Map<Integer, Integer> previousNode = new HashMap<>();
        Map<Integer, Edge> edgeUsed = new HashMap<>();

        // Initialize distances
        for (int node : locationNames.keySet()) {
            distances.put(node, Integer.MAX_VALUE);
        }
        distances.put(start, 0);
        pq.add(new int[]{start, 0});

        while (!pq.isEmpty()) {
            int[] current = pq.poll();
            int currNode = current[0];

            if (currNode == end) break;

            if (adjacencyList.containsKey(currNode)) {
                for (Edge edge : adjacencyList.get(currNode)) {
                    int newDist = distances.get(currNode) + edge.markerDistance;
                    if (newDist < distances.get(edge.targetNode)) {
                        distances.put(edge.targetNode, newDist);
                        previousNode.put(edge.targetNode, currNode);
                        edgeUsed.put(edge.targetNode, edge);
                        pq.add(new int[]{edge.targetNode, newDist});
                    }
                }
            }
        }

        if (distances.get(end) == Integer.MAX_VALUE) return null; // Unreachable

        // Reconstruct path
        List<RouteStep> path = new LinkedList<>();
        int curr = end;
        while (curr != start) {
            Edge e = edgeUsed.get(curr);
            path.add(0, new RouteStep(curr, e.instruction, e.markerDistance));
            curr = previousNode.get(curr);
        }
        return path;
    }

    // --- Real-time Tracking Logic ---

    private void announceCurrentStep() {
        if (currentRouteStepIndex < currentRoute.size()) {
            RouteStep step = currentRoute.get(currentRouteStepIndex);
            markersLeftForCurrentSegment = step.markerDistance;
            updateMarkerUI();

            String msg = step.instruction + ". Follow the track for " + markersLeftForCurrentSegment + " markers.";
            speak(msg);
        } else {
            isNavigating = false;
            tvMarkerCount.setVisibility(View.GONE);
            String msg = "You have arrived at your destination: " + locationNames.get(spinnerDestination.getSelectedItemPosition());
            tvDirections.setText(msg);
            speak(msg);
        }
    }

    private void handleTrackDetected() {
        if (markersLeftForCurrentSegment > 0) {
            markersLeftForCurrentSegment--;
            updateMarkerUI();

            if (markersLeftForCurrentSegment == 0) {
                // Reached the node, move to next step
                currentRouteStepIndex++;
                announceCurrentStep();
            } else if (markersLeftForCurrentSegment == 2) {
                // Optional: Provide reassurance feedback
                speak("2 markers remaining.");
            } else {
                // Optional: Small beep or haptic feedback per marker could go here
                // speak(String.valueOf(markersLeftForCurrentSegment));
            }
        }
    }

    private void updateMarkerUI() {
        runOnUiThread(() -> tvMarkerCount.setText("Markers Left: " + markersLeftForCurrentSegment));
    }

    // --- Helper Classes ---

    private static class Edge {
        int targetNode;
        String instruction;
        int markerDistance; // Number of 'track' markers to this node

        Edge(int targetNode, String instruction, int markerDistance) {
            this.targetNode = targetNode;
            this.instruction = instruction;
            this.markerDistance = markerDistance;
        }
    }

    private static class RouteStep {
        int targetNode;
        String instruction;
        int markerDistance;

        RouteStep(int targetNode, String instruction, int markerDistance) {
            this.targetNode = targetNode;
            this.instruction = instruction;
            this.markerDistance = markerDistance;
        }
    }

    // --- Sensor, TTS, and Lifecycle Logic ---

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) textToSpeech.setLanguage(Locale.US);
        });
    }

    private void speak(String text) {
        if (textToSpeech != null) textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (stepSensor != null) sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
        // Register Broadcast Receiver for UDP events
        LocalBroadcastManager.getInstance(this).registerReceiver(trackReceiver, new IntentFilter(WifiUdpService.ACTION_TRACK_DETECTED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (stepSensor != null) sensorManager.unregisterListener(this);
        // Unregister Broadcast Receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(trackReceiver);
        if (textToSpeech != null) textToSpeech.stop();
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) textToSpeech.shutdown();
        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            stepCount++;
            tvSteps.setText("Steps: " + stepCount);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}