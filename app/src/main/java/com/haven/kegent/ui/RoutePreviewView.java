package com.haven.kegent.ui;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.haven.kegent.R;
import com.haven.kegent.core.RunRoute;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Route preview with OSM tiles first and a readable offline fallback. */
public final class RoutePreviewView extends View {
    private static final int TILE_SIZE = 256;
    private static final int MIN_ZOOM = 10;
    private static final int MAX_ZOOM = 18;
    private static final long TILE_CACHE_MAX_AGE_MILLIS = 7L * 24L * 60L * 60L * 1000L;
    private static final ExecutorService TILE_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Bitmap> MEMORY_TILES = new LinkedHashMap<String, Bitmap>(48, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > 48;
        }
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Map<String, LoadedTile> loadedTiles = new HashMap<>();
    private RunRoute route;
    private ValueAnimator movement;
    private double targetDistance;
    private double traveled;
    private double minLon, maxLon, minLat, maxLat;
    private double zoom, worldSize, centerWorldX, centerWorldY, offsetX, offsetY;
    private int tileGeneration;
    private boolean hasOnlineTile;

    public RoutePreviewView(Context c) {
        super(c);
        setMinimumHeight(Ui.dp(c, 190));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setRoute(RunRoute route) {
        cancelMovement();
        this.route = route;
        targetDistance = 0;
        traveled = 0;
        loadedTiles.clear();
        hasOnlineTile = false;
        updateDescription();
        post(this::scheduleTileLoad);
        invalidate();
    }

    public void setDistance(double distance, boolean animate) {
        if (targetDistance == distance && (traveled == distance || movement != null)) return;
        cancelMovement();
        targetDistance = distance;
        if (!animate || !ValueAnimator.areAnimatorsEnabled() || !isLaidOut() || !isShown()) {
            traveled = distance;
            invalidate();
            return;
        }
        double from = traveled;
        movement = ValueAnimator.ofFloat(0, 1);
        movement.setDuration(220);
        movement.setInterpolator(new DecelerateInterpolator(2));
        movement.addUpdateListener(
                animation -> {
                    traveled = from + (distance - from) * (float) animation.getAnimatedValue();
                    invalidate();
                });
        movement.start();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        scheduleTileLoad();
    }

    private void scheduleTileLoad() {
        if (route == null || route.size() == 0 || getWidth() <= 0 || getHeight() <= 0) return;
        calculateProjection();
        final int generation = ++tileGeneration;
        loadedTiles.clear();
        hasOnlineTile = false;
        Map<String, TileRef> requested = visibleTiles();
        List<TileRef> missing = new ArrayList<>();
        for (TileRef ref : requested.values()) {
            Bitmap cached = getMemoryTile(ref.key());
            if (cached == null) cached = readDiskTile(ref);
            if (cached != null) {
                putLoaded(ref, cached);
                hasOnlineTile = true;
            } else {
                missing.add(ref);
            }
        }
        updateDescription();
        invalidate();
        for (TileRef ref : missing) {
            final TileRef requestedTile = ref;
            TILE_EXECUTOR.execute(
                    () -> {
                        Bitmap bitmap = fetchTile(requestedTile);
                        if (bitmap == null) return;
                        MAIN.post(
                                () -> {
                                    if (generation != tileGeneration || route == null) return;
                                    putLoaded(requestedTile, bitmap);
                                    hasOnlineTile = true;
                                    updateDescription();
                                    invalidate();
                                });
                    });
        }
    }

    private void calculateProjection() {
        minLon = Double.MAX_VALUE;
        maxLon = -Double.MAX_VALUE;
        minLat = Double.MAX_VALUE;
        maxLat = -Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            RunRoute.Point p = route.get(i);
            minLon = Math.min(minLon, p.longitude);
            maxLon = Math.max(maxLon, p.longitude);
            minLat = Math.min(minLat, p.latitude);
            maxLat = Math.max(maxLat, p.latitude);
        }
        double fitWidth = Math.max(80, getWidth() * 0.66);
        double fitHeight = Math.max(80, getHeight() * 0.66);
        zoom = MIN_ZOOM;
        for (int candidate = MAX_ZOOM; candidate >= MIN_ZOOM; candidate--) {
            double spanX = projectX(maxLon, candidate) - projectX(minLon, candidate);
            double spanY = projectY(minLat, candidate) - projectY(maxLat, candidate);
            if (spanX <= fitWidth && spanY <= fitHeight) {
                zoom = candidate;
                break;
            }
        }
        worldSize = TILE_SIZE * Math.pow(2, zoom);
        double left = projectX(minLon, zoom);
        double right = projectX(maxLon, zoom);
        double top = projectY(maxLat, zoom);
        double bottom = projectY(minLat, zoom);
        centerWorldX = (left + right) * 0.5;
        centerWorldY = (top + bottom) * 0.5;
        offsetX = getWidth() * 0.5 - centerWorldX;
        offsetY = getHeight() * 0.5 - centerWorldY;
    }

    private Map<String, TileRef> visibleTiles() {
        Map<String, TileRef> result = new LinkedHashMap<>();
        double left = centerWorldX - getWidth() * 0.5;
        double right = centerWorldX + getWidth() * 0.5;
        double top = centerWorldY - getHeight() * 0.5;
        double bottom = centerWorldY + getHeight() * 0.5;
        int tileCount = 1 << (int) zoom;
        int minTileX = (int) Math.floor(left / TILE_SIZE) - 1;
        int maxTileX = (int) Math.floor(right / TILE_SIZE) + 1;
        int minTileY = Math.max(0, (int) Math.floor(top / TILE_SIZE) - 1);
        int maxTileY = Math.min(tileCount - 1, (int) Math.floor(bottom / TILE_SIZE) + 1);
        for (int y = minTileY; y <= maxTileY; y++) {
            for (int rawX = minTileX; rawX <= maxTileX; rawX++) {
                int x = ((rawX % tileCount) + tileCount) % tileCount;
                TileRef ref = new TileRef((int) zoom, x, y);
                result.put(ref.key(), ref);
            }
        }
        return result;
    }

    private Bitmap fetchTile(TileRef ref) {
        Bitmap cached = readDiskTile(ref);
        if (cached != null) {
            putMemoryTile(ref.key(), cached);
            return cached;
        }
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://tile.openstreetmap.org/" + ref.z + "/" + ref.x + "/" + ref.y + ".png");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(7000);
            connection.setRequestProperty("User-Agent", "kegent/1.3 (+https://github.com/morluto/rea)");
            connection.setRequestProperty("Accept", "image/png,image/*;q=0.8");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream input = connection.getInputStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap == null) return null;
                writeDiskTile(ref, bitmap);
                putMemoryTile(ref.key(), bitmap);
                return bitmap;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private Bitmap readDiskTile(TileRef ref) {
        File file = tileFile(ref);
        if (!file.isFile() || file.length() < 128) return null;
        if (file.lastModified() + TILE_CACHE_MAX_AGE_MILLIS < System.currentTimeMillis()) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap != null) putMemoryTile(ref.key(), bitmap);
            return bitmap;
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressLint("WrongThread")
    private void writeDiskTile(TileRef ref, Bitmap bitmap) {
        File dir = new File(getContext().getCacheDir(), "osm_tiles");
        if (!dir.exists() && !dir.mkdirs()) return;
        File temp = new File(dir, ref.fileName() + ".tmp");
        File target = tileFile(ref);
        try (FileOutputStream output = new FileOutputStream(temp)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            output.flush();
            if (!temp.renameTo(target)) temp.delete();
        } catch (Exception ignored) {
            temp.delete();
        }
    }

    private File tileFile(TileRef ref) {
        return new File(new File(getContext().getCacheDir(), "osm_tiles"), ref.fileName());
    }

    private void putLoaded(TileRef ref, Bitmap bitmap) {
        loadedTiles.put(ref.key(), new LoadedTile(ref, bitmap));
    }

    private static synchronized Bitmap getMemoryTile(String key) {
        return MEMORY_TILES.get(key);
    }

    private static synchronized void putMemoryTile(String key, Bitmap bitmap) {
        MEMORY_TILES.put(key, bitmap);
    }

    private void cancelMovement() {
        if (movement != null) {
            movement.cancel();
            movement = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelMovement();
        traveled = targetDistance;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        drawMapBackground(c);
        if (route == null || route.size() == 0) return;
        if (worldSize == 0) calculateProjection();
        drawRoute(c);
    }

    private void drawMapBackground(Canvas c) {
        float radius = Ui.dp(getContext(), 14);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.color(getContext(), R.color.map_background));
        rect.set(0, 0, getWidth(), getHeight());
        c.drawRoundRect(rect, radius, radius, paint);
        Path clip = new Path();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        drawFallbackBlocks(c);
        drawOnlineTiles(c);
        c.restore();
        if (hasOnlineTile) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xCCFFFFFF);
            float width = Ui.dp(getContext(), 120);
            float height = Ui.dp(getContext(), 16);
            rect.set(getWidth() - width - Ui.dp(getContext(), 5), getHeight() - height - Ui.dp(getContext(), 5),
                    getWidth() - Ui.dp(getContext(), 5), getHeight() - Ui.dp(getContext(), 5));
            c.drawRoundRect(rect, Ui.dp(getContext(), 4), Ui.dp(getContext(), 4), paint);
            paint.setColor(0xFF34454E);
            paint.setTextSize(Ui.dp(getContext(), 7));
            c.drawText("© OpenStreetMap contributors", rect.left + Ui.dp(getContext(), 4), rect.bottom - Ui.dp(getContext(), 5), paint);
        }
    }

    private void drawFallbackBlocks(Canvas c) {
        float width = getWidth();
        float height = getHeight();
        float inset = Ui.dp(getContext(), 4);
        int columns = 6;
        int rows = 4;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                float left = width * column / columns + inset;
                float top = height * row / rows + inset;
                float right = width * (column + 1) / columns - inset;
                float bottom = height * (row + 1) / rows - inset;
                boolean park = (column * 3 + row * 5) % 11 == 0;
                paint.setColor(Ui.color(getContext(), park ? R.color.map_park
                        : ((column + row) % 3 == 0 ? R.color.map_block_alt : R.color.map_block)));
                rect.set(left, top, right, bottom);
                c.drawRoundRect(rect, Ui.dp(getContext(), 7), Ui.dp(getContext(), 7), paint);
            }
        }
        Path water = new Path();
        water.moveTo(-width * 0.05f, height * 0.72f);
        water.cubicTo(width * 0.25f, height * 0.58f, width * 0.46f, height * 0.78f, width * 0.66f, height * 0.62f);
        water.cubicTo(width * 0.83f, height * 0.48f, width * 1.02f, height * 0.57f, width * 1.05f, height * 0.46f);
        water.lineTo(width * 1.05f, height * 0.60f);
        water.cubicTo(width * 0.84f, height * 0.71f, width * 0.78f, height * 0.62f, width * 0.61f, height * 0.78f);
        water.cubicTo(width * 0.42f, height * 0.94f, width * 0.22f, height * 0.72f, -width * 0.05f, height * 0.87f);
        water.close();
        paint.setColor(Ui.color(getContext(), R.color.map_water));
        c.drawPath(water, paint);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Ui.color(getContext(), R.color.map_road_minor));
        paint.setStrokeWidth(Ui.dp(getContext(), 1.5f));
        for (int i = 1; i < 8; i++) {
            float x = width * i / 8f;
            c.drawLine(x, -height * 0.1f, x + width * 0.08f, height * 1.1f, paint);
        }
        for (int i = 1; i < 5; i++) {
            float y = height * i / 5f;
            c.drawLine(-width * 0.1f, y, width * 1.1f, y - height * 0.04f, paint);
        }
        paint.setColor(Ui.color(getContext(), R.color.map_road));
        paint.setStrokeWidth(Ui.dp(getContext(), 5));
        c.drawLine(width * 0.08f, height * 0.18f, width * 0.94f, height * 0.32f, paint);
        c.drawLine(width * 0.14f, height * 0.92f, width * 0.80f, height * 0.06f, paint);
        paint.setColor(Ui.color(getContext(), R.color.map_label));
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(Ui.dp(getContext(), 8));
        paint.setLetterSpacing(0.08f);
        c.drawText("PARK", width * 0.09f, height * 0.17f, paint);
        c.drawText("BLOCK", width * 0.72f, height * 0.87f, paint);
        paint.setLetterSpacing(0f);
    }

    private void drawOnlineTiles(Canvas c) {
        if (worldSize == 0) return;
        paint.setFilterBitmap(true);
        for (LoadedTile loaded : loadedTiles.values()) {
            double tileWorldX = loaded.ref.x * TILE_SIZE;
            while (tileWorldX - centerWorldX > worldSize * 0.5) tileWorldX -= worldSize;
            while (tileWorldX - centerWorldX < -worldSize * 0.5) tileWorldX += worldSize;
            float left = (float) (tileWorldX + offsetX);
            float top = (float) (loaded.ref.y * TILE_SIZE + offsetY);
            rect.set(left, top, left + TILE_SIZE, top + TILE_SIZE);
            c.drawBitmap(loaded.bitmap, null, rect, paint);
        }
    }

    private void drawRoute(Canvas c) {
        Path all = new Path();
        for (int i = 0; i < route.size(); i++) {
            RunRoute.Point p = route.get(i);
            if (i == 0) all.moveTo(x(p), y(p));
            else all.lineTo(x(p), y(p));
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(getContext(), 6));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Ui.color(getContext(), R.color.map_road));
        c.drawPath(all, paint);
        paint.setStrokeWidth(Ui.dp(getContext(), 3));
        paint.setColor(Ui.color(getContext(), R.color.ui_outline));
        c.drawPath(all, paint);
        double position = route.routePosition(traveled);
        Path covered = new Path();
        covered.moveTo(x(route.get(0)), y(route.get(0)));
        for (int i = 1; i < route.size() && route.distanceAt(i) <= position; i++) covered.lineTo(x(route.get(i)), y(route.get(i)));
        RunRoute.Point point = route.at(traveled);
        covered.lineTo(x(point), y(point));
        paint.setStrokeWidth(Ui.dp(getContext(), 3.5f));
        paint.setColor(Ui.color(getContext(), R.color.ui_accent));
        c.drawPath(traveled > 0 ? covered : all, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.color(getContext(), R.color.ui_surface));
        c.drawCircle(x(point), y(point), Ui.dp(getContext(), 7), paint);
        paint.setColor(Ui.color(getContext(), R.color.ui_accent));
        c.drawCircle(x(point), y(point), Ui.dp(getContext(), 4), paint);
    }

    private float x(RunRoute.Point p) {
        return (float) (projectX(p.longitude, zoom) + offsetX);
    }

    private float y(RunRoute.Point p) {
        return (float) (projectY(p.latitude, zoom) + offsetY);
    }

    private double projectX(double longitude, double candidateZoom) {
        return (longitude + 180d) / 360d * TILE_SIZE * Math.pow(2, candidateZoom);
    }

    private double projectY(double latitude, double candidateZoom) {
        double bounded = Math.max(-85.05112878, Math.min(85.05112878, latitude));
        double radians = Math.toRadians(bounded);
        double normalized = (1d - Math.log(Math.tan(radians) + 1d / Math.cos(radians)) / Math.PI) * 0.5d;
        return normalized * TILE_SIZE * Math.pow(2, candidateZoom);
    }

    private void updateDescription() {
        if (route == null) {
            setContentDescription("未选择路线");
            return;
        }
        String source = hasOnlineTile ? "OSM 在线街区地图" : "离线街区风格底图";
        setContentDescription(route.name + "，" + route.size() + " 个点，约 " + Math.round(route.lengthMeters)
                + " 米；" + source + "，路线叠加层");
    }

    private static final class LoadedTile {
        final TileRef ref;
        final Bitmap bitmap;

        LoadedTile(TileRef ref, Bitmap bitmap) {
            this.ref = ref;
            this.bitmap = bitmap;
        }
    }

    private static final class TileRef {
        final int z;
        final int x;
        final int y;

        TileRef(int z, int x, int y) {
            this.z = z;
            this.x = x;
            this.y = y;
        }

        String key() {
            return z + "/" + x + "/" + y;
        }

        String fileName() {
            return z + "_" + x + "_" + y + ".png";
        }
    }
}
