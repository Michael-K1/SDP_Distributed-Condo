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
    private final int homeID;
    private final LamportClock lampClock;
    private final Hashtable<Integer, LinkedList<Pair<Long,Double>>> complexMeans;
    private  Timer timer;
    private final MessageDispatcher mexDispatcher;
    private boolean askingBoost=false;
    public HouseManagementService(HousePeer parent){
        this.parent=parent;
        this.homeID =parent.HomeID;
        lampClock =parent.lamportClock;
        mexDispatcher=parent.getMexDispatcher();
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
                s="added "+sender+" to peerList.\t Hello!";
                print("[HOUSE "+ homeID +"] added "+sender+" to peerList.");

                synchronized (complexMeans){
                    if(!complexMeans.containsKey(sender)){
                        complexMeans.put(sender, new LinkedList<>());
                    }
                }
            }else {
                channel.shutdown();
                s="says Hello!";
            }
        }

        //testTimeWaster();

        responseObserver.onNext(simpleAck(true, s));
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
        if(request.getId()!= homeID) {//if NOT self removal
            printHigh("HOUSE "+ homeID," trying removal of  "+sender+" from peerList....");

            if(parent.isCoordinator(sender)){
                parent.setCoordinator(-1);
            }

            synchronized (parent.peerList){
                if(parent.peerList.containsKey(sender)) {
                    parent.peerList.remove(sender);
                    s = "removed from peerList ";
                    printHigh("HOUSE "+ homeID," removal of "+sender+" COMPLETED!");

                    synchronized (complexMeans){
                        complexMeans.remove(sender);
                    }
                }else
                    s=s= "peer "+sender+" not present";
            }

        }else{
            s="[HOUSE"+ homeID +"] self deletion completed";


        }
        //testTimeWaster();

        responseObserver.onNext(simpleAck(true,s));
        responseObserver.onCompleted();

        if(parent.isCoordinator() && sender==homeID){
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
        responseObserver.onNext(simpleAck(true,""));
        responseObserver.onCompleted();
    }

    @Override
    public void sendGlobalMean(Measure request, StreamObserver<Ack> responseObserver) {
        Pair<Long, Double> globalMean = Pair.of(request.getTimeStamp(), request.getMeasurement());

        printMeasure("\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\tGLOBAL MEAN:", globalMean);

        responseObserver.onNext(simpleAck(true,""));
        responseObserver.onCompleted();

    }

    @Override
    public void election(Coordinator request, StreamObserver<Ack> responseObserver) {
        //testTimeWaster(6);
        responseObserver.onNext(simpleAck(true,""));
        responseObserver.onCompleted();


    }

    @Override
    public void newCoordinator(Coordinator request, StreamObserver<Ack> responseObserver) {
        int coord=request.getCoordinatorID();
        parent.setCoordinator(coord);
        //testTimeWaster(6);
        responseObserver.onNext(simpleAck(true,"NEW COORDINATOR IS: "+coord));
        responseObserver.onCompleted();



        if(parent.isCoordinator()){
            startScheduler();
        }
    }

    @Override
    public void boostRequest(RequestBoost request, StreamObserver<Ack> responseObserver) {
        int sender=request.getRequester();
        Pair<Integer, Integer> otherClock = Pair.of(sender, request.getLamportTimestamp());

        if(homeID!=sender)
            printHigh("house "+homeID, sender+" requested to boost");

        if(mexDispatcher.isInBoost()){
            mexDispatcher.enqueue(sender);
            printHigh("house "+homeID, " permission denied to "+sender);
            responseObserver.onNext(simpleAck(false, "PERMISSION TO BOOST DENIED"));

        }else if(askingBoost && !mexDispatcher.isInBoost()){//if I'm asking boost but have not started yet
            if(lampClock.before(otherClock)){//if "this" has a lower clock
                mexDispatcher.enqueue(sender);
                printRED("[HOUSE "+homeID+"] waiting for boost permission. "+sender+" enqueued!");
                responseObserver.onNext(simpleAck(false, "ALREADY IN QUEUE FOR BOOST PERMISSION. WAIT YOUR TURN "));
            }else {
                responseObserver.onNext(simpleAck(true,"PERMISSION TO BOOST GRANTED"));
            }
        }else{
            if(sender==this.homeID){
                setAskingBoost(true);
                //testTimeWaster(10);
            }
            responseObserver.onNext(simpleAck(true,"PERMISSION TO BOOST GRANTED"));
        }

        responseObserver.onCompleted();
    }

    @Override
    public void boostResponse(ResponseBoost request, StreamObserver<Ack> responseObserver) {
        int sender=request.getSender();


        setAskingBoost(false);
        //testTimeWaster(10);


        if (request.getBoostPermission()){
            printHigh("remote "+sender, "PERMISSION TO BOOST GRANTED");
        }else{
            printHigh("remote "+sender, "still in boost mode");
        }

        responseObserver.onNext(simpleAck(true, "notification received"));
        responseObserver.onCompleted();

    }
    //endregion

    private Ack simpleAck(boolean val,String text){
        lampClock.afterEvent();
        synchronized (parent) {
            return Ack.newBuilder().setAck(val).setCoordinator(parent.getCoordinator()).setMessage("[REMOTE "+homeID+"] "+text).setLamportTimestamp(lampClock.peekClock()).setSender(homeID).build();
        }
    }

    private void startScheduler(){
        if(timer!=null)return;
        timer= new Timer("daemonMeanCalculator");
        timer.schedule(new MeanCalculationTask(), 0,2500);
    }

    private void stopScheduler(){
        timer.cancel();
    }





    public synchronized void setAskingBoost(boolean askingBoost) {
        this.askingBoost = askingBoost;
    }

    private class MeanCalculationTask extends TimerTask{

        private List<Pair<Long,Double>> pairs=new ArrayList<>();

        private void getMeasures(){
            synchronized (HouseManagementService.this.complexMeans){
                for(LinkedList<Pair<Long,Double>> x:complexMeans.values()){ //foreach list removes the first element in the queue
                    if(!x.isEmpty()){
                        pairs.add(x.removeFirst());         //every peer will remove the first element in the queue
                    }
                }
            }
        }

        @Override
        public void run() {

            getMeasures();

            if (pairs.isEmpty() || !parent.isCoordinator()) {
//                print("no mean yet");
//                print(new Timestamp(System.currentTimeMillis()).toString());
                pairs.clear();
                return;
            }

            final double[] val = {0};
            pairs.forEach(p -> val[0] += p.right);

            //pairs.forEach(p->printHigh("coord "+homeID,"single value "+p.right));
            //printHigh("\ncoord "+homeID,"sum of the local means "+val[0]+" with n° Peer= "+pairs.size());

            Pair<Long, Double> globalMean = Pair.of(System.currentTimeMillis(), val[0] / pairs.size());
            pairs.clear();
            mexDispatcher.sendGlobalStatistics(globalMean);



        }
    }
}
