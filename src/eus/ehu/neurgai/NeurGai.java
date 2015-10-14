package eus.ehu.neurgai;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
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
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.helper.StaticLabelsFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.interpolation.UnivariateInterpolator;
import org.jtransforms.fft.DoubleFFT_1D;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import eus.ehu.neurgai.BaseDatosNeurGAI.ColumnasCostes;
import eus.ehu.neurgai.BaseDatosNeurGAI.ColumnasTarifas;

public class NeurGai extends ActionBarActivity {

    private Menu menu;

    private boolean medidaSonda = true;    // si false la medida es a través de bluetooth + KL05Z
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

    private double medidaRMS;

    final Handler handler = new Handler();
    GraphView graficoMedidas;

    LineGraphSeries<DataPoint> serieMedidas;

    private MyPhoneStateListener phoneStateListener = new MyPhoneStateListener();

    private String comentario = null; //Revisar por si da error en la BBDD.

    /**************************
     * BASE DE DATOS
     ******************************************/

    //Leerá la tabla de los costes de energía que hay en la BBDD de datos
    private List<Coste> extraerTablaCostesBBDD() {

        BaseDatosNeurGAI bbdd = new BaseDatosNeurGAI(this);
        SQLiteDatabase dbRead = bbdd.getReadableDatabase();
        List<Coste> tablaCostes = new ArrayList<Coste>();


        Cursor c = dbRead.query(
                ColumnasCostes.TABLE_NAME, BaseDatosNeurGAI.projectionCostes, null, null, null, null, null, null
        );


        c.moveToFirst();
        if (c.getCount() > 0 && c != null) {
            do {

                Coste costeFila = new Coste();


                /*************************Tabla de los Costes*************************************/

                costeFila.setComentario(c.getString(
                        c.getColumnIndex(
                                BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_COMENTARIOS)));

                costeFila.setFechaMedida(c.getString(
                        c.getColumnIndex(
                                BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_FECHA_MEDIDA)));
                costeFila.setTiempoMedida(c.getDouble
                        (c.getColumnIndex
                                (BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_TIEMPO_MEDIDA)));
                costeFila.setPotencia(c.getInt
                        (c.getColumnIndex
                                (BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_POTENCIA)));


                costeFila.setTarifa20A(
                        c.getDouble(
                                c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_TARIFA20A)));
                costeFila.setTarifa20DHA(
                        c.getDouble(
                                c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_TARIFA20DHA)));
                costeFila.setTarifa20DHS(
                        c.getDouble(
                                c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_TARIFA20DHS)));

                costeFila.setTarifa20A_PVPC(
                        c.getDouble(
                                c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_TARIFA20A_PVPC)));
                costeFila.setTarifa20DHA_PVPC(
                        c.getDouble(
                                c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_TARIFA20DHA_PVPC)));
                costeFila.setTarifa20DHS_PVPC(
                        c.getDouble(
                                c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_TARIFA20DHS_PVPC)));


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

                costeFila.setEmisionesAcumuladasLibre(
                        c.getDouble(
                                c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_EMISIONES_TOTALESLibre)));
                costeFila.setEmisionesAcumuladasPVPC(
                        c.getDouble(
                                c.getColumnIndex(BaseDatosNeurGAI.ColumnasCostes.COLUMN_NAME_EMISIONES_TOTALESPVPC)));
                tablaCostes.add(costeFila);

            } while (c.moveToNext());
        }

        dbRead.delete(ColumnasCostes.TABLE_NAME, null, null);
        c.close();
        dbRead.close();
        bbdd.close();
        return tablaCostes;
    }

    //Escribe los datos recogidos en la BBDD en los ficheros y flashea la BBDD.
    private void volcarBBDDFichero_LimpiarBBBDD() {
        // definición de ficheros y path
        FileOutputStream osCostesBBDDCSV = null;
        String cabecera, filaCSV, COMA = ",";

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());

        List<Coste> costes = null;


        String diaSistema = new SimpleDateFormat("ddMMMyyyy").format(calendar.getTime()).toString();
        String horaSistema = new SimpleDateFormat("HH'h'mm'm'ss's'").format(calendar.getTime()).toString();

        costes = extraerTablaCostesBBDD();
        cabecera = getString(R.string.cabeceraFicheroCostes);

        try {
            new File(Constants.pathFicherosRegistros + "/" + diaSistema).mkdir();
            osCostesBBDDCSV = new FileOutputStream(Constants.pathFicherosRegistros + "/" + diaSistema + "/" + getString(R.string.costes) + horaSistema + ".csv");        // borra el fichero si existe

            osCostesBBDDCSV.write(cabecera.getBytes(Charset.forName("UTF-8")));
            for (int i = 0; i < costes.size(); i++) {

                //Extraemos los datos de la lista y la convertimos en una fila de string.
                filaCSV = costes.get(i).getComentario() + COMA +
                        costes.get(i).getFechaMedida() + COMA +
                        costes.get(i).getTiempoMedida() + COMA +
                        Integer.toString(costes.get(i).getPotencia()) + COMA +

                        Double.toString(costes.get(i).getEmisionesAcumuladasPVPC()) + COMA +
                        Double.toString(costes.get(i).getTarifa20A_PVPC()) + COMA +
                        Double.toString(costes.get(i).getCoste20A_PVPC()) + COMA +
                        Double.toString(costes.get(i).getCosteAcumulado20A_PVPC()) + COMA +

                        Double.toString(costes.get(i).getTarifa20DHA_PVPC()) + COMA +
                        Double.toString(costes.get(i).getCoste20DHA_PVPC()) + COMA +
                        Double.toString(costes.get(i).getCosteAcumulado20DHA_PVPC()) + COMA +

                        Double.toString(costes.get(i).getTarifa20DHS_PVPC()) + COMA +
                        Double.toString(costes.get(i).getCoste20DHS_PVPC()) + COMA +
                        Double.toString(costes.get(i).getCosteAcumulado20DHS_PVPC()) + COMA +

                        Double.toString(costes.get(i).getEmisionesAcumuladasLibre()) + COMA +
                        Double.toString(costes.get(i).getTarifa20A()) + COMA +
                        Double.toString(costes.get(i).getCoste20A()) + COMA +
                        Double.toString(costes.get(i).getCosteAcumulado20A()) + COMA +

                        Double.toString(costes.get(i).getTarifa20DHA()) + COMA +
                        Double.toString(costes.get(i).getCoste20DHA()) + COMA +
                        Double.toString(costes.get(i).getCosteAcumulado20DHA()) + COMA +

                        Double.toString(costes.get(i).getTarifa20DHS()) + COMA +
                        Double.toString(costes.get(i).getCoste20DHS()) + COMA +
                        Double.toString(costes.get(i).getCosteAcumulado20DHS()) + COMA + "\n";

                osCostesBBDDCSV.write(filaCSV.getBytes(Charset.forName("UTF-8")));
            }
            osCostesBBDDCSV.close();
        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    //Escribe las tarifas PVPC a la BBDD
    private void volcarTarifasBDD(ParsearTarifas parseo) {
        BaseDatosNeurGAI bbdd = new BaseDatosNeurGAI(this);
        SQLiteDatabase dbWrite = bbdd.getWritableDatabase();
        ContentValues values = new ContentValues();

        List<Tarifa> tarifa20A_PVPC;
        List<Tarifa> tarifa20DHA_PVPC;
        List<Tarifa> tarifa20DHS_PVPC;


        //Borramos la tabla de las tarifas para evitar que se acumulen tarifas de días anteriores
        dbWrite.delete(ColumnasTarifas.TABLE_NAME, null, null);
        if (parseo.domPVPC != null) {
            tarifa20A_PVPC = parseo.getTarifa20A_PVPC();
            tarifa20DHA_PVPC = parseo.getTarifa20DHA_PVPC();
            tarifa20DHS_PVPC = parseo.getTarifa20DHS_PVPC();

            for (int i = 0; i < 24; i++) {

                values.put(ColumnasTarifas.COLUMN_NAME_FECHA, tarifa20A_PVPC.get(i).getFecha());
                values.put(ColumnasTarifas.COLUMN_NAME_HORA, tarifa20A_PVPC.get(i).getHora());

                values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20A_PVPC, (tarifa20A_PVPC.get(i).getFeu()));
                values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHA_PVPC, (tarifa20DHA_PVPC.get(i).getFeu()));
                values.put(ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS_PVPC, (tarifa20DHS_PVPC.get(i).getFeu()));

                dbWrite.insert(ColumnasTarifas.TABLE_NAME, null, values);
            }
        }

        dbWrite.close();
        bbdd.close();
    }


    private double getTarifa20DHS() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        int hora = Integer.parseInt(new SimpleDateFormat("HH").format(calendar.getTime()));

        double tarifa20DHS;

        //Comprueba si estamos en horario de verano (Daylight Savings Time)
        Time horaActual = new Time();
        horaActual.setToNow();
        if (horaActual.isDst > 0) {
            //Punta
            if (hora >= 13 && hora < 23) {
                tarifa20DHS = getTarifaLibre(R.string.nombreT20DHSpunta, R.string.valor2_0DHSpunta);
            } else {
                //Supervalle
                if (hora >= 1 && hora < 7) {
                    tarifa20DHS = getTarifaLibre(R.string.nombreT20DHSllano, R.string.valor2_0DHSllano);

                } else {
                    //Valle DHS.
                    tarifa20DHS = getTarifaLibre(R.string.nombreT20DHSsupervalle, R.string.valor2_0DHSsvalle);
                }
            }
        } else {
            //En caso contrario, estamos en horario de invierno (fuera de Daylight Savings Time)
            //Punta
            if (hora >= 12 && hora < 22) {
                tarifa20DHS = getTarifaLibre(R.string.nombreT20DHSpunta, R.string.valor2_0DHSpunta);

            } else {
                //Valle y supervalle.
                if (hora >= 0 && hora < 6) {
                    tarifa20DHS = getTarifaLibre(R.string.nombreT20DHSllano, R.string.valor2_0DHSllano);
                } else {
                    tarifa20DHS = getTarifaLibre(R.string.nombreT20DHSsupervalle, R.string.valor2_0DHSsvalle);
                }
            }
        }
        return tarifa20DHS;
    }

    private double getTarifa20DHA() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        int hora = Integer.parseInt(new SimpleDateFormat("HH").format(calendar.getTime()));

        double tarifa20DHA;

        //Comprueba si estamos en horario de verano (Daylight Savings Time)
        Time horaActual = new Time();
        horaActual.setToNow();
        if (horaActual.isDst > 0) {
            //Punta
            if (hora >= 13 && hora < 23) {
                tarifa20DHA = getTarifaLibre(R.string.nombreT20DHApunta, R.string.valor2_0DHApunta);
            } else {
                tarifa20DHA = getTarifaLibre(R.string.nombreT20DHAvalle, R.string.valor2_0DHAvalle);
            }
        } else {
            //En caso contrario, estamos en horario de invierno (fuera de Daylight Savings Time)
            //Punta
            if (hora >= 12 && hora < 22) {
                tarifa20DHA = getTarifaLibre(R.string.nombreT20DHApunta, R.string.valor2_0DHApunta);

            } else {
                tarifa20DHA = getTarifaLibre(R.string.nombreT20DHAvalle, R.string.valor2_0DHAvalle);
            }
        }

        return tarifa20DHA;
    }


    //Lee el último registro de tipo double de cualquiera de las tablas de la bbdd.
    //Este método sirve para saber el tiempo de la medida anterior.
    private double leerUltimoRegistroDouble(String registro, String nombreTabla) {
        BaseDatosNeurGAI bbdd = new BaseDatosNeurGAI(this);
        SQLiteDatabase dbRead = bbdd.getReadableDatabase();
        double registroDouble;

        String[] projection = {
                registro,
        };

        Cursor c = dbRead.query(
                nombreTabla, projection, null, null, null, null, null, null
        );


        if (c.getCount() == 0) {
            registroDouble = 0;
        } else {
            c.moveToLast();
            registroDouble = c.getDouble(
                    c.getColumnIndex(registro));
        }

        c.close();
        dbRead.close();
        bbdd.close();
        return registroDouble;
    }

    //Lee el último registro en en cualquiera de las tablas de la BBDD.
    private String leerRegistroString(String registro, String nombreTabla) {
        BaseDatosNeurGAI bbdd = new BaseDatosNeurGAI(this);
        SQLiteDatabase dbRead = bbdd.getReadableDatabase();

        String registroString;

        String[] projection = {
                registro,
        };

        Cursor c = dbRead.query(
                nombreTabla, projection, null, null, null, null, null, null
        );


        c.moveToFirst();
        if (c.getCount() == 0) {
            registroString = null;
        } else {

            registroString = c.getString(
                    c.getColumnIndex(registro));
        }

        c.close();
        dbRead.close();
        bbdd.close();
        return registroString;
    }


    //Realiza una consulta en la BBDD del feu de un tipo de tarifa, en una hora determinada.
    private double leerFEU(int hora, String tipoTarifa) {
        BaseDatosNeurGAI bbdd = new BaseDatosNeurGAI(this);

        SQLiteDatabase dbRead = bbdd.getReadableDatabase();

        double feu;


        //Consulta sqlite para el feu de una hora determinada.
        String sql =
                "SELECT " + ColumnasTarifas.COLUMN_NAME_HORA + ", " +
                        tipoTarifa + " FROM " +
                        ColumnasTarifas.TABLE_NAME + " WHERE " +
                        ColumnasTarifas.COLUMN_NAME_HORA + " = " + Integer.toString(hora);


        //Consulta las filas y se asegura que existe al menos una fila.
        Cursor c = dbRead.rawQuery(sql, null);
        c.moveToFirst();


        if (c.getCount() == 0) {
            feu = 0;
        } else {
            feu = c.getDouble(c.getColumnIndex(tipoTarifa));
        }

        c.close();
        dbRead.close();
        bbdd.close();
        return feu;
    }

    private void guardarRegistros(ContentValues values, String nombreTabla) {
        BaseDatosNeurGAI bbdd = new BaseDatosNeurGAI(this);
        SQLiteDatabase dbWrite = bbdd.getWritableDatabase();


        dbWrite.insert(nombreTabla, null, values);

        dbWrite.close();
        bbdd.close();
    }

    private void calcularPrecioMedida(double potencia, double tiempoMedida) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        ContentValues values = new ContentValues();


        //Se consulta la hora para elegir adecuadamente el feu correspondiente a esa hora.
        //Se consulta el día para verificar la vigencia de las tarifas de la BBDD.
        int horaSistema = calendar.get(Calendar.HOUR_OF_DAY);
        String fechaSistema = new SimpleDateFormat("yyyyMMdd").format(calendar.getTime());

        double diferenciaTiempo, tiempoAnterior;

        //Método que comprueba y actualiza si es necesario la tabla de las tarifas en la BBDD.
        consultaVigenciaTarifas(fechaSistema);


        //Se comprueba si es la primera medida, para evitar errores cuando no existen tiempos guardados en la BBDD.
        tiempoAnterior = leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_TIEMPO_MEDIDA, ColumnasCostes.TABLE_NAME);
        if (tiempoAnterior == 0) {
            diferenciaTiempo = 0;
        } else {
            diferenciaTiempo = tiempoMedida - tiempoAnterior;
        }


        //El objeto coste contiene los métodos que calculan los costes.
        final Coste coste = new Coste();
        double kWh = (potencia / 1000) * (diferenciaTiempo / 3600);
        //Se lee la tarifa de la BBDD con leerFEU() y se calcula el coste de la energía y el coste acumulado..

        coste.setTarifa20A_PVPC(leerFEU(horaSistema, ColumnasTarifas.COLUMN_NAME_TARIFA_20A_PVPC));
        coste.setCoste20A_PVPC((coste.getTarifa20A_PVPC()) * kWh);
        coste.setCosteAcumulado20A_PVPC(coste.getCoste20A_PVPC() +
                leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20A_PVPC, ColumnasCostes.TABLE_NAME));

        coste.setTarifa20DHA_PVPC(leerFEU(horaSistema, ColumnasTarifas.COLUMN_NAME_TARIFA_20DHA_PVPC));
        coste.setCoste20DHA_PVPC(coste.getTarifa20DHA_PVPC() * kWh);
        coste.setCosteAcumulado20DHA_PVPC(coste.getCoste20DHA_PVPC() +
                leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHA_PVPC, ColumnasCostes.TABLE_NAME));

        coste.setTarifa20DHS_PVPC(leerFEU(horaSistema, ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS_PVPC));
        coste.setCoste20DHS_PVPC(coste.getTarifa20DHS_PVPC() * kWh);
        coste.setCosteAcumulado20DHS_PVPC(coste.getCoste20DHS_PVPC() +
                leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHS_PVPC, ColumnasCostes.TABLE_NAME));


        //FALTA LEER LAS TARIFAS LIBRES DE LA CONFIGURACION
        //Tarifa Goiener
        coste.setTarifa20A(getTarifaLibre(R.string.nombreT20Apunta, R.string.valor2_0Apunta));
        coste.setCoste20A(coste.getTarifa20A() * kWh);
        coste.setCosteAcumulado20A(coste.getCoste20A() +
                leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20A, ColumnasCostes.TABLE_NAME));

        coste.setTarifa20DHA(getTarifa20DHA());
        coste.setCoste20DHA(coste.getTarifa20DHA() * kWh);
        coste.setCosteAcumulado20DHA(coste.getCoste20DHA() +
                leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHA, ColumnasCostes.TABLE_NAME));

        coste.setTarifa20DHS(getTarifa20DHS());
        coste.setCoste20DHS(coste.getTarifa20DHS() * kWh);
        coste.setCosteAcumulado20DHS(coste.getCoste20DHS() +
                leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHS, ColumnasCostes.TABLE_NAME));


        //Calculan las emisiones acumuladas. kgCO2*kWh
        coste.setEmisionesAcumuladasLibre(leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_EMISIONES_TOTALESLibre, ColumnasCostes.TABLE_NAME) +
                (kWh * getEmisionesCO2(R.string.nombreEmisionLibre, R.string.cantidadEmisionLibre)));

        coste.setEmisionesAcumuladasPVPC(leerUltimoRegistroDouble(ColumnasCostes.COLUMN_NAME_EMISIONES_TOTALESPVPC, ColumnasCostes.TABLE_NAME) +
                (kWh * getEmisionesCO2(R.string.nombreEmisionPVPC, R.string.cantidadEmisionPVPC)));
        //Hilo para mostrar los resultados en los correspondientes TextView
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                //se multiplican por 100 para indicar el precio por pantalla en céntimos.
                final TextView text20APVPC = (TextView) findViewById(R.id.text20APVPC);
                text20APVPC.setText(String.format("%.2f", 100 * coste.getCosteAcumulado20A_PVPC()));

                final TextView text20DHAPVPC = (TextView) findViewById(R.id.text20DHAPVPC);
                text20DHAPVPC.setText(String.format("%.2f", 100 * coste.getCosteAcumulado20DHA_PVPC()));

                final TextView text20DHSPVPC = (TextView) findViewById(R.id.text20DHSPVPC);
                text20DHSPVPC.setText(String.format("%.2f", 100 * coste.getCosteAcumulado20DHS_PVPC()));

                final TextView text20A = (TextView) findViewById(R.id.text20A);
                text20A.setText(String.format("%.2f", 100 * coste.getCosteAcumulado20A()));

                final TextView text20DHA = (TextView) findViewById(R.id.text20DHA);
                text20DHA.setText(String.format("%.2f", 100 * coste.getCosteAcumulado20DHA()));

                final TextView text20DHS = (TextView) findViewById(R.id.text20DHS);
                text20DHS.setText(String.format("%.2f", 100 * coste.getCosteAcumulado20DHS()));

                //Las emisiones se multiplican por 1000 para que se muestren en gramos de CO2
                final TextView textCo2PVPC = (TextView) findViewById(R.id.co2pvpc);
                textCo2PVPC.setText(String.format("%.2f", 1000 * coste.getEmisionesAcumuladasPVPC()));

                final TextView textCo2Libre = (TextView) findViewById(R.id.co2libre);
                textCo2Libre.setText(String.format("%.2f", 1000 * coste.getEmisionesAcumuladasLibre()));
            }
        });

        //Se guardan los medidos y calculades en la table de costes de la BBDD.
        values.put(ColumnasCostes.COLUMN_NAME_POTENCIA, potencia);
        values.put(ColumnasCostes.COLUMN_NAME_TIEMPO_MEDIDA, tiempoMedida);
        values.put(ColumnasCostes.COLUMN_NAME_FECHA_MEDIDA, new SimpleDateFormat("dd'/'MM'/'yyyy HH:mm:ss").format(calendar.getTime()));

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

        values.put(ColumnasCostes.COLUMN_NAME_EMISIONES_TOTALESLibre, coste.getEmisionesAcumuladasLibre());
        values.put(ColumnasCostes.COLUMN_NAME_EMISIONES_TOTALESPVPC, coste.getEmisionesAcumuladasPVPC());

        if (anotar == true) {
            values.put(ColumnasCostes.COLUMN_NAME_COMENTARIOS, comentario);
            anotar = false;
        }

        guardarRegistros(values, ColumnasCostes.TABLE_NAME);
    }

    private class TareaDescarga extends AsyncTask<Void, Integer, Boolean> {

        @Override
        protected Boolean doInBackground(Void... params) {

            ParsearTarifas parseo = new ParsearTarifas();
            volcarTarifasBDD(parseo);
            return true;
        }

        protected void onPostExecute() {

        }
    }

    //Consulta si las tarifas están actualizadas y las actualiza en caso necesario.
    private void consultaVigenciaTarifas(final String fechaSistema) {
        String fechaTarifaBBDD;

        //Si la tabla está vacía o la fecha del sistema y de la base de datos es diferente, se actualiza la tabla de tarifas.
        fechaTarifaBBDD = leerRegistroString(ColumnasTarifas.COLUMN_NAME_FECHA, ColumnasTarifas.TABLE_NAME);

        if (fechaTarifaBBDD == null) {
            TareaDescarga tDescarga = new TareaDescarga();
            tDescarga.execute();
            tDescarga.onPostExecute();


        } else if (fechaTarifaBBDD.compareTo(fechaSistema) != 0) {
            TareaDescarga tDescarga = new TareaDescarga();
            tDescarga.execute();
            tDescarga.onPostExecute();
        }

    }

    /******************************
     * Bluetooth
     *************************************/
    private int requestCode = 0000;
    private String potenciaBluetooth;
    private ConnectThread mConnectThread;

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        BluetoothDevice device;
        if (requestCode == this.requestCode) {
            if (resultCode == Activity.RESULT_OK) {
                device = data.getParcelableExtra("dispositivo");
                mConnectThread = new ConnectThread(device);
                mConnectThread.start();
            }
        }
    }

    // Handler que recogerá los valores enviados por hilo de conexión.
    private final Handler handlerBT = new Handler() {

        public void handleMessage(Message mensaje) {
            StringBuilder tiempo = new StringBuilder();
            StringBuilder potencia = new StringBuilder();
            char[] charArray = mensaje.obj.toString().toCharArray();
            int j = 0;


            if (charArray.length > 0) {

                if (charArray[0] == '#') {
                    if (charArray[j] == '#') {
                        j++;
                        while (charArray[j] != '€') {
                            tiempo.append(charArray[j]);
                            j++;
                        }
                    }
                    if (charArray[j] == '€') {

                        j++;

                        while (charArray[j] != '*') {

                            potencia.append(charArray[j]);
                            j++;
                        }
                    }
                } else {
                    Toast.makeText(getBaseContext(), getString(R.string.configurandoRXBT), Toast.LENGTH_SHORT).show();
                }
            } else {
                potencia.append(0F);
            }

            potenciaBluetooth = potencia.toString();
            Log.i("Medidas", "Tiempo: " + tiempo + " Potencia: " + potencia);
        }
    };

    private final Handler handlerToast = new Handler() {

        public void handleMessage(Message mensaje) {
            Toast.makeText(getBaseContext(), mensaje.obj.toString(), Toast.LENGTH_LONG).show();
        }
    };

    private class ConnectThread extends Thread {
        private BluetoothSocket mSocket;
        private final UUID direccionSerieDefecto = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        private ConnectedThread mConnectedThred;

        public ConnectThread(BluetoothDevice device) {

            BluetoothSocket tmp = null;
            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(direccionSerieDefecto);

            } catch (IOException e) {
                Log.i("Problema creación del socket", e.getMessage());
            }
            mSocket = tmp;
        }

        public void run() {

            try {

                //Se conecta el dispositivo a través del socket.
                mSocket.connect();
                Message msg = new Message();
                msg.obj = getString(R.string.conexionExitosa);
                handlerToast.sendMessage(msg);

            } catch (IOException connectException) {
                Log.i("Problemas en la conexión a través del socket.", connectException.getMessage());

                Message msg = new Message();
                msg.obj = getString(R.string.errorConexionBT);
                handlerToast.sendMessage(msg);
                try {
                    mSocket.close();
                } catch (IOException closeException) {
                    cancel();
                }
                return;
            }

            //Para la gestión de la conexión se utilia otro hilo.
            mConnectedThred = new ConnectedThread(mSocket);
            mConnectedThred.start();

        }


        //Cerrar socket.
        public void cancel() {
            try {
                mSocket.close();
                mSocket = null;
            } catch (IOException e) {
                Log.i("Error desconexion", e.getLocalizedMessage());
            }
        }
    }


    private class ConnectedThread extends Thread {

        private final BluetoothSocket mSocket;            //El socket abierto por el hilo padre.
        private final InputStream mInputStream;            //Stream de recepción de datos que estará asociado al socket.
        private boolean llaveBT = true;

        public ConnectedThread(BluetoothSocket socket) {
            mSocket = socket;
            InputStream tmpIn = null;

            try {
                tmpIn = socket.getInputStream();

            } catch (IOException e) {
                Log.i("Socket temporal no se ha podido crear: ", e.getMessage());

                Message msg = new Message();
                msg.obj = getString(R.string.errorConexionBT);
                handlerToast.sendMessage(msg);
            }

            mInputStream = tmpIn;

            Message msg = new Message();
            msg.obj = getString(R.string.resetearDispositivo);
            handlerToast.sendMessage(msg);
        }

        public void run() {
            Log.i("Inicio datos.", "Iniciada la recepción de datos");
            //Permance a la escucha de datos.
            while (llaveBT) {
                read();
            }
        }


        public void read() {

            BufferedReader bufferedReader = null;
            String lineaLeida;

            //Se construye un BufferedReader para facilitar la lectura de los datos en InputStream
            bufferedReader = new BufferedReader(new InputStreamReader(mInputStream));


            try {
                //Se recorre el BufferedReader, en cada salto de línea
                while ((lineaLeida = bufferedReader.readLine()) != null) {
                    Message msg = new Message();
                    msg.obj = lineaLeida;
                    handlerBT.sendMessage(msg);
                }
            } catch (IOException e) {
                cancel();
                e.printStackTrace();
            }
        }


        //Se cierra la conexión del socket.
        public void cancel() {
            llaveBT = false;
            try {
                mInputStream.close();
                mSocket.close();


            } catch (IOException e) {
            }
        }
    }
    /******************************Bluetooth*************************************/

    /**********************************************************************************************/
    private final Runnable realizarUnaMedida = new Runnable() {

        @Override
        public void run() {
            if (!grabarDatos && midiendo) {                            // mientras graba datos de última medida no realiza más medidas

                int potenciaCorregida;

                if (medidaSonda) {
                    //recorder.read(new short[bufferSize], 0, Constants.LONGITUD_TONO_GRABADO);		// vacía el buffer de grabación del micro
                    double[] muestras = guardarMuestras();                            // lee las muestras del canal de grabación
                    for (int i = 0; i < Constants.LONGITUD_TONO_GRABADO; i++)        // prescinde de los términos nulos antes de guardarlos
                        muestras_grabar[i] = muestras[2 * i];
                    muestras = compensarCAG(muestras);                                // compensa el efecto del CAG sobre las muestras leídas
                    DEP_Y_grabar = calcularDEP_Y(muestras);                            // calcula la densidad espectral de frecuencia de la señal leída
                    DEP_X_grabar = calcularDEP_X(DEP_Y_grabar);                        // calcula la densidad espectral de frecuencia de la señal antes del filtro
                    final double potencia = calcularPotenciaX(DEP_X_grabar);        // calcula la densidad espectral de frecuencia de la señal antes del filtro

                    potenciaCorregida = (int) (potencia / datosCalibrado.coeficienteAjuste / 10 + 0.5) * 10;
                } else {
                    potenciaCorregida = (int) Float.parseFloat((!(potenciaBluetooth == null || (potenciaBluetooth.equals("")))) ? potenciaBluetooth : "0.0"); // coger la última medida del bluetooth (en W)
                    if (potenciaCorregida < 0)
                        potenciaCorregida = 0;
                }

                double tiempoActual = GregorianCalendar.getInstance().getTimeInMillis() / 1000;

                if (empezar) {
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

                            findViewById(R.id.co2).setVisibility(View.VISIBLE);
                            findViewById(R.id.co2libre).setVisibility(View.VISIBLE);
                            findViewById(R.id.co2pvpc).setVisibility(View.VISIBLE);


                            // crea el gráfico con los datos de las medidas
                            graficoMedidas = (GraphView) findViewById(R.id.grafica);
                            graficoMedidas.removeAllSeries();
                            graficoMedidas.clearAnimation();

                            serieMedidas.setColor(Color.BLACK);
                            graficoMedidas.setTitleColor(Color.BLACK);
                            graficoMedidas.setTitle(getString(R.string.medidasDePotencia));

                            graficoMedidas.getGridLabelRenderer().setGridColor(Color.BLACK);
                            graficoMedidas.getGridLabelRenderer().setHorizontalLabelsColor(Color.BLACK);
                            graficoMedidas.getGridLabelRenderer().setVerticalLabelsColor(Color.BLACK);

                            serieMedidas.setDrawBackground(true);
                            serieMedidas.setBackgroundColor(Color.BLUE);
                            graficoMedidas.getViewport().setYAxisBoundsManual(true);
                            graficoMedidas.addSeries(serieMedidas);                // data
                            graficoMedidas.setVisibility(View.VISIBLE);        // hace visible el gráfico

                            if (periodicidadMedidaEnSegundos == 1)
                                menu.findItem(R.id.tiempo).setIcon(null).setTitle("2 seg");        // pone el icono si no está puesto

                            empezar = false;
                            pantallaInicializada = true;
                            synchronized (llavePantallaInicializada) {
                                llavePantallaInicializada.notifyAll();
                            }
                        }
                    });
                }
                ;

                synchronized (llavePantallaInicializada) {
                    while (!pantallaInicializada) {
                        try {
                            llavePantallaInicializada.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }

                // se añade la medida a la serie
                serieMedidas.appendData(
                        new DataPoint(tiempoActual - tiempoInicioMedida, potenciaCorregida),
                        true,
                        Constants.NUMERO_MAXIMO_MEDIDAS_EN_GRAFICO
                );


                // acondiciona los ejes verticales, si necesario, para que las marcas verticales coincidan con las potencias normalizadas de las tarifas
                String[] escalasPotencia = new String[]{"0", "1,15", "2,30", "3,45", "4,60", "5,75", "6,90", "8,05", "9,20"};
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

                final int potenciaCorregidaAuxiliar = potenciaCorregida;

                if (visible) {
                    // muestra en pantalla el dato
                    // y actualiza los ejes del gráfico
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            final TextView textViewMedida = (TextView) findViewById(R.id.texto1);
                            textViewMedida.setText(Integer.toString(potenciaCorregidaAuxiliar) + " W");
                            graficoMedidas.onDataChanged(true, false);
                        }
                    });
                }
            }
            if (!isFinishing() && midiendo)
                handler.postDelayed(this, periodicidadMedidaEnSegundos * 1000);
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
        public void onCallStateChanged(int state, String incomingNumber) {
            if (state == TelephonyManager.CALL_STATE_RINGING) {
                midiendo = false;
                menu.findItem(R.id.grabacion).setIcon(R.drawable.ic_parar);
                handler.removeCallbacks(realizarUnaMedida);        // elimina la última llamada a realizar nueva medida
            }
            super.onCallStateChanged(state, incomingNumber);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("neurGAI", "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.neurgai);

        // configura actionBar
        ActionBar bar = getSupportActionBar();
        bar.setDisplayShowHomeEnabled(true);
        bar.setTitle(R.string.app_name);
        bar.setLogo(R.drawable.ic_launcher);
        bar.setDisplayShowTitleEnabled(true);
        bar.setDisplayUseLogoEnabled(true);

        cargarIdioma();

        //Hay que comentarlas después de la primera instalación.

        //BaseDatosNeurGAI bbdd = new BaseDatosNeurGAI(this);
        //SQLiteDatabase dbWrite=bbdd.getWritableDatabase();
        //dbWrite.deleteDatabase(new File(dbWrite.getPath()));

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
    protected void onResume() {

        Log.i("neurGAI", "onResume");
        visible = true;
        super.onResume();
    }

    @Override
    protected void onPause() {

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
        handler.removeCallbacks(realizarUnaMedida);        // elimina la última llamada a realizar nueva medida
        if (audioTrack != null) {
            if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) audioTrack.pause();
            audioTrack.release();
        }

        // para la grabación de sonido si en marcha
        if (recorder != null) {
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) recorder.stop();
            recorder.release();
        }

        //Salva la tabla de costes y eliminar los valores guardados en la tabla Costes de la BDD.
        volcarBBDDFichero_LimpiarBBBDD();

        // desregistra el listener del teléfono
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        if (mConnectThread != null) {
            mConnectThread.cancel();
        }

        super.onDestroy();
    }

    private void actividadNeurgai() {

        // crea los subdirectorios si no existen
        new File(Constants.pathNeurgai).mkdir();
        new File(Constants.pathFicheroCalibracion).mkdir();
        new File(Constants.pathFicherosComplementarios).mkdir();
        new File(Constants.pathFicherosRegistros).mkdir();

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

        // trata de recuperar los datos de calibración, y si no existen comienza el proceso de calibrado
        // comprueba si existe el fichero de calibración
        if (false == new File(Constants.pathFicheroCalibracion + "/calibracion.bin").exists()) {

            mProgress = (ProgressBar) findViewById(R.id.progressBar);

            // el control de volumen es el de MEDIA
            setVolumeControlStream(AudioManager.STREAM_MUSIC);

            //no existe, así que iniciamos el calibrado
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mProgress.setVisibility(View.VISIBLE);    //hace visible la barra de progreso, solo utilizada aquí
                    boton.setVisibility(View.VISIBLE);        //hace visible el botón
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
                    if (AutomaticGainControl.isAvailable()) {
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
                        } catch (InterruptedException e) {
                        }
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
                    amplitudes[n] = (short) (amplitudInicial * Math.exp(n * Math.log(amplitudFinal / amplitudInicial) / (Constants.numeroAmplitudes - 1)));
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
                                    (double) Constants.RECORDER_SAMPLERATE    //duración del tono
                                    , FRECUENCIA                            //frecuencia del tono
                                    , A0);                                    //amplitud del tono

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
                            } catch (InterruptedException e) {
                            }
                        }
                    }

                    muestrasGrabadas = seleccionarMuestrasGrabacion(Constants.DATOS_INICIALES_NO_VALIDOS, Constants.DATOS_VALIDOS);

                    //calcula la potencia de la muestra grabada
                    double p = 0;
                    for (short m : muestrasGrabadas) {
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
                recorder.stop();    // cierra la grabación
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

                        graficoCAG.addSeries(serieCalibradoCAG);        // data

                        //RelativeLayout grafico = (RelativeLayout) findViewById(R.id.grafica);
                        //grafico.addView(graficoCAG);
                        graficoCAG.setVisibility(View.VISIBLE);        //hace visible el gráfico
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
                                } catch (InterruptedException e) {
                                }
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
                                } catch (InterruptedException e) {
                                }
                            }
                        }
                    } catch (NumberFormatException e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showMessage(getString(R.string.error), getString(R.string.introducirAmplitudCorrectamente));
                            }
                        });
                    }
                }
                ;

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
                    frecuencias[n] = frecuenciaInicial * Math.exp(n * Math.log(frecuenciaFinal / frecuenciaInicial) / (Constants.numeroFrecuencias - 1));
                }

                //borra el gráfico del CAG
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        GraphView graficoCAG = (GraphView) findViewById(R.id.grafica);
                        graficoCAG.removeAllSeries();
                        graficoCAG.clearAnimation();
                        graficoCAG.setVisibility(View.GONE);        //hace invisible el gráfico
                    }
                });

                recorder.startRecording();
                recorder.read(new short[bufferSize], 0, Constants.RECORDER_SAMPLERATE);
                recorder.stop();

                //realiza el barrido de frecuencias
                recorder.startRecording();        // abre el canal de grabación
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
                                    (double) Constants.RECORDER_SAMPLERATE    //duración del tono
                                    , FRECUENCIA                            //frecuencia del tono
                                    , A0);                                    //amplitud del tono

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
                            } catch (InterruptedException e) {
                            }
                        }
                    }

                    muestrasGrabadas = seleccionarMuestrasGrabacion(
                            Constants.DATOS_INICIALES_NO_VALIDOS, Constants.DATOS_VALIDOS);        // lee los datos del micrófono y los graba

                    //calcula la potencia de la muestra grabada
                    double p = 0;
                    for (short m : muestrasGrabadas) {
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

                        graficoFiltro.addSeries(serieCalibradoFiltro);        // data

                        //RelativeLayout grafico = (RelativeLayout) findViewById(R.id.grafica);
                        //grafico.addView(graficoFiltro);
                        graficoFiltro.setVisibility(View.VISIBLE);        //hace visible el gráfico
                    }
                });

                // graba los datos en los ficheros correspondientes
                File fCalBIN = new File(Constants.pathFicheroCalibracion + "/calibracion.csv");

                // definición de ficheros y path
                FileOutputStream osCalibCSV = null;
                FileOutputStream osCalibBIN = null;
                DataOutputStream dosBIN = null;

                // crea los ficheros y define los streams de salida
                try {
                    osCalibCSV = new FileOutputStream(fCalBIN, false);        // borra el fichero si existe
                    osCalibBIN = new FileOutputStream(Constants.pathFicheroCalibracion + "/calibracion.bin", false);

                    String cabecera = getString(R.string.cabeceraFicheroCalibracion);
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
                } catch (Exception e) {
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
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }

            // ha acabado el calibrado, borra barra de progreso y el gráfico del calibrado
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mProgress.setVisibility(View.GONE);                            //hace invisible la barra de progreso
                    GraphView graficoFiltro = (GraphView) findViewById(R.id.grafica);
                    graficoFiltro.removeAllSeries();
                    graficoFiltro.clearAnimation();
                    graficoFiltro.setVisibility(View.GONE);        //hace invisible el gráfico
                }
            });
        }

        // lee el fichero de calibración
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boton.setVisibility(View.VISIBLE);        //hace visible el botón
                boton.setText(R.string.leyendoCalibracion);
            }
        });

        try {
            FileInputStream fis = new FileInputStream(Constants.pathFicheroCalibracion + "/calibracion.bin");
            DataInputStream dis = new DataInputStream(fis);

            // primero estan guardados numeroAmplitudes para la calibración del CAG
            // según la variación de la amplitud a frecuencia constante
            for (int i = 0; i < Constants.numeroAmplitudes; i++) {
                datosCalibrado.amplitudes[i] = dis.readInt();
                dis.readDouble();    // se prescinde de la frecuencia, que es cte
                datosCalibrado.RMSajusteCAG[i] = dis.readDouble();
                datosCalibrado.ajusteCAG[i] = datosCalibrado.RMSajusteCAG[i] / datosCalibrado.amplitudes[i]; // calcula el vector para el ajuste de CAG
            }

            // y después están guardados numeroFrecuencias para la calibración
            // según la frecuencia a amplitud constante

            // calcula la función para interpolar el ajuste de CAG
            UnivariateInterpolator interpolator = new LinearInterpolator();
            UnivariateFunction funcionCAG = null;
            funcionCAG = interpolator.interpolate(datosCalibrado.RMSajusteCAG, datosCalibrado.ajusteCAG);

            for (int i = 0; i < Constants.numeroFrecuencias; i++) {
                dis.readInt();        // se prescinde de la amplitud, que es cte
                datosCalibrado.frecuencias[i] = dis.readDouble();
                datosCalibrado.RMS[i] = dis.readDouble();

                // corrige el efecto del CAG
                if (datosCalibrado.RMS[i] <= datosCalibrado.RMSajusteCAG[0]) {
                    datosCalibrado.RMS[i] = datosCalibrado.RMS[i] * datosCalibrado.ajusteCAG[0];
                } else {
                    if (datosCalibrado.RMS[i] > datosCalibrado.RMSajusteCAG[Constants.numeroAmplitudes - 1])
                        datosCalibrado.RMS[i] = datosCalibrado.RMS[i] * datosCalibrado.ajusteCAG[Constants.numeroAmplitudes - 1];
                    else
                        datosCalibrado.RMS[i] = datosCalibrado.RMS[i] * funcionCAG.value(datosCalibrado.RMS[i]);
                }
            }

            // normaliza la calibración del filtro con respecto al valor de RMS a 50 Hz
            double RMS_50Hz = datosCalibrado.RMS[4];
            for (int i = 0; i < Constants.numeroFrecuencias; i++) {
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
            FileInputStream fis;
            try {
                fis = new FileInputStream(ficheroCoeficiente);
                StringBuffer fileContent = new StringBuffer("");
                byte[] buffer = new byte[1024];
                int n;
                while ((n = fis.read(buffer)) != -1) {
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

            final TextView textViewMedida = (TextView) findViewById(R.id.texto1);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //boton.setVisibility(View.VISIBLE); 					//hace visible el botón
                    textViewMedida.setVisibility(View.VISIBLE);        //hace visible una ventana de texto, para mostrar el dato
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
                        textViewMedida.setText(Double.toString(potencia) + " / " + Double.toString(medidaRMS));
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
                        } catch (InterruptedException e) {
                        }
                    }
                }
            } catch (NumberFormatException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showMessage(getString(R.string.error), getString(R.string.introducirMedidaCorrectamente));
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
        for (int n = 0; n < Constants.RECORDER_SAMPLERATE; n++) {
            m[n] = (short) (A0 * Math.cos(fase));
            fase += 2 * Math.PI * (fr / fs);
        }
        return (m);
    }

    private short[] seleccionarMuestrasGrabacion(int DATOS_INICIALES_NO_VALIDOS, int DATOS_VALIDOS) {
        // DATOS_INICIALES_NO_VALIDOS = datos iniciales de cada pulsación que se desprecian por no tener todavía el pulso estabilizado
        // DATOS_VALIDOS = LONGITUD_TONO_GRABADO - DATOS_INICIALES_NO_VALIDOS;
        final int DATOS_TOTALES = DATOS_VALIDOS + DATOS_INICIALES_NO_VALIDOS;

        short[] muestrasValidas = new short[DATOS_VALIDOS];
        short[] datosBuffer = new short[bufferSize];
        short[] datosTotal = new short[Constants.LONGITUD_TONO_GRABADO * 2];
        int datosLeidosBuffer = 0;
        int datosLeidosTotales = 0;
        while (datosLeidosTotales < (DATOS_TOTALES)) { // sigue leyendo del buffer hasta tener DATOS_TOTALES
            datosLeidosBuffer = recorder.read(datosBuffer, 0, DATOS_TOTALES);
            if (datosLeidosBuffer > 0) {
                datosLeidosTotales += datosLeidosBuffer;
                System.arraycopy(datosBuffer                        //src -- This is the source array
                        , 0                                            //srcPos -- This is the starting position in the source array
                        , datosTotal                                //dest -- This is the destination array
                        , datosLeidosTotales - datosLeidosBuffer    //destPos -- This is the starting position in the destination data
                        , datosLeidosBuffer);                        //length -- This is the number of array elements to be copied
            }
        }

        // selecciona solo los datos válidos y los guarda
        System.arraycopy(datosTotal                            //src -- This is the source array
                , DATOS_INICIALES_NO_VALIDOS                //srcPos -- This is the starting position in the source array
                , muestrasValidas                            //dest -- This is the destination array
                , 0                                            //destPos -- This is the starting position in the destination data
                , DATOS_VALIDOS);                            //length -- This is the number of array elements to be copied
        return (muestrasValidas);
    }

    private double[] guardarMuestras() {

        double[] muestras = new double[Constants.LONGITUD_TONO_GRABADO * 2];

        int datosTotales = 0;
        int datosLeidos = 0;
        short[] sTotal = new short[Constants.LONGITUD_TONO_GRABADO * 2];

        datosLeidos = 0;
        while (datosTotales < Constants.LONGITUD_TONO_GRABADO) {
            datosLeidos = recorder.read(datosBuffer, 0, Constants.LONGITUD_TONO_GRABADO);
            datosTotales += datosLeidos;
            System.arraycopy(datosBuffer, 0, sTotal, datosTotales - datosLeidos, datosLeidos);
        }

        for (int i = 0; i < Constants.LONGITUD_TONO_GRABADO; i++) {
            muestras[i * 2] = sTotal[i];
            muestras[i * 2 + 1] = 0;
        }
        return muestras;
    }

    private double[] compensarCAG(double[] muestras) {

        // calcula RMS de la muestra grabada
        medidaRMS = 0;
        double energiaMedida = 0;
        for (int i = 0; i < Constants.LONGITUD_TONO_GRABADO; i++) {
            energiaMedida += muestras[i * 2] * muestras[i * 2];
        }
        medidaRMS = Math.sqrt(energiaMedida / Constants.LONGITUD_TONO_GRABADO);

        // calcula la función para interpolar el ajuste de CAG
        UnivariateInterpolator interpolator = new LinearInterpolator();
        UnivariateFunction function = null;
        function = interpolator.interpolate(datosCalibrado.RMSajusteCAG, datosCalibrado.ajusteCAG);
        double coeficienteAjusteCAG = datosCalibrado.ajusteCAG[0];
        if (medidaRMS > datosCalibrado.RMSajusteCAG[0]) {
            if (medidaRMS > datosCalibrado.RMSajusteCAG[Constants.numeroAmplitudes - 1])
                coeficienteAjusteCAG = datosCalibrado.ajusteCAG[Constants.numeroAmplitudes - 1];
            else coeficienteAjusteCAG = function.value(medidaRMS);
        }

        // multiplica el valor de las muestras por el coeficiente de ajuste CAG
        for (int i = 0; i < Constants.LONGITUD_TONO_GRABADO; i++) {
            muestras[i * 2] = muestras[i * 2] / coeficienteAjusteCAG;
        }
        return muestras;
    }

    private double[] calcularDEP_Y(double[] muestras) {

        double[] dep_Y = new double[Constants.numeroFrecuenciasInterp];
        double[] modFFT = new double[Constants.LONGITUD_TONO_GRABADO];
        DoubleFFT_1D fft = new DoubleFFT_1D(Constants.LONGITUD_TONO_GRABADO);
        //FastFourierTransformer fft=new FastFourierTransformer(DftNormalization.STANDARD);
        //Complex[] tftMuestras;

        fft.complexForward(muestras);
        //tftMuestras=fft.transform(muestras, TransformType.FORWARD);


        for (int i = 0; i < Constants.LONGITUD_TONO_GRABADO; i++) {
            //No se incluye la raíz cuadrada porque necesitamos el módulo al cuadrado.
            modFFT[i] = (muestras[2 * i] * muestras[2 * i] + muestras[2 * i + 1] * muestras[2 * i + 1]);

            //modFFT[i]=Math.pow(tftMuestras[i].abs(), 2);
        }

        //toma las componentes desde frecuenciaInterpInicial hasta frecuenciaInterpFinal
        for (int frecuencia = Constants.frecuenciaInterpInicial; frecuencia <= Constants.frecuenciaInterpFinal; frecuencia++) {
            int i = frecuencia - Constants.frecuenciaInterpInicial;
            dep_Y[i] = modFFT[frecuencia];
        }
        return dep_Y;
    }

    private double[] calcularDEP_X(double[] dep_y) {

        double[] dep_x = new double[Constants.numeroFrecuenciasInterp];

        for (int i = 0; i < Constants.numeroFrecuenciasInterp; i++) {
            dep_x[i] = dep_y[i] / DEP_H[i];
        }
        return (dep_x);
    }

    //Calcula la potencia de la señal original, X(w).
    private double calcularPotenciaX(double[] dep_x) {

        double energia = 0;

        for (int i = 0; i < Constants.numeroFrecuenciasInterp; i++) {
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


    @SuppressLint("InflateParams")
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
            case R.id.tarifas: {
                handler.removeCallbacks(realizarUnaMedida);                    // elimina la última llamada a realizar nueva medida

                AlertDialog.Builder alert = new AlertDialog.Builder(this);

                alert.setTitle(R.string.tituloConfigTarifasLibres);

                LayoutInflater factory = LayoutInflater.from(this);
                View layout = factory.inflate(R.layout.row, null);

                final EditText feu20A = (EditText) layout.findViewById(R.id.editText1);
                final EditText feu20DHAPunta = (EditText) layout.findViewById(R.id.editText2);
                final EditText feu20DHAValle = (EditText) layout.findViewById(R.id.editText3);

                final EditText feu20DHSPunta = (EditText) layout.findViewById(R.id.editText4);
                final EditText feu20DHSLlano = (EditText) layout.findViewById(R.id.editText5);
                final EditText feu20DHSSuperValle = (EditText) layout.findViewById(R.id.editText6);

                final EditText emisionesPVPC = (EditText) layout.findViewById(R.id.editText7);
                final EditText emisionesLibre = (EditText) layout.findViewById(R.id.editText8);

                alert.setView(layout);

                alert.setPositiveButton(R.string.aceptar, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        double feu20ap, feu20dhap, feu20dhav, feu20dhsp, feu20dhsll, feu20dhsv, emispvpc, emislibre;

                        //Validacion de los datos.
                        if (feu20A != null && !feu20A.getText().toString().isEmpty()) {
                            feu20ap = Double.parseDouble(feu20A.getText().toString());
                        } else {
                            feu20ap = (Double.parseDouble(getString(R.string.valor2_0Apunta)));
                        }
                        if (feu20DHAPunta != null && !feu20DHAPunta.getText().toString().isEmpty()) {
                            feu20dhap = Double.parseDouble(feu20DHAPunta.getText().toString());
                        } else {
                            feu20dhap = (Double.parseDouble(getString(R.string.valor2_0DHApunta)));
                        }
                        if (feu20DHAValle != null && !feu20DHAValle.getText().toString().isEmpty()) {
                            feu20dhav = Double.parseDouble(feu20DHAValle.getText().toString());
                        } else {
                            feu20dhav = (Double.parseDouble(getString(R.string.valor2_0DHAvalle)));
                        }
                        if (feu20DHSPunta != null && !feu20DHSPunta.getText().toString().isEmpty()) {
                            feu20dhsp = Double.parseDouble(feu20DHSPunta.getText().toString());
                        } else {
                            feu20dhsp = (Double.parseDouble(getString(R.string.valor2_0DHSpunta)));
                        }
                        if (feu20DHSLlano != null && !feu20DHSLlano.getText().toString().isEmpty()) {
                            feu20dhsll = Double.parseDouble(feu20DHSLlano.getText().toString());
                        } else {
                            feu20dhsll = (Double.parseDouble(getString(R.string.valor2_0DHSllano)));
                        }
                        if (feu20DHSSuperValle != null && !feu20DHSSuperValle.getText().toString().isEmpty()) {
                            feu20dhsv = Double.parseDouble(feu20DHSSuperValle.getText().toString());
                        } else {
                            feu20dhsv = (Double.parseDouble(getString(R.string.valor2_0DHSsvalle)));
                        }
                        if (emisionesLibre != null && !emisionesLibre.getText().toString().isEmpty()) {
                            emislibre = Double.parseDouble(emisionesLibre.getText().toString());
                        } else {
                            emislibre = (Double.parseDouble(getString(R.string.cantidadEmisionLibre)));
                        }
                        if (emisionesPVPC != null && !emisionesPVPC.getText().toString().isEmpty()) {
                            emispvpc = Double.parseDouble(emisionesPVPC.getText().toString());
                        } else {
                            emispvpc = (Double.parseDouble(getString(R.string.cantidadEmisionPVPC)));
                        }

                        cambiarTarifaLibre(getString(R.string.nombreT20Apunta), feu20ap);
                        cambiarTarifaLibre(getString(R.string.nombreT20DHApunta), feu20dhap);
                        cambiarTarifaLibre(getString(R.string.nombreT20DHAvalle), feu20dhav);
                        cambiarTarifaLibre(getString(R.string.nombreT20DHSpunta), feu20dhsp);
                        cambiarTarifaLibre(getString(R.string.nombreT20DHSllano), feu20dhsll);
                        cambiarTarifaLibre(getString(R.string.nombreT20DHSsupervalle), feu20dhsv);

                        cambiarEmisionesCO2(R.string.nombreEmisionPVPC, emispvpc);
                        cambiarEmisionesCO2(R.string.nombreEmisionLibre, emislibre);

                        //Se vuelcan las tarifas definidas por el usuario a la BBDD.
                        //volcadoTarifasLibreUsuario(feu20, feu20dhap, feu20dhav, feu20dhsp, feu20dhsv, feu20dhssv, emispvpc, emislibre);
                    }

                });

                alert.setNegativeButton(R.string.cancelar, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        return;
                    }
                });

                alert.show();
                handler.postDelayed(realizarUnaMedida, periodicidadMedidaEnSegundos);

                return true;

            }
            case R.id.sonda:
                medidaSonda = true;
                menu.findItem(R.id.sonda_bluetooth).setIcon(R.drawable.ic_sondanegro);
                return true;

            case R.id.bluetooth:
                medidaSonda = false;
                menu.findItem(R.id.sonda_bluetooth).setIcon(R.drawable.ic_bluetoothnegro);
                Intent intent = new Intent(getApplicationContext(), BluetoothDialog.class);
                startActivityForResult(intent, requestCode);

                return true;

            case R.id.s1:
                periodicidadMedidaEnSegundos = 1;
                menu.findItem(R.id.tiempo).setIcon(null).setTitle("2 seg");

                handler.removeCallbacks(realizarUnaMedida);                    // elimina la última llamada a realizar nueva medida
                handler.postDelayed(realizarUnaMedida, periodicidadMedidaEnSegundos);

                return true;

            case R.id.s10:
                periodicidadMedidaEnSegundos = 10 - 1;
                menu.findItem(R.id.tiempo).setIcon(null).setTitle("10 seg");

                handler.removeCallbacks(realizarUnaMedida);        // elimina la última llamada a realizar nueva medida
                handler.postDelayed(realizarUnaMedida, periodicidadMedidaEnSegundos);

                return true;

            case R.id.s30:
                periodicidadMedidaEnSegundos = 30 - 1;
                menu.findItem(R.id.tiempo).setIcon(null).setTitle("30 seg");

                handler.removeCallbacks(realizarUnaMedida);                    // elimina la última llamada a realizar nueva medida
                handler.postDelayed(realizarUnaMedida, periodicidadMedidaEnSegundos);

                return true;

            case R.id.m1:
                periodicidadMedidaEnSegundos = 60 - 1;
                menu.findItem(R.id.tiempo).setIcon(null).setTitle("1 min");

                handler.removeCallbacks(realizarUnaMedida);                    // elimina la última llamada a realizar nueva medida
                handler.postDelayed(realizarUnaMedida, periodicidadMedidaEnSegundos);

                return true;

            case R.id.m5:
                periodicidadMedidaEnSegundos = 300 - 1;
                menu.findItem(R.id.tiempo).setIcon(null).setTitle("5 min");

                handler.removeCallbacks(realizarUnaMedida);                    // elimina la última llamada a realizar nueva medida
                handler.postDelayed(realizarUnaMedida, periodicidadMedidaEnSegundos);

                return true;

            case R.id.m10:
                periodicidadMedidaEnSegundos = 600 - 1;
                menu.findItem(R.id.tiempo).setIcon(null).setTitle("10 min");

                handler.removeCallbacks(realizarUnaMedida);                    // elimina la última llamada a realizar nueva medida
                handler.postDelayed(realizarUnaMedida, periodicidadMedidaEnSegundos);

                return true;

            case R.id.continuar:
                midiendo = true;
                menu.findItem(R.id.grabacion).setIcon(R.drawable.ic_continuar);
                handler.postDelayed(realizarUnaMedida, 0);

                return true;

            case R.id.parar:
                midiendo = false;
                menu.findItem(R.id.grabacion).setIcon(R.drawable.ic_parar);
                handler.removeCallbacks(realizarUnaMedida);                    // elimina la última llamada a realizar nueva medida

                return true;

            case R.id.grabaryreiniciar:
                handler.removeCallbacks(realizarUnaMedida);                    // elimina la última llamada a realizar nueva medida
                empezar = true;
                midiendo = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        findViewById(R.id.grafica).setVisibility(View.GONE);        // hace invisible el gráfico y datos
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

                        findViewById(R.id.co2).setVisibility(View.GONE);
                        findViewById(R.id.co2libre).setVisibility(View.GONE);
                        findViewById(R.id.co2pvpc).setVisibility(View.GONE);

                        findViewById(R.id.logoEHU).setVisibility(View.VISIBLE);        // habilita el logo EHU
                    }
                });
                volcarBBDDFichero_LimpiarBBBDD();                                    // Salva la tabla de costes de la BBDD y eliminar los registros.
                menu.findItem(R.id.grabacion).setIcon(R.drawable.ic_empezar);

                return true;

            case R.id.anotar:

                handler.removeCallbacks(realizarUnaMedida);                    // elimina la última llamada a realizar nueva medida
                //Lanza diálogo para recoger comentario.
                //Guardar comentario en la base de datos.
                //
                //Incluir anotar=true; en la condición de Aceptar del diálogo.
                //Guardarlo una vez en la bbdd.

                AlertDialog.Builder alerta = new AlertDialog.Builder(this);
                alerta.setTitle(getString(R.string.tituloAnotar));
                final EditText input = new EditText(this);
                alerta.setView(input);

                alerta.setPositiveButton(R.string.aceptar, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Editable value = input.getText();
                        comentario = value.toString();
                        anotar = true;
                    }
                });

                alerta.setNegativeButton(R.string.cancelar, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        return;
                    }
                });

                alerta.show();
                handler.postDelayed(realizarUnaMedida, 0);
                return true;

            case R.id.grabarDatos:
                handler.removeCallbacks(realizarUnaMedida);                    // elimina la última llamada a realizar nueva medida
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
                        guardarArrays(Constants.pathFicherosComplementarios + "/" + Long.toString(tiempo.toMillis(false)) + "Muestras.csv", "n," + getString(R.string.muestras), muestras_grabar);
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

                handler.postDelayed(realizarUnaMedida, periodicidadMedidaEnSegundos);
                return true;

            case R.id.castellano:

                //Guarda la configuración
                if (cambiarIdioma(Constants.castellanoCode)) {
                    Toast.makeText(this, "Idioma Castellano", Toast.LENGTH_LONG).show();
                }
                supportInvalidateOptionsMenu();

                return true;

            case R.id.euskera:
                //Guarda la configuración
                if (cambiarIdioma(Constants.euskeraCode)) {
                    Toast.makeText(this, "Euskaraz aukeratuta", Toast.LENGTH_LONG).show();
                }
                supportInvalidateOptionsMenu();

                return true;
            case R.id.arabe:
                //Guarda la configuración
                if (cambiarIdioma(Constants.arabeCode)) {
                    Toast.makeText(this, "اللغة العربية", Toast.LENGTH_LONG).show();
                }
                supportInvalidateOptionsMenu();

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
        FileOutputStream os;

        try {
            os = new FileOutputStream(path, false);
            cabecera = cabecera + "\n";
            os.write(cabecera.getBytes(Charset.forName(Constants.formatoFichero)));

            for (int i = 0; i < arrayDatos.length; i++) {
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
                .setTitle(getString(R.string.amplitudCalibrado))
                .setView(viewT)
                .setPositiveButton(R.string.aceptar, new DialogInterface.OnClickListener() {
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

    //Gurda las tarifas que el usuario intoduce. Sustituyen las tarifas por defecto.
    private void cambiarTarifaLibre(String nombreTarifa, double valor) {

        SharedPreferences prefs =
                getSharedPreferences("tarifasLibres", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(nombreTarifa, Double.toString(valor));

        editor.commit();
    }

    //Devuelve la tarifa libre que el usuario tenga guardada en su configuración. Sino hay ninguna, devuelve una tarifa por defecto.
    private double getTarifaLibre(int nombreTarifa, int valorDefecto) {
        SharedPreferences prefs =
                getSharedPreferences("tarifasLibres", Context.MODE_PRIVATE);
        String tarifa = prefs.getString(getString(nombreTarifa), getString(valorDefecto));

        return Double.parseDouble(tarifa);
    }

    //Devuelve la emisión de CO2 que el usuario tenga guardada sino tuviera, devuelve un valor por defecto.
    private double getEmisionesCO2(int nombreEmision, int valorDefecto) {
        SharedPreferences prefs =
                getSharedPreferences("emisionesCO2", Context.MODE_PRIVATE);
        String emision = prefs.getString(getString(nombreEmision), getString(valorDefecto));
        return Double.parseDouble(emision);
    }

    //Modifica el valor por defecto.
    private boolean cambiarEmisionesCO2(int nombreEmision, double valor) {

        SharedPreferences prefs =
                getSharedPreferences("emisionesCO2", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(getString(nombreEmision), Double.toString(valor));

        return editor.commit();
    }

    private boolean cambiarIdioma(String idiomaCod) {

        //Fuerza la configuración del idioma.
        Locale locale = new Locale(idiomaCod);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());

        SharedPreferences prefs =
                getSharedPreferences("idiomaApp", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("idioma", idiomaCod);

        return editor.commit();
    }

    private void cargarIdioma() {
        SharedPreferences prefs =
                getSharedPreferences("idiomaApp", Context.MODE_PRIVATE);
        String idiomaCod = prefs.getString("idioma", Constants.castellanoCode);

        Locale locale = new Locale(idiomaCod);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());

    }

    @SuppressLint("InflateParams")
    public void preguntarMedidaReferencia() throws NumberFormatException {

        LayoutInflater inflater = LayoutInflater.from(this);
        View viewT = inflater.inflate(R.layout.medidareferencia, null);

        final EditText ventanaDato = (EditText) viewT.findViewById(R.id.ventanaDato);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.medidaReferencia))
                .setView(viewT)
                .setPositiveButton(R.string.aceptar, new DialogInterface.OnClickListener() {
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
