package eus.ehu.neurgai;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;



public class BaseDatosNeurGAI extends SQLiteOpenHelper {
	//private static final String TEXT_TYPE = " TEXT";
    private static final String REAL_TYPE=" REAL";
    private static final String INT_TYPE=" INTEGER";
    private static final String TEXT_TYPE=" TEXT";
    private static final String COMMA_SEP = ", ";
    private static final String DATE=" DATE";
    
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "neurgai.db";
    
	
    public static abstract class ColumnasTarifas implements BaseColumns{

    	public static final String TABLE_NAME = "tarifas";
		 
	    //Hora y fecha de la tarifa.
	    public static final String COLUMN_NAME_HORA="hora";
	    public static final String COLUMN_NAME_FECHA="fecha";
	    public static final String COLUMN_NAME_EMISIONES_PVPC="emisionesPVPC";
	    public static final String COLUMN_NAME_EMISIONES_LIBRE="emisionesLibre";
	    
	    //Tarifas de Goiener.
	    public static final String COLUMN_NAME_TARIFA_20A = "tarifa20A";
	    public static final String COLUMN_NAME_TARIFA_20DHA = "tarifa20DHA";
	    public static final String COLUMN_NAME_TARIFA_20DHS = "tarifa20DHS";
	    	
	    //Tarifas oficiales en cada hora.
	    public static final String COLUMN_NAME_TARIFA_20A_PVPC = "tarifa20A_PVPC";
	    public static final String COLUMN_NAME_TARIFA_20DHA_PVPC = "tarifa20DHA_PVPC";
	    public static final String COLUMN_NAME_TARIFA_20DHS_PVPC = "tarifa20DHS_PVPC";
	}
	
	//sql para crear bbdd tarifas.
    private static final String SQL_CREATE_TARIFAS =
        "CREATE TABLE " + ColumnasTarifas.TABLE_NAME + " (" +
        ColumnasTarifas._ID + " INTEGER PRIMARY KEY," +
        
        ColumnasTarifas.COLUMN_NAME_FECHA + TEXT_TYPE + COMMA_SEP+
		ColumnasTarifas.COLUMN_NAME_HORA + INT_TYPE + COMMA_SEP+
		ColumnasTarifas.COLUMN_NAME_EMISIONES_PVPC + REAL_TYPE+ COMMA_SEP+
		ColumnasTarifas.COLUMN_NAME_EMISIONES_LIBRE + REAL_TYPE+ COMMA_SEP+
		
		
		ColumnasTarifas.COLUMN_NAME_TARIFA_20A + REAL_TYPE + COMMA_SEP+
		ColumnasTarifas.COLUMN_NAME_TARIFA_20DHA + REAL_TYPE + COMMA_SEP+
		ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS + REAL_TYPE + COMMA_SEP+
		
		ColumnasTarifas.COLUMN_NAME_TARIFA_20A_PVPC + REAL_TYPE + COMMA_SEP+
		ColumnasTarifas.COLUMN_NAME_TARIFA_20DHA_PVPC + REAL_TYPE + COMMA_SEP+
		ColumnasTarifas.COLUMN_NAME_TARIFA_20DHS_PVPC + REAL_TYPE +
        
        " )";
	
    public static final String[] projectionCostes = {
		ColumnasCostes.COLUMN_NAME_COSTE_COMENTARIOS,
		ColumnasCostes.COLUMN_NAME_FECHA_MEDIDA,
		ColumnasCostes.COLUMN_NAME_TIEMPO_MEDIDA,
		ColumnasCostes.COLUMN_NAME_POTENCIA,
		
		ColumnasCostes.COLUMN_NAME_TARIFA20A_PVPC,
		ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20A_PVPC,
		ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20A_PVPC ,

		ColumnasCostes.COLUMN_NAME_TARIFA20DHA_PVPC,
		ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHA_PVPC,
	    ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHA_PVPC,

		ColumnasCostes.COLUMN_NAME_TARIFA20DHS_PVPC,
	    ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHS_PVPC,
	    ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHS_PVPC ,
	    

		ColumnasCostes.COLUMN_NAME_TARIFA20A,
	    ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20A,
	    ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20A,

		ColumnasCostes.COLUMN_NAME_TARIFA20DHA,
	    ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHA,
	    ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHA,

		ColumnasCostes.COLUMN_NAME_TARIFA20DHS,
	    ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHS,
	    ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHS,
	    ColumnasCostes.COLUMN_NAME_EMISIONES_TOTALESLibre,
	    ColumnasCostes.COLUMN_NAME_EMISIONES_TOTALESPVPC,
    };
    //Estructura table de costetes.
	public static abstract class ColumnasCostes implements BaseColumns{
		
		public static final String TABLE_NAME = "costes";
		
		 //Potencia, tiempo, Coste energía en cada instántante y coste acumulado.
		 public static final String COLUMN_NAME_COSTE_COMENTARIOS="comentarios";
	    public static final String COLUMN_NAME_POTENCIA = "potencia";
	    public static final String COLUMN_NAME_TIEMPO_MEDIDA = "tiempo";
	    public static final String COLUMN_NAME_FECHA_MEDIDA="fecha";
	    
	    //Costes con la tarifa PVPC
	    public static final String COLUMN_NAME_TARIFA20A_PVPC="tarifa20A_PVPC";
	    public static final String COLUMN_NAME_COSTE_ENERGIA20A_PVPC="coste20A_PVPC";
	    public static final String COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20A_PVPC="coste_acumulado20_PVPC";
	    
	    public static final String COLUMN_NAME_TARIFA20DHA_PVPC="tarifa20DHA_PVPC";
	    public static final String COLUMN_NAME_COSTE_ENERGIA20DHA_PVPC="coste20DHA_PVPC";
	    public static final String COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHA_PVPC="coste_acumulado20DHA_PVPC";
	    
	    public static final String COLUMN_NAME_TARIFA20DHS_PVPC="tarifa20DHS_PVPC";
	    public static final String COLUMN_NAME_COSTE_ENERGIA20DHS_PVPC="coste20DHS_PVPC";
	    public static final String COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHS_PVPC="coste_acumulado20DHS_PVPC";
	    
	    public static final String COLUMN_NAME_EMISIONES_TOTALESPVPC="emisionteTotalesPVPC";
	    public static final String COLUMN_NAME_EMISIONES_TOTALESLibre="emisionteTotalesLibre";
	    
	    //Costes con la tarifa Goiener.
	    public static final String COLUMN_NAME_TARIFA20A="tarifa20A";
	    public static final String COLUMN_NAME_COSTE_ENERGIA20A="coste20A";
	    public static final String COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20A="coste_acumulado20";
	    
	    public static final String COLUMN_NAME_TARIFA20DHA="tarifa20DHA";
	    public static final String COLUMN_NAME_COSTE_ENERGIA20DHA="coste20DHA";
	    public static final String COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHA="coste_acumulado20DHA";
	    
	    public static final String COLUMN_NAME_TARIFA20DHS="tarifa20DHS";
	    public static final String COLUMN_NAME_COSTE_ENERGIA20DHS="coste20DHS";
	    public static final String COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHS="coste_acumulado20DHS";
	}
	
	private static final String SQL_CREATE_COSTES=
		"CREATE TABLE "+ ColumnasCostes.TABLE_NAME+ " (" +
		ColumnasCostes._ID + " INTEGER PRIMARY KEY," +
		ColumnasCostes.COLUMN_NAME_COSTE_COMENTARIOS + TEXT_TYPE+COMMA_SEP+
	    ColumnasCostes.COLUMN_NAME_TIEMPO_MEDIDA + REAL_TYPE + COMMA_SEP +
	    ColumnasCostes.COLUMN_NAME_FECHA_MEDIDA+ TEXT_TYPE + COMMA_SEP+
	    ColumnasCostes.COLUMN_NAME_POTENCIA + INT_TYPE + COMMA_SEP +
	    
	    ColumnasCostes.COLUMN_NAME_TARIFA20A_PVPC+REAL_TYPE+COMMA_SEP+
	    ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20A_PVPC+ REAL_TYPE + COMMA_SEP +
	    ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20A_PVPC + REAL_TYPE + COMMA_SEP+

	    ColumnasCostes.COLUMN_NAME_TARIFA20DHA_PVPC+REAL_TYPE+COMMA_SEP+
	    ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHA_PVPC+ REAL_TYPE+COMMA_SEP+
	    ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHA_PVPC + REAL_TYPE +COMMA_SEP+

	    ColumnasCostes.COLUMN_NAME_TARIFA20DHS_PVPC+REAL_TYPE+COMMA_SEP+
	    ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHS_PVPC+REAL_TYPE+COMMA_SEP+
	    ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHS_PVPC + REAL_TYPE+COMMA_SEP+
	    
	    ColumnasCostes.COLUMN_NAME_TARIFA20A+REAL_TYPE+COMMA_SEP+
	    ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20A+ REAL_TYPE + COMMA_SEP +
	    ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20A + REAL_TYPE + COMMA_SEP+
	    
	    ColumnasCostes.COLUMN_NAME_TARIFA20DHA+REAL_TYPE+COMMA_SEP+
	    ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHA+ REAL_TYPE+COMMA_SEP+
	    ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHA+ REAL_TYPE +COMMA_SEP+
	    
	    ColumnasCostes.COLUMN_NAME_TARIFA20DHS+REAL_TYPE+COMMA_SEP+
	    ColumnasCostes.COLUMN_NAME_COSTE_ENERGIA20DHS+REAL_TYPE+COMMA_SEP+
	    ColumnasCostes.COLUMN_NAME_COSTE_ACUMULADO_ENERGIA20DHS+ REAL_TYPE+COMMA_SEP +
	    ColumnasCostes.COLUMN_NAME_EMISIONES_TOTALESPVPC + REAL_TYPE+ COMMA_SEP+
	    ColumnasCostes.COLUMN_NAME_EMISIONES_TOTALESLibre + REAL_TYPE+ 
	    " )";
    
    
    public BaseDatosNeurGAI(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);	
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		// TODO Auto-generated method stub
		db.execSQL(SQL_CREATE_TARIFAS);
		db.execSQL(SQL_CREATE_COSTES);
		Log.i("onCreateDB","BBDD creada." );
		
	}
	
	
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}
	
	String SQL_DELETE_TABLE_TARIFAS ="DROP TABLE IF EXISTS " + ColumnasTarifas.TABLE_NAME;
	String SQL_DELETE_TABLE_COSTES="DROP TABLE IF EXISTS " + ColumnasCostes.TABLE_NAME;
    
    //private static final String SQL_DELETE_REGISTER= "delete from " +BaseDatosNeurGAI.ColumnasTarifas.TABLE_NAME;
    /*public void eliminarRegistros(SQLiteDatabase db){

	db.execSQL(borrarRegistros);
	}*/
}