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
    public final Hashtable<Integer,Home> complex;
    @XmlElement(name = "Global_Stat_List")
    private final  List<Pair<Long,Double>> complexStat;
    private static Complex instance;
    private final Hashtable<Integer,Home> suspendedHouse;

    public static Complex getInstance(){
        if(instance==null)
            instance=new Complex();
        return instance;
    }

    private Complex(){
        complex= new Hashtable<>();
        suspendedHouse= new Hashtable<>();

        complexStat= new ArrayList<>();
    }
    //region REST REQUEST

    //POST
    public boolean addHouse (Home h) {                      //synced to avoid double insertion attempt

        synchronized (complex) {

            if (complex.containsKey(h.HomeID)) {
                return false;
            }

            if (suspendedHouse.containsKey(h.HomeID)) {
                complex.put(h.HomeID, suspendedHouse.remove(h.HomeID));

            } else {
                complex.put(h.HomeID, h);
            }
            return complex.containsKey(h.HomeID);               //check if the insertion has been completed
        }
    }

    //DELETE
    public boolean deleteHouse(int id){                    //synced to avoid concurrent access while deleting a house
        synchronized (complex) {
            suspendedHouse.put(id, complex.remove(id));

            return !complex.containsKey(id);
        }
    }

    //PUT
    public boolean addLocalStat(int id, Pair<Long,Double> measure){
        boolean b;
        synchronized (complex){
           b= complex.get(id).AddMeasure(measure);
        }
        return b;
    }

    //PUT
    public boolean addGlobalStat(Pair<Long,Double> measure){
         synchronized (complex) {
             complexStat.add(measure);
             return complexStat.contains(measure);
         }
    }
    //GET
    public Home getHouse(int id){
        if(!complex.containsKey(id)) return null;

        return complex.get(id);
    }

    //GET
    public List<Pair<Long,Double>> getLastLocalStat(int ID, int n){  //synced to avoid deletion or insertion attempt while retreaving the list of statistics
        synchronized (complex) {
            if (!complex.containsKey(ID)) return null;

            return complex.get(ID).getLastN(n);
        }

    }

    //GET
    public Pair<Double, Double> getLocalMeanDev(int ID, int n){

        List<Pair<Long,Double>> measurement;
        synchronized (complex){                                         //synced to take the most updated copy of the stats of the house (also synced inside getLastN) without occupying the OBJ for too long
            if(!complex.containsKey(ID)) return null;

            measurement=complex.get(ID).getLastN(n);
        }

        return calculateMeanDeviation(measurement);

    }

    //GET
    public  ArrayList<Pair<Long,Double>> getLastGlobalStat(int n){
        List<Pair<Long,Double>> copy;
        synchronized (complexStat){                                     //synced to take the most updated copy of the stats of the complex without occupying the OBJ for too long
            copy= new ArrayList<Pair<Long,Double>>(complexStat);
        }

        return new ArrayList<>(copy.subList(copy.size() - Math.min(copy.size(), n), copy.size()));
    }

    //GET
    public Pair<Double, Double> getGlobalMeanDev(int n){

        return calculateMeanDeviation(getLastGlobalStat(n));

    }

    //endregion

    private Pair<Double, Double> calculateMeanDeviation(List<Pair<Long,Double>> m){  //Pair: left=mean, right= standardDeviation
        if(m.size()<=0) return null;    //no need for calculation

        double mean=0,deviation=0;

        //mean
        for (Pair<Long,Double> measure : m) {
            mean += measure.right;
        }
        mean= mean/m.size();

        double temp=0;

        //deviation
        for (Pair<Long,Double> measure : m) {
            temp+=Math.pow(measure.right -mean, 2);
        }

        deviation=Math.sqrt(temp/m.size());

        return Pair.of(mean, deviation);
    }

}
