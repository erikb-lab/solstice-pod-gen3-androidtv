package com.podtv.volumebar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        c.startForegroundService(new Intent(c, VolumeService.class));
    }
}
