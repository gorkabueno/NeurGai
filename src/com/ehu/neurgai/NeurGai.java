package com.ehu.neurgai;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.interpolation.UnivariateInterpolator;
import org.jtransforms.fft.DoubleFFT_1D;
import org.w3c.dom.Text;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Paint.Align;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder.AudioSource;
import android.media.audiofx.AutomaticGainControl;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.Layout;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.ehu.neurgai.BaseDatosNeurGAI.ColumnasCostes;
import com.ehu.neurgai.BaseDatosNeurGAI.ColumnasTarifas;
import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.helper.StaticLabelsFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class NeurGai extends ActionBarActivity {
	
	private Menu menu;
	
	private boolean medidaSonda = true; 	// si false la medida es a través de bluetooth + KL05Z
	private boolean euskaraz = false;
	private short periodicidadMedidaEnSegundos = 1;
	private boolean empezar = true;
	private boolean midiendo = false;
	private boolean anotar = false;
	private boolean grabarDatos = false;
	private boolean visible = true;
	
	private boolean botonPulsado = false;
	private boolean tonoLanzado = false;
	private boolean pantallaInicializada = false;
	
	private Object llaveBotonPulsado = new Object();
	private Object llaveTonoLanzado = new Object();
	private Object llavePantallaInicializada = new Object();
	
	private int bufferSize = 0;
	private AudioRecord recorder = null;
	private int bufferSizeAudio = 0;
	private AudioTrack audioTrack;
	private short[] datosBuffer;
	
	static AutomaticGainControl cag;
	
	private Button boton;
	
	private ProgressBar mProgress;
    private int mProgressStatus = 0;
    
    short[] muestrasTonoGenerado;
    short[] muestrasGrabadas;
    
    short amplitudCalibradoFiltro = 0;
    double medidaReferencia = 0;
    double tiempoInicioMedida = 0;

    private double[] DEP_H = new double[Constants.numeroFrecuenciasInterp];
	private double[] DEP_X_grabar = new double[Constants.numeroFrecuenciasInterp];
	private double[] DEP_Y_grabar = new double[Constants.numeroFrecuenciasInterp];
	private double[] muestras_grabar = new double[Constants.LONGITUD_TONO_GRABADO];
	
	final Handler handler = new Handler();
	GraphView graficoMedidas;
	
	LineGraphSeries<DataPoint> serieMedidas;
	
	private MyPhoneStateListener phoneStateListener = new MyPhoneStateListener();
	
	private String comentario = null; //Revisar por si da error en la BBDD.
	
	/**************************BASE DE DATOS******************************************/
	
	//Leerá la tabla de los costes de energía que hay en la BBDD de datos 
	private List<Coste> extraerTablaCostesBBDD(){
		
		BaseDatosNeurGAI bbdd = new BaseDatosNeurGAI(this);
		SQLiteDatabase dbRead=bbdd.getReadableDatabase();
		List<Coste> tablaCostes=new ArrayList<Coste>();
		
		

		Cursor c = dbRead.query(
				ColumnasCostes.TABLE_NAME, BaseDatosNeurGAI.projectionCostes , null, null, null, null, null, null
		    );
	
		c.moveToFirst();
		do{
			
			Coste costeFila=new Coste();
			
			

			/*************************Tabla de los Costes*************************************/
			
			costeFila.setComentario(c.getString(
					c.getColumnIndex(
							BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_COSTE_COMENTARIOS)));

			costeFila.setFechaMedida(c.getString(
								c.getColumnIndex(
										BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_FECHA_MEDIDA)));
			costeFila.setTiempoMedida(c.getDouble
								(c.getColumnIndex
									(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_TIEMPO_MEDIDA)));
			costeFila.setPotencia(c.getInt
							(c.getColumnIndex
								(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_POTENCIA)));
			
			costeFila.setTarifa20A_PVPC(c.getDouble(
							c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_TARIFA20A_PVPC)));
			
			costeFila.setTarifa20DHA_PVPC(c.getDouble(
					c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_TARIFA20DHA_PVPC)));
			
			costeFila.setTarifa20DHS_PVPC(c.getDouble(
					c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_TARIFA20DHS_PVPC)));
			
			costeFila.setTarifa20A(c.getDouble(
					c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_TARIFA20A)));
			
			costeFila.setTarifa20DHA(c.getDouble(
					c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_TARIFA20DHA)));
			
			costeFila.setTarifa20DHS(c.getDouble(
					c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_TARIFA20DHS)));
	
			costeFila.setCoste20A_PVPC(
					c.getDouble(
							c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20A_PVPC)));
			
			costeFila.setCoste20DHA_PVPC(
					c.getDouble(
							c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHA_PVPC)));
			costeFila.setCoste20DHS_PVPC(
					c.getDouble(
							c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHS_PVPC)));

			costeFila.setCoste20A(
					c.getDouble(
							c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20A)));
			costeFila.setCoste20DHA(
					c.getDouble(
							c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHA)));
			costeFila.setCoste20DHS(
					c.getDouble(
							c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHS)));
			
			
			costeFila.setCosteAcumulado20A_PVPC(
					c.getDouble(
							c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20A_PVPC)));
			costeFila.setCosteAcumulado20DHA_PVPC(
					c.getDouble(
							c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHA_PVPC)));
			costeFila.setCosteAcumulado20DHS_PVPC(
					c.getDouble(
							c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHS_PVPC)));

			costeFila.setCosteAcumulado20A(
					c.getDouble(
							c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20A)));
			costeFila.setCosteAcumulado20DHA(
					c.getDouble(
							c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHA)));
			costeFila.setCosteAcumulado20DHS(
					c.getDouble(
							c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHS)));
			tablaCostes.add(costeFila);
		}while(c.moveToNext());
		
		dbRead.delete(ColumnasCostes.TABLE_NAME, null, null);
		c.close();
		dbRead.close();
		bbdd.close();
		return tablaCostes;
	}
	
	//Escribe los datos recogidos en la BBDD en los ficheros y flashea la BBDD.
	private void volcarBBDDFichero_LimpiarBBBDD(){
		// definición de ficheros y path
        FileOutputStream osCostesBBDDCSV = null;
    	String cabecera, filaCSV, COMA=",";
    	
    	Calendar calendar=Calendar.getInstance();
		calendar.setTime(new Date());
		
		List<Coste> costes=null;
		
		
		String diaSistema=new SimpleDateFormat("ddMMMyyyy").format(calendar.getTime()).toString();
		String horaSistema=new SimpleDateFormat("HH'h'mm'm'ss's'").format(calendar.getTime()).toString();
		
		costes=extraerTablaCostesBBDD();
		
		
		cabecera = 
					"Comentarios, Fecha y Hora, Unix Time, Potencia(W),"+
					"PVPC"+",,,,,,,,,"+"Comercializadora libre"+"\n"+
					",,,,"+"2.0A"+",,,"+"2.0DHA"+",,,"+"2.0DHS"+",,,"+"2.0A"+",,,"+"2.0DHA"+",,,"+"2.0DHS"+" \n"+
					",,,,"+
					"Tarifa(€/kWh)"+ COMA+"Coste medida(€)"+ COMA+"Coste acumulado(€)"+ COMA+
					"Tarifa(€/kWh)"+ COMA+"Coste medida(€)"+ COMA+"Coste acumulado(€)"+ COMA+
					"Tarifa(€/kWh)"+ COMA+"Coste medida(€)"+ COMA+"Coste acumulado(€)"+ COMA+
					"Tarifa(€/kWh)"+ COMA+"Coste medida(€)"+ COMA+"Coste acumulado(€)"+ COMA+
					"Tarifa(€/kWh)"+ COMA+"Coste medida(€)"+ COMA+"Coste acumulado(€)"+ COMA+
					"Tarifa(€/kWh)"+ COMA+"Coste medida(€)"+ COMA+"Coste acumulado(€)"+ COMA+
					"\n";
		
    	try {
    		new File(Constants.pathFicherosRegistros+"/"+diaSistema).mkdir();
    		osCostesBBDDCSV = new FileOutputStream(Constants.pathFicherosRegistros+"/"+diaSistema+"/costes"+horaSistema+".csv");		// borra el fichero si existe
            
    		osCostesBBDDCSV.write(cabecera.getBytes(Charset.forName("UTF-8")));
            for(int i=0;i<costes.size();i++){
            	
            	//Extraemos los datos de la lista y la convertimos en una fila de string.
            	filaCSV=costes.get(i).getComentario()+COMA+
            			costes.get(i).getFechaMedida()+COMA+
            			costes.get(i).getTiempoMedida()+COMA+
            			Integer.toString(costes.get(i).getPotencia())+COMA+
            			
            			Double.toString(costes.get(i).getTarifa20A_PVPC())+COMA+
            			Double.toString(costes.get(i).getCoste20A_PVPC())+COMA+
            			Double.toString(costes.get(i).getCosteAcumulado20A_PVPC())+COMA+
            			
            			
            			Double.toString(costes.get(i).getTarifa20DHA_PVPC())+COMA+
            			Double.toString(costes.get(i).getCoste20DHA_PVPC())+COMA+
            			Double.toString(costes.get(i).getCosteAcumulado20DHA_PVPC())+COMA+
            			
            			Double.toString(costes.get(i).getTarifa20DHS_PVPC())+COMA+
            			Double.toString(costes.get(i).getCoste20DHS_PVPC())+COMA+
            			Double.toString(costes.get(i).getCosteAcumulado20DHS_PVPC())+COMA+
            			
            			Double.toString(costes.get(i).getTarifa20A())+COMA+
            			Double.toString(costes.get(i).getCoste20A())+COMA+
            			Double.toString(costes.get(i).getCosteAcumulado20A())+COMA+
            			
            			Double.toString(costes.get(i).getTarifa20DHA())+COMA+
            			Double.toString(costes.get(i).getCoste20DHA())+COMA+
            			Double.toString(costes.get(i).getCosteAcumulado20DHA())+COMA+
            			
            			Double.toString(costes.get(i).getTarifa20DHS())+COMA+
            			Double.toString(costes.get(i).getCoste20DHS())+COMA+
            			Double.toString(costes.get(i).getCosteAcumulado20DHS())+COMA+"\n";
            			
            	osCostesBBDDCSV.write(filaCSV.getBytes(Charset.forName("UTF-8")));
            }
            osCostesBBDDCSV.close();
        } catch (Exception e) {
            e.printStackTrace();
            
        }
    	
    	
	}
	
	//Escribe las tarifas en la BBDD
	private void volcarTarifasBDD(ParsearTarifas parseo){
		BaseDatosNeurGAI bbdd = new BaseDatosNeurGAI(this);
		SQLiteDatabase dbWrite= bbdd.getWritableDatabase();
		ContentValues values = new ContentValues();
		
		List<Tarifa> tarifa20A;
		List<Tarifa> tarifa20DHA;
		List<Tarifa> tarifa20DHS;
		
		List<Tarifa> tarifa20A_PVPC;
		List<Tarifa> tarifa20DHA_PVPC;
		List<Tarifa> tarifa20DHS_PVPC;
			
		
		//Borramos la tabla de las tarifas para evitar que se acumulen tarifas de días anteriores..
		dbWrite.delete(ColumnasTarifas.TABLE_NAME, null, null);
		if(parseo.domPVPC!=null&&parseo.domEstandar!=null){

			tarifa20A=parseo.getTarifa20A();
			tarifa20DHA=parseo.getTarifa20DHA();
			tarifa20DHS=parseo.getTarifa20DHS();
			
			tarifa20A_PVPC=parseo.getTarifa20A_PVPC();
			tarifa20DHA_PVPC=parseo.getTarifa20DHA_PVPC();
			tarifa20DHS_PVPC=parseo.getTarifa20DHS_PVPC();
			
			for( int i=0;i<24;i++)
			{
				
				values.put(ColumnasTarifas.COLUMN_NAME_FECHA,tarifa20A_PVPC.get(i).getFecha());
				values.put(ColumnasTarifas.COLUMN_NAME_HORA,tarifa20A_PVPC.get(i).getHora());
				
				values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20A_PVPC, (tarifa20A_PVPC.get(i).getFeu()));
				values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHA_PVPC, (tarifa20DHA_PVPC.get(i).getFeu()));
				values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS_PVPC, (tarifa20DHS_PVPC.get(i).getFeu()));
				
				values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20A, (tarifa20A.get(i).getFeu()));
				values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHA, (tarifa20DHA.get(i).getFeu()));
				values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS, (tarifa20DHS.get(i).getFeu()));
				dbWrite.insert(ColumnasTarifas.TABLE_NAME, null, values);
			}
		}else if(parseo.domPVPC!=null){
			tarifa20A_PVPC=parseo.getTarifa20A_PVPC();
			tarifa20DHA_PVPC=parseo.getTarifa20DHA_PVPC();
			tarifa20DHS_PVPC=parseo.getTarifa20DHS_PVPC();
			for( int i=0;i<24;i++)
			{
				
				values.put(ColumnasTarifas.COLUMN_NAME_FECHA,tarifa20A_PVPC.get(i).getFecha());
				values.put(ColumnasTarifas.COLUMN_NAME_HORA,tarifa20A_PVPC.get(i).getHora());
				
				values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20A_PVPC, (tarifa20A_PVPC.get(i).getFeu()));
				values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHA_PVPC, (tarifa20DHA_PVPC.get(i).getFeu()));
				values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS_PVPC, (tarifa20DHS_PVPC.get(i).getFeu()));
				
				dbWrite.insert(ColumnasTarifas.TABLE_NAME, null, values);
			}
		}else if(parseo.domEstandar!=null){
			tarifa20A=parseo.getTarifa20A();
			tarifa20DHA=parseo.getTarifa20DHA();
			tarifa20DHS=parseo.getTarifa20DHS();
			
			for( int i=0;i<24;i++)
			{
				values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20A, (tarifa20A.get(i).getFeu()));
				values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHA, (tarifa20DHA.get(i).getFeu()));
				values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS, (tarifa20DHS.get(i).getFeu()));
				dbWrite.insert(ColumnasTarifas.TABLE_NAME, null, values);
			}
		}
		
		dbWrite.close();
		bbdd.close();
	}
	public void volcadoTarifasLibreUsuario(double feu20A,double feu20DHAP, double feu20DHAV ,double feu20DHSP,double feu20DHSV, double feu20DHSSV){
		BaseDatosNeurGAI bbdd = new BaseDatosNeurGAI(this);
		SQLiteDatabase dbWrite= bbdd.getWritableDatabase();
		ContentValues values = new ContentValues();
		
		Calendar calendar=Calendar.getInstance();
		calendar.setTime(new Date());
		String fechaSistema=new SimpleDateFormat("ddMM").format(calendar.getTime());
		int eqinocVera=2106;
		int eqinocInv=2212;
		int fechaSistemInt=Integer.parseInt(fechaSistema);
		
		//dbWrite.delete(ColumnasTarifas.TABLE_NAME, ColumnasTarifas.COLUMN_NAME_TARIFA_20A, null);
		//dbWrite.delete(ColumnasTarifas.TABLE_NAME, ColumnasTarifas.COLUMN_NAME_TARIFA_20DHA, null);
		//dbWrite.delete(ColumnasTarifas.TABLE_NAME, ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS, null);
		
		for( int i=0;i<24;i++)
		{
			//db.execSQL("UPDATE Usuarios SET nombre='usunuevo' WHERE codigo=6 ");
			//dbWrite.execSQL("UPDATE "+);
			//Supuesto de que estamos en invierno

			//plana
			values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20A, feu20A);
			
			if((fechaSistemInt>=eqinocVera)&&(fechaSistemInt<eqinocInv)){
				
				//Punta
				if(i>=13&&i<23){
					values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHA, feu20DHAP);
					values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS, feu20DHSP);
					
				}else{
					values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHA, feu20DHAV);
					//Supervalle
					if(i>=1&&i<7){
						
						values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS, feu20DHSSV);
					}else{
						//Valle DHS.
						values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS, feu20DHSV);
					}
				}
			}else{
				//En caso contrario, estamos en verano.
				//plana
				values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20A, feu20A);
				
				
				//Punta
				if(i>=12&&i<22){
					values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHA, feu20DHAP);
					values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS, feu20DHSP);
					
				}else{
					//Valle y supervalle.
					values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHA, feu20DHAV);
					
					if(i>=0&&i<6){
						values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS, feu20DHSSV);
					}else{
						values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS, feu20DHSV);
					}
				}
			}
			
			
			dbWrite.update(ColumnasTarifas.TABLE_NAME, values, ColumnasTarifas.COLUMN_NAME_HORA+"="+Integer.toString(i), null);
			//dbWrite.insert(ColumnasTarifas.TABLE_NAME, null, values);
		}
		
		bbdd.close();
		dbWrite.close();
	}
	
	//Lee el último registro de tipo double de cualquiera de las tablas de la bbdd.
	//Este método sirve para saber el tiempo de la medida anterior.
	private double leerUltimoRegistroDouble(String registro, String nombreTabla){
		BaseDatosNeurGAI bbdd = new BaseDatosNeurGAI(this);
		SQLiteDatabase dbRead=bbdd.getReadableDatabase();
		double registroDouble;
		
		String[] projection = {
				registro,
		    };

		Cursor c = dbRead.query(
				nombreTabla, projection, null, null, null, null, null, null
		    );
		
		
		
		if(c.getCount()==0){
			registroDouble=0;
		}
		else{
			c.moveToLast();
			registroDouble=c.getDouble(
								c.getColumnIndex(registro));
		}
		
		c.close();
		dbRead.close();
		bbdd.close();
		return registroDouble;
	}
	
	//Lee el último registro en en cualquiera de las tablas de la BBDD.
	private String leerRegistroString(String registro, String nombreTabla){
		BaseDatosNeurGAI bbdd = new BaseDatosNeurGAI(this);
		SQLiteDatabase dbRead=bbdd.getReadableDatabase();
		
		String registroString;
		
		String[] projection = {
				registro,
		    };

		Cursor c = dbRead.query(
				nombreTabla, projection, null, null, null, null, null, null
		    );
		
		
		c.moveToFirst();
		if(c.getCount()==0){
			registroString=null;
		}
		else{
			
			registroString=c.getString(
								c.getColumnIndex(registro));
		}
		
		c.close();
		dbRead.close();
		bbdd.close();
		return registroString;
	}
	
	
	//Realiza una consulta en la BBDD del feu de un tipo de tarifa, en una hora determinada.
	private double leerFEU(int hora, String tipoTarifa){
		BaseDatosNeurGAI bbdd = new BaseDatosNeurGAI(this);
		
		SQLiteDatabase dbRead=bbdd.getReadableDatabase();
		
		double feu;
		
		
		//Consulta sqlite para el feu de una hora determinada.
		String sql=
				"SELECT "+ColumnasTarifas.COLUMN_NAME_HORA+", "+
					tipoTarifa+" FROM "+
						ColumnasTarifas.TABLE_NAME+" WHERE "+
							ColumnasTarifas.COLUMN_NAME_HORA+" = "+Integer.toString(hora);
		
		
		//Consulta las filas y se asegura que existe al menos una fila.
		Cursor c=dbRead.rawQuery(sql, null );
		c.moveToFirst();
		
		
		if(c.getCount()==0){
			feu=0;
		}
		else{
			feu=c.getDouble(c.getColumnIndex(tipoTarifa));
		}
		
		c.close();
		dbRead.close();
		bbdd.close();
		return feu;
	}
	
	private void guardarRegistros(ContentValues values, String nombreTabla){
		BaseDatosNeurGAI bbdd = new BaseDatosNeurGAI(this);
		SQLiteDatabase dbWrite= bbdd.getWritableDatabase();
		
		
		dbWrite.insert(nombreTabla, null, values);
		
		dbWrite.close();
		bbdd.close();
	}
	
	private void calcularPrecioMedida(double potencia, double tiempoMedida) {
		Calendar calendar=Calendar.getInstance();
		calendar.setTime(new Date());
		ContentValues values = new ContentValues();
		
		
		//Se consulta la hora para elegir adecuadamente el feu correspondiente a esa hora.
		//Se consulta el día para verificar la vigencia de las tarifas de la BBDD.
		int horaSistema=calendar.get(Calendar.HOUR_OF_DAY);
		String fechaSistema=new SimpleDateFormat("yyyyMMdd").format(calendar.getTime());
		
		double diferenciaTiempo, tiempoAnterior;
		
		final Coste coste=new Coste();
		
		
		//Método que comprueba y actualiza si es necesario la tabla de las tarifas en la BBDD.
		consultaVigenciaTarifas(fechaSistema);
		
	    
		//Se comprueba si es la primera medida, para evitar errores cuando no existen tiempos guardados en la BBDD.
  		tiempoAnterior=leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_TIEMPO_MEDIDA, ColumnasCostes.TABLE_NAME);
		if(tiempoAnterior==0){
  			diferenciaTiempo=0;
  		}
  		else{
  			diferenciaTiempo=tiempoMedida-tiempoAnterior;
  		}
		
		//Se realizan los cálculos y las consultas necesarias para determinar los costes.
		
		//Tarifa PVPC
		coste.setTarifa20A_PVPC(leerFEU(horaSistema, ColumnasTarifas.COLUMN_NAME_TARIFA_20A_PVPC));
		coste.setCoste20A_PVPC(coste.getTarifa20A_PVPC()* (potencia / 1000) * (diferenciaTiempo / 3600));
		coste.setCosteAcumulado20A_PVPC(coste.getCoste20A_PVPC()+leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20A_PVPC,ColumnasCostes.TABLE_NAME));
		
		coste.setTarifa20DHA_PVPC(leerFEU(horaSistema, ColumnasTarifas.COLUMN_NAME_TARIFA_20DHA_PVPC));
		coste.setCoste20DHA_PVPC(coste.getTarifa20DHA_PVPC()* (potencia / 1000) * (diferenciaTiempo / 3600));
		coste.setCosteAcumulado20DHA_PVPC(coste.getCoste20DHA_PVPC()+leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHA_PVPC,ColumnasCostes.TABLE_NAME));
		
		coste.setTarifa20DHS_PVPC(leerFEU(horaSistema, ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS_PVPC));
		coste.setCoste20DHS_PVPC(coste.getTarifa20DHS_PVPC()* (potencia / 1000) * (diferenciaTiempo / 3600));
		coste.setCosteAcumulado20DHS_PVPC(coste.getCoste20DHS_PVPC()+leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHS_PVPC,ColumnasCostes.TABLE_NAME));
		
		
		
		//Tarifa Goiener
		coste.setTarifa20A(leerFEU(horaSistema, ColumnasTarifas.COLUMN_NAME_TARIFA_20A) );
		coste.setCoste20A(coste.getTarifa20A()* (potencia / 1000) * (diferenciaTiempo / 3600));
		coste.setCosteAcumulado20A(coste.getCoste20A()+leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20A,ColumnasCostes.TABLE_NAME));

		coste.setTarifa20DHA(leerFEU(horaSistema, ColumnasTarifas.COLUMN_NAME_TARIFA_20DHA));
		coste.setCoste20DHA(coste.getTarifa20DHA()* (potencia / 1000) * (diferenciaTiempo / 3600));
		coste.setCosteAcumulado20DHA(coste.getCoste20DHA()+leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHA,ColumnasCostes.TABLE_NAME));
		
		coste.setTarifa20DHS(leerFEU(horaSistema, ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS));
		coste.setCoste20DHS(coste.getTarifa20DHS()*(potencia / 1000) * (diferenciaTiempo / 3600));
		coste.setCosteAcumulado20DHS(coste.getCoste20DHS()+leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHS,ColumnasCostes.TABLE_NAME));
		
		
		
		//Hilo para mostrar los resultados en los correspondientes TextView
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				
				final TextView  text20APVPC= (TextView)findViewById(R.id.text20APVPC);
				text20APVPC.setText(String.format("%.2f", 100 * coste.getCosteAcumulado20A_PVPC()) );	
				
				final TextView  text20DHAPVPC= (TextView)findViewById(R.id.text20DHAPVPC);
				text20DHAPVPC.setText(String.format("%.2f", 100 * coste.getCosteAcumulado20DHA_PVPC()));

				final TextView  text20DHSPVPC= (TextView)findViewById(R.id.text20DHSPVPC);
				text20DHSPVPC.setText(String.format("%.2f", 100 * coste.getCosteAcumulado20DHS_PVPC()) );
	
				final TextView  text20A= (TextView)findViewById(R.id.text20A);
				text20A.setText(String.format("%.2f", 100 * coste.getCosteAcumulado20A()) );
				
				final TextView  text20DHA= (TextView)findViewById(R.id.text20DHA);
				text20DHA.setText(String.format("%.2f", 100 * coste.getCosteAcumulado20DHA()) );
				
				final TextView  text20DHS= (TextView)findViewById(R.id.text20DHS);
				text20DHS.setText(String.format("%.2f", 100 * coste.getCosteAcumulado20DHS()) );
				
			}
		});
		
		//Se guardan los medidos y calculades en la table de costes de la BBDD.
		values.put(ColumnasCostes.COLUMN_NAME_POTENCIA, potencia);
		values.put(ColumnasCostes.COLUMN_NAME_TIEMPO_MEDIDA, tiempoMedida);
		values.put(ColumnasCostes.COLUMN_NAME_FECHA_MEDIDA, new SimpleDateFormat("dd'/'MM'/'yyyy HH:mm:ss").format(calendar.getTime()));
		//values.put(, value);
		
		//PVPC
		values.put(ColumnasCostes.COLUMN_NAME_TARIFA20A_PVPC, coste.getTarifa20A_PVPC());
		values.put(ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20A_PVPC, coste.getCoste20A_PVPC());		
		values.put(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20A_PVPC, coste.getCosteAcumulado20A_PVPC());
		
		values.put(ColumnasCostes.COLUMN_NAME_TARIFA20DHA_PVPC, coste.getTarifa20DHA_PVPC());
		values.put(ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHA_PVPC, coste.getCoste20DHA_PVPC());
		values.put(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHA_PVPC, coste.getCosteAcumulado20DHA_PVPC());
		
		values.put(ColumnasCostes.COLUMN_NAME_TARIFA20DHS_PVPC, coste.getTarifa20DHS_PVPC());
		values.put(ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHS_PVPC, coste.getCoste20DHS_PVPC());
		values.put(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHS_PVPC, coste.getCosteAcumulado20DHS_PVPC());

		
		//GOIENER
		values.put(ColumnasCostes.COLUMN_NAME_TARIFA20A, coste.getTarifa20A());
		values.put(ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20A, coste.getCoste20A());		
		values.put(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20A, coste.getCosteAcumulado20A());
		
		values.put(ColumnasCostes.COLUMN_NAME_TARIFA20DHA, coste.getTarifa20DHA());
		values.put(ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHA, coste.getCoste20DHA());
		values.put(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHA, coste.getCosteAcumulado20DHA());
		
		values.put(ColumnasCostes.COLUMN_NAME_TARIFA20DHS, coste.getTarifa20DHS());
		values.put(ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHS, coste.getCoste20DHS());
		values.put(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHS, coste.getCosteAcumulado20DHS());
		
		if(anotar==true){
			values.put(ColumnasCostes.COLUMN_NAME_COSTE_COMENTARIOS, comentario);
			anotar=false;
		}
		
		guardarRegistros(values, ColumnasCostes.TABLE_NAME);
	}
	
	private class TareaDescarga extends AsyncTask<Void, Integer, Boolean> {
		 
	    @Override
	    protected Boolean doInBackground(Void... params) {
	    	
	    	ParsearTarifas parseo=new ParsearTarifas();
			volcarTarifasBDD(parseo);
	        return true;
	    }
	    protected void onPostExecute(){
	    	
	    }
	}
	
	//Consulta si las tarifas están actualizadas y las actualiza en caso necesario.
	private void consultaVigenciaTarifas(final String fechaSistema) {
		String fechaTarifaBBDD;
		
		//Si la tabla está vacía o la fecha del sistema y de la base de datos es diferente, se actualiza la tabla de tarifas.
		fechaTarifaBBDD=leerRegistroString(ColumnasTarifas.COLUMN_NAME_FECHA, ColumnasTarifas.TABLE_NAME);
		
		if(fechaTarifaBBDD==null){
			TareaDescarga tDescarga=new TareaDescarga();
			tDescarga.execute();
			tDescarga.onPostExecute();
			
			
		}else if(fechaTarifaBBDD.compareTo(fechaSistema)!=0){
			TareaDescarga tDescarga=new TareaDescarga();
			tDescarga.execute();
			tDescarga.onPostExecute();
		}
		
	}

	/**********************************************************************************************/
	
	
	
	
	private final Runnable realizarUnaMedida = new Runnable(){
		@Override
	    public void run() {
			if (!grabarDatos&&midiendo) {							// mientras graba datos de última medida no realiza más medidas
				
				//recorder.read(new short[bufferSize], 0, Constants.LONGITUD_TONO_GRABADO);
				double[] muestras = guardarMuestras();							// lee las muestras del canal de grabación
				for (int i = 0; i < Constants.LONGITUD_TONO_GRABADO; i++)		// prescinde de los términos nulos antes de guardarlos
					muestras_grabar[i] = muestras[2 * i];
				muestras = compensarCAG(muestras);								// compensa el efecto del CAG sobre las muestras leídas
				DEP_Y_grabar = calcularDEP_Y(muestras);							// calcula la densidad espectral de frecuencia de la señal leída
				DEP_X_grabar = calcularDEP_X(DEP_Y_grabar);						// calcula la densidad espectral de frecuencia de la señal antes del filtro
				final double potencia = calcularPotenciaX(DEP_X_grabar);		// calcula la densidad espectral de frecuencia de la señal antes del filtro
				
				final int potenciaCorregida = (int) (potencia / datosCalibrado.coeficienteAjuste / 10 + 0.5) * 10;
				
				double tiempoActual = GregorianCalendar.getInstance().getTimeInMillis() / 1000;
				
				if(empezar) {
					pantallaInicializada = false;
					runOnUiThread(new Runnable() {	
		    			@Override
		    			public void run() {
		    				// establece el tiempo de inicio de las medidas, y crea la serie de medidas
		    				tiempoInicioMedida = GregorianCalendar.getInstance().getTimeInMillis() / 1000;
		    				serieMedidas = new LineGraphSeries<DataPoint>();
		    				
		    				// deshabilita el logo EHU
		    				findViewById(R.id.logoEHU).setVisibility(View.INVISIBLE);
		    				
		    				// habilita ventanas de texto para mostrar la medida
		    				findViewById(R.id.texto1).setVisibility(View.VISIBLE);
		    				findViewById(R.id.textVCeldaEmpty).setVisibility(View.VISIBLE);
		    				findViewById(R.id.TextVLibre).setVisibility(View.VISIBLE);
		    				findViewById(R.id.TextV20A).setVisibility(View.VISIBLE);
		    				findViewById(R.id.textV20DHA).setVisibility(View.VISIBLE);
		    				
		    				findViewById(R.id.text20A).setVisibility(View.VISIBLE);
		    				findViewById(R.id.text20DHA).setVisibility(View.VISIBLE);
		    				findViewById(R.id.text20DHS).setVisibility(View.VISIBLE);
		    				
		    				findViewById(R.id.TextVPVPC).setVisibility(View.VISIBLE);
		    				findViewById(R.id.textV20DHSPVPC).setVisibility(View.VISIBLE);
		    				
		    				findViewById(R.id.text20APVPC).setVisibility(View.VISIBLE);
		    				findViewById(R.id.text20DHAPVPC).setVisibility(View.VISIBLE);
		    				findViewById(R.id.text20DHSPVPC).setVisibility(View.VISIBLE);
		    				
		    				// crea el gráfico con los datos de las medidas
		    				graficoMedidas = (GraphView) findViewById(R.id.grafica);
		    				graficoMedidas.removeAllSeries();
		    				graficoMedidas.clearAnimation();
		    				
		    				serieMedidas.setColor(Color.BLACK);
		    				graficoMedidas.setTitleColor(Color.BLACK);
		    				graficoMedidas.setTitle("Medidas de potencia (kW)");

		    				graficoMedidas.getGridLabelRenderer().setGridColor(Color.BLACK);
		    				graficoMedidas.getGridLabelRenderer().setHorizontalLabelsColor(Color.BLACK);
		    				graficoMedidas.getGridLabelRenderer().setVerticalLabelsColor(Color.BLACK);
		    				
		    				serieMedidas.setDrawBackground(true);
		    				serieMedidas.setBackgroundColor(Color.BLUE);
		    				graficoMedidas.getViewport().setYAxisBoundsManual(true);
		    				graficoMedidas.addSeries(serieMedidas);				// data
		    				graficoMedidas.setVisibility(View.VISIBLE); 		// hace visible el gráfico
		    				
		    				menu.findItem(R.id.tiempo).setIcon(null).setTitle("2 seg");		// las medidas se tomarán cada 2 segundos
		    				periodicidadMedidaEnSegundos = 1;	
		    				
		    				empezar = false;
							pantallaInicializada = true;
					    	synchronized (llavePantallaInicializada) {
					    		llavePantallaInicializada.notifyAll();
					    	}
		    			}
		    		});
				};
				
				synchronized (llavePantallaInicializada) {
		    		while (!pantallaInicializada) {
						try {
							llavePantallaInicializada.wait();
						} catch (InterruptedException e) {}
					}
		    	}

				// se añade la medida a la serie
				serieMedidas.appendData(
						new DataPoint(tiempoActual - tiempoInicioMedida, potencia / datosCalibrado.coeficienteAjuste),
						true,
						Constants.NUMERO_MAXIMO_MEDIDAS_EN_GRAFICO
				);
				
				// acondiciona los ejes verticales, si necesario, para que las marcas verticales coincidan con las potencias normalizadas de las tarifas
				String[] escalasPotencia = new String[] {"0", "1,15", "2,30", "3,45", "4,60", "5,75", "6,90", "8,05", "9,20"};
				int numeroMarcasVerticales = (int) serieMedidas.getHighestValueY() / 1150 + 2;
				String[] marcasVerticales = new String[numeroMarcasVerticales];
				System.arraycopy(escalasPotencia, 0, marcasVerticales, 0, numeroMarcasVerticales);
				graficoMedidas.getViewport().setMaxY(1150.0 * (numeroMarcasVerticales - 1));
				StaticLabelsFormatter staticLabelsFormatter = new StaticLabelsFormatter(graficoMedidas);
				staticLabelsFormatter.setVerticalLabels(marcasVerticales);
				graficoMedidas.getGridLabelRenderer().setLabelFormatter(staticLabelsFormatter);
				
				//Consultar hora medida.
				//Extraer el FEU correpondiente a esa hora.
				//Calcular precio de la medida.
				//Acumular.
				//Guardar bbdd, los dos valores
				//Mostrar las medidas en los TEXTVIEW
				
				calcularPrecioMedida(potenciaCorregida, tiempoActual);
				
				if (visible) {
					// muestra en pantalla el dato
					// y actualiza los ejes del gráfico
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							final TextView textViewMedida = (TextView)findViewById(R.id.texto1);
							textViewMedida.setText(Integer.toString(potenciaCorregida) + " W");
							graficoMedidas.onDataChanged(true, false);
						}
					});
				}
			}
			if (!isFinishing()) handler.postDelayed(this, periodicidadMedidaEnSegundos * 1000);
	    }
	};
	
	private DatosCalibrado datosCalibrado = new DatosCalibrado(Constants.numeroAmplitudes, Constants.numeroFrecuencias);
	
	private class DatosCalibrado {
		int[] amplitudes;
		double[] RMSajusteCAG;
		double[] ajusteCAG;
		double[] frecuencias;
		double[] RMS;
		double coeficienteAjuste;
		
		DatosCalibrado(int numeroAmplitudes, int numeroFrecuencias) {
			amplitudes = new int[numeroAmplitudes];
			RMSajusteCAG = new double[numeroAmplitudes];
			ajusteCAG = new double[numeroAmplitudes];
			frecuencias = new double[numeroFrecuencias];
			RMS = new double[numeroFrecuencias];
			coeficienteAjuste = 0;
		}
	}
	
	public class MyPhoneStateListener extends PhoneStateListener {
	    @Override
	    public void onCallStateChanged(int state, String incomingNumber) 
	    {
	        if (state == TelephonyManager.CALL_STATE_RINGING) {
	        	midiendo = false;
	        	menu.findItem(R.id.grabacion).setIcon(R.drawable.ic_parar);
	        	handler.removeCallbacks(realizarUnaMedida);		// elimina la última llamada a realizar nueva medida
	        }
	        super.onCallStateChanged(state, incomingNumber);
	    }
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i("neurGAI", "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.neurgai);
		
		//Hay que comentarlas después de la primera instalación.
		
		//BaseDatosNeurGAI bbdd = new BaseDatosNeurGAI(this);
		//SQLiteDatabase dbWrite=bbdd.getWritableDatabase();
		//dbWrite.deleteDatabase(new File(dbWrite.getPath()));

		// configura actionBar
		ActionBar bar = getSupportActionBar();
		bar.setDisplayShowHomeEnabled(true);
		bar.setTitle(R.string.app_name);
		bar.setLogo(R.drawable.ic_launcher);
		bar.setDisplayShowTitleEnabled(true);
		bar.setDisplayUseLogoEnabled(true);
		
		mProgress = (ProgressBar) findViewById(R.id.progressBar);
		
		boton = (Button) findViewById(R.id.boton);
    	boton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				botonPulsado = true;
		    	synchronized (llaveBotonPulsado) {
		    		llaveBotonPulsado.notifyAll();
		    	}
			}
		});
    	
    	// registra el listener del teléfono, para parar las medidas cuando llegan llamadas
    	TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
    	telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    	
    	// lanza el hilo con la actividad principal
    	new Thread() {
    		public void run() {
    			actividadNeurgai();
    		}
		}.start();
	}
	
	@Override
	protected void onResume(){
		
		Log.i("neurGAI", "onResume");
		visible = true;        
		super.onResume();
	}
	
	@Override
	protected void onPause(){
		
		Log.i("neurGAI", "onPause");
		visible = false;        
		super.onPause();
	}
	
	@Override
    protected void onStop() {
		Log.i("neurGAI", "onStop");
        super.onStop();
    }
	
	@Override
    protected void onDestroy() {
		Log.i("neurGAI", "onDestroy");
		// para el envío de audio si en marcha
		midiendo = false;
		menu.findItem(R.id.grabacion).setIcon(R.drawable.ic_parar);
        handler.removeCallbacks(realizarUnaMedida);		// elimina la última llamada a realizar nueva medida
        if (audioTrack != null) {
        	if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) audioTrack.pause();
        	audioTrack.release();
        }
        
        // para la grabación de sonido si en marcha
        if (recorder != null) {
        	if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING ) recorder.stop();
        	recorder.release();
        }

        //Salva la tabla de costes y eliminar los valores guardados en la tabla Costes de la BDD.
        volcarBBDDFichero_LimpiarBBBDD();
        
        // desregistra el listener del teléfono
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        
        super.onDestroy();
    }

	private void actividadNeurgai() {
		
		// crea los subdirectorios si no existen
		new File(Constants.pathNeurgai).mkdir();
		new File(Constants.pathFicheroCalibracion).mkdir();
		new File(Constants.pathFicherosComplementarios).mkdir();
		new File(Constants.pathFicherosRegistros).mkdir();
	
		/****************Permisos*****************/
		
		
		
		
		
		// trata de recuperar los datos de calibración, y si no existen comienza el proceso de calibrado
		// comprueba si existe el fichero de calibración
		if (false == new File(Constants.pathFicheroCalibracion + "/calibracion.bin").exists()) {
			
			// el control de volumen es el de MEDIA
			setVolumeControlStream(AudioManager.STREAM_MUSIC);
			
			//no existe, así que iniciamos el calibrado
			runOnUiThread(new Runnable() {	
				@Override
				public void run() {
					mProgress.setVisibility(View.VISIBLE); 	//hace visible la barra de progreso, solo utilizada aquí
					boton.setVisibility(View.VISIBLE); 		//hace visible el botón
				}
			});
			
			{	
				//definición de los arrays donde se guardarán temporalmente los valores de calibrado, antes de guardar en fichero
				final double[] frecuenciaCalibradoAmplitudes = new double[Constants.numeroAmplitudes];
				final short[] amplitudesCalibradoAmplitudes = new short[Constants.numeroAmplitudes];
				final double[] potenciaRMSCalibradoAmplitudes = new double[Constants.numeroAmplitudes];
				final double[] frecuenciaCalibradoFrecuencias = new double[Constants.numeroFrecuencias];
				final short[] amplitudesCalibradoFrecuencias = new short[Constants.numeroFrecuencias];
				final double[] potenciaRMSCalibradoFrecuencias = new double[Constants.numeroFrecuencias];
				
				// configura el canal de grabación
		    	bufferSize = AudioRecord.getMinBufferSize(
		    			Constants.RECORDER_SAMPLERATE, 
		    			Constants.RECORDER_CHANNELS, 
		    			Constants.RECORDER_AUDIO_ENCODING);
		    	
		    	if (bufferSize < Constants.LONGITUD_TONO_GRABADO * 2)
		    		bufferSize = Constants.LONGITUD_TONO_GRABADO * 2;
		    	
		    	recorder = new AudioRecord(
		    			AudioSource.MIC,
		    			Constants.RECORDER_SAMPLERATE, 
		    			Constants.RECORDER_CHANNELS,
		    			Constants.RECORDER_AUDIO_ENCODING, 
		                bufferSize);
		    	if (android.os.Build.VERSION.SDK_INT >= 16) {
		    		// se asegura de que no hay CAG-android en marcha
			    	cag = AutomaticGainControl.create(recorder.getAudioSessionId());
			    	if (AutomaticGainControl.isAvailable()){
			    		if ((cag.hasControl()) && (cag.getEnabled())) cag.setEnabled(false);
			    	}
		    	}
		    	
		    	// configura el canal de salida de audio
		    	bufferSizeAudio = AudioTrack.getMinBufferSize(
		    			Constants.RECORDER_SAMPLERATE, 
		    			AudioFormat.CHANNEL_OUT_MONO, 
		    			Constants.RECORDER_AUDIO_ENCODING);

		    	audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
		    			Constants.RECORDER_SAMPLERATE,
		    				AudioFormat.CHANNEL_OUT_MONO,
		    				Constants.RECORDER_AUDIO_ENCODING, 
		    				bufferSizeAudio,
		    				AudioTrack.MODE_STREAM);
		    	
		    	mProgress.setProgress(0);
		    	mProgress.setMax(Constants.numeroAmplitudes + Constants.numeroFrecuencias);
		    	
		    	// espera a que se pulse el botón para iniciar el calibrado
		    	botonPulsado = false;
		    
		    	runOnUiThread(new Runnable() {	
					@Override
					public void run() {
						boton.setText(R.string.ajustarConectoryVolumen);
					}
				});
		    	
		    	synchronized (llaveBotonPulsado) {
		    		while (!botonPulsado) {
						try {
							llaveBotonPulsado.wait();
						} catch (InterruptedException e) {}
					}
		    	}
		    	
		    	// muestra el mensaje de calibrado del CAG
		    	runOnUiThread(new Runnable() {	
					@Override
					public void run() {
						boton.setText(R.string.calibrandoCAG);
					}
				});		   
				
				
				
		    	//genera vector de amplitudes
		    	final double amplitudInicial = 1000;
		    	final double amplitudFinal = 30000;
		    	short[] amplitudes = new short[Constants.numeroAmplitudes];
		    	
		    	for (int n = 0; n < Constants.numeroAmplitudes; n++) {
		    		amplitudes[n] = (short) (amplitudInicial * Math.exp(n * Math.log(amplitudFinal/amplitudInicial) / (Constants.numeroAmplitudes-1)));
		    	}
		    	
		    	// abre el canal de grabación
		    	recorder.startRecording();
		    	recorder.read(new short[bufferSize], 0, Constants.RECORDER_SAMPLERATE);
		    	recorder.stop();
		    	recorder.startRecording();
		    	
		    	//realiza el barrido de amplitudes
		    	for (short i = 0; i < Constants.numeroAmplitudes; i++) {
		    		final double FRECUENCIA = 2000.0;
		    		final short A0 = amplitudes[i];
		    		
		    		mProgressStatus++;
		    		mProgress.setProgress(mProgressStatus);
		    		
		    		
		    		// vacía el buffer
		    		recorder.read(new short[bufferSize], 0, Constants.RECORDER_SAMPLERATE);
		    		
		    		//manda el tono al canal de audio
		    		tonoLanzado = false;
		    		new Thread() {
		    			public void run() {
		    				setPriority(Thread.MAX_PRIORITY);
		    	        		    	        	 
		    	        	//Genera las muestras de un pulso que escribimos en audioTrack.
		    	        	muestrasTonoGenerado = generadorCoseno(
		    	        			(double) Constants.RECORDER_SAMPLERATE	//duración del tono
		    	        			, FRECUENCIA							//frecuencia del tono
		    	        			, A0);									//amplitud del tono
		    	        	 
		    	        	audioTrack.pause();
		    	            audioTrack.flush(); // vacía el buffer de sonido si ha acabado la grabación
		    	        	audioTrack.play();
		    	        	audioTrack.write(muestrasTonoGenerado, 0, Constants.RECORDER_SAMPLERATE);
		    	        	
		    	        	tonoLanzado = true;
		    		    	synchronized (llaveTonoLanzado) {
		    		    		llaveTonoLanzado.notifyAll();
		    		    	}
		    	        }
		    		}.start();
		    		
		    		synchronized (llaveTonoLanzado) {
			    		while (!tonoLanzado) {
							try {
								llaveTonoLanzado.wait();
							} catch (InterruptedException e) {}
						}
			    	}
		    		
		    		muestrasGrabadas = seleccionarMuestrasGrabacion(Constants.DATOS_INICIALES_NO_VALIDOS, Constants.DATOS_VALIDOS);
		            
		            //calcula la potencia de la muestra grabada
		            double p = 0;
		           	for (short m: muestrasGrabadas) {
		           		p += m * m;
		           	}
		           	double potenciaRMSMuestraGrabada = Math.sqrt(p / muestrasGrabadas.length);
		            
		            //guarda los resultados del calibrado de amplitudes en los arrays
		            frecuenciaCalibradoAmplitudes[i] = FRECUENCIA;
		    		amplitudesCalibradoAmplitudes[i] = A0;
		    		potenciaRMSCalibradoAmplitudes[i] = potenciaRMSMuestraGrabada;
		    	}
		    	
		    	audioTrack.pause();
		    	audioTrack.flush();
		    	recorder.stop(); 	// cierra la grabación
		    	//recorder.release();

		    	//muestra el gráfico con los datos de la medida del CAG
		    	runOnUiThread(new Runnable() {
					@Override
					public void run() {
						
						int anchuraLinea = 10;

						LineGraphSeries<DataPoint> serieCalibradoCAG = new LineGraphSeries<DataPoint>();
						serieCalibradoCAG.setColor(Color.BLACK);
						serieCalibradoCAG.setThickness(anchuraLinea);
						
						//GraphView graficoCAG = new GraphView(getBaseContext());
						GraphView graficoCAG = (GraphView) findViewById(R.id.grafica);
						
						graficoCAG.setTitleColor(Color.BLACK);
						graficoCAG.setTitle("x-log(AUDIO) vs y-log(MIC)");
						
						// carga los datos en la serie de puntos gráficos
						for (int i = 0; i < Constants.numeroAmplitudes; i++) {
			    			serieCalibradoCAG.appendData(
			    					new DataPoint(Math.log10(amplitudesCalibradoAmplitudes[i]), Math.log10(potenciaRMSCalibradoAmplitudes[i])),
			    					true,
			    					(int) Constants.numeroAmplitudes
			    			);
			    		}

						graficoCAG.getGridLabelRenderer().setGridColor(Color.BLUE);
						graficoCAG.getGridLabelRenderer().setHorizontalLabelsColor(Color.BLACK);
						graficoCAG.getGridLabelRenderer().setVerticalLabelsColor(Color.BLACK);
						
						graficoCAG.addSeries(serieCalibradoCAG);		// data
						
						//RelativeLayout grafico = (RelativeLayout) findViewById(R.id.grafica);
						//grafico.addView(graficoCAG);
						graficoCAG.setVisibility(View.VISIBLE); 		//hace visible el gráfico
					}
				});
		    	
		    	// abre una ventana para introducir la amplitud de calibrado del filtro

		    	while ((amplitudCalibradoFiltro < amplitudInicial) || (amplitudCalibradoFiltro > amplitudFinal)) {
		        	try {
		        		botonPulsado = false;
		            	runOnUiThread(new Runnable() {	
		        			@Override
		        			public void run() {
		        				boton.setText(R.string.pulsaParaContinuar);
		        			}
		        		});
		            	synchronized (llaveBotonPulsado) {
				    		while (!botonPulsado) {
				    			try {
				    				llaveBotonPulsado.wait();
				    			} catch (InterruptedException e) {}
							}
				    	}
		        		
		        		botonPulsado = false;
		            	runOnUiThread(new Runnable() {	
		        			@Override
		        			public void run() {
		        				preguntarAmplitudCalibrado();
		        			}
		        		});
		            	synchronized (llaveBotonPulsado) {
				    		while (!botonPulsado) {
				    			try {
				    				llaveBotonPulsado.wait();
				    			} catch (InterruptedException e) {}
							}
				    	}      		
		            } catch (NumberFormatException e) {
		            	runOnUiThread(new Runnable() {	
		        			@Override
		        			public void run() {
		        				showMessage("Error", getString(R.string.introducirAmplitudCorrectamente));
		        			}
		        		});
		            }
		    	};
		    	
		    	// calibra el filtro de entrada
		    	botonPulsado = false;
		    	runOnUiThread(new Runnable() {	
					@Override
					public void run() {
						boton.setText(R.string.calibrandoFiltro);
					}
				});

		    	//genera vector de frecuencias
				
		    	final double frecuenciaInicial = 40;
		    	final double frecuenciaFinal = 7000;
		    	double[] frecuencias = new double[Constants.numeroFrecuencias];
		    	for (int n = 0; n < Constants.numeroFrecuencias; n++) {
		    		frecuencias[n] = frecuenciaInicial * Math.exp(n * Math.log(frecuenciaFinal/frecuenciaInicial) / (Constants.numeroFrecuencias-1));
		    	}
		    	
		    	//borra el gráfico del CAG
		    	runOnUiThread(new Runnable() {	
        			@Override
        			public void run() {
        				GraphView graficoCAG = (GraphView) findViewById(R.id.grafica);
	    				graficoCAG.removeAllSeries();
	    				graficoCAG.clearAnimation();
        				graficoCAG.setVisibility(View.GONE); 		//hace invisible el gráfico
        			}
        		});
		    	
		    	recorder.startRecording();
		    	recorder.read(new short[bufferSize], 0, Constants.RECORDER_SAMPLERATE);
		    	recorder.stop();
		    	
		    	//realiza el barrido de frecuencias
		    	recorder.startRecording();		// abre el canal de grabación
		    	for (short i = 0; i < Constants.numeroFrecuencias; i++) {
		    		final double FRECUENCIA = frecuencias[i];
		    		final short A0 = amplitudCalibradoFiltro;
		    		
		    		mProgressStatus++;
		    		mProgress.setProgress(mProgressStatus);

		    		// vacía el buffer
		    		recorder.read(new short[bufferSize], 0, Constants.RECORDER_SAMPLERATE);
		    		
		    		//manda el tono al canal de audio
		    		tonoLanzado = false;
		    		new Thread() {
		    			public void run() {
		    				setPriority(Thread.MAX_PRIORITY);
		    	        		    	        	 
		    	        	//Genera las muestras de un pulso que escribimos en audioTrack.
		    	        	muestrasTonoGenerado = generadorCoseno(
		    	        			(double) Constants.RECORDER_SAMPLERATE	//duración del tono
		    	        			, FRECUENCIA							//frecuencia del tono
		    	        			, A0);									//amplitud del tono
		    	        	 
		    	        	audioTrack.pause();
		    	            audioTrack.flush(); // vacía el buffer de sonido si ha acabado la grabación
		    	        	audioTrack.play();
		    	        	audioTrack.write(muestrasTonoGenerado, 0, Constants.RECORDER_SAMPLERATE);
		    	        	
		    	        	tonoLanzado = true;
		    		    	synchronized (llaveTonoLanzado) {
		    		    		llaveTonoLanzado.notifyAll();
		    		    	}
		    	        }
		    		}.start();
		    		
		    		synchronized (llaveTonoLanzado) {
			    		while (!tonoLanzado) {
							try {
								llaveTonoLanzado.wait();
							} catch (InterruptedException e) {}
						}
			    	}
			
		    		muestrasGrabadas = seleccionarMuestrasGrabacion(
		    				Constants.DATOS_INICIALES_NO_VALIDOS, Constants.DATOS_VALIDOS);		// lee los datos del micrófono y los graba
		            
		            //calcula la potencia de la muestra grabada
		            double p = 0;
		           	for (short m: muestrasGrabadas) {
		           		p += m * m;
		           	}
		           	double potenciaRMSMuestraGrabada = Math.sqrt(p / muestrasGrabadas.length);
		            
		            //guarda los resultados del calibrado de amplitudes en los arrays
		            frecuenciaCalibradoFrecuencias[i] = FRECUENCIA;
		    		amplitudesCalibradoFrecuencias[i] = A0;
		    		potenciaRMSCalibradoFrecuencias[i] = potenciaRMSMuestraGrabada;
		    	}
		    	
		    	// acabadas todas las frecuencias, se liberan el canal de audio y el canal de grabación
		    	recorder.stop();
		    	audioTrack.release();
		    	recorder.release();
		    	
		    	// muestra el gráfico con los datos de la medida del filtro
		    	runOnUiThread(new Runnable() {
					@Override
					public void run() {
						// carga los datos en en la serie de puntos gráficos
						int anchuraLinea = 10;
						
						LineGraphSeries<DataPoint> serieCalibradoFiltro = new LineGraphSeries<DataPoint>();
						serieCalibradoFiltro.setColor(Color.BLACK);
						serieCalibradoFiltro.setThickness(anchuraLinea);
						
						//GraphView graficoFiltro = new GraphView(getBaseContext());
						GraphView graficoFiltro = (GraphView) findViewById(R.id.grafica);
						graficoFiltro.setTitleColor(Color.BLACK);
						graficoFiltro.setTitle("Audio-in vs Log(Hz)");
						
						// carga los datos en la serie de puntos gráficos
						for (int i = 0; i < Constants.numeroFrecuencias; i++) {
			    			serieCalibradoFiltro.appendData(
			    					new DataPoint(Math.log10(frecuenciaCalibradoFrecuencias[i]), potenciaRMSCalibradoFrecuencias[i]),
			    					true,
			    					(int) Constants.numeroFrecuencias
			    			);
			    		}
						
						// define un escalado horizontal lineal
						
						NumberFormat nf = NumberFormat.getInstance();
						nf.setMinimumFractionDigits(0);
						nf.setMinimumIntegerDigits(1);
						graficoFiltro.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter(nf, nf));
						
						graficoFiltro.getGridLabelRenderer().setGridColor(Color.BLUE);
						graficoFiltro.getGridLabelRenderer().setHorizontalLabelsColor(Color.BLACK);
						graficoFiltro.getGridLabelRenderer().setVerticalLabelsColor(Color.BLACK);
						
						graficoFiltro.addSeries(serieCalibradoFiltro);		// data
						
						//RelativeLayout grafico = (RelativeLayout) findViewById(R.id.grafica);
						//grafico.addView(graficoFiltro);
						graficoFiltro.setVisibility(View.VISIBLE); 		//hace visible el gráfico
					}
				});
		    	
		    	// graba los datos en los ficheros correspondientes
		    	File fCalBIN=new File(Constants.pathFicheroCalibracion + "/calibracion.csv");
		    	
		    	// definición de ficheros y path
		        FileOutputStream osCalibCSV = null;
		    	FileOutputStream osCalibBIN = null;
		    	DataOutputStream dosBIN = null;
		    
		    	// crea los ficheros y define los streams de salida
		    	try {
		    		osCalibCSV = new FileOutputStream(fCalBIN, false);		// borra el fichero si existe
		    		osCalibBIN = new FileOutputStream(Constants.pathFicheroCalibracion + "/calibracion.bin", false);
		    		
		            String cabecera = "amplitud, frecuencia, RMS\n";
		            osCalibCSV.write(cabecera.getBytes(Charset.forName("UTF-8")));
		            dosBIN = new DataOutputStream(osCalibBIN);   
		        } catch (Exception e) {
		            e.printStackTrace();
		        }
		    	
		    	// guarda los datos en los ficheros
		    	// primero guarda los datos del calibrado de CAG
		    	for (short i = 0; i < Constants.numeroAmplitudes; i++) {
		    		try {
		    			String stringData = 
		    					Integer.toString(amplitudesCalibradoAmplitudes[i])
		    					.concat(",")
		    					.concat(Double.toString(frecuenciaCalibradoAmplitudes[i]))
		    					.concat(",")
		    					.concat(Double.toString(potenciaRMSCalibradoAmplitudes[i]))
		    					.concat("\n");
		    			osCalibCSV.write(stringData.getBytes(Charset.forName("UTF-8")));
		               
		    			dosBIN.writeInt(amplitudesCalibradoAmplitudes[i]);
		    			dosBIN.writeDouble(frecuenciaCalibradoAmplitudes[i]);
		                dosBIN.writeDouble(potenciaRMSCalibradoAmplitudes[i]);
		            } catch (IOException e) {
		                e.printStackTrace();
		            }
		    	}
		    	
		    	// después guarda los datos del calibrado de frecuencias
		    	for (short i = 0; i < Constants.numeroFrecuencias; i++) {
		    		try {
		    			String stringData = 
		    					Integer.toString(amplitudesCalibradoFrecuencias[i])
		    					.concat(",")
		    					.concat(Double.toString(frecuenciaCalibradoFrecuencias[i]))
		    					.concat(",")
		    					.concat(Double.toString(potenciaRMSCalibradoFrecuencias[i]))
		    					.concat("\n");
		    			osCalibCSV.write(stringData.getBytes(Charset.forName("UTF-8")));
		               
		                dosBIN.writeInt(amplitudesCalibradoFrecuencias[i]);
		                dosBIN.writeDouble(frecuenciaCalibradoFrecuencias[i]);
		                dosBIN.writeDouble(potenciaRMSCalibradoFrecuencias[i]);
		            } catch (IOException e) {
		                e.printStackTrace();
		            }
		    	}
		    	
		    	// cierra los ficheros
		    	try {
					fCalBIN.setReadOnly();
					dosBIN.close();
		    		osCalibBIN.close();
		    		}
				catch (Exception e) {
		        	e.printStackTrace();
		        }
		    	
		    	botonPulsado = false;
		    	runOnUiThread(new Runnable() {	
					@Override
					public void run() {
						boton.setText(R.string.cerrarCalibrado);
					}
				});
		    	synchronized (llaveBotonPulsado) {
		    		while (!botonPulsado) {
		    			try {
		    				llaveBotonPulsado.wait();
		    			} catch (InterruptedException e) {}
					}
		    	}
		    }
			
			// ha acabado el calibrado, borra barra de progreso y el gráfico del calibrado
			runOnUiThread(new Runnable() {	
				@Override
				public void run() {
					mProgress.setVisibility(View.GONE); 							//hace invisible la barra de progreso
					GraphView graficoFiltro = (GraphView) findViewById(R.id.grafica);
					graficoFiltro.removeAllSeries();
					graficoFiltro.clearAnimation();
					graficoFiltro.setVisibility(View.GONE); 		//hace invisible el gráfico
				}
			});
		}
		
		// lee el fichero de calibración
		runOnUiThread(new Runnable() {	
			@Override
			public void run() {
				boton.setVisibility(View.VISIBLE); 		//hace visible el botón
				boton.setText(R.string.leyendoCalibracion);
			}
		});
		
		try {
			FileInputStream fis = new FileInputStream(Constants.pathFicheroCalibracion + "/calibracion.bin");
			DataInputStream dis = new DataInputStream(fis);
			
			// primero estan guardados numeroAmplitudes para la calibración del CAG
			// según la variación de la amplitud a frecuencia constante
			for(int i = 0; i < Constants.numeroAmplitudes; i++) {	
				datosCalibrado.amplitudes[i] = dis.readInt();
				dis.readDouble();	// se prescinde de la frecuencia, que es cte
				datosCalibrado.RMSajusteCAG[i] = dis.readDouble();
				datosCalibrado.ajusteCAG[i] = datosCalibrado.RMSajusteCAG[i] / datosCalibrado.amplitudes[i]; // calcula el vector para el ajuste de CAG
			}
			
			// y después están guardados numeroFrecuencias para la calibración 
			// según la frecuencia a amplitud constante
			
			// calcula la función para interpolar el ajuste de CAG
			UnivariateInterpolator interpolator = new LinearInterpolator();
			UnivariateFunction funcionCAG = null;
			funcionCAG = interpolator.interpolate(datosCalibrado.RMSajusteCAG, datosCalibrado.ajusteCAG);
			
			for(int i = 0; i < Constants.numeroFrecuencias; i++) {	
				dis.readInt();		// se prescinde de la amplitud, que es cte
				datosCalibrado.frecuencias[i] = dis.readDouble();
				datosCalibrado.RMS[i] = dis.readDouble();
				
				// corrige el efecto del CAG
				if (datosCalibrado.RMS[i] <= datosCalibrado.RMSajusteCAG[0]) {
					datosCalibrado.RMS[i] = datosCalibrado.RMS[i] * datosCalibrado.ajusteCAG[0];
				} else {
					if (datosCalibrado.RMS[i] > datosCalibrado.RMSajusteCAG[Constants.numeroAmplitudes - 1]) datosCalibrado.RMS[i] = datosCalibrado.RMS[i] * datosCalibrado.ajusteCAG[Constants.numeroAmplitudes - 1];
					else datosCalibrado.RMS[i] = datosCalibrado.RMS[i] * funcionCAG.value(datosCalibrado.RMS[i]);
				}
			}
			
			// normaliza la calibración del filtro con respecto al valor de RMS a 50 Hz
			double RMS_50Hz = datosCalibrado.RMS[4];
			for(int i = 0; i < Constants.numeroFrecuencias; i++) {	
				datosCalibrado.RMS[i] = datosCalibrado.RMS[i] / RMS_50Hz;
			}

    		dis.close();
    		fis.close();
    	} catch (Exception e) {
            e.printStackTrace();
        }
		
		// Interpola y corrige el filtro. Elimina bandas superiores e inferiores (<40Hz, >6400Hz)
		UnivariateInterpolator interpolator = new LinearInterpolator();
		UnivariateFunction function = null;
		function = interpolator.interpolate(datosCalibrado.frecuencias, datosCalibrado.RMS);
		//toma las componentes desde frecuenciaInterpInicial hasta frecuenciaInterpFinal
		for (int frecuencia = Constants.frecuenciaInterpInicial; frecuencia <= Constants.frecuenciaInterpFinal; frecuencia++) {
			int i = frecuencia - Constants.frecuenciaInterpInicial;
			DEP_H[i] = Math.pow(function.value(frecuencia), 2.0);
		}
		
		// Configura el canal de grabación
    	bufferSize = AudioRecord.getMinBufferSize(
    			Constants.RECORDER_SAMPLERATE, 
    			Constants.RECORDER_CHANNELS, 
    			Constants.RECORDER_AUDIO_ENCODING);
    	
    	// asegura que el buffer tiene una capacidad doble a la de la señal grabada
    	if (bufferSize < Constants.LONGITUD_TONO_GRABADO * 2)
    		bufferSize = Constants.LONGITUD_TONO_GRABADO * 2;
    	
    	recorder = new AudioRecord(
    			AudioSource.MIC,
    			Constants.RECORDER_SAMPLERATE, 
    			Constants.RECORDER_CHANNELS,
    			Constants.RECORDER_AUDIO_ENCODING, 
                bufferSize);
    	
    	datosBuffer = new short[bufferSize];
    	
    	// lee el coeficiente de calibración en el fichero, si existe
    	// si no, lee una medida y pregunta por la real para calcularlo
    	String nombreFicheroCoeficiente = Constants.pathFicheroCalibracion + "/coeficienteAjuste.csv";
		File ficheroCoeficiente = new File(nombreFicheroCoeficiente);
		if (ficheroCoeficiente.exists()) {
			FileInputStream fis ;
			try {
				fis = new FileInputStream(ficheroCoeficiente);
				StringBuffer fileContent = new StringBuffer("");
				byte[] buffer = new byte[1024];
				int n;
				while ((n = fis.read(buffer)) != -1) 
				{ 
				  fileContent.append(new String(buffer, 0, n)); 
				}
				fis.close();
				datosCalibrado.coeficienteAjuste = (double) Double.valueOf(fileContent.toString());
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			
			// muestras resultados de la medida sin ajustar el coeficiente,
			// y cuando se pulse el botón se introducirá el dato
			
			final TextView textViewMedida = (TextView)findViewById(R.id.texto1);
			
			runOnUiThread(new Runnable() {	
				@Override
				public void run() {
					//boton.setVisibility(View.VISIBLE); 					//hace visible el botón
					textViewMedida.setVisibility(View.VISIBLE); 		//hace visible una ventana de texto, para mostrar el dato
				}
			});
			
			botonPulsado = false;
			runOnUiThread(new Runnable() {	
				@Override
				public void run() {
					boton.setText(R.string.realizandoMedidasSinCalibrar);
				}
			});
			
			double potencia_X = 0;
			recorder.startRecording();
			while (!botonPulsado) {
				// lee y calcula el dato
				potencia_X = 
						calcularPotenciaX(
								calcularDEP_X(
										calcularDEP_Y(
												compensarCAG(
														guardarMuestras()))));

				// muestra en pantalla el dato
				final double potencia = potencia_X;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						textViewMedida.setText(Double.toString(potencia));
					}
				});
			}
			recorder.stop();

			// pregunta al usuario el valor correcto del consumo, medido con otro medidor, en watios
			try {
        		botonPulsado = false;
            	runOnUiThread(new Runnable() {	
        			@Override
        			public void run() {
        				preguntarMedidaReferencia();
        			}
        		});
            	synchronized (llaveBotonPulsado) {
		    		while (!botonPulsado) {
		    			try {
		    				llaveBotonPulsado.wait();
		    			} catch (InterruptedException e) {}
					}
		    	}  		
            } catch (NumberFormatException e) {
            	runOnUiThread(new Runnable() {	
        			@Override
        			public void run() {
        				showMessage("Error", getString(R.string.introducirMedidaCorrectamente));
        			}
        		});
            }
			
			// deshabilita botón y ventana de texto
			runOnUiThread(new Runnable() {	
    			@Override
    			public void run() {
    				boton.setVisibility(View.GONE);
    				textViewMedida.setVisibility(View.GONE);
    			}
    		});
			
			// calcula el coeficiente de ajuste
			datosCalibrado.coeficienteAjuste = potencia_X / medidaReferencia;
						
			// graba el coeficiente de ajuste en el fichero
			try {
				FileOutputStream fileOutputStream = new FileOutputStream(ficheroCoeficiente, false);
				fileOutputStream.write(Double.toString(datosCalibrado.coeficienteAjuste).getBytes(Charset.forName(Constants.formatoFichero)));
				fileOutputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		//quita el botón y hace visible una ventana de texto, para mostrar el dato
		runOnUiThread(new Runnable() {	
			@Override
			public void run() {
				boton.setVisibility(View.GONE);
			}
		}); 
		
		empezar = true;
		midiendo = false;
		recorder.startRecording();
		// lanza la toma periódica de medidas
		Looper.prepare();		
		handler.postDelayed(realizarUnaMedida, 0);
		Looper.loop();
	}
	
	private short[] generadorCoseno(double fs, double fr, short A0) {
		final short m[] = new short[Constants.RECORDER_SAMPLERATE]; // se van a generar tonos de 1 segundo de duración, aunque suenen menos tiempo
		double fase = 0;
   	 	// genera el vector con el tono
   	 	for(int n = 0; n < Constants.RECORDER_SAMPLERATE; n++) {
   	 		m[n] =  (short) (A0 * Math.cos(fase));
   	 		fase += 2 * Math.PI * (fr / fs);
   	 	}
   	 	return(m);
	}
	
	private short[] seleccionarMuestrasGrabacion(int DATOS_INICIALES_NO_VALIDOS, int DATOS_VALIDOS) {
		// DATOS_INICIALES_NO_VALIDOS = datos iniciales de cada pulsación que se desprecian por no tener todavía el pulso estabilizado
		// DATOS_VALIDOS = LONGITUD_TONO_GRABADO - DATOS_INICIALES_NO_VALIDOS;
		final int DATOS_TOTALES = DATOS_VALIDOS + DATOS_INICIALES_NO_VALIDOS;
		
		short[] muestrasValidas = new short[DATOS_VALIDOS];		
		short[] datosBuffer = new short[bufferSize];
		short[] datosTotal= new short[Constants.LONGITUD_TONO_GRABADO * 2];
		int datosLeidosBuffer = 0;
		int datosLeidosTotales = 0;
		while (datosLeidosTotales < (DATOS_TOTALES) ) { // sigue leyendo del buffer hasta tener DATOS_TOTALES	
			datosLeidosBuffer = recorder.read(datosBuffer, 0, DATOS_TOTALES);
			if (datosLeidosBuffer > 0) {
				datosLeidosTotales += datosLeidosBuffer;
				System.arraycopy(datosBuffer						//src -- This is the source array
						, 0											//srcPos -- This is the starting position in the source array
						, datosTotal								//dest -- This is the destination array
						, datosLeidosTotales - datosLeidosBuffer	//destPos -- This is the starting position in the destination data
						, datosLeidosBuffer);						//length -- This is the number of array elements to be copied
			}
		}
	
		// selecciona solo los datos válidos y los guarda
		System.arraycopy(datosTotal							//src -- This is the source array
				, DATOS_INICIALES_NO_VALIDOS				//srcPos -- This is the starting position in the source array
				, muestrasValidas							//dest -- This is the destination array
				, 0											//destPos -- This is the starting position in the destination data
				, DATOS_VALIDOS);							//length -- This is the number of array elements to be copied
		return(muestrasValidas);
	}

	private double[] guardarMuestras() {
		
		double[] muestras = new double[Constants.LONGITUD_TONO_GRABADO * 2];
		
		int datosTotales = 0;
		int datosLeidos = 0;
		short[] sTotal = new short[Constants.LONGITUD_TONO_GRABADO * 2];
		
		datosLeidos = 0;
		while(datosTotales < Constants.LONGITUD_TONO_GRABADO) {
			datosLeidos = recorder.read(datosBuffer, 0, Constants.LONGITUD_TONO_GRABADO);
			datosTotales += datosLeidos;
			System.arraycopy(datosBuffer, 0, sTotal, datosTotales - datosLeidos, datosLeidos);
		}
		
		for(int i = 0; i < Constants.LONGITUD_TONO_GRABADO; i++) {
			muestras[i * 2] = sTotal[i];
			muestras[i * 2 + 1] = 0;
		}
		return muestras;
	}
	
	private double[] compensarCAG(double[] muestras) {
		
		// calcula RMS de la muestra grabada
		double medidaRMS = 0;
		double energiaMedida = 0;
		for(int i = 0; i < Constants.LONGITUD_TONO_GRABADO; i++){
			energiaMedida += muestras[i * 2] * muestras[i * 2];
		}
		medidaRMS = Math.sqrt(energiaMedida / Constants.LONGITUD_TONO_GRABADO);
		
		// calcula la función para interpolar el ajuste de CAG
		UnivariateInterpolator interpolator = new LinearInterpolator();
		UnivariateFunction function = null;
		function = interpolator.interpolate(datosCalibrado.RMSajusteCAG, datosCalibrado.ajusteCAG);
		double coeficienteAjusteCAG = datosCalibrado.ajusteCAG[0];
		if (medidaRMS > datosCalibrado.RMSajusteCAG[0]) {
			if (medidaRMS > datosCalibrado.RMSajusteCAG[Constants.numeroAmplitudes - 1]) coeficienteAjusteCAG = datosCalibrado.ajusteCAG[Constants.numeroAmplitudes - 1];
			else coeficienteAjusteCAG = function.value(medidaRMS);
		}
		
		// multiplica el valor de las muestras por el coeficiente de ajuste CAG
		for(int i = 0; i < Constants.LONGITUD_TONO_GRABADO; i++){
			muestras[i * 2] = muestras[i * 2] / coeficienteAjusteCAG;
		}
		return muestras;
	}
	
	private double[] calcularDEP_Y(double[] muestras) {
		
		double[] dep_Y = new double[Constants.numeroFrecuenciasInterp];
		
		DoubleFFT_1D fft = new DoubleFFT_1D(Constants.LONGITUD_TONO_GRABADO); 
		double[] modFFT = new double[Constants.LONGITUD_TONO_GRABADO];
		
		fft.complexForward(muestras);
		
		for(int i = 0; i < Constants.LONGITUD_TONO_GRABADO; i++) {
			//No se incluye la raíz cuadrada porque necesitamos el módulo al cuadrado.
			modFFT[i] = (muestras[2 * i] * muestras[2 * i] + muestras[2 * i + 1] * muestras[2 * i + 1]);	 
		}
		
		//toma las componentes desde frecuenciaInterpInicial hasta frecuenciaInterpFinal
		for(int frecuencia = Constants.frecuenciaInterpInicial; frecuencia <= Constants.frecuenciaInterpFinal; frecuencia++) {
			int i = frecuencia - Constants.frecuenciaInterpInicial;
			dep_Y[i] = modFFT[frecuencia];
		}
		return dep_Y;
	}
	
	private double[] calcularDEP_X(double[] dep_y) {
		
		double[] dep_x = new double[Constants.numeroFrecuenciasInterp];
		
		for(int i = 0; i < Constants.numeroFrecuenciasInterp; i++) {	
			dep_x[i] = dep_y[i] / DEP_H[i];
		}
		return (dep_x);
	}
	
	//Calcula la potencia de la señal original, X(w).
	private double calcularPotenciaX(double[] dep_x) {
		
		double energia = 0;
		
		for(int i = 0; i < Constants.numeroFrecuenciasInterp; i++) {	
			energia += dep_x[i];
		}
		return (Math.sqrt(energia / (Constants.LONGITUD_TONO_GRABADO / 2)) * 1);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.neur_gai, menu);
		this.menu = menu;
		return true;
	}

	 
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		
		switch (item.getItemId()) {
		
		case R.id.ajustes:
        	// introducir ajustes
            return true;
            

        //Falta, la verificación de los datos.     
		case R.id.tarifas:{
			AlertDialog.Builder alert = new AlertDialog.Builder(this);
			euskaraz = true;
			alert.setTitle(euskaraz? "Sartu merkatu librearen tarifak (€/kWh) eta CO2 isurketak (gCO2/kWh)" : "Introduce las tarifas de libre mercado (€/kWh) y emisiones de CO2 (gCO2/kWh)");
			LayoutInflater factory = LayoutInflater.from(this);
            View layout = factory.inflate(R.layout.row, null);

            final EditText feu20A =(EditText)layout.findViewById(R.id.editText1);
			final EditText feu20DHAPunta =(EditText)layout.findViewById(R.id.editText2);
			final EditText feu20DHAValle =(EditText)layout.findViewById(R.id.editText3);

			final EditText feu20DHSPunta =(EditText)layout.findViewById(R.id.editText4);
			final EditText feu20DHSValle =(EditText)layout.findViewById(R.id.editText5);
			final EditText feu20DHSSuperValle =(EditText)layout.findViewById(R.id.editText6);

			alert.setView(layout);
			
			alert.setPositiveButton(euskaraz? "Onartu" : "Aceptar", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				
				//Se vuelcan las tarifas definidas por el usuario a la BBDD.
				 volcadoTarifasLibreUsuario(Double.parseDouble(feu20A.getText().toString()), 
						 					Double.parseDouble(feu20DHAPunta.getText().toString()),
						 					Double.parseDouble(feu20DHAValle.getText().toString()),
						 					Double.parseDouble(feu20DHSPunta.getText().toString()),
						 					Double.parseDouble(feu20DHSValle.getText().toString()),
						 					Double.parseDouble(feu20DHSSuperValle.getText().toString()));
			  }
			});
			
			alert.setNegativeButton(euskaraz? "Ezeztatu" : "Cancelar", new DialogInterface.OnClickListener() {
			  public void onClick(DialogInterface dialog, int whichButton) {
				  return;
			  }
			});

			alert.show();
			return true;
            
		}
		case R.id.sonda:
			medidaSonda = true;
			menu.findItem(R.id.sonda_bluetooth).setIcon(R.drawable.ic_sondanegro);
            return true;
            
		case R.id.bluetooth:
			medidaSonda = false;
			menu.findItem(R.id.sonda_bluetooth).setIcon(R.drawable.ic_bluetoothnegro);
            return true;
            
		case R.id.s1:
			periodicidadMedidaEnSegundos = 1;
			menu.findItem(R.id.tiempo).setIcon(null).setTitle("2 seg");
			
			//Hay que 
			handler.removeCallbacks(realizarUnaMedida);					// elimina la última llamada a realizar nueva medida
			handler.postDelayed(realizarUnaMedida, periodicidadMedidaEnSegundos);
			
            return true;

		case R.id.s10:
			periodicidadMedidaEnSegundos = 10 - 1;
			menu.findItem(R.id.tiempo).setIcon(null).setTitle("10 seg");
			//Hay que 
			handler.removeCallbacks(realizarUnaMedida);		// elimina la última llamada a realizar nueva medida
			handler.postDelayed(realizarUnaMedida, periodicidadMedidaEnSegundos);
			
            return true;
        
		case R.id.s30:
			periodicidadMedidaEnSegundos = 30 - 1;
			menu.findItem(R.id.tiempo).setIcon(null).setTitle("30 seg");
			//Hay que 
			handler.removeCallbacks(realizarUnaMedida);					// elimina la última llamada a realizar nueva medida
			handler.postDelayed(realizarUnaMedida, periodicidadMedidaEnSegundos);
			
			return true;
            
		case R.id.m1:
			periodicidadMedidaEnSegundos = 60 - 1;
			menu.findItem(R.id.tiempo).setIcon(null).setTitle("1 min");
			//Hay que 
			handler.removeCallbacks(realizarUnaMedida);					// elimina la última llamada a realizar nueva medida
			handler.postDelayed(realizarUnaMedida, periodicidadMedidaEnSegundos);
			
			return true;
            
		case R.id.m5:
			periodicidadMedidaEnSegundos = 300 - 1;
			menu.findItem(R.id.tiempo).setIcon(null).setTitle("5 min");
			//Hay que 
			handler.removeCallbacks(realizarUnaMedida);					// elimina la última llamada a realizar nueva medida
			handler.postDelayed(realizarUnaMedida, periodicidadMedidaEnSegundos);
			
			return true;

		case R.id.m10:
			periodicidadMedidaEnSegundos = 600 - 1;
			menu.findItem(R.id.tiempo).setIcon(null).setTitle("10 min");
			//Hay que 
			handler.removeCallbacks(realizarUnaMedida);					// elimina la última llamada a realizar nueva medida
			handler.postDelayed(realizarUnaMedida, periodicidadMedidaEnSegundos);
			
			return true;
        
		case R.id.continuar:
			midiendo = true;
			menu.findItem(R.id.grabacion).setIcon(R.drawable.ic_continuar);
			handler.postDelayed(realizarUnaMedida, 0);
			//empezar=false;
			return true;
            
		case R.id.parar:
			midiendo = false;
			menu.findItem(R.id.grabacion).setIcon(R.drawable.ic_parar);
			handler.removeCallbacks(realizarUnaMedida);					// elimina la última llamada a realizar nueva medida
	        
            return true;
            
		case R.id.grabaryreiniciar:
			empezar = true;
			midiendo = false;
			runOnUiThread(new Runnable() {	
    			@Override
    			public void run() {
    				findViewById(R.id.grafica).setVisibility(View.GONE); 		// hace invisible el gráfico y datos
    				findViewById(R.id.texto1).setVisibility(View.GONE);
    				findViewById(R.id.textVCeldaEmpty).setVisibility(View.GONE);
    				findViewById(R.id.TextVLibre).setVisibility(View.GONE);
    				findViewById(R.id.TextV20A).setVisibility(View.GONE);
    				findViewById(R.id.textV20DHA).setVisibility(View.GONE);
    				
    				findViewById(R.id.text20A).setVisibility(View.GONE);
    				findViewById(R.id.text20DHA).setVisibility(View.GONE);
    				findViewById(R.id.text20DHS).setVisibility(View.GONE);
    				
    				findViewById(R.id.TextVPVPC).setVisibility(View.GONE);
    				findViewById(R.id.textV20DHSPVPC).setVisibility(View.GONE);
    				
    				findViewById(R.id.text20APVPC).setVisibility(View.GONE);
    				findViewById(R.id.text20DHAPVPC).setVisibility(View.GONE);
    				findViewById(R.id.text20DHSPVPC).setVisibility(View.GONE);
    				
    				findViewById(R.id.logoEHU).setVisibility(View.VISIBLE);		// habilita el logo EHU
    			}
    		});
	    	volcarBBDDFichero_LimpiarBBBDD();									// Salva la tabla de costes de la BBDD y eliminar los registros.
			menu.findItem(R.id.grabacion).setIcon(R.drawable.ic_empezar);
            
			return true;
        
		case R.id.anotar:
			//Lanza diálogo para recoger comentario.
			//Guardar comentario en la base de datos.
			//
			//Incluir anotar=true; en la condición de Aceptar del diálogo.
			//Guardarlo una vez en la bbdd.
			
			AlertDialog.Builder alert = new AlertDialog.Builder(this);
			alert.setTitle("Comenta la medida");
			final EditText input = new EditText(this);
			alert.setView(input);

			alert.setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
			  Editable value= input.getText();
			  comentario=value.toString();
			  anotar=true;
			  }
			});
			
			alert.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
			  public void onClick(DialogInterface dialog, int whichButton) {
				  return;
			  }
			});

			alert.show();
			return true;
            
		case R.id.grabarDatos:

			runOnUiThread(new Runnable() {	
				@Override
				public void run() {
					final TextView textViewMedida = (TextView) findViewById(R.id.texto1);
					textViewMedida.setText(R.string.grabandoDatosMedida);
				}
			});		
			
			//lanza otro hilo con la grabación de datos
			new Thread() {
				public void run() {
					Time tiempo = new Time();
		 			tiempo.setToNow();
		 			guardarArrays(Constants.pathFicherosComplementarios + "/" + Long.toString(tiempo.toMillis(false)) + "DEP_X.csv", "Hz, DEP_X(f)", DEP_X_grabar);
		 			guardarArrays(Constants.pathFicherosComplementarios + "/" + Long.toString(tiempo.toMillis(false)) + "DEP_H.csv", "Hz, DEP_H(f)", DEP_H);
		 			guardarArrays(Constants.pathFicherosComplementarios + "/" + Long.toString(tiempo.toMillis(false)) + "DEP_Y.csv", "Hz, DEP_Y(f)", DEP_Y_grabar);
		 			guardarArrays(Constants.pathFicherosComplementarios + "/" + Long.toString(tiempo.toMillis(false)) + "Muestras.csv", "n, muestras", muestras_grabar);
		 			runOnUiThread(new Runnable() {	
						@Override
						public void run() {
							final TextView textViewMedida = (TextView) findViewById(R.id.texto1);
							textViewMedida.setVisibility(View.GONE);
						}
					});	
		 			grabarDatos = false;
		 		}
			}.start();
            return true;
  
        default:
            return super.onOptionsItemSelected(item);
		}
	}
	
	private void showMessage(String title, String message) {
		
		new AlertDialog.Builder(this)
	    .setTitle(title)
	    .setMessage(message)
	    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int which) {
	        	botonPulsado = true;
	        }
	     })
	     .show();
	}
	
	private void guardarArrays(String path, String cabecera, double[] arrayDatos) {

		String stringData;
		FileOutputStream os ;
		
		try {
			os = new FileOutputStream(path, false);
			cabecera = cabecera + "\n";
			os.write(cabecera.getBytes(Charset.forName(Constants.formatoFichero)));
			
			for(int i = 0; i < arrayDatos.length; i++) {
				stringData = Integer.toString(i) + "," + Double.toString(arrayDatos[i]) + "\n";
				os.write(stringData.getBytes(Charset.forName(Constants.formatoFichero)));
			}
			os.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@SuppressLint("InflateParams")
	private void preguntarAmplitudCalibrado() throws NumberFormatException {
		
		LayoutInflater inflater = LayoutInflater.from(this);
		View viewT = inflater.inflate(R.layout.amplitudcalibrado, null);
		
		final EditText ventanaDato = (EditText) viewT.findViewById(R.id.ventanaDato);
		new AlertDialog.Builder(this)
		.setTitle("Amplitud de calibrado")
		.setView(viewT)
		.setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) throws NumberFormatException {
				try {
					amplitudCalibradoFiltro = Short.valueOf(ventanaDato.getText().toString());
					botonPulsado = true;
					synchronized (llaveBotonPulsado) {
			    		llaveBotonPulsado.notifyAll();
			    	}
	            } catch (NumberFormatException e) {
	            	throw e;
	            }
	        }
		})
		.show();
	}
	
	@SuppressLint("InflateParams")
	public void preguntarMedidaReferencia() throws NumberFormatException {
		
		LayoutInflater inflater = LayoutInflater.from(this);
		View viewT = inflater.inflate(R.layout.medidareferencia, null);
		
		final EditText ventanaDato = (EditText) viewT.findViewById(R.id.ventanaDato);
		new AlertDialog.Builder(this)
		.setTitle("Medida de referencia")
		.setView(viewT)
		.setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) throws NumberFormatException {
				try {
					medidaReferencia = Double.valueOf(ventanaDato.getText().toString());
					botonPulsado = true;
					synchronized (llaveBotonPulsado) {
			    		llaveBotonPulsado.notifyAll();
			    	}
	            } catch (NumberFormatException e) {
	            	throw e;
	            }
	        }
		})
		.show();
	}
}

