package com.hjoel.pod;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;
import android.os.Environment;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends android.app.Activity {

    private static final int CAMERA_REQUEST = 3001;
    private static final int UPLOAD_REQUEST = 3002;
    private static final int CAMERA_PERMISSION_REQUEST = 3003;
    private static final int MEDIA_PERMISSION_REQUEST = 3004;

    private static final String WEB_APP_URL = "https://script.google.com/macros/s/AKfycbxAKv3UnWrFM2R4s1h0EXrELPpxOwH6ctTrUcOj9h-ExWQkBj_Y_ivnJ86m4QaOhPxV/exec";
    private static final String TIMESTAMP_PACKAGE = "com.jeyluta.timestampcamerafree";

    private WebView webView;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private int pendingPhotoSlot = 0;
    private Uri cameraOutputUri;
    private boolean waitingTimestamp = false;
    private boolean timestampPaused = false;
    private long timestampStartedAt = 0L;
    private boolean pendingTimestampPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setGeolocationEnabled(true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (checkSelfPermission(Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        requestPermissions(
                                new String[]{Manifest.permission.CAMERA},
                                CAMERA_PERMISSION_REQUEST
                        );
                    }
                });
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin,
                    GeolocationPermissions.Callback callback) {

                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                } else {
                    requestPermissions(
                            new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                            },
                            3010
                    );
                    callback.invoke(origin, true, false);
                }
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void loadAreas() {
            io.execute(() -> {
                try {
                    String text = httpGet(WEB_APP_URL + "?action=getAreas");
                    JSONObject obj = new JSONObject(text);
                    JSONArray arr = obj.optJSONArray("areas");
                    if (arr == null) arr = new JSONArray();
                    final String json = arr.toString();
                    runOnUiThread(() ->
                        webView.evaluateJavascript("setAreas(" + JSONObject.quote(json) + ")", null)
                    );
                } catch (Exception e) {
                    runOnUiThread(() ->
                        webView.evaluateJavascript("setAreas('[]')", null)
                    );
                }
            });
        }

        @JavascriptInterface
        public void savePOD(String json) {
            io.execute(() -> {
                try {
                    String response = httpPost(WEB_APP_URL, json);
                    JSONObject obj = new JSONObject(response);
                    boolean success = obj.optBoolean("success", false);
                    String message = obj.optString(
                            "message",
                            success ? "POD berhasil disimpan." : "Gagal menyimpan POD."
                    );

                    runOnUiThread(() -> {
                        String js = "saveResult(" + success + "," + JSONObject.quote(message) + ")";
                        webView.evaluateJavascript(js, null);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        String js = "saveResult(false," +
                                JSONObject.quote("Gagal menyimpan: " + e.getMessage()) + ")";
                        webView.evaluateJavascript(js, null);
                    });
                }
            });
        }

        @JavascriptInterface
        public void savePhotosToGallery(String awb, String photosJson) {
            io.execute(() -> {
                int saved = 0;

                try {
                    JSONObject obj = new JSONObject(photosJson);
                    String safeAwb = String.valueOf(awb == null ? "" : awb)
                            .replaceAll("[^A-Za-z0-9_-]", "_");

                    for (int i = 1; i <= 4; i++) {
                        String dataUrl = obj.optString("foto" + i, "");
                        if (dataUrl == null || dataUrl.isEmpty()) continue;

                        String name;
                        if (i == 1) name = "UNIT_PENERIMA";
                        else if (i == 2) name = "PLANG_SEKOLAH";
                        else if (i == 3) name = "BAST";
                        else name = "SERIAL_NUMBER";

                        if (saveDataUrlToGallery(
                                dataUrl,
                                "HJOEL_" + safeAwb + "_" + name + "_" + System.currentTimeMillis() + ".jpg"
                        )) {
                            saved++;
                        }
                    }

                    final int totalSaved = saved;

                    runOnUiThread(() -> Toast.makeText(
                            MainActivity.this,
                            totalSaved + " foto POD masuk ke Galeri.",
                            Toast.LENGTH_LONG
                    ).show());

                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(
                            MainActivity.this,
                            "Data POD tersimpan, tetapi foto gagal disalin ke Galeri.",
                            Toast.LENGTH_LONG
                    ).show());
                }
            });
        }

        @JavascriptInterface
        public void choosePhoto(int slot) {
            runOnUiThread(() -> {
                pendingPhotoSlot = slot;
                showPhotoSourceDialog();
            });
        }
    }

    private void showPhotoSourceDialog() {
        String[] options = new String[] {
                "Timestamp Camera",
                "Kamera HP",
                "Upload Foto"
        };

        new AlertDialog.Builder(this)
                .setTitle("Pilih sumber foto")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) prepareTimestampCamera();
                    else if (which == 1) launchNormalCamera();
                    else launchUpload();
                })
                .show();
    }

    private void prepareTimestampCamera() {
        if (!hasMediaPermission()) {
            pendingTimestampPermission = true;
            requestMediaPermission();
            return;
        }
        launchTimestampCamera();
    }

    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                    MEDIA_PERMISSION_REQUEST
            );
        } else {
            requestPermissions(
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    MEDIA_PERMISSION_REQUEST
            );
        }
    }

    private void launchTimestampCamera() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(TIMESTAMP_PACKAGE);
        if (launch == null) {
            return;
        }

        timestampStartedAt = System.currentTimeMillis();
        waitingTimestamp = true;
        timestampPaused = false;
        startActivity(launch);
    }

    private void launchNormalCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST
            );
            return;
        }

        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraOutputUri = createCameraOutputUri();

        if (cameraOutputUri != null) {
            camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri);
            camera.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        }

        startActivityForResult(camera, CAMERA_REQUEST);
    }

    private void launchUpload() {
        Intent upload = new Intent(Intent.ACTION_GET_CONTENT);
        upload.addCategory(Intent.CATEGORY_OPENABLE);
        upload.setType("image/*");
        startActivityForResult(
                Intent.createChooser(upload, "Pilih Foto"),
                UPLOAD_REQUEST
        );
    }

    private Uri createCameraOutputUri() {
        try {
            ContentValues values = new ContentValues();
            values.put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "HJOEL_POD_" + System.currentTimeMillis() + ".jpg"
            );
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

            return getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
            );
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (waitingTimestamp) {
            timestampPaused = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (waitingTimestamp && timestampPaused) {
            waitingTimestamp = false;
            timestampPaused = false;

            new Handler().postDelayed(() -> {
                Uri latest = findNewestImageSince(timestampStartedAt);
                if (latest != null) {
                    sendImageToHtml(pendingPhotoSlot, latest);
                }
            }, 900);
        }
    }

    private Uri findNewestImageSince(long startedAtMillis) {
        long minimumSeconds = (startedAtMillis / 1000L) - 5L;

        String[] projection = new String[] {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
        };

        String selection = MediaStore.Images.Media.DATE_ADDED + " >= ?";
        String[] args = new String[] { String.valueOf(minimumSeconds) };
        String sort = MediaStore.Images.Media.DATE_ADDED + " DESC";

        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    args,
                    sort
            );

            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                );

                return ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                );
            }
        } finally {
            if (cursor != null) cursor.close();
        }

        return null;
    }

    private void sendImageToHtml(int slot, Uri uri) {
        io.execute(() -> {
            try {
                String dataUrl = uriToDataUrl(uri);
                runOnUiThread(() -> {
                    String js = "setPhotoFromAndroid(" + slot + "," +
                            JSONObject.quote(dataUrl) + ")";
                    webView.evaluateJavascript(js, null);
                });
            } catch (Exception ignored) {}
        });
    }

    private String uriToDataUrl(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;

        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }

        in.close();

        String b64 = Base64.encodeToString(
                out.toByteArray(),
                Base64.NO_WRAP
        );

        return "data:image/jpeg;base64," + b64;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        boolean granted =
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED;

        if (requestCode == MEDIA_PERMISSION_REQUEST &&
                granted &&
                pendingTimestampPermission) {

            pendingTimestampPermission = false;
            launchTimestampCamera();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == CAMERA_REQUEST) {
            Uri result = null;

            if (resultCode == RESULT_OK) {
                if (data != null && data.getData() != null) {
                    result = data.getData();
                } else {
                    result = cameraOutputUri;
                }
            }

            if (result != null) {
                sendImageToHtml(pendingPhotoSlot, result);
            }

            cameraOutputUri = null;
            return;
        }

        if (requestCode == UPLOAD_REQUEST &&
                resultCode == RESULT_OK &&
                data != null &&
                data.getData() != null) {

            sendImageToHtml(
                    pendingPhotoSlot,
                    data.getData()
            );
        }
    }


    private boolean saveDataUrlToGallery(String dataUrl, String fileName) {
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0) return false;

            String base64 = dataUrl.substring(comma + 1);
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/H JOEL POD"
                );
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }

            Uri uri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
            );

            if (uri == null) return false;

            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) return false;

            os.write(bytes);
            os.flush();
            os.close();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(uri, done, null, null);
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private String httpGet(String urlText) throws Exception {
        HttpURLConnection con = (HttpURLConnection)new URL(urlText).openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(15000);
        con.setReadTimeout(15000);

        BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8)
        );

        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            sb.append(line);
        }

        br.close();
        con.disconnect();

        return sb.toString();
    }

    private String httpPost(String urlText, String json) throws Exception {
        HttpURLConnection con = (HttpURLConnection)new URL(urlText).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setConnectTimeout(30000);
        con.setReadTimeout(30000);
        con.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");

        OutputStream os = con.getOutputStream();
        os.write(json.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8)
        );

        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            sb.append(line);
        }

        br.close();
        con.disconnect();

        return sb.toString();
    }
}
