package mk1.sdp.PeerToPeer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import mk1.sdp.GRPC.HouseManagementGrpc.HouseManagementImplBase;
import mk1.sdp.GRPC.PeerMessages.*;
import mk1.sdp.PeerToPeer.Mutex.LamportClock;
import mk1.sdp.misc.Pair;
import mk1.sdp.PeerToPeer.Mutex.SyncObj;

import static mk1.sdp.misc.Common.*;

import java.util.*;

public class HouseManagementService extends HouseManagementImplBase{
    private final HousePeer parent;
    private final int homeID;

    private final LamportClock lampClock;
    private final Hashtable<Integer, LinkedList<Pair<Long,Double>>> complexMeans;
    private  Timer timer;

    private final MessageDispatcher mexDispatcher;

    HouseManagementService(HousePeer parent){
        this.parent=parent;
        this.homeID =parent.HomeID;
        lampClock =parent.lamportClock;
        mexDispatcher=parent.getMexDispatcher();
        complexMeans =new Hashtable<>();
    }

    //region RPCs

    //region HOUSE HANDLER
    @Override
    public void addHome(SelfIntroduction request, StreamObserver<Ack> responseObserver) {
        int sender=request.getId();
        String s;

        //testTimeWaster(60);
        synchronized (parent.peerTable){
            if(!parent.peerTable.containsKey(sender)){
                ManagedChannel channel= ManagedChannelBuilder.forAddress(request.getAddress(),request.getPort()).usePlaintext(true).build();
                parent.peerTable.put(sender,channel);
                s="added "+sender+" to peerTable.\t Hello!";
                print("[HOUSE "+ homeID +"] added "+sender+" to peerTable.");

                synchronized (complexMeans){
                    if(!complexMeans.containsKey(sender)){
                        complexMeans.put(sender, new LinkedList<>());
                    }
                }
            }else {
                s="says Hello!";
            }
        }

        responseObserver.onNext(simpleAck(s));
        responseObserver.onCompleted();

        startScheduler();
    }

    @Override
    public void removeHome(SelfIntroduction request, StreamObserver<Ack> responseObserver) {
        int sender=request.getId();

        String s;

        if(request.getId()!= homeID) {//if NOT self removal
            printHigh("HOUSE "+ homeID," trying removal of  "+sender+" from peerTable....");

            if(parent.isCoordinator(sender)){
                parent.setCoordinator(-1);
            }

            synchronized (parent.peerTable){
                if(parent.peerTable.containsKey(sender)) {
                    parent.peerTable.remove(sender);
                    s = "removed from peerTable ";
                    printHigh("HOUSE "+ homeID," removal of "+sender+" COMPLETED!");

                    synchronized (complexMeans){
                        complexMeans.remove(sender);
                    }
                }else
                    s= "peer "+sender+" not present";
            }

        }else{
            s="[HOUSE"+ homeID +"] self deletion completed";
        }
        //testTimeWaster();

        responseObserver.onNext(simpleAck(s));
        responseObserver.onCompleted();

        if(parent.isCoordinator() && sender==homeID){
            stopScheduler();
        }
    }
    //endregion

    //region MEAN HANDLER
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
    //endregion

    //region ELECTION HANDLER
    @Override
    public void election(Coordinator request, StreamObserver<Ack> responseObserver) {
        //testTimeWaster(6);
        responseObserver.onNext(simpleAck(""));
        responseObserver.onCompleted();
    }

    @Override
    public void newCoordinator(Coordinator request, StreamObserver<Ack> responseObserver) {
        int coord=request.getCoordinatorID();
        parent.setCoordinator(coord);
        //testTimeWaster(6);
        responseObserver.onNext(simpleAck("NEW COORDINATOR IS: "+coord));
        responseObserver.onCompleted();
    }
    //endregion

    @Override
    public void boostRequest(RequestBoost request, StreamObserver<Ack> responseObserver) {  //todo ad ogni richiesta di boost, chi è in coda viene notificato e fa partire il proprio boost
        int sender=request.getRequester();
        Pair<Integer, Integer> otherClock = Pair.of(sender, request.getLamportTimestamp());
        boolean printOnce=true;
        if(homeID!=sender)
            printHigh("house "+homeID, sender+" requested to boost");

        while(mexDispatcher.isInBoost() || (mexDispatcher.isAskingBoost() && lampClock.before(otherClock))){
           if(printOnce) {
               printRED("add " + sender + " to boost queue");
               printOnce=false;
           }
           SyncObj.getInstance().waiter(6);

            /*could trigger print of
            *   io.grpc.netty.NettyServerTransport notifyTerminated INFO: Transport failed
            * when "this" has to notify a peer who got out of the network
            * */

        }

        responseObserver.onNext(simpleAck("PERMISSION TO BOOST GRANTED"));
        responseObserver.onCompleted();
    }
    //endregion

    private Ack simpleAck(String text){
        lampClock.afterEvent();
        synchronized (parent) {
            return Ack.newBuilder()
                    .setAck(true)
                    .setCoordinator(parent.getCoordinator())
                    .setMessage("[REMOTE "+homeID+"] "+text)
                    .setLamportTimestamp(lampClock.peekClock())
                    .setSender(homeID)
                    .build();
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

//            pairs.forEach(p->printHigh("coord "+homeID,"single value "+p.right+ " time "+new Timestamp(p.left).toString()+"\n"));
//            printHigh("coord "+homeID,"sum of the local means "+val[0]+" with n° Peer= "+pairs.size());

            Pair<Long, Double> globalMean = Pair.of(System.currentTimeMillis(), val[0] / pairs.size());
            pairs.clear();
            mexDispatcher.sendGlobalStatistics(globalMean);



        }
    }
}
