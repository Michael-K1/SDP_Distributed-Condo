package mk1.sdp.PeerToPeer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import mk1.sdp.GRPC.HouseManagementGrpc.HouseManagementImplBase;
import mk1.sdp.GRPC.PeerMessages.*;
import mk1.sdp.PeerToPeer.Mutex.LamportClock;
import static mk1.sdp.misc.Common.printErr;

import java.util.concurrent.TimeUnit;


public class HouseManagementService extends HouseManagementImplBase{
    private final HousePeer parent;
    private final int id;
    private final LamportClock lamportClock;

    public HouseManagementService(HousePeer parent){
        this.parent=parent;
        this.id=parent.ID;
        lamportClock=parent.lamportClock;
    }
    @Override
    public void addHome(SelfIntroduction request, StreamObserver<Ack> responseObserver) {
        ManagedChannel channel= ManagedChannelBuilder.forAddress(request.getAddress(),request.getPort()).usePlaintext(true).build();
        String s;
        synchronized (parent.peerList){
            if(!parent.peerList.containsKey(request.getId())){
                parent.peerList.put(request.getId(),channel);
                s="[REMOTE "+id+"] added "+request.getId()+" to peerList.\t Hello!";
            }else {
                channel.shutdown();
                s="[REMOTE "+id+"] says Hello!";
            }
        }
        responseObserver.onNext(simpleAck(s));
        responseObserver.onCompleted();
        lamportClock.afterEvent();

    }

    @Override
    public void removeHome(SelfIntroduction request, StreamObserver<Ack> responseObserver) {
        ManagedChannel toRemove=null;

        String s= "[REMOTE " + id + "] peer "+request.getId()+" nor present";
        synchronized (parent.peerList){
            if(parent.peerList.containsKey(request.getId())) {
                toRemove= parent.peerList.remove(request.getId());

                s = "[REMOTE " + id + "] removed from peerList ";
            }
        }
        responseObserver.onNext(simpleAck(s));
        responseObserver.onCompleted();

        lamportClock.afterEvent();
        if(toRemove!=null){

            try {
                toRemove.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                printErr("[house "+id+"] interrupted while shutting connection to house "+request.getId());
            }
        }
    }

    private Ack simpleAck(String text){
        return Ack.newBuilder().setAck(true).setCanBoost(false).setMessage(text).build();
    }
}
