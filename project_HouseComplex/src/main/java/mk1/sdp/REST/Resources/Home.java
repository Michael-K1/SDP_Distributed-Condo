package mk1.sdp.REST.Resources;

import mk1.sdp.misc.Pair;

import java.util.ArrayList;
import java.util.List;

public class Home {
    public int HomeID;
    public String address;
    public int listeningPort;

    private final  List<Pair<Long,Double>> measureList; //pair: left=timestampFromMidnight, right=measure

    public Home(){
        measureList= new ArrayList<>();
    }

    public Home(int homeID,String address,int listeningPort){
        this.HomeID=homeID;
        this.address=address;
        this.listeningPort=listeningPort;
        measureList = new ArrayList<>();
    }

    public Home(Home h){
        this(h.HomeID,h.address,h.listeningPort);
    }

    boolean AddMeasure(Pair<Long, Double> m){
        synchronized (measureList) {
            measureList.add(m);
            return measureList.contains(m);
        }
    }

    ArrayList<Pair<Long,Double>> getLastN(int n){
        List<Pair<Long,Double>> copy;
        synchronized (measureList){
            copy= new ArrayList<>(measureList);
        }

        return new ArrayList<>(copy.subList(copy.size() - Math.min(copy.size(), n), copy.size()));   //the minimum between n and list.size() --> if n >list.size there would be IndexOutOfBoundException
    }
}
