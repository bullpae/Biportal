package com.example.biportal;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;

import com.google.android.gms.maps.model.LatLng;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class GPSListener implements LocationListener
{
    public static HashMap<String, LatLng> _location = null;

    public GPSListener()
    {
        _location = new HashMap<>();
    }

    public void onLocationChanged(Location location)
    {
        Double latitude = location.getLatitude();
        Double longitude = location.getLongitude();

        long time = System.currentTimeMillis();
        SimpleDateFormat dayTime = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

        _location.put(dayTime.format(new Date(time)), new LatLng(latitude, longitude));
    }

    public void onProviderDisabled(String provider)
    {
    }

    public void onProviderEnabled(String provider)
    {
    }

    public void onStatusChanged(String provider, int status, Bundle extras)
    {
    }
}
