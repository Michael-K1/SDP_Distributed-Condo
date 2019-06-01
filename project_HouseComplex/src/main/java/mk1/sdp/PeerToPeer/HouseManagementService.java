package mk1.sdp.PeerToPeer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import mk1.sdp.GRPC.HouseManagementGrpc.HouseManagementImplBase;
import mk1.sdp.GRPC.PeerMessages.*;
import mk1.sdp.PeerToPeer.Mutex.LamportClock;
import mk1.sdp.misc.Pair;

import java.util.*;

import static mk1.sdp.misc.Common.*;

public class HouseManagementService extends HouseManagementImplBase{
    private final HousePeer parent;
    private final int id;
    private final LamportClock lampClock;
    private final Hashtable<Integer, LinkedList<Pair<Long,Double>>> complexMeans;
    private  Timer timer;

    public HouseManagementService(HousePeer parent){
        this.parent=parent;
        this.id=parent.ID;
        lampClock =parent.lamportClock;
        complexMeans =new Hashtable<>();
    }

    //region RPCs

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

//        if(parent.isCoordinator()){
//            startScheduler();
//        }
        startScheduler();
    }

    @Override
    public void removeHome(SelfIntroduction request, StreamObserver<Ack> responseObserver) {
        int sender=request.getId();

        String s;
        if(request.getId()!=id) {//if NOT self removal
            printHigh("HOUSE "+id," trying removal of  "+sender+" from peerList....");

            if(parent.isCoordinator(sender)){
                parent.setCoordinator(-1);
            }

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
    }

    @Override
    public void sendMeasure(Measure request, StreamObserver<Ack> responseObserver) {
        int sender=request.getSenderID();
        Pair<Long, Double> mean = Pair.of(request.getTimeStamp(), request.getMeasurement());

        //printMeasure("HOUSE "+sender+" sends",mean);
        synchronized (complexMeans){
            if(!complexMeans.containsKey(sender)){
                complexMeans.put(sender, new LinkedList<>());
            }
            complexMeans.get(sender).offerLast(mean);
        }
        responseObserver.onNext(simpleAck(""));
        responseObserver.onCompleted();
    }

    @Override
    public void sendGlobalMean(Measure request, StreamObserver<Ack> responseObserver) {
        Pair<Long, Double> globalMean = Pair.of(request.getTimeStamp(), request.getMeasurement());

        printMeasure("\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\tGLOBAL MEAN:", globalMean);

        responseObserver.onNext(simpleAck(""));
        responseObserver.onCompleted();

    }

    @Override
    public void election(Coordinator request, StreamObserver<Ack> responseObserver) {
        testTimeWaster();
        responseObserver.onNext(simpleAck("[REMOTE "+id+"]"));
        responseObserver.onCompleted();


    }

    @Override
    public void newCoordinator(Coordinator request, StreamObserver<Ack> responseObserver) {
        int coord=request.getCoordinatorID();
        parent.setCoordinator(coord);
        testTimeWaster();
        responseObserver.onNext(simpleAck("[REMOTE "+id+"] NEW COORDINATOR IS: "+coord));
        responseObserver.onCompleted();



        if(parent.isCoordinator()){
            startScheduler();
        }
    }
    //endregion

    private Ack simpleAck(String text){
        lampClock.afterEvent();
        synchronized (parent) {
            return Ack.newBuilder().setAck(true).setCoordinator(parent.getCoordinator()).setMessage(text).setLamportTimestamp(lampClock.peekClock()).setSender(id).build();
        }
    }

    private void startScheduler(){
        if(timer!=null)return;
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
            Thread.sleep(6*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private class MeanCalculationTask extends TimerTask{
        private MessageDispatcher mex=parent.getMexDispatcher();
        private List<Pair<Long,Double>> pairs=new ArrayList<>();

        @Override
        public void run() {
            synchronized (HouseManagementService.this.complexMeans){
                for(LinkedList<Pair<Long,Double>> x:complexMeans.values()){ //foreach list removes the first element in the queue
                    if(!x.isEmpty()){
                        pairs.add(x.removeFirst());         //every peer will remove the first element in the queue
                    }
                }
            }

            if (pairs.size()==0 ||!parent.isCoordinator()) {
//                print("no mean yet");
//                print(new Timestamp(System.currentTimeMillis()).toString());
                pairs.clear();
                return;
            }

            final double[] val = {0};
            pairs.forEach(p-> val[0] +=p.right);

//            pairs.forEach(p->printHigh("coord "+id,"single value "+p.right));
//            printHigh("coord "+id,"sum of the local means "+val[0]+" with n° Peer= "+pairs.size());

            Pair<Long, Double> globalMean = Pair.of(System.currentTimeMillis(), val[0] / pairs.size());
            pairs.clear();
            mex.sendGlobalStatistics(globalMean);
        }
    }
}
