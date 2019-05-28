package mk1.sdp.PeerToPeer;

import mk1.sdp.misc.Pair;
import simulation_src_2019.Buffer;
import simulation_src_2019.Measurement;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class SlidingBuffer implements Buffer {
    private List<Measurement> measureList;
    private int bufferDim;
    private int overlap;
    private HousePeer parent;
    private Pair<Long, Double> lastCalculated;
//    public Queue<Pair<Long,Double>> measureQueue;

    public SlidingBuffer(HousePeer parent,int bufferDim, float overlap){     //0.00<=overlap<=1.00
        this.parent=parent;
        this.bufferDim=bufferDim;
        this.overlap=Math.round(bufferDim*overlap);
        measureList=new ArrayList<>();
    }

    @Override
    public synchronized void addMeasurement(Measurement m) {
        measureList.add(m);

        if(measureList.size()==bufferDim){
            lastCalculated= createMeanMeasure();
            //parent.printMeasure(lastCalculated);
            int size=measureList.size();
            measureList=measureList.subList(overlap, size);

        }
    }

    private Pair<Long,Double> createMeanMeasure() {
        long time=0;
        double finalMeasure=0;

        for(Measurement x:measureList){

            finalMeasure+=x.getValue();
        }

        time=System.currentTimeMillis();    //time at the moment of creation
        finalMeasure/=bufferDim;

        return Pair.of(time, finalMeasure);
    }

}
