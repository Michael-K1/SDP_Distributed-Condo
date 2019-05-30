package mk1.sdp.PeerToPeer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import mk1.sdp.GRPC.HouseManagementGrpc.HouseManagementImplBase;
import mk1.sdp.GRPC.PeerMessages.*;
import mk1.sdp.PeerToPeer.Mutex.LamportClock;
import mk1.sdp.misc.Pair;

import java.sql.Timestamp;
import java.util.*;


import static mk1.sdp.misc.Common.*;


public class HouseManagementService extends HouseManagementImplBase{
    private final HousePeer parent;
    private final int id;
    private final LamportClock lamportClock;
    private final Hashtable<Integer, LinkedList<Pair<Long,Double>>> complexMeans;
    private  Timer timer;

    public HouseManagementService(HousePeer parent){
        this.parent=parent;
        this.id=parent.ID;
        lamportClock=parent.lamportClock;
        complexMeans =new Hashtable<>();



    }

    @Override
    public void addHome(SelfIntroduction request, StreamObserver<Ack> responseObserver) {
        int sender=request.getId();
        ManagedChannel channel= ManagedChannelBuilder.forAddress(request.getAddress(),request.getPort()).usePlaintext(true).build();
        String s;

        synchronized (parent.peerList){
            if(!parent.peerList.containsKey(sender)){
                parent.peerList.put(sender,channel);
                s="[REMOTE "+id+"] added "+sender+" to peerList.\t Hello!";
                print("[HOUSE "+id+"] added "+sender+" to peerList.");

                synchronized (complexMeans){
                    if(!complexMeans.containsKey(sender)){
                        complexMeans.put(sender, new LinkedList<>());
                    }
                }
            }else {
                channel.shutdown();
                s="[REMOTE "+id+"] says Hello!";
            }
        }

        //testTimeWaster();

        responseObserver.onNext(simpleAck(s));
        responseObserver.onCompleted();
        lamportClock.afterEvent();

        if(parent.isCoordinator()){
            startScheduler();
        }
    }

    @Override
    public void removeHome(SelfIntroduction request, StreamObserver<Ack> responseObserver) {
        int sender=request.getId();

        String s;
        if(request.getId()!=id) {//if NOT self removal
            printHigh("HOUSE "+id," trying removal of  "+sender+" from peerList....");
            synchronized (parent.peerList){
                if(parent.peerList.containsKey(sender)) {
                    parent.peerList.remove(sender);
                    s = "[REMOTE " + id + "] removed from peerList ";
                    printHigh("HOUSE "+id," removal of "+sender+" COMPLETED!");

                    synchronized (complexMeans){
                        complexMeans.remove(sender);
                    }
                }else
                    s=s= "[REMOTE " + id + "] peer "+sender+" not present";
            }

        }else{
            s="[HOUSE"+id+"] self deletion completed";


        }
        //testTimeWaster();

        responseObserver.onNext(simpleAck(s));
        responseObserver.onCompleted();

        if(parent.isCoordinator()){
            stopScheduler();
        }

        lamportClock.afterEvent();
    }

    @Override
    public void sendMeasure(Measure request, StreamObserver<Ack> responseObserver) {
        int sender=request.getSenderID();
        Pair<Long, Double> mean = Pair.of(request.getTimeStamp(), request.getMeasurement());
        int complexSize;
        synchronized (parent.peerList){
            complexSize=parent.peerList.size();
        }

        synchronized (complexMeans){
            if(!complexMeans.containsKey(sender)){
                complexMeans.put(sender, new LinkedList<>());
            }
            complexMeans.get(sender).offerLast(mean);
        }
        responseObserver.onNext(littleAck());
        responseObserver.onCompleted();


    }

    private Ack littleAck(){
        return Ack.newBuilder().setAck(true).build();
    }
    private Ack simpleAck(String text){
        synchronized (parent) {
            return Ack.newBuilder().setAck(true).setCoordinator(parent.getCoordinator()).setMessage(text).build();
        }

    }

    private void startScheduler(){
        timer= new Timer("daemonMeanCalculator");
        timer.schedule(new MeanCalculationTask(), 0,5000);
    }

    private void stopScheduler(){
        timer.cancel();
    }



    /**
     * method that wastes some time(less than deadline)
     */
    private void testTimeWaster(){
        try {
            Thread.sleep(4*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private class MeanCalculationTask extends TimerTask{

        @Override
        public void run() {
            int count=0;
            List<Pair<Long,Double>> pairs=new ArrayList<>();
            synchronized (HouseManagementService.this.complexMeans){
                for(LinkedList<Pair<Long,Double>> x:complexMeans.values()){ //foreach list removes the first element in the queue
                    if(!x.isEmpty()){
                        pairs.add(x.removeFirst());
                        count+=1;
                    }
                }
            }

            if (count==0) {
//                print("no mean yet");
//                print(new Timestamp(System.currentTimeMillis()).toString());
                return;
            }

            final double[] val = {0};
            pairs.forEach(p-> val[0] +=p.right);

            Pair<Long, Double> p = Pair.of(System.currentTimeMillis(), val[0] / count);

            print(p.left+" "+p.right);

        }
    }
}
