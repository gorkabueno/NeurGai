package eus.ehu.neurgai;


import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class ParsearTarifas {
	

	public Document domPVPC, domEstandar;
	//private Document domEstandar;
	private List<Tarifa> tarifa20APVPC=new ArrayList<Tarifa>();
	private List<Tarifa> tarifa20DHAPVPC=new ArrayList<Tarifa>();
	private List<Tarifa> tarifa20DHSPVPC=new ArrayList<Tarifa>();
	private String fecha;
	
	
	//Al crear el objeto, se descargan los datos de la web.
	public ParsearTarifas(){
		
		//algoritmoActualizacionURLPVPC();
		Calendar calendar=Calendar.getInstance();
		calendar.setTime(new Date());
		String fechaSistema = new SimpleDateFormat("yy-MM-dd").format(calendar.getTime());
		fecha = fechaSistema;
		// https://api.esios.ree.es/archives/80/download?date=23-06-2016
		String direccion = "https://api.esios.ree.es/archives/80/download?date=" + fechaSistema;
		
		domPVPC=descargarDOM(direccion);
		domEstandar=null;
		
	}
	
	
	//Método con el algoritmo de extracción de las tarifas del DOM.
	private List<Tarifa> asignarTarifaPVPC(int i){
				
		List<Tarifa> tarifas =new ArrayList<Tarifa>();
		
		
		try {
			
			//Accdemos al tag PVPCDesgloseHorario, que es el nodo raíz del xml.
			NodeList seriesTemporales=domPVPC.getElementsByTagName("SeriesTemporales");	
			Node serie=seriesTemporales.item(i);
			Node periodo= serie.getLastChild();
			NodeList nodosPeriodo=periodo.getChildNodes();
			
			for(int j=0;j<nodosPeriodo.getLength();j++){
				Tarifa tarifa=new Tarifa();
				Node nodoPeriodo=nodosPeriodo.item(j);
				String name=nodoPeriodo.getNodeName();
				if(name.equals("Intervalo")){
					tarifa.setHora(Integer.parseInt(nodoPeriodo.getFirstChild().getAttributes().getNamedItem("v").getNodeValue())-1);
					tarifa.setFeu(Double.parseDouble(nodoPeriodo.getLastChild().getAttributes().getNamedItem("v").getNodeValue()));
					tarifa.setFecha(fecha);
					tarifas.add(tarifa);
				}
			}
			
			return tarifas;
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	//Descarga la jerarquía DOM de la web del ministerio.
	public Document descargarDOM(String direccion){
		URL url;
		Document dom = null;
			try {
				
				url=new URL(direccion);
				DocumentBuilderFactory factory= DocumentBuilderFactory.newInstance();
				DocumentBuilder builder;
				builder = factory.newDocumentBuilder();
				dom=builder.parse(url.openConnection().getInputStream());
				
			} catch (Exception e ){
				return null;
			}
			return dom;
	}
	public List<Tarifa> getTarifa20A_PVPC() {
		if(domPVPC!=null){
			tarifa20APVPC=asignarTarifaPVPC(6);
		}
		return tarifa20APVPC;
	}
	public List<Tarifa> getTarifa20DHS_PVPC() {
		if(domPVPC!=null){
			tarifa20DHSPVPC=asignarTarifaPVPC(8);
		}
		return tarifa20DHSPVPC;
	}
	public List<Tarifa> getTarifa20DHA_PVPC() {
		if(domPVPC!=null){
			tarifa20DHAPVPC=asignarTarifaPVPC(7);
		}
		return tarifa20DHAPVPC;
	}
}
