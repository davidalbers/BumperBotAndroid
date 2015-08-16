package edu.dalbers.btligth;

import java.util.Timer;
import java.util.TimerTask;

import edu.dalbers.arduinobt.R;

import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout.LayoutParams;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ToggleButton;


public class MainActivity extends Activity implements SensorEventListener {


	// Message types sent from the BluetoothReadService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;	

	// Key names received from the BluetoothChatService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	public static final int DEVICE_LIST_RESULT = 0;

	private BluetoothAdapter btAdapter;
	private BluetoothSerialService btService;
	
	private Button forwardButton;
	private Button leftButton;
	private Button rightButton;
	
	private SharedPreferences prefs;
	private String PREF_STRING = "btlight_prefs";
	private final double STEERING_SENSITIVITY = 1.75;
	private SensorManager sensorManager;
	private Sensor sensor;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		leftButton = (Button)findViewById(R.id.left_button);
		leftButton.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if(event.getAction() == MotionEvent.ACTION_DOWN) {
					if(forwardButton.isPressed())
						slightLeft();
					else 
						left();
				}
				else if(event.getAction() == MotionEvent.ACTION_UP) {
					if(!forwardButton.isPressed())
						stop();
					else
						forward();
				}
				return false;
			}
		});
		
		
		rightButton = (Button)findViewById(R.id.right_button);
		rightButton.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if(event.getAction() == MotionEvent.ACTION_DOWN) {
					if(forwardButton.isPressed())
						slightRight();
					else
						right();
				}
				else if(event.getAction() == MotionEvent.ACTION_UP) {
					if(!forwardButton.isPressed())
						stop();
					else 
						forward();
				}
				return false;
			}
		});
		
		forwardButton = (Button)findViewById(R.id.up_button);
		forwardButton.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if(event.getAction() == MotionEvent.ACTION_DOWN) {
					if(!leftButton.isPressed() && !rightButton.isPressed())
						forward();
				}
				else if(event.getAction() == MotionEvent.ACTION_UP) {
					if(!leftButton.isPressed() && !rightButton.isPressed())
						stop();
				}
				return false;
			}
		});
		
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		sensorManager.registerListener(this, sensor, 100000);

		btAdapter = BluetoothAdapter.getDefaultAdapter();
		btService = new BluetoothSerialService(this, btHandler);

		prefs = getSharedPreferences(PREF_STRING, 0);
	}

	@Override
	public void onResume(){
		super.onResume();
		String deviceAddr = prefs.getString("device_address", "none");
		if(!deviceAddr.equals("none")) {
			BluetoothDevice device = btAdapter.getRemoteDevice(deviceAddr);
			btService.connect(device);
		}
	}
	
	@Override
	public void onStop(){
		super.onStop();
		btService.stop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	boolean useSteeringWheel = false;
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.steering) {
			LinearLayout topRow = (LinearLayout)findViewById(R.id.top_row);
			LayoutParams topParams = (LayoutParams) topRow.getLayoutParams();
			topParams.weight = 1;
			topRow.setLayoutParams(topParams);
			LinearLayout bottomRow = (LinearLayout)findViewById(R.id.bottom_row);
			LayoutParams bottomParams = (LayoutParams) bottomRow.getLayoutParams();
			bottomParams.weight = 0;
			bottomRow.setLayoutParams(bottomParams);
			bottomRow.setVisibility(View.INVISIBLE);
			useSteeringWheel = true;
			return true;
		}
		else if (id == R.id.buttons) {
			LinearLayout topRow = (LinearLayout)findViewById(R.id.top_row);
			LayoutParams topParams = (LayoutParams) topRow.getLayoutParams();
			topParams.weight = .5f;
			topRow.setLayoutParams(topParams);
			LinearLayout bottomRow = (LinearLayout)findViewById(R.id.bottom_row);
			LayoutParams bottomParams = (LayoutParams) bottomRow.getLayoutParams();
			bottomParams.weight = .5f;
			bottomRow.setLayoutParams(bottomParams);
			bottomRow.setVisibility(View.VISIBLE);
			useSteeringWheel = false;
			return true;
		}
		else if(id == R.id.connect) {
			Intent deviceListIntent = new Intent(getBaseContext(), DeviceListActivity.class);
			startActivityForResult(deviceListIntent, DEVICE_LIST_RESULT);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {

		case DEVICE_LIST_RESULT:

			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				// Get the device MAC address
				String address = data.getExtras()
						.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

				//save the device
				Editor prefEditor = prefs.edit();
				prefEditor.putString("device_address", address);
				prefEditor.commit();

				// Get the BLuetoothDevice object
				BluetoothDevice device = btAdapter.getRemoteDevice(address);
				// Attempt to connect to the device
				btService.connect(device);                
			}
			break;


		}
	}
	

	private final Handler btHandler = new Handler() {
		//Gets messages from bluetooth, you could do stuff with those messages
		//but for now the only one we care about is the connect message
		@Override
		public void handleMessage(Message msg) {        	
			switch (msg.what) 
			{
			case MESSAGE_STATE_CHANGE:

				switch (msg.arg1) 
				{
				case BluetoothSerialService.STATE_CONNECTED:
					Toast.makeText(getBaseContext(), "Connected", Toast.LENGTH_SHORT).show();
//					sendMessage.cancel(); UNCOMMENT
//					sendMessage.purge();  TO 
//					sendMessage = new Timer(); CONTINUOUSLY SEND
//					sendMessage.schedule(new SendMessageTask(), 100, 100); UPDATES
					break;

				case BluetoothSerialService.STATE_CONNECTING:
				case BluetoothSerialService.STATE_LISTEN:
				case BluetoothSerialService.STATE_NONE:
					break;
				default:
					break;
				}

			case MESSAGE_WRITE:
			case MESSAGE_DEVICE_NAME:
			case MESSAGE_TOAST:
				break;
			default:
				break;
			}
		}
	};    

	private void forward() {
		if(btService != null) {
			byte[] buf = new byte[] { (byte) 0x03, (byte) 165, (byte) 20 };
			btService.write(buf);
		}
	}
	
	private void backward() {
		if(btService != null) {
			byte[] buf = new byte[] { (byte) 0x03, (byte) 20, (byte) 165 };
			btService.write(buf);
		}
	}
	
	private void left() {
		if(btService != null) {
			byte[] buf = new byte[] { (byte) 0x03, (byte) 75, (byte) 75 };
			btService.write(buf);
		}
	}
	
	private void right() {
		if(btService != null) {
			byte[] buf = new byte[] { (byte) 0x03, (byte) 110, (byte) 110 };
			btService.write(buf);
		}
	}
	
	private void slightRight() {
		if(btService != null) {
			byte[] buf = new byte[] { (byte) 0x03, (byte) 165, (byte) 75 };
			btService.write(buf);
		}
	}
	
	private void slightLeft() {
		if(btService != null) {
			byte[] buf = new byte[] { (byte) 0x03, (byte) 110, (byte) 20 };
			btService.write(buf);
		}
	}
	
	private void stop() {
		if(btService != null) {
			byte[] buf = new byte[] { (byte) 0x03, (byte) 95, (byte) 95 };
			btService.write(buf);
		}
	}
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		//right 165
		//left 20
		//forward l 165, r 20
		//horizontal 0
		//tilt left -
		//tilt right +
		if(useSteeringWheel) {
			if(((Button)findViewById(R.id.up_button)).isPressed() && btService != null) {
				if(event.values[1] > 1) {
					//turning right
					double multiplier = Math.min(Math.abs(event.values[1]), 9.8) / 9.8;
					double speed = 70 * Math.pow(multiplier, 1.0/STEERING_SENSITIVITY);
					int leftSpeed = (int)(165);
					int rightSpeed = (int) (20 + speed);
					byte[] buf = new byte[] { (byte) 0x03, (byte) leftSpeed, (byte) rightSpeed };
					Log.d("Wheel", "left " + leftSpeed);
					btService.write(buf);
				}
				else if (event.values[1] < -1) {
					double multiplier = Math.min(Math.abs(event.values[1]), 9.8) / 9.8;
					double speed = 70 * Math.pow(multiplier, 1.0/STEERING_SENSITIVITY);
					int leftSpeed = (int)(165 - speed);
					int rightSpeed = (int) (20);
					byte[] buf = new byte[] { (byte) 0x03, (byte) leftSpeed, (byte) rightSpeed };
					Log.d("Wheel", "left " + leftSpeed);
					btService.write(buf);
					
				}
				else {
					byte[] buf = new byte[] { (byte) 0x03, (byte) 165, (byte) 20 };
					Log.d("Wheel", "left " + 165);
					btService.write(buf);
					
				}
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}
	
}
