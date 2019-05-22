package mk1.sdp.PeerToPeer;

import mk1.sdp.misc.Pair;
import simulation_src_2019.Buffer;
import simulation_src_2019.Measurement;

import java.util.ArrayList;
import java.util.List;

public class SlidingBuffer implements Buffer {
    private List<Pair<Long,Double>> measureList;
    private int bufferDim;
    private int overlap;
    public Pair<Long, Double> lastCalculated;

    public SlidingBuffer(int bufferDim, float overlap){     //0.00<=overlap<=1.00
        this.bufferDim=bufferDim;
        this.overlap=Math.round(bufferDim*overlap);
        measureList=new ArrayList<>();
    }

    @Override
    public synchronized void addMeasurement(Measurement m) {
        measureList.add(Pair.of(m.getTimestamp(), m.getValue()));

        if(measureList.size()==bufferDim){
            lastCalculated= createMeanMeasure();

            int size=measureList.size();
            measureList=measureList.subList(overlap, size);

        }
    }

    private Pair<Long,Double> createMeanMeasure() {
        long time=0;
        double finalMeasure=0;

        for(Pair<Long,Double> x:measureList){
            time+=x.left;
            finalMeasure+=x.right;
        }

        time=Math.floorDiv(time,bufferDim);
        finalMeasure/=bufferDim;

        return Pair.of(time,finalMeasure);
    }

}
