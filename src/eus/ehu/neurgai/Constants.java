package eus.ehu.neurgai;

import android.media.AudioFormat;
import android.os.Environment;

final public class Constants{
	
	//
	public static final int numeroFrecuencias = 61;
	public static final int numeroAmplitudes = 21;
	public static final int NUMERO_MAXIMO_MEDIDAS_EN_GRAFICO = 10000;
	public static final int DATOS_INICIALES_NO_VALIDOS = 15000;
	public static final int DATOS_VALIDOS = 5000;
	public static final String pathNeurgai = Environment.getExternalStorageDirectory().getAbsolutePath() + "/NeurGAI";
	public static final String pathFicheroCalibracion = Environment.getExternalStorageDirectory().getAbsolutePath() + "/NeurGAI/calibracion";
	public static final String pathFicherosRegistros = Environment.getExternalStorageDirectory().getAbsolutePath() + "/NeurGAI/registros";
	public static final String pathFicherosComplementarios = Environment.getExternalStorageDirectory().getAbsolutePath() + "/NeurGAI/medidasComplementarias";

	public static final String formatoFichero = "UTF-8";
	public static final String euskeraCode="eu";
	public static final String castellanoCode="es";
	
	///
	public static final int RECORDER_SAMPLERATE = 44100;
	public static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
	public static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
	public static final int LONGITUD_TONO_GRABADO = RECORDER_SAMPLERATE * 1;
	
	//
	public static final int frecuenciaInterpInicial = 45;
	public static final int frecuenciaInterpFinal = 6400;
	public static final int numeroFrecuenciasInterp = frecuenciaInterpFinal - frecuenciaInterpInicial + 1;
	
	
	private Constants()
    {
    }
}