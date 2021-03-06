package com.example.bluechatapp.MainActivity;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.example.bluechatapp.BlueTooth.BlueToothBroadcast;
import com.example.bluechatapp.BlueTooth.BlueToothDevice;
import com.example.bluechatapp.BlueTooth.BluetoothChatService;
import com.example.bluechatapp.BlueTooth.BluetoothManager;
import com.example.bluechatapp.BlueTooth.FreeListRVAdapter;
import com.example.bluechatapp.BlueTooth.PairedListRVAdapter;
import com.example.bluechatapp.DataProcessing.MessageHandler;
import com.example.bluechatapp.DataProcessing.ModelRunnable;
import com.example.bluechatapp.DataProcessing.PredictHandler;
import com.example.bluechatapp.DataProcessing.PythonExecutor;
import com.example.bluechatapp.DataShowActivity.DataTableActivity;
import com.example.bluechatapp.Fragments.LineChartFragment;
import com.example.bluechatapp.MapActivity.BaiduMapActivity;
import com.example.bluechatapp.MusicActivity.MusicListActivity;
import com.example.bluechatapp.R;
import com.example.bluechatapp.Static.Utils.ThreadPoolUtils;
import com.example.bluechatapp.Static.Utils.ToastUtil;

import com.github.mikephil.charting.charts.LineChart;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.nightonke.boommenu.Animation.BoomEnum;
import com.nightonke.boommenu.BoomButtons.ButtonPlaceEnum;
import com.nightonke.boommenu.BoomButtons.OnBMClickListener;
import com.nightonke.boommenu.BoomButtons.TextOutsideCircleButton;
import com.nightonke.boommenu.BoomMenuButton;
import com.nightonke.boommenu.ButtonEnum;
import com.nightonke.boommenu.Piece.PiecePlaceEnum;

import org.jetbrains.annotations.NotNull;

import java.security.Permission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int REQUEST_CODE_PERMISSION_LOCATION = 1;
    private static final int REQUEST_CODE_OPEN_GPS = 2;
    /* ???????????? */
    private BoomMenuButton menuButton  ; // ????????????boomMenu
    private ExtendedFloatingActionButton toastButton ;
    private RecyclerView pairedDevicesRV ;
    private RecyclerView freeDevicesRV ;
    private Switch bluetoothSwitch ;
    private Button scanButton ;
    // Fragment
    private LineChartFragment lineChartFragment ;
    // ??????
    private BluetoothManager bluetoothManager ;
    private PairedListRVAdapter.BuildConnectionListener listener ;
    private BluetoothChatService chatService ;
    // ????????????????????????
    private AlertDialog.Builder builder ;
    private AlertDialog bluetoothAlertDialog ;
    // ????????????
    private BlueToothBroadcast mReceiver ;
    // predict handler
    private PredictHandler predictHandler ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewForAllComponent() ; // ???????????????????????????
        initializeComponent();      // ?????????????????????
        registerClickEvent();       // ??????????????????
        initPython();
    }
    // ?????????python??????
    public void initPython() {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(MainActivity.this));
        }
    }
    private void initializeFragments(int flag) {
        lineChartFragment = LineChartFragment.newInstance(flag) ;
        getSupportFragmentManager().beginTransaction().add(R.id.chart_fragment_layout,lineChartFragment).commit() ;
    }

    // ???????????????????????????
    private void findViewForAllComponent() {
        menuButton = findViewById(R.id.boomMenu_Button) ;
        toastButton = findViewById(R.id.toast_control) ;
        bluetoothManager = new BluetoothManager(MainActivity.this) ;
        chatService = new BluetoothChatService(MainActivity.this,new MessageHandler(MainActivity.this)) ;
        mReceiver = new BlueToothBroadcast() ;
    }
    // ??????????????????????????????
    private void initializeComponent() {
        createBoomMenu();
    }
    // ??????????????????
    private void createBluetoothSwitch(View view) {
        bluetoothSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked==true){
                    // ??????????????????
                    bluetoothManager.openBlueAsyn();
                    // Register for broadcasts when a device is discovered
                    IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                    registerReceiver(mReceiver, filter);
                    // Register for broadcasts when discovery has finished
                    filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                    registerReceiver(mReceiver, filter);
                    Set<BluetoothDevice> boundedDevices = bluetoothManager.getBoundedDevices() ;
                    // ???????????????
                    pairedDevicesRV = view.findViewById(R.id.paired_devices_list_rv) ;
                    pairedDevicesRV.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                    pairedDevicesRV.addItemDecoration(new MyDecoraton());
                    pairedDevicesRV.setAdapter(new PairedListRVAdapter(MainActivity.this, new PairedListRVAdapter.OnItemClickListener() {
                        @Override
                        public void onClick(int pos) {
                            bluetoothManager.cancelDiscovery();
                            bluetoothAlertDialog.dismiss();
                        }
                    }, new PairedListRVAdapter.BuildConnectionListener() {
                        @Override
                        public void build(BluetoothDevice device) {
                            // TODO: 2021/9/13 ????????????????????????

                            chatService.connect(device,true);
                        }
                    },boundedDevices));
                    // ???????????????
                    freeDevicesRV = view.findViewById(R.id.free_devices_list_rv) ;
                    freeDevicesRV.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                    freeDevicesRV.addItemDecoration(new MyDecoraton());
                    freeDevicesRV.setAdapter(new FreeListRVAdapter(MainActivity.this, new FreeListRVAdapter.OnItemClickListener() {
                        @Override
                        public void onClick(int pos) {
                            bluetoothManager.cancelDiscovery() ;
                            bluetoothAlertDialog.dismiss() ;
                        }
                    }));
                    ModelRunnable runnable = new ModelRunnable(MainActivity.this) ;
//                    ThreadPoolUtils.newInstance().getSingleThreadPool().submit(runnable) ;
                    new Thread(runnable).start();
                }else{
                    bluetoothManager.shutdownBlue();
                    unregisterReceiver(mReceiver);
                }
            }
        });
    }

    // ??????????????????
    private void registerClickEvent() {
        toastButton.setOnClickListener(this);
    }
    // ????????????
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.toast_control:{
                // ????????????toast??????
                ToastUtil.changeState() ;
                if (ToastUtil.getState()==ToastUtil.state_on){
                    toastButton.setText("Toast:On");
                    ToastUtil.showShortMessage(MainActivity.this,"Toast??????");
                }else{
                    toastButton.setText("Toast:close");
                    Toast.makeText(MainActivity.this,"Toast??????",Toast.LENGTH_SHORT).show();
                }
                break ;
            }
            case R.id.scan_btn:{
                // ???????????????????????????
                bluetoothManager.doDiscovery() ;
                break ;
            }

        }
    }
    // ????????????
    // ??????boom??????
    private void createBoomMenu(){
        menuButton.setButtonEnum(ButtonEnum.TextOutsideCircle);
        menuButton.setPiecePlaceEnum(PiecePlaceEnum.DOT_7_4);
        menuButton.setButtonPlaceEnum(ButtonPlaceEnum.SC_7_4);
        menuButton.setBoomEnum(BoomEnum.PARABOLA_4);
        menuButton.setNormalColor(Color.WHITE);
        // ???????????????
        TextOutsideCircleButton.Builder bluetoothListBuilder = new TextOutsideCircleButton.Builder().listener(new OnBMClickListener() {
            @Override
            public void onBoomButtonClick(int index) {
                // ????????????????????????
                builder = new AlertDialog.Builder(MainActivity.this) ;
                View alertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.bluetooth_layout_alert,null) ;
                bluetoothSwitch = alertView.findViewById(R.id.blueTooth_switch) ;
                scanButton = alertView.findViewById(R.id.scan_btn) ;
                createBluetoothSwitch(alertView) ;   // ????????????
                createScanButton(alertView) ;
                // ??????????????????
                bluetoothAlertDialog = builder.setTitle("    ????????????").setView(alertView).setIcon(R.drawable.ic_baseline_bluetooth_24).create() ;
                bluetoothAlertDialog.setCancelable(true);
                bluetoothAlertDialog.setCanceledOnTouchOutside(true);   // ???????????????????????????

                // ????????????
                bluetoothAlertDialog.show();
            }
        }) ;
        // ?????????
        TextOutsideCircleButton.Builder angularVelocityChartBuilder = new TextOutsideCircleButton.Builder().listener(new OnBMClickListener() {
            @Override
            public void onBoomButtonClick(int index) {
                if (lineChartFragment==null){
                    initializeFragments(LineChartFragment.G);
                    LineChartFragment.setState(true);
                }else if (LineChartFragment.getState()==true){
                    getSupportFragmentManager().beginTransaction().remove(lineChartFragment).commit() ;
                    LineChartFragment.setState(false);
                }else{
                    initializeFragments(LineChartFragment.G);
                    LineChartFragment.setState(true);
                }
            }
        }) ;
        // ??????
        TextOutsideCircleButton.Builder angelChartBuilder = new TextOutsideCircleButton.Builder().listener(new OnBMClickListener() {
            @Override
            public void onBoomButtonClick(int index) {
                if (lineChartFragment==null){
                    initializeFragments(LineChartFragment.A);
                    LineChartFragment.setState(true);
                }else if (LineChartFragment.getState()==true){
                    getSupportFragmentManager().beginTransaction().remove(lineChartFragment).commit() ;
                    LineChartFragment.setState(false);
                }else{
                    initializeFragments(LineChartFragment.G);
                    LineChartFragment.setState(true);
                }
            }
        }) ;
        // ?????????
        TextOutsideCircleButton.Builder accelerationChartBuilder = new TextOutsideCircleButton.Builder().listener(new OnBMClickListener() {
            @Override
            public void onBoomButtonClick(int index) {
                if (lineChartFragment==null){
                    initializeFragments(LineChartFragment.A);
                    LineChartFragment.setState(true);
                }else if (LineChartFragment.getState()==true){
                    getSupportFragmentManager().beginTransaction().remove(lineChartFragment).commit() ;
                    LineChartFragment.setState(false);
                }else{
                    initializeFragments(LineChartFragment.G);
                    LineChartFragment.setState(true);
                }
            }
        }) ;
        // ?????????
        TextOutsideCircleButton.Builder dataListBuilder = new TextOutsideCircleButton.Builder().listener(new OnBMClickListener() {
            @Override
            public void onBoomButtonClick(int index) {
                Intent toDataTableActivity = new Intent(MainActivity.this, DataTableActivity.class) ;
                startActivity(toDataTableActivity);
            }
        }) ;
        //??????
        TextOutsideCircleButton.Builder mapBuilder = new TextOutsideCircleButton.Builder().listener(new OnBMClickListener() {
            @Override
            public void onBoomButtonClick(int index) {
                Intent toMapActivity = new Intent(MainActivity.this, BaiduMapActivity.class) ;
                startActivity(toMapActivity);
            }
        }) ;
        // ??????
        TextOutsideCircleButton.Builder musicPlayerBuilder = new TextOutsideCircleButton.Builder().listener(new OnBMClickListener() {
            @Override
            public void onBoomButtonClick(int index) {
                Intent toMusicActivity = new Intent(MainActivity.this, MusicListActivity.class) ;
                startActivity(toMusicActivity);
            }
        }) ;
        // ??????????????????
        bluetoothListBuilder.normalColorRes(R.color.red_material);    // ????????????
        angularVelocityChartBuilder.normalColorRes(R.color.orange_material);    // ?????????
        accelerationChartBuilder.normalColorRes(R.color.red_material); // ?????????
        angelChartBuilder.normalColorRes(R.color.blueGreen_material); //??????
        dataListBuilder.normalColorRes(R.color.purple_material); // data
        mapBuilder.normalColorRes(R.color.green_material); // map
        musicPlayerBuilder.normalColorRes(R.color.gray_material); // music
        menuButton.addBuilder(bluetoothListBuilder
                .normalImageRes(R.drawable.bluetooth).normalText("????????????"));
        menuButton.addBuilder(angularVelocityChartBuilder
                .normalImageRes(R.drawable.chart).normalText("?????????"));
        menuButton.addBuilder(accelerationChartBuilder
                .normalImageRes(R.drawable.chart).normalText("?????????"));
        menuButton.addBuilder(angelChartBuilder
                .normalImageRes(R.drawable.chart).normalText("??????"));
        menuButton.addBuilder(dataListBuilder
                .normalImageRes(R.drawable.message).normalText("DATA"));
        menuButton.addBuilder(mapBuilder
                .normalImageRes(R.drawable.map).normalText("MAP"));
        menuButton.addBuilder(musicPlayerBuilder
                .normalImageRes(R.drawable.play).normalText("MUSIC"));
    }

    private void createScanButton(View view) {
        scanButton.setOnClickListener(this);
    }

    // recycleViewItem ??? decoration
    class MyDecoraton extends RecyclerView.ItemDecoration{
        @Override
        public void getItemOffsets(@NonNull @NotNull Rect outRect, @NonNull @NotNull View view, @NonNull @NotNull RecyclerView parent, @NonNull @NotNull RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            outRect.set(0,0,0,3);
        }
    }

    /**
     * ????????????
     */
    private void checkPermissions() {
        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
        List<String> permissionDeniedList = new ArrayList<>();
        for (String permission : permissions) {
            int permissionCheck = ContextCompat.checkSelfPermission(this, permission);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                onPermissionGranted(permission);
            } else {
                permissionDeniedList.add(permission);
            }
        }
        if (!permissionDeniedList.isEmpty()) {
            String[] deniedPermissions = permissionDeniedList.toArray(new String[permissionDeniedList.size()]);
            ActivityCompat.requestPermissions(this, deniedPermissions, REQUEST_CODE_PERMISSION_LOCATION);
        }
    }

    /**
     * ????????????
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public final void onRequestPermissionsResult(int requestCode,
                                                 @NonNull String[] permissions,
                                                 @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE_PERMISSION_LOCATION:
                if (grantResults.length > 0) {
                    for (int i = 0; i < grantResults.length; i++) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            onPermissionGranted(permissions[i]);
                        }
                    }
                }
                break;
        }
    }
    /**
     * ??????GPS
     * @param permission
     */
    private void onPermissionGranted(String permission) {
        switch (permission) {
            case Manifest.permission.ACCESS_FINE_LOCATION:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !checkGPSIsOpen()) {
                    new AlertDialog.Builder(this)
                            .setTitle("??????")
                            .setMessage("???????????????????????????????????????????????????")
                            .setNegativeButton("??????",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            finish();
                                        }
                                    })
                            .setPositiveButton("????????????",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                            startActivityForResult(intent, REQUEST_CODE_OPEN_GPS);
                                        }
                                    })
                            .setCancelable(false)
                            .show();
                } else {
                    //GPS???????????????
                }
                break;
        }
    }
    /**
     * ??????GPS????????????
     * @return
     */
    private boolean checkGPSIsOpen() {
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null)
            return false;
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
    }

}