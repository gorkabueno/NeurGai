package eus.ehu.neurgai;

import java.util.Set;

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

public class BluetoothDialog extends ListActivity {
	private ListView lsv=null;
	private static final int REQUEST_ENABLE_BT = 0;
	private ArrayAdapter<String> mArrayAdapter=null;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothDevice device ;
	private BroadcastReceiver mReceiver;
	private Set<BluetoothDevice> pairedDevices;
	private Button buscando;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		setContentView(R.layout.bluetooth_dialog);
		lsv=(ListView)findViewById(android.R.id.list);
		buscando=(Button)findViewById(R.id.button1);
		configurarBT();
		//mostrarDispositivosBT();
		//onDiscovery();
	}
	
	public void onDiscovery(View view){
		
		mBluetoothAdapter.startDiscovery();
		buscando.setText("Buscando...");
		lsv.setVisibility(View.VISIBLE);
		buscando.setEnabled(false);
		mReceiver = new BroadcastReceiver() {
		    public void onReceive(Context context, Intent intent) {
		        String action = intent.getAction();
		        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
		        	device  = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);		//Mirar Documenación.
		            mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
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
		else{
			Toast.makeText(getApplicationContext(), "BT activado.", Toast.LENGTH_LONG).show();
		}
		mBluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
		pairedDevices = mBluetoothAdapter.getBondedDevices();
		mArrayAdapter= new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
		lsv.setAdapter(mArrayAdapter);
	}
	
	protected void onListItemClick(ListView l, View v, int position, long id) {
	      super.onListItemClick(l, v, position, id);
	      
	      Toast.makeText(
	           BluetoothDialog.this
	            ,l.getItemAtPosition(position).toString()
	            ,Toast.LENGTH_LONG)
	            .show();
	      buscando.setText("Buscar dipositivo");
	      buscando.setEnabled(true);
	      Toast.makeText(getApplicationContext(), "Hilo lanzado", Toast.LENGTH_SHORT).show();
	      Intent i = new Intent( BluetoothDialog.this, NeurGai.class);
	      mBluetoothAdapter.cancelDiscovery();
	      i.putExtra("dispositivo",device);
	      setResult( Activity.RESULT_OK, i );
	      BluetoothDialog.this.finish();
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
