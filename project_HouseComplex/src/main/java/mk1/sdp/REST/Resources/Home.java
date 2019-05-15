package mk1.sdp.REST.Resources;




import mk1.sdp.misc.Pair;

import java.util.ArrayList;
import java.util.List;

public class Home {
    public int HomeID;
    public String address;
    public int listeningPort;

    private List<Pair<Integer,Double>> measureList; //pair: first=timestampFromMidnight, second=measure

    public Home(){
        measureList= new ArrayList<>();
    }


    public synchronized boolean AddMeasure(Pair<Integer,Double> m){
        measureList.add(m);
        return measureList.contains(m);
    }


    public ArrayList<Pair<Integer,Double>> getLastN(int n){
        List<Pair<Integer,Double>> copy;
        synchronized (measureList){
            copy= new ArrayList<>(measureList);
        }

        return new ArrayList<>(copy.subList(copy.size() - Math.min(copy.size(), n), copy.size()));   //the minimum between n and list.size() --> if n >list.size there would be IndexOutOfBoundException
    }

    //TODO add measurements to the list



}
