package eus.ehu.neurgai;

import java.util.HashMap;
import java.util.Map;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

@SuppressLint("InlinedApi")
public class BluetoothDialog extends ListActivity {
	private ListView lsv=null;
	private static final int REQUEST_ENABLE_BT = 0;
	private ArrayAdapter<String> mArrayAdapterNombres=null;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothDevice device ;
	private BroadcastReceiver mReceiver;
	private Button buscando;
	Map<String, BluetoothDevice> dispositivosBT= new HashMap<String, BluetoothDevice>();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setResult(Activity.RESULT_CANCELED);
		
		setContentView(R.layout.bluetooth_dialog);
		lsv=(ListView)findViewById(android.R.id.list);
		buscando=(Button)findViewById(R.id.button1);
		configurarBT();
	}
	
	public void onDiscovery(View view){
		
		mArrayAdapterNombres.clear();
		dispositivosBT.clear();
		if(mReceiver!=null){
			unregisterReceiver(mReceiver);
		}
		
		mBluetoothAdapter.startDiscovery();
		buscando.setText(getText(R.string.buscandoBT));
		lsv.setVisibility(View.VISIBLE);
		buscando.setEnabled(false);
		mReceiver = new BroadcastReceiver() {
		    public void onReceive(Context context, Intent intent) {
		        String action = intent.getAction();
		        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
		        	device  = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		            mArrayAdapterNombres.add(device.getName());
		            dispositivosBT.put(device.getName(), device);
		        }
		    }
		};
		// Register the BroadcastReceiver
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		registerReceiver(mReceiver, filter);
	}
	
	
	private void configurarBT() {
		
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
		    
		}
		if (!mBluetoothAdapter.isEnabled()) {
		    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
		mBluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
		
		
		mArrayAdapterNombres= new ArrayAdapter<String>(this, android.R.layout.simple_selectable_list_item);
		lsv.setAdapter(mArrayAdapterNombres);
	}
	
	protected void onListItemClick(ListView l, View v, int position, long id) {
	      super.onListItemClick(l, v, position, id);
	      
	      Toast.makeText(
	           BluetoothDialog.this
	            ,l.getItemAtPosition(position).toString()
	            ,Toast.LENGTH_LONG)
	            .show();
	      
	      Intent i = new Intent( BluetoothDialog.this, NeurGai.class);
	      
	      i.putExtra("dispositivo",dispositivosBT.get(l.getItemAtPosition(position).toString()));
	      setResult( Activity.RESULT_OK, i );
	      BluetoothDialog.this.finish();
	}
	
	
	public void onCancel(View view){
		mBluetoothAdapter.cancelDiscovery();
		buscando.setText(getText(R.string.buscarBT));
		buscando.setEnabled(true);
	}
	
	 @Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		
	}
	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		
		unregisterReceiver(mReceiver);
		
		super.onPause();
	}
	
	@Override
	protected void onRestart() {
		// TODO Auto-generated method stub
		super.onRestart();
		// Register the BroadcastReceiver
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		
		registerReceiver(mReceiver, filter);
		
	}
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		if(mBluetoothAdapter!=null){
			mBluetoothAdapter.cancelDiscovery();
		}
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.bluetooth_dialog, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
