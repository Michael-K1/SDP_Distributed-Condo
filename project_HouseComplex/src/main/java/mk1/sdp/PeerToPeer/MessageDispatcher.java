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

import static mk1.sdp.misc.Common.print;
import static mk1.sdp.misc.Common.printErr;

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
        HouseManagementGrpc.HouseManagementStub asyncstub;

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
//                throwable.printStackTrace();
                throwable.getCause().printStackTrace();
            }

            @Override
            public void onCompleted() {
                p.left+=1;

                print("[HOUSE "+id+"] successful introduction: "+p.left+"/"+copy.size());
                if(p.right!=-1){
                    synchronized (parent){
                        if(parent.coordinator!=p.right)
                            parent.coordinator=p.right;
                    }


                }


            }
        };
        for (ManagedChannel chan:copy) {
            asyncstub=HouseManagementGrpc.newStub(chan);

            asyncstub.addHome(selfIntro, respObs);

        }


    }

    public void removeSelfFromPeers(List<ManagedChannel> copy){
        SelfIntroduction selfIntro = SelfIntroduction.newBuilder().setId(id).build();
        HouseManagementGrpc.HouseManagementStub asyncstub;

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
                count[0]+=1;
                print("[HOUSE "+id+"] successful removal: "+count[0]+"/"+copy.size());
            }
        };

        for (ManagedChannel chan:copy) {
            if(chan.isShutdown()) continue;
            asyncstub=HouseManagementGrpc.newStub(chan);
            asyncstub.removeHome(selfIntro, respObs);
        }
    }
}
