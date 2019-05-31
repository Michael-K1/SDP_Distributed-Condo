package mk1.sdp.PeerToPeer;

import io.grpc.ManagedChannel;

import io.grpc.stub.StreamObserver;
import mk1.sdp.GRPC.HouseManagementGrpc;
import mk1.sdp.GRPC.PeerMessages.*;

import mk1.sdp.misc.Pair;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.concurrent.TimeUnit;

//import java.util.concurrent.TimeUnit;


import static mk1.sdp.misc.Common.*;

public class MessageDispatcher {
    private final int id;
    private final HousePeer parent;
    private final String address;
    private final int port;
    private final WebTarget toLocalStat;
    private final WebTarget toGlobalStat;


    public MessageDispatcher(HousePeer parent,WebTarget server){
        this.parent=parent;
        synchronized (this.parent){
            this.id=parent.ID;
            this.address=parent.host;
            this.port=parent.port;
        }

        toLocalStat = server.path("/complex/house/add").queryParam("id", id);
        toGlobalStat = server.path("/complex/global/add");
    }

    //region NETWORK MESSAGES
    public void sendGlobalStatistics( Pair<Long,Double> measure){
        if (!parent.isCoordinator())return;

        if(sendToServer(toGlobalStat, measure)) {
            return;
        }

        List<ManagedChannel> copy=parent.getFullPeerListCopy();

        Measure globalMean= Measure.newBuilder().setMeasurement(measure.right).setTimeStamp(measure.left).build();

        StreamObserver<Ack> respObs=new StreamObserver<Ack>() {
            @Override
            public void onNext(Ack ack) {

            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        };

        copy.stream().parallel().forEach(chan->HouseManagementGrpc.newStub(chan).withDeadlineAfter(5, TimeUnit.SECONDS).sendGlobalMean(globalMean,respObs));
    }

    public void sendToPeer(List<ManagedChannel> copy, Pair<Long, Double> measure) {


        if(sendToServer(toLocalStat, measure)){
//            if(tries.length==0)           //todo controllo se non riesce ad inviare
//                sendLocalStatistics(measure, 1);
//            else if (tries)

            return;
        }

        Measure newMean= Measure.newBuilder().setSenderID(id).setTimeStamp(measure.left).setMeasurement(measure.right).build();


        Pair<Integer, Boolean> respVal = Pair.of(0, false);

        StreamObserver<Ack> respObs=new StreamObserver<Ack>() {
            @Override
            public void onNext(Ack ack) {
                synchronized (MessageDispatcher.this){
                    int x=ack.getCoordinator();
                    if (parent.isCoordinator(x) && !respVal.right){
                        respVal.right=true;                          //the coordinator has answered
                    }
                    //printHigh("house "+id, respVal.toString()+ " x="+x);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                if(throwable.getMessage().toUpperCase().matches("(.*)DEADLINE_EXCEEDED(.*)")){
                    printErr("deadline problem detected");
                    //todo start election
                }
            }

            @Override
            public void onCompleted() {
                synchronized (MessageDispatcher.this){
                    respVal.left+=1;

                    if(respVal.left==copy.size())
                        if(!respVal.right){
                            //todo indire elezione
                            printErr("election needed");


                        }

                }
            }
        };


        copy.stream().parallel().forEach(chan-> HouseManagementGrpc.newStub(chan).withDeadlineAfter(5, TimeUnit.SECONDS).sendMeasure(newMean, respObs));

        parent.lamportClock.afterEvent();

    }

    private boolean sendToServer(WebTarget wt, Pair<Long, Double> measure) {   //wt is already correct

        Response resp = wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).put(Entity.entity(measure, MediaType.APPLICATION_JSON_TYPE));

        if(resp==null) return true;

        return responseHasError(resp);

    }

    public void addSelfToPeers(List<ManagedChannel> copy){
        SelfIntroduction selfIntro = SelfIntroduction.newBuilder().setId(id).setAddress(address).setPort(port).build();





        final  Pair<Integer, Integer> p = Pair.of(0,-1);

        StreamObserver<Ack> respObs=new StreamObserver<Ack>() {

            @Override
            public void onNext(Ack ack) {
                if(ack.getAck()){
                    print(ack.getMessage());
                    int coord=ack.getCoordinator();
                    if(coord!=-1 && p.right!=coord){
                        p.right=coord;
                    }
                }
            }

            @Override
            public void onError(Throwable throwable) {
                printErr("Async self introduction "+ throwable.getMessage());
                //throwable.getCause().printStackTrace();
                if(throwable.getMessage().toUpperCase().matches("(.*)DEADLINE_EXCEEDED(.*)")){
                    printErr("deadline problem detected");
                }
            }

            @Override
            public void onCompleted() {

                synchronized (MessageDispatcher.this){
                    p.left+=1;
                    print("[HOUSE "+id+"] successful introduction: "+p.left+"/"+copy.size());
                }

                if(p.right!=-1){
                    synchronized (parent){
                        if(parent.getCoordinator() !=p.right)
                            parent.setCoordinator(p.right);
                    }
                }
            }
        };


        copy.stream().parallel().forEach(chan -> HouseManagementGrpc.newStub(chan).withDeadlineAfter(5, TimeUnit.SECONDS).addHome(selfIntro, respObs));


        parent.lamportClock.afterEvent();
    }

    public void removeSelfFromPeers(List<ManagedChannel> copy){
        SelfIntroduction selfIntro = SelfIntroduction.newBuilder().setId(id).build();

        final int[] count = {0};
        StreamObserver<Ack> respObs=new StreamObserver<Ack>() {
            @Override
            public void onNext(Ack ack) {
                if(ack.getAck())
                    print(ack.getMessage());
            }

            @Override
            public void onError(Throwable throwable) {
                printErr("Async self deletion "+ throwable.getMessage());
               // throwable.printStackTrace();
                if(throwable.getMessage().toUpperCase().matches("(.*)DEADLINE_EXCEEDED(.*)")){
                    printErr("deadline problem detected");
                }

            }

            @Override
            public void onCompleted() {
                synchronized (MessageDispatcher.this) {
                    count[0] += 1;
                    print("[HOUSE "+id+"] successful removal: "+count[0]+"/"+copy.size());
                }

            }
        };

        copy.stream().parallel().forEach(chan->HouseManagementGrpc.newStub(chan).withDeadlineAfter(5, TimeUnit.SECONDS).removeHome(selfIntro, respObs));
    }

    public void startElection(){
        List<ManagedChannel> copy = parent.getGTPeerListCopy();
        if(copy.size()==0){

        }

        StreamObserver<Ack> respObs=new StreamObserver<Ack>() {
            @Override
            public void onNext(Ack ack) {

            }

            @Override
            public void onError(Throwable throwable) {
                if(throwable.getMessage().toUpperCase().matches("(.*)DEADLINE_EXCEEDED(.*)")){
                    printErr("deadline problem detected");
                    becomeCoordinator();//I am new coordinator
                }
            }

            @Override
            public void onCompleted() {

            }
        };
        copy.stream().parallel().forEach(chan->HouseManagementGrpc.newStub(chan).withDeadlineAfter(5, TimeUnit.SECONDS).election(null, respObs));
    }

    private void becomeCoordinator() {
        Coordinator coord= Coordinator.newBuilder().setCoordinatorID(id).build();
        List<ManagedChannel> copy=parent.getFullPeerListCopy();

        final Pair<Integer, Integer> pair = Pair.of(0, 0);

        StreamObserver<Ack> respObs=new StreamObserver<Ack>() {
            @Override
            public void onNext(Ack ack) {
                print(ack.getMessage());
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {
                    synchronized (MessageDispatcher.this){
                        pair.left+=1;
                        print("[HOUSE "+id+"] houses notified: "+pair.left+"/"+copy.size());
                    }
            }
        };

        copy.stream().parallel().forEach(chan->HouseManagementGrpc.newStub(chan).newCoordinator(coord, respObs));
    }
    //endregion
}
