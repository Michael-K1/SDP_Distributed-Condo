package mk1.sdp.PeerToPeer;

import io.grpc.ManagedChannel;

import io.grpc.stub.StreamObserver;
import mk1.sdp.GRPC.HouseManagementGrpc;
import mk1.sdp.GRPC.PeerMessages.*;
import mk1.sdp.PeerToPeer.Mutex.LamportClock;
import mk1.sdp.misc.Pair;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
//import java.util.concurrent.TimeUnit;

import static mk1.sdp.misc.Common.*;

public class MessageDispatcher {
    private final int id;
    private final HousePeer parent;
    private final String address;
    private final int port;
    private LamportClock lamportClock;

    public MessageDispatcher(HousePeer parent){
        this.parent=parent;
        synchronized (this.parent){
            this.id=parent.ID;
            this.address=parent.host;
            this.port=parent.port;
        }
    }

    public void sendStatistics(WebTarget wt, Pair<Long,Double> measure){
        sendToServer(wt,measure);
        List<ManagedChannel> copy;
        synchronized (parent){
            copy=new ArrayList<>(parent.peerList.values());
        }
        sendToPeer(copy,measure);
        synchronized (parent.lamportClock) {
            parent.lamportClock.afterEvent();
        }
    }

    private void sendToServer(WebTarget wt, Pair<Long, Double> measure) {   //wt is already correct //todo add check if response has error

        Response resp = wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).put(Entity.entity(measure, MediaType.APPLICATION_JSON_TYPE));

    }

    private void sendToPeer(List<ManagedChannel> copy, Pair<Long, Double> measure) {
        int clock=lamportClock.peekClock();

        for(ManagedChannel chan:copy){


        }
    }

    public void addSelfToPeers(){
        SelfIntroduction selfIntro = SelfIntroduction.newBuilder().setId(id).setAddress(address).setPort(port).build();


        List<ManagedChannel> copy ;
        synchronized (parent.peerList){
            copy = new ArrayList<>(parent.peerList.values());
        }

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
                throwable.getCause().printStackTrace();
            }

            @Override
            public void onCompleted() {
                synchronized (MessageDispatcher.this){
                    p.left+=1;
                    print("[HOUSE "+id+"] successful introduction: "+p.left+"/"+copy.size());
                }

                if(p.right!=-1){
                    synchronized (parent){
                        if(parent.coordinator!=p.right)
                            parent.coordinator=p.right;
                    }
                }
            }
        };


        HouseManagementGrpc.HouseManagementStub asyncStub;
        for (ManagedChannel chan:copy) {
            asyncStub=HouseManagementGrpc.newStub(chan);

            asyncStub.addHome(selfIntro, respObs);
         /*   Runnable runner=new Runnable() {
                @Override
                public void run() {
                    HouseManagementGrpc.HouseManagementStub asyncStub;
                    asyncStub=HouseManagementGrpc.newStub(chan);

                    asyncStub.addHome(selfIntro, respObs);

                    try {
                        chan.awaitTermination(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        printErr("interrupted while waiting for termination in self introduction on channel: "+chan.toString());
                    }
                }
            };

            new Thread(runner).start();
*/
        }

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
                throwable.printStackTrace();
            }

            @Override
            public void onCompleted() {
                synchronized (MessageDispatcher.this) {
                    count[0] += 1;
                    print("[HOUSE "+id+"] successful removal: "+count[0]+"/"+copy.size());
                }

            }
        };

        HouseManagementGrpc.HouseManagementStub asyncStub;
        for (ManagedChannel chan:copy) {
            if(chan.isShutdown()) {
                printHigh("house"+id,"channel already closed");
                continue;
            }

            asyncStub=HouseManagementGrpc.newStub(chan);
            asyncStub.removeHome(selfIntro, respObs);
        }

    }
}
