package mk1.sdp.REST.Resources;



import java.util.ArrayList;
import java.util.List;

public class Home {
    public int HomeID;
    public String address;
    public int listeningPort;
    private List<Measure> measureList;

    public Home(){
        measureList=new ArrayList<Measure>();
    }

    public synchronized boolean AddMeasure(Measure m){
        measureList.add(m);
        return measureList.contains(m);
    }


    public List<Measure> getLastN(int n){
        List<Measure> copy;
        synchronized (measureList){
            copy=new ArrayList<Measure>(measureList);
        }

        return copy.subList(copy.size() - Math.min(copy.size(), n), copy.size());   //the minimum between n and list.size() --> if n >list.size there would be IndexOutOfBoundException
    }

    //TODO add measurements to the list

    public class Measure{
        public int timestampFromMidnight;
        public double measure;
    }

}
