package com.example.biportal;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.location.*;
import android.os.Environment;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class MainActivity extends ActionBarActivity {

    // UI controls
    private TextView _txtTime = null;
    private ListView _listView = null;
    private GoogleMap _map = null;

    // internal variables
    private ArrayAdapter<String> _adapter = null;
    private LocationManager _locationManager = null;
    private boolean _recoding = false;
    private String _dumpFileName = null;
    private String _directoryPath = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _txtTime = (TextView) findViewById(R.id.txtTime);
        _listView = (ListView) findViewById(R.id.listView);
        _map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
        _listView = (ListView) findViewById(R.id.listView);

        _locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        _directoryPath = Environment.getExternalStorageDirectory()+"/biportal/";

        File file = new File(_directoryPath);
        if(!file.exists())
            file.mkdirs();

        _map.setMyLocationEnabled(true);

        Location location = GetLastKnownLocation();

        if(location == null)
            ShowGPSActivationDialog();
        else
            _map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 15));

        AddListViewItems();

        AdapterView.OnItemClickListener onClickListItem = new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3)
            {
                try
                {
                    if (_recoding == true)
                        Toast.makeText(getBaseContext(), "경로를 기록 중이므로 예전 기록을 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
                    else
                    {
                        _map.clear();

                        String line;
                        LatLng preLocation = null;
                        LatLng curLocation = null;

                        BufferedReader reader = new BufferedReader(new FileReader(_directoryPath + _adapter.getItem(arg2)));

                        while ((line = reader.readLine()) != null)
                        {
                            String[] info = line.split("\\|");

                            curLocation = new LatLng(Double.parseDouble(info[1]), Double.parseDouble(info[2]));

                            if(preLocation != null)
                                _map.addPolyline(new PolylineOptions().add(preLocation, curLocation).width(15).color(Color.RED));

                            preLocation = curLocation;
                        }

                        reader.close();

                        _txtTime.setText(_adapter.getItem(arg2));
                    }
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        };

        // ListView 아이템 터치 시 이벤트 추가
        _listView.setOnItemClickListener(onClickListItem);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.


        return super.onOptionsItemSelected(item);
    }

    public void btnStart_onClick(View v)
    {
        if(StartLocationService() == true) {
            long time = System.currentTimeMillis();
            SimpleDateFormat dayTime = new SimpleDateFormat("yyyyMMdd_hhmmss");
            _dumpFileName = dayTime.format(new Date(time)) + ".txt";

            _recoding = true;

            _txtTime.setText("기록을 시작합니다.");
        }
        else
            _txtTime.setText("이미 기록 중입니다.");
    }

    public void btnStop_onClick(View v)
    {
        if(_recoding == true) {
            if(DumpLocation() == true) {
                Toast.makeText(getBaseContext(), "저장 완료.", Toast.LENGTH_SHORT).show();
                AddListViewItems();
            }
            else
                Toast.makeText(getBaseContext(), "저장 실패", Toast.LENGTH_SHORT).show();

            _recoding = false;

            _txtTime.setText("기록을 종료합니다.");
        }
        else
            _txtTime.setText("기록하고 있지 않습니다.");
    }

    private void ShowGPSActivationDialog()
    {

        // Build the alert dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("GPS 꺼짐");
        builder.setMessage("GPS 를 켭니다.");
        builder.setPositiveButton("확인", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                // Show location settings when the user acknowledges the alert dialog
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        });

        Dialog alertDialog = builder.create();
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.show();
    }

    private Location GetLastKnownLocation()
    {
        Location location = null;

        try
        {
            boolean gpsEnabled = _locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean networkEnabled = _locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (networkEnabled)
                location = _locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (location == null && gpsEnabled)
                location = _locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return location;
    }

    private boolean StartLocationService()
    {
        boolean retVal = true;

        try
        {
            GPSListener gpsListener = new GPSListener();
            long minTime = 5000;
            float minDistance = 0;

            boolean gpsEnabled = _locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean networkEnabled = _locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if(gpsEnabled)
                _locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, minDistance, gpsListener);

            if(!gpsEnabled && networkEnabled)
                _locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTime, minDistance, gpsListener);

            if(!gpsEnabled && !networkEnabled)
            {
                ShowGPSActivationDialog();
                retVal = false;
            }
        }
        catch(Exception ex)
        {
        }

        return retVal;
    }

    private boolean DumpLocation()
    {
        boolean retVal = true;

        try
        {
            if(GPSListener._location.size() > 0) {
                FileOutputStream outputStream = new FileOutputStream(_directoryPath + _dumpFileName);

                TreeMap<String,LatLng> tm = new TreeMap<String,LatLng>(GPSListener._location);
                Iterator<String> it = tm.keySet( ).iterator();
                //Iterator<String> iteratorKey = tm.descendingKeySet().iterator(); // 내림차순

                while(it.hasNext()){
                    String key = it.next();
                    LatLng position = tm.get(key);
                    String line = key + " | " + position.latitude + " | " + position.longitude + "\n";

                    outputStream.write(line.getBytes());
                }

                outputStream.close();
            }
        }catch( Exception e){
            e.printStackTrace();
            retVal = false;
        }


        return retVal;
    }

    private void AddListViewItems()
    {
        try
        {

            FilenameFilter filter = new FilenameFilter()
            {
                    public boolean accept(File dir, String name)
                    {
                        return name.endsWith("txt");
                    }
            };

            File file = new File(_directoryPath);
            File[] files = file.listFiles(filter);
            ArrayList<String> titleList = new ArrayList<String>();

            for(int i = 0;i < files.length; ++i)
               titleList.add(files[i].getName());

            Collections.sort(titleList, Collections.reverseOrder());

            _adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, titleList);
            _listView.setAdapter(_adapter);
        }
        catch (Exception ex)
        {
        }
    }
}
