package mk1.sdp.REST.Resources;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import mk1.sdp.misc.Pair;


@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Complex {

    @XmlElement(name = "HouseList")
    public Hashtable<Integer,Home> complex;

    private List<Home.Measure> complexStat;      //TODO check with the measurements once created the peerToPeer
    private static Complex instance;


    public static Complex getInstance(){
        if(instance==null)
            instance=new Complex();
        return instance;
    }

    private Complex(){
        complex=new Hashtable<Integer,Home>();
        complexStat=new ArrayList<Home.Measure>();
    }
    //region REST REQUEST

    //POST
    public synchronized boolean addHouse (Home h){                      //synced to avoid double insertion attempt


        if(!complex.containsKey(h.HomeID)){
            complex.put(h.HomeID,h);
        }
        return complex.containsKey(h.HomeID);                           //check if the insertion has been completed
    }

    //DELETE
    public synchronized boolean deleteHouse(int id){                    //synced to avoid concurrent access while deleting a house
        complex.remove(id);
        return complex.containsKey(id);
    }

    //PUT
    //todo inserire la nuova statistica relativa alla casa passata (locale)

    //PUT
    //todo inserire la nuova statistica relativa al condominio (globale)

    //GET
    public Home getHouse(int id){
        return complex.get(id);
    }

    //GET
    public synchronized List<Home.Measure> getLastHomeStat(int ID, int n){  //synced to avoid deletion or insertion attempt while retreaving the list of statistics

      return complex.get(ID).getLastN(n);

    }

    //GET
    public Pair<Double, Double> getHomeMeanDev(int ID, int n){

        List<Home.Measure> measurement;
        synchronized (complex){                                         //synced to take the most updated copy of the stats of the house (also synced inside getLastN) without occupying the OBJ for too long
            measurement=complex.get(ID).getLastN(n);
        }

        return calculateMeanDeviation(measurement);

    }

    //GET
    public  List<Home.Measure> getLastGlobalStat(int n){
        List<Home.Measure> copy;
        synchronized (complexStat){                                     //synced to take the most updated copy of the stats of the complex without occupying the OBJ for too long
            copy=new ArrayList<>(complexStat);
        }

        return copy.subList(copy.size()- Math.min(copy.size(), n), copy.size());
    }

    //GET
    public Pair<Double, Double> getGlobalMeanDev(int n){

        return calculateMeanDeviation(getLastGlobalStat(n));

    }

    //endregion

    private Pair<Double, Double> calculateMeanDeviation(List<Home.Measure> m){
        double mean=0,deviation=0;

        //mean
        for (Home.Measure measure : m) {
            mean += measure.measure;
        }
        mean= mean/m.size();

        double temp=0;

        //deviation
        for (Home.Measure measure : m) {
            temp+=Math.pow(measure.measure-mean, 2);
        }

        deviation=Math.sqrt(temp/m.size());

        return new Pair<Double, Double>(mean,deviation);
    }

}
