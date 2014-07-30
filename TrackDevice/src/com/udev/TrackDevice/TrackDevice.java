/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.udev.TrackDevice;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.udev.TrackDevice.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.NeighboringCellInfo;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Track device GPS coordinates, bearing, speed and cellular signal strength, LAC, cell ID, ,....
 * continuously. user have option to log data to file in CSV format and file would save to
 * /mnt/sdcard/media/YYMMDD-HHMMSS.csv
 * 
 * Author: Jihshin Jacob Jan
 * Date: July 28, 2014
 */
public class TrackDevice extends Activity {
	private static final int FILE_SELECT_CODE = 0;
	protected int phoneASU=0;
	protected String lac, cellId;
			
	TextView locView, phoneView, cellView;
	String me = getClass().getSimpleName();
	String ss, cellLocation, callState, dataActivity, dataConnectionState;
	String buf = "";
	String logFilePath, localPath ="/media/";
	boolean ToastIt = true, logToFile = false;
	File myFile;
	FileOutputStream fos;
	int BUFFER_LIMIT=1024;
	Button buttonPressed;
	
	public void onCreate(Bundle savedInstanceState) {
	       super.onCreate(savedInstanceState);    
	       // Register the listener with the telephony manager
	       // Create a new PhoneStateListener
		   TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
	       /* Use the LocationManager class to obtain GPS locations */
	       LocationManager mlocManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
	       LocationListener mlocListener = new MyLocationListener();
	       mlocManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, 0, 0, mlocListener);	
	       if ( !mlocManager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {
	           buildAlertMessageNoGps();
	       }
	       setContentView( R.layout.trackdevice );
	       locView = (TextView)findViewById(R.id.locationText);
	       
		   myFile = new File(Environment.getExternalStorageDirectory(), localPath + getTimeStampFname() + ".csv");
		   logFilePath = myFile.getAbsolutePath();
		    if (!myFile.exists()) {
				try {
					myFile.createNewFile();
					fos = new FileOutputStream(myFile);
					String Title="DateTime,longitude,Latitude,Altitude,Bearing,Speed,ASU,LAC,CellID";
					writeToFile(fos, Title);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
		    }
	       CheckBox checkBox = (CheckBox) findViewById(R.id.saveToFile);
	       OnCheckedChangeListener chkBoxListner = new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if ( isChecked ) {
						logToFile=true;
						if ( null == fos ) {
							try {
								fos = new FileOutputStream(myFile);
							} catch (FileNotFoundException e) {
								e.printStackTrace();
							}
						}
					} else {
						logToFile=false;  // flush the buffer
					    if ( null != fos && buf.length() > 0) {
							try {
								writeToFile( fos, buf );
							} catch (IOException e) {
								e.printStackTrace();
							}
							buf = "";
					    } 
					}
				}
	       };
	         
	       locView.setBackgroundColor(Color.parseColor("#000000"));
	       locView.setTextColor(Color.parseColor("#FFFFFF"));
	       locView.setMovementMethod(new ScrollingMovementMethod());
	       locView.setText(me);
	       
	       PhoneStateListener mPhoneStatelistener = new PhoneStateListener() {
		         @Override
		         public void onSignalStrengthChanged(int asu) {
		        	 phoneASU = asu;
		        	 ss = "\ndBm:" + (2 * asu - 113) + " ASU :" + asu;
		         }
		         
		         public void onCellLocationChanged(CellLocation celllocation) {
		        	 String celllocstr, celllocInfo[];
		        	 celllocstr = celllocation.toString();
		        	 celllocstr = celllocstr.substring(1, celllocstr.length()-1);
		        	 celllocInfo = celllocstr.split(",");
		        	 cellLocation = "\nLac/CellID/Sec:" + celllocation.toString();
		        	 lac = celllocInfo[0];
		        	 cellId = celllocInfo[1];
		         }
		         
		         public void onCallStateChanged(int state, String incomingNumber){
		        	 callState = "\nCall State:" + state + " incoming:" + incomingNumber;
		         }
		         
		         public void onDataConnectionStateChanged(int state) {
		        	 dataConnectionState = "\nState:" + state;
		         }
		         
		         public void onDataActivity(int direction) {
		        	 dataActivity = "\nData Activity:" + direction;
		         }
		   };
		   tm.listen(mPhoneStatelistener, PhoneStateListener.LISTEN_SIGNAL_STRENGTH 
		    		   							| PhoneStateListener.LISTEN_CELL_LOCATION
		    		   							| PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
		    		   							| PhoneStateListener.LISTEN_DATA_ACTIVITY
		    		   							| PhoneStateListener.LISTEN_CALL_STATE);
		   String mccmnc = tm.getSimOperator();

	} // of onCreate
	
	/* Class to implement My Location Listener */
	public class MyLocationListener implements LocationListener {
		@Override
		public void onLocationChanged(Location loc) {

			String cltime = convertTime(loc.getTime());
			String longitude = String.format("%.4f", loc.getLongitude());
			String latitude = String.format("%.4f", loc.getLatitude());
			String altitude = String.format("%.4f", loc.getAltitude());
			String bearing = String.format("%.4f", loc.getBearing());
			String speed = String.format("%.4f", loc.getSpeed() * 3.6);  // 3600 sec / 1000 meters
			String Text = "Date/Time:" + cltime +
					"\nLongitude :" + longitude +
					"\nLatitude :" + latitude +
					"\nAltitude :" + altitude +
					"\nBearing :" + bearing + ss + cellLocation + dataActivity + callState +
					"\nSpeed (km/hr) :" + speed;
			locView.setText(Text);
			String fData="\n"+cltime+","+longitude +","+latitude+","+altitude+","+bearing+","+speed+","+phoneASU+
						","+lac+","+cellId;
			buf += fData;
			if (logToFile && buf.length() > BUFFER_LIMIT) {
				try {
					writeToFile( fos, buf );
				} catch (IOException e) {
					e.printStackTrace();
				}
				buf = "";
			}
		    Log.i(me, Text);
		}
				
		@Override
		public void onProviderDisabled(String provider) {
			String Text = "GPS provider " + provider + " disabled"; 
			locView.setText(Text);
		}
	
		@Override
		public void onProviderEnabled(String provider) {
			String Text = "GPS provider " + provider + " enabled"; 
			locView.setText(Text);
		}
	
		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
		}		
	} /* End of Class MyLocationListener */

	private void buildAlertMessageNoGps() {
	    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
	           .setCancelable(false)
	           .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	               public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
	                   startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
	               }
	           })
	           .setNegativeButton("No", new DialogInterface.OnClickListener() {
	               public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
	                    dialog.cancel();
	               }
	           });
	    
	    final AlertDialog alert = builder.create();
	    alert.show();
	}
	
	@SuppressLint("Override")
	public void onBackPressed() {
	    new AlertDialog.Builder(this)
	           .setMessage("Are you sure you want to exit?")
	           .setCancelable(false)
	           .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	               public void onClick(DialogInterface dialog, int id) {
	                   try {
	   	       	    	if ( null != fos ) {
		    	    		if ( buf.length() > 0) {
		    	    			writeToFile(fos, buf);
		    		    	}
		    		    	fos.close();
		    	    	}
	                   } catch (IOException e) {
	                	   e.printStackTrace();
	                   }
	                   finish();
	               }
	           })
	           .setNegativeButton("No", new DialogInterface.OnClickListener() {
	               public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
	            	    myFile.delete();
	            	    dialog.cancel();
	               }
	           })
	           .show();
	}
	
	@Override
	public void onStop() {
	    super.onStop();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		showToast( "Data save to file :" + logFilePath, Toast.LENGTH_LONG);
	}

	protected boolean writeToFile(FileOutputStream fileoutstream, String buf) throws IOException {	    
	    byte[] data = buf.getBytes();
	    try {
	    	fileoutstream.write(data);	       
	    	fileoutstream.flush();
	        return true;
	    } catch (FileNotFoundException e) {
	        e.printStackTrace();
	        return false;
	    }
	}
	
	public void showToast(String text, int duration) {
		if ( ToastIt )
			Toast.makeText(getApplicationContext(), text, duration).show();	
	}

	public String convertTime(long time){
	    Date date = new Date(time);
	    Format simpleformat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	    return simpleformat.format(date).toString();
	}
	
	public String getTimeStampFname() {
	    Date date = new Date();
	    Format fname = new SimpleDateFormat("yyMMdd-HHmmss");
	    return fname.format(date).toString();
	}
}