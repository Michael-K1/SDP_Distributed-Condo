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
            lamportClock=parent.lamportClock;
        }
    }

    public void sendStatistics(WebTarget wt, Pair<Long,Double> measure){
        sendToServer(wt,measure);
        List<ManagedChannel> copy;
        synchronized (parent){
            copy=new ArrayList<>(parent.peerList.values());
        }
        sendToPeer(copy,measure);

        lamportClock.afterEvent();

    }

    private void sendToServer(WebTarget wt, Pair<Long, Double> measure) {   //wt is already correct

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

        final int[] count = {0,-1};
        StreamObserver<Ack> respObs=new StreamObserver<Ack>() {
            @Override
            public void onNext(Ack ack) {
                if(ack.getAck()){
                    print(ack.getMessage());

                    if(count[1]!=ack.getCoordinator()){
                        count[1]=ack.getCoordinator();
                    }
                }
            }

            @Override
            public void onError(Throwable throwable) {
                printErr("Async self introduction "+ throwable.getMessage());
                throwable.printStackTrace();
            }

            @Override
            public void onCompleted() {
                count[0]+=1;
                print("[HOUSE "+id+"] successful introduction: "+count[0]+"/"+copy.size());
                if(count[0]==copy.size()){
                    synchronized (parent){
                        parent.coordinator=count[1];
                    }

                    print("coordinator is: "+count[1]);
                }


            }
        };
        for (ManagedChannel chan:copy) {
            asyncstub=HouseManagementGrpc.newStub(chan);

            asyncstub.addHome(selfIntro, respObs);

        }


    }

    public void removeSelfFromPeers(){
        SelfIntroduction selfIntro = SelfIntroduction.newBuilder().setId(id).build();
        HouseManagementGrpc.HouseManagementStub asyncstub;

        List<ManagedChannel> copy ;
        synchronized (parent.peerList){
            copy = new ArrayList<>(parent.peerList.values());
        }

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
            asyncstub=HouseManagementGrpc.newStub(chan);
            asyncstub.removeHome(selfIntro, respObs);


        }


    }
}
