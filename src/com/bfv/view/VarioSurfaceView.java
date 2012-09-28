package com.bfv.view;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.*;
import android.text.InputFilter;
import android.util.Log;
import android.util.Xml;
import android.view.*;
import android.widget.EditText;
import android.widget.FrameLayout;
import com.bfv.*;
import com.bfv.model.Altitude;
import com.bfv.model.Vario;
import com.bfv.util.Point2d;
import com.bfv.view.component.*;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.*;
import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: Alistair
 * Date: 11/06/12
 * Time: 2:05 PM
 */
public class VarioSurfaceView extends SurfaceView implements Runnable, SurfaceHolder.Callback, View.OnTouchListener, GestureDetector.OnGestureListener {

    public static BFVViewComponent editingComponent;//todo - this is nasty, there must be a better way in android...
    public static BFVViewPage editingViewPage;

    public static final String defaultFileName = "myBlueFlyVarioViews.xml";


    private Thread thread = null;
    private SurfaceHolder surfaceHolder;
    private volatile boolean running = false;
    private volatile boolean surfaceCreated = false;


    public BlueFlyVario bfv;
    public BFVService service;
    public Altitude alt;
    public Vario var1;
    public Vario var2;


    private boolean scheduledSetUpData;
    private boolean scheduleRemoveCurrentView;
    private Rect frame;
    private Rect oldFrame;

    private FieldManager fieldManager;

    private ArrayList<BFVViewPage> viewPages;
    private int currentView = 0;
    private int newViewIndex = -1;

    private double fps;

    private GestureDetector gestureDetector;

    private BFVViewComponent selectedComponent;
    private PointF downPoint;

    private boolean allowDragOnTouch = false;

    private boolean firstRun;

    private boolean layoutEnabled;
    private int layoutParameterSelectRadius;
    private boolean layoutDragEnabled;
    private int layoutDragSelectRadius;
    private boolean drawTouchPoints;
    private boolean layoutResizeOrientationChange;
    private boolean layoutResizeImport;

    private boolean layoutResizeOrientationChangeFlag;

    private boolean loading;


    public VarioSurfaceView(BlueFlyVario bfv, BFVService service) {
        super(bfv);
        this.bfv = bfv;
        this.service = service;
        firstRun = true;
        surfaceHolder = getHolder();

        bfv.setRequestedOrientation(bfv.getResources().getConfiguration().orientation);//this makes sure it is set at least once to whatever we started with

        surfaceHolder.addCallback(this);
        fieldManager = new FieldManager(this);
        this.setOnTouchListener(this);
        gestureDetector = new GestureDetector(bfv, this);

        alt = service.getAltitude("Alt");
        var1 = alt.getVario("Var1");
        var2 = alt.getVario("Var2");
        readSettings();

    }

    public void onDestroy() {
        //Log.i("BFV", "onDestroySurface");
        this.saveViewsToXML(true, defaultFileName);
        this.saveViewsToXML(false, defaultFileName);
    }


    public void scheduleSetUpData() {
        if (!running) {
            service.setUpData();
        } else {
            this.scheduledSetUpData = true;
        }

    }


    public synchronized void onResumeVarioSurfaceView() {
        if (!running && surfaceCreated) {

            thread = new Thread(this);
            running = true;
            thread.start();

        }


    }

    public synchronized void onPauseVarioSurfaceView() {
        if (thread == null) {
            return;
        }
        boolean retry = true;
        running = false;
        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {

                e.printStackTrace();
            }
        }

    }

    public void setUpDefaultViews() {
        InputStream inputStream = BlueFlyVario.blueFlyVario.getResources().openRawResource(R.raw.default_layout);
        boolean oldLayoutResizeImport = layoutResizeImport;
        layoutResizeImport = true;
        loadViewsFromXML(inputStream);
        layoutResizeImport = oldLayoutResizeImport;

    }


    public void setUpDefaultViewsManually() {
        // Log.i("BFV", "SetUpDefault");
        //this.stopRunning();

        int orientation = BlueFlyVario.blueFlyVario.getRequestedOrientation();
        viewPages = new ArrayList<BFVViewPage>();
        BFVViewPage viewPage0 = new BFVViewPage(new RectF(frame), this);
        viewPage0.setOrientation(orientation);

        RectF altRect = new RectF(1, 1, 100, 61);
        FieldViewComponent altViewComp = new FieldViewComponent(altRect, this, fieldManager, FieldManager.FIELD_DAMPED_ALTITUDE);
        //altViewComp.setMultiplierIndex(1);
        altViewComp.setDefaultLabel();
        viewPage0.addViewComponent(altViewComp);

        RectF varioRect = new RectF(1, 66, 100, 126);
        FieldViewComponent varioViewComp = new FieldViewComponent(varioRect, this, fieldManager, FieldManager.FIELD_VARIO2);
        viewPage0.addViewComponent(varioViewComp);

        RectF batRect = new RectF(1, 131, 100, 191);
        FieldViewComponent batViewComp = new FieldViewComponent(batRect, this, fieldManager, FieldManager.FIELD_FLIGHT_TIME);
        viewPage0.addViewComponent(batViewComp);

        RectF fpsRect = new RectF(1, 196, 100, 256);
        FieldViewComponent fpsViewComp = new FieldViewComponent(fpsRect, this, fieldManager, FieldManager.FIELD_BAT_PERCENT);
        viewPage0.addViewComponent(fpsViewComp);

        RectF varioComponentRect = new RectF(1, 258, frame.width() - 1, frame.height() - 1);
        VarioTraceViewComponent varioTrace = new VarioTraceViewComponent(varioComponentRect, this);
        viewPage0.addViewComponent(varioTrace);

        RectF locationComponentRect = new RectF(102, 1, frame.width() - 1, 258);
        LocationViewComponent locationView = new LocationViewComponent(locationComponentRect, this);
        viewPage0.addViewComponent(locationView);
        viewPages.add(viewPage0);

        BFVViewPage viewPage1 = new BFVViewPage(new RectF(frame), this);
        viewPage1.setOrientation(orientation);
        RectF label1Rect = new RectF(1, 1, frame.width() - 1, 50);
        LabelViewComponent label1 = new LabelViewComponent(label1Rect, this);
        label1.setLabel("All Fields");

        viewPage1.addViewComponent(label1);
        ArrayList<Field> fields = fieldManager.getFields();

        int cols = 2;
        float fieldHeight = 60;
        float fieldWidth = (float) frame.width() / cols - cols;

        int col = 0;
        int row = 0;

        for (int i = 0; i < fields.size(); i++) {

            Field field = fields.get(i);
            float top = 61 + row * fieldHeight + row * 3;
            float left = 1 + col * fieldWidth + col * 3;
            RectF rect = new RectF(left, top, left + fieldWidth, top + fieldHeight);
            FieldViewComponent fieldViewComponent = new FieldViewComponent(rect, this, fieldManager, field.getId());
            viewPage1.addViewComponent(fieldViewComponent);

            col++;
            if (col == cols) {
                row++;
                col = 0;

            }


        }


        viewPages.add(viewPage1);

        BFVViewPage viewPage2 = new BFVViewPage(new RectF(frame), this);
        viewPage2.setOrientation(orientation);
        RectF label2Rect = new RectF(1, 1, frame.width() - 1, 50);
        LabelViewComponent label2 = new LabelViewComponent(label2Rect, this);
        label2.setLabel("Location Example");
        viewPage2.addViewComponent(label2);

        RectF locationComponentRect2 = new RectF(1, frame.height() - frame.width(), frame.width() - 1, frame.height() - 1);
        LocationViewComponent locationView2 = new LocationViewComponent(locationComponentRect2, this);
        viewPage2.addViewComponent(locationView2);
        viewPages.add(viewPage2);

        BFVViewPage viewPage3 = new BFVViewPage(new RectF(frame), this);
        viewPage3.setOrientation(orientation);
        RectF label3Rect = new RectF(1, 1, frame.width() - 1, 50);
        LabelViewComponent label3 = new LabelViewComponent(label3Rect, this);
        label3.setLabel("Wind Trace Test");
        viewPage3.addViewComponent(label3);
        RectF windComponentRect3 = new RectF(1, frame.height() - frame.width(), frame.width() - 1, frame.height() - 1);
        WindTraceViewComponent windTrace = new WindTraceViewComponent(windComponentRect3, this);
        viewPage3.addViewComponent(windTrace);
        viewPages.add(viewPage3);

        setViewPage(0);
        // this.onResumeVarioSurfaceView();


    }

    public int getNumViews() {
        return viewPages.size();

    }

    public void addView() {
        int orientation = BlueFlyVario.blueFlyVario.getRequestedOrientation();
        viewPages.add(currentView, new BFVViewPage(new RectF(frame), this));
        viewPages.get(currentView).setOrientation(orientation);

    }

    public void addViewComponent(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final Context con = context;
        final VarioSurfaceView view = this;
        builder.setTitle("Select Component Type");

        builder.setItems(ViewComponentManager.viewComponentTypes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                BFVViewComponent newComponent = null;
                addDefaultViewComponent(ViewComponentManager.viewComponentTypes[item], con);


            }
        });
        AlertDialog alert = builder.create();
        alert.show();

    }

    public void addDefaultViewComponent(String name, Context context) {
        RectF rect = new RectF(10, 10, frame.width() - 10, frame.height() - 10);
        if (name.equals("Field")) {
            final String[] names = fieldManager.getFieldNames();
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            final Context con = context;
            final VarioSurfaceView view = this;

            builder.setTitle("Select Field Type");

            builder.setItems(names, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
                    RectF rect = new RectF(frame.centerX() - 50, frame.centerY() - 30, frame.centerX() + 50, frame.centerY() + 30);
                    FieldViewComponent component = new FieldViewComponent(rect, view, fieldManager, names[item]);
                    addViewComponent(component);
                }
            });
            AlertDialog alert = builder.create();
            alert.show();


        } else if (name.equals("Location")) {

            addViewComponent(new LocationViewComponent(rect, this));


        } else if (name.equals("VarioTrace")) {

            addViewComponent(new VarioTraceViewComponent(rect, this));

        } else if (name.equals("WindTrace")) {

            addViewComponent(new WindTraceViewComponent(rect, this));

        } else if (name.equals("Label")) {

            RectF labRect = new RectF(20, frame.height() / 2 - 20, frame.width() - 20, frame.height() / 2 + 20);
            addViewComponent(new LabelViewComponent(labRect, this));

        }


    }

    private void addViewComponent(BFVViewComponent newComponent) {
        viewPages.get(currentView).addViewComponent(newComponent);
        setEditingComponent(newComponent);
        Intent parameterIntent = new Intent(bfv, ViewComponentListActivity.class);
        bfv.startActivity(parameterIntent);
    }


    public void deleteView(Context context) {

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage("Are you sure you want to delete the current view?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        scheduleRemoveCurrentView = true;
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();

    }

    private void removeCurrentView() {
        viewPages.remove(currentView);
        if (viewPages.size() == 0) {
            viewPages.add(new BFVViewPage(new RectF(frame), this));
            setViewPage(0);
        }

        if (currentView >= viewPages.size()) {
            setViewPage(viewPages.size() - 1);
        }
        scheduleRemoveCurrentView = false;

    }


    public void run() {
        frame = null;
        while (frame == null) {
            if (surfaceHolder.getSurface().isValid()) {
                Canvas c = surfaceHolder.lockCanvas();
                Rect r = surfaceHolder.getSurfaceFrame();
                if (r != null) {
                    frame = new Rect(r);
                }

                surfaceHolder.unlockCanvasAndPost(c);
            }

        }


        if (firstRun) { //have to do this in here so we get the frame size for setting up default views.


            try {
                InputStream in = BlueFlyVario.blueFlyVario.openFileInput(defaultFileName);

                viewPages = readViewsFromXML(in);
                setViewPage(newViewIndex);
                newViewIndex = -1;
                if (viewPages == null) {
                    this.setUpDefaultViews();

                }


            } catch (FileNotFoundException e) {
                this.setUpDefaultViews();
            }

            firstRun = false;
        }


        Canvas c;
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        //fps stuff
        double timeavg = 0.0;
        long last = System.nanoTime();


        while (running) {
            //fps stuff
            long current = System.nanoTime();
            double s = (current - last) / 1000000000.0;
            timeavg = 0.05 * s + 0.95 * timeavg;
            fps = (1.0 / timeavg);
            last = current;

            if (scheduledSetUpData) {
                service.setUpData();
                scheduledSetUpData = false;

            }

            if (scheduleRemoveCurrentView) {
                removeCurrentView();
            }
            if (surfaceHolder.getSurface().isValid()) {


                c = surfaceHolder.lockCanvas();

                Rect r = surfaceHolder.getSurfaceFrame();

                if (r != null) {
                    frame = new Rect(r);
                    if (layoutResizeOrientationChangeFlag && oldFrame.width() != frame.width()) {


                        BFVViewPage viewPage = viewPages.get(currentView);
                        ArrayList<BFVViewComponent> components = viewPage.getViewComponents();
                        for (int j = 0; j < components.size(); j++) {
                            BFVViewComponent viewComponent = components.get(j);
                            viewComponent.scaleComponent((double) frame.width() / (double) oldFrame.width(), (double) frame.height() / (double) oldFrame.height());

                        }
                        viewPage.setPageFrame(new RectF(frame));
                        layoutResizeOrientationChangeFlag = false;
                        oldFrame = null;
                    }


                }

                c.drawColor(Color.BLACK); //background

                if (viewPages.size() > 0 && currentView >= 0 && currentView < viewPages.size()) {
                    BFVViewPage viewPage = viewPages.get(currentView);
                    viewPage.addToCanvas(c, paint);
                }


                surfaceHolder.unlockCanvasAndPost(c);
            }

        }


    }

    private void stopRunning() {
        boolean retry = true;
        running = false;

        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {

                e.printStackTrace();
            }
        }

    }

    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        //Log.i("BFV", "surfaceCreated");


    }

    public synchronized void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        // Log.i("BFV", "surfaceChanged");

        if (running) {
            stopRunning();

        }
        thread = new Thread(this);
        running = true;
        thread.start();
        surfaceCreated = true;

    }

    public synchronized void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        // Log.i("BFV", "surfaceDestroyed");


        stopRunning();
        surfaceCreated = false;
        //To change body of implemented methods use File | Settings | File Templates.
    }


    public BFVService getService() {
        return service;
    }

    public double getFps() {
        return fps;
    }

    public void setViewPage(int viewPage) {
        this.currentView = viewPage;
        if (viewPages != null) {
            int orientation = viewPages.get(viewPage).getOrientation();
            setCurrentViewPageOrientation(orientation, false);
        }


        service.getmHandler().obtainMessage(BlueFlyVario.MESSAGE_VIEW_PAGE_CHANGE, currentView, -1).sendToTarget();

    }

    public void setCurrentViewPageOrientation(int orientation, boolean maybeResize) {
        //Log.i("BFV", "loading" + loading);
        if (loading) {
            return;
        }
        int viewOrientation = BlueFlyVario.blueFlyVario.getRequestedOrientation();
        if (orientation != viewOrientation) {

            if (layoutResizeOrientationChange && maybeResize) {

                oldFrame = new Rect(frame);

                stopRunning();
                BlueFlyVario.blueFlyVario.setRequestedOrientation(orientation);
                layoutResizeOrientationChangeFlag = true;
                thread = new Thread(this);
                running = true;
                thread.start();

            } else {
                BlueFlyVario.blueFlyVario.setRequestedOrientation(orientation);
            }

        }
    }

    public void incrementViewPage() {
        if (currentView >= viewPages.size() - 1) {
            setViewPage(0);
        } else {
            setViewPage(currentView + 1);
        }


    }

    public void decrementViewPage() {
        if (currentView <= 0) {
            setViewPage(viewPages.size() - 1);
        } else {
            setViewPage(currentView - 1);
        }

    }

    public void readSettings() {
        layoutEnabled = BFVSettings.sharedPrefs.getBoolean("layout_enabled", false);
        layoutParameterSelectRadius = Integer.valueOf(BFVSettings.sharedPrefs.getString("layout_parameter_select_radius", "50"));
        layoutDragEnabled = BFVSettings.sharedPrefs.getBoolean("layout_drag_enabled", false);
        layoutDragSelectRadius = Integer.valueOf(BFVSettings.sharedPrefs.getString("layout_drag_select_radius", "20"));
        drawTouchPoints = BFVSettings.sharedPrefs.getBoolean("layout_draw_touch_points", false);
        layoutResizeImport = BFVSettings.sharedPrefs.getBoolean("layout_resize_import", true);
        layoutResizeOrientationChange = BFVSettings.sharedPrefs.getBoolean("layout_resize_orientation_change", true);


    }

    public boolean drawTouchPoints() {
        return layoutEnabled && drawTouchPoints;
    }

    public float getTouchRadius() {
        return layoutParameterSelectRadius;
    }


    public boolean onTouch(View view, MotionEvent e) {
        // Log.i("BFV", "onTouch");

        if (e.getAction() == MotionEvent.ACTION_UP && selectedComponent != null) {

            if (selectedComponent.isDraging()) {
                viewPages.get(currentView).getViewComponents().remove(selectedComponent);
                viewPages.get(currentView).getViewComponents().add(selectedComponent);

            }
            selectedComponent.setSelected(false, BFVViewComponent.SELECTED_CENTER);


            selectedComponent = null;
            downPoint = null;
            allowDragOnTouch = false;

        }
        if (selectedComponent != null && allowDragOnTouch) {
            selectedComponent.processScroll(e.getX() - downPoint.x, e.getY() - downPoint.y, frame);

        }

        return gestureDetector.onTouchEvent(e);


    }

    public boolean onDown(MotionEvent motionEvent) {
        // Log.i("BFV", "onDown");

        return true;
    }

    public void onShowPress(MotionEvent motionEvent) {
        // Log.i("BFV", "onShowPress" + motionEvent.getX());
        if (layoutEnabled && layoutDragEnabled) {

            ArrayList<BFVViewComponent> viewComponents = viewPages.get(currentView).getViewComponents();
            double min = Double.MAX_VALUE;
            BFVViewComponent closestComponent = null;
            Point2d press = new Point2d(motionEvent.getX(), motionEvent.getY());
            for (int i = 0; i < viewComponents.size(); i++) {
                BFVViewComponent viewComponent = viewComponents.get(i);
                viewComponent.setSelected(false, BFVViewComponent.SELECTED_CENTER);
                double dist = viewComponent.distance(press);
                if (dist < min) {
                    min = dist;
                    closestComponent = viewComponent;
                }
            }

            if (min < layoutDragSelectRadius && closestComponent != null) {
                int index = closestComponent.closest(press);
                closestComponent.setSelected(true, index);
                selectedComponent = closestComponent;
                downPoint = new PointF(motionEvent.getX(), motionEvent.getY());
            }
        }


    }

    public boolean onSingleTapUp(MotionEvent motionEvent) {
        // Log.i("BFV", "onSingleTapUp");
        if (layoutEnabled) {


            double min = Double.MAX_VALUE;
            ArrayList<BFVViewComponent> viewComponents = viewPages.get(currentView).getViewComponents();
            BFVViewComponent closestComponent = null;
            Point2d press = new Point2d(motionEvent.getX(), motionEvent.getY());
            for (int i = 0; i < viewComponents.size(); i++) {
                BFVViewComponent viewComponent = viewComponents.get(i);
                viewComponent.setSelected(false, BFVViewComponent.SELECTED_CENTER);
                double dist = viewComponent.distanceCenter(press);
                if (dist < min) {
                    min = dist;
                    closestComponent = viewComponent;
                }


            }


            if (min < layoutParameterSelectRadius && closestComponent != null) {
                setEditingComponent(closestComponent);
                Intent parameterIntent = new Intent(bfv, ViewComponentListActivity.class);
                bfv.startActivity(parameterIntent);
                downPoint = null;

            }
        }
        return false;
    }

    public void viewPageProperties() {
        setEditingComponent(viewPages.get(currentView));
        Intent parameterIntent = new Intent(bfv, ViewComponentListActivity.class);
        bfv.startActivity(parameterIntent);
    }

    public boolean onScroll(MotionEvent e, MotionEvent e1, float x, float y) {
        //Log.i("BFV", "onScroll");

        return false;
    }

    public void onLongPress(MotionEvent e) {
        // Log.i("BFV", "onLongPress" + e.getX());
        if (selectedComponent != null && layoutDragEnabled) {
            selectedComponent.setDraging(true);
            allowDragOnTouch = true;

        }


    }

    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float x, float y) {
        //Log.i("BFV", "onFling");
        if (x < -500.0) {
            incrementViewPage();

        }
        if (x > 500.0) {
            decrementViewPage();

        }
        return true;
    }

    private void setEditingComponent(BFVViewComponent component) {
        editingComponent = component;
        editingViewPage = viewPages.get(currentView);
    }


    public boolean saveViewsToXML(boolean internal, String name) {
        XmlSerializer serializer = Xml.newSerializer();


        FileOutputStream out = null;

        try {
            if (internal) {
                out = BlueFlyVario.blueFlyVario.openFileOutput(name, Context.MODE_PRIVATE);
            } else {
                out = new FileOutputStream(new File(BlueFlyVario.blueFlyVario.getExternalFilesDir(null), name));
            }


            serializer.setOutput(out, "UTF-8");
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            serializer.startDocument("UTF-8", true);
            serializer.startTag("", "BlueFlyVarioViewPages");
            serializer.attribute("", "currentViewPage", currentView + "");

            for (int i = 0; i < viewPages.size(); i++) {
                BFVViewPage bfvViewPage = viewPages.get(i);

                serializer.startTag("", "BFVViewPage");
                ArrayList<ViewComponentParameter> viewParameters = bfvViewPage.getParameters();
                for (int k = 0; k < viewParameters.size(); k++) {
                    ViewComponentParameter parameter = viewParameters.get(k);
                    serializer.attribute("", parameter.getName(), parameter.getXmlValue());

                }
                ArrayList<BFVViewComponent> components = bfvViewPage.getViewComponents();
                for (int j = 0; j < components.size(); j++) {
                    BFVViewComponent blueFlyViewComponent = components.get(j);
                    String type = blueFlyViewComponent.getViewComponentType();
                    serializer.startTag("", type);
                    ArrayList<ViewComponentParameter> parameters = blueFlyViewComponent.getParameters();
                    for (int k = 0; k < parameters.size(); k++) {
                        ViewComponentParameter parameter = parameters.get(k);
                        serializer.attribute("", parameter.getName(), parameter.getXmlValue());

                    }
                    serializer.endTag("", type);
                }
                serializer.endTag("", "BFVViewPage");
            }
            serializer.endTag("", "BlueFlyVarioViewPages");
            serializer.endDocument();
            serializer.flush();
            out.close();
        } catch (IOException e) {

            return false;
        }
        return true;

    }

    public ArrayList<BFVViewPage> readViewsFromXML(boolean internal, String name) {

        FileInputStream in = null;


        try {
            if (internal) {
                in = BlueFlyVario.blueFlyVario.openFileInput(name);
            } else {
                in = new FileInputStream(new File(BlueFlyVario.blueFlyVario.getExternalFilesDir(null), name));
            }
            return readViewsFromXML(in);


        } catch (Exception e) {


            return null;
        }


    }

    public void exportViews() {
        AlertDialog.Builder alert = new AlertDialog.Builder(bfv);

        alert.setTitle("File Name");
        alert.setMessage("Save the view with this filename");

        // Set an EditText view to get user input
        final EditText input = new EditText(bfv);
        input.setFilters(new InputFilter[]{new FilenameInputFilter()});

        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString();
                saveViewsToXML(false, value + ".xml");

            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });

        alert.show();
    }

    public void loadViewsFromXML(InputStream in) {

        ArrayList<BFVViewPage> newViewPages = readViewsFromXML(in);

        if (layoutResizeImport) {
            for (int i = 0; i < newViewPages.size(); i++) {
                BFVViewPage viewPage = newViewPages.get(i);
                double pageWidth = viewPage.getPageFrame().width();
                double pageHeight = viewPage.getPageFrame().height();
                ArrayList<BFVViewComponent> components = viewPage.getViewComponents();
                for (int j = 0; j < components.size(); j++) {
                    BFVViewComponent viewComponent = components.get(j);
                    viewComponent.scaleComponent(frame.width() / pageWidth, frame.height() / pageHeight);


                }
                viewPage.setPageFrame(new RectF(frame));
                viewPage.setOrientation(BlueFlyVario.blueFlyVario.getRequestedOrientation());

            }

        }

        if (newViewPages != null) {
            viewPages = newViewPages;
            setViewPage(newViewIndex);
            newViewIndex = -1;


        }


    }

    public ArrayList<BFVViewPage> readViewsFromXML(InputStream in) {
        loading = true;
        //Log.i("BFV", "readViews");
        XmlPullParser parser = null;

        ArrayList<BFVViewPage> viewPages = null;
        newViewIndex = -1;

        try {
            parser = XmlPullParserFactory.newInstance().newPullParser();
        } catch (XmlPullParserException e) {
            return null;

        }


        try {

            parser.setInput(in, "UTF-8");

            BFVViewPage currentViewPage = null;

            int eventType = parser.next();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                //Log.i("BFV", "event" + eventType);

                switch (eventType) {
                    case (XmlPullParser.START_DOCUMENT):
                        break;
                    case (XmlPullParser.START_TAG):
                        String tag = parser.getName();
                        //Log.i("BFV", "tag" + tag);
                        if (tag.equals("BlueFlyVarioViewPages")) {
                            viewPages = new ArrayList<BFVViewPage>();

                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                if (parser.getAttributeName(i).equals("currentViewPage")) {
                                    newViewIndex = Integer.parseInt(parser.getAttributeValue(i));
                                }


                            }
                        } else if (tag.equals("BFVViewPage")) {
                            currentViewPage = new BFVViewPage(new RectF(frame), this);
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                currentViewPage.setParameterValue(new ViewComponentParameter(parser.getAttributeName(i), parser.getAttributeValue(i)));
                            }
                            viewPages.add(currentViewPage);
                        } else if (tag.startsWith("Field")) {
                            String[] split = tag.split("_");
                            FieldViewComponent component = new FieldViewComponent(new RectF(), this, fieldManager, split[1]);
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                component.setParameterValue(new ViewComponentParameter(parser.getAttributeName(i), parser.getAttributeValue(i)));
                            }
                            currentViewPage.addViewComponent(component);
                        } else if (tag.equals("Location")) {
                            LocationViewComponent component = new LocationViewComponent(new RectF(), this);
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                component.setParameterValue(new ViewComponentParameter(parser.getAttributeName(i), parser.getAttributeValue(i)));
                            }
                            currentViewPage.addViewComponent(component);
                        } else if (tag.equals("VarioTrace")) {
                            VarioTraceViewComponent component = new VarioTraceViewComponent(new RectF(), this);
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                component.setParameterValue(new ViewComponentParameter(parser.getAttributeName(i), parser.getAttributeValue(i)));
                            }
                            currentViewPage.addViewComponent(component);
                        } else if (tag.equals("WindTrace")) {
                            WindTraceViewComponent component = new WindTraceViewComponent(new RectF(), this);
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                component.setParameterValue(new ViewComponentParameter(parser.getAttributeName(i), parser.getAttributeValue(i)));
                            }
                            currentViewPage.addViewComponent(component);
                        } else if (tag.equals("Label")) {
                            LabelViewComponent component = new LabelViewComponent(new RectF(), this);
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                component.setParameterValue(new ViewComponentParameter(parser.getAttributeName(i), parser.getAttributeValue(i)));
                            }
                            currentViewPage.addViewComponent(component);
                        } else {  //unknown tag - quit
                            return null;
                        }


                        break;
                    case (XmlPullParser.END_TAG):
                        //Log.i("BFV", "end" + parser.getName());
                        if (parser.getName().equals("BlueFlyVarioViewPages")) {
                            in.close();
                            if (newViewIndex == -1) {
                                newViewIndex = 0;
                            }

                            loading = false;
                            return viewPages;
                        }
                        break;
                    case (XmlPullParser.TEXT):
                        // Log.i("BFV", "text" + parser.getText());
                        break;

                }


                eventType = parser.next();

            }

        } catch (Exception e) {
            //Log.i("BFV", e.getMessage() + e.toString());
            loading = false;
            return null;
        }
        loading = false;
        return null;

    }


    public static void removeEditingComponent() {
        AlertDialog.Builder builder = new AlertDialog.Builder(BlueFlyVario.blueFlyVario);
        builder.setMessage("Are you sure you want to delete this component?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        editingViewPage.getViewComponents().remove(editingComponent);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();


    }

    public static void moveEditingComponentFront() {
        editingViewPage.getViewComponents().remove(editingComponent);
        editingViewPage.addViewComponent(editingComponent);
    }

    public static void moveEditingComponentBack() {
        editingViewPage.getViewComponents().remove(editingComponent);
        editingViewPage.addViewComponentBack(editingComponent);
    }
}
