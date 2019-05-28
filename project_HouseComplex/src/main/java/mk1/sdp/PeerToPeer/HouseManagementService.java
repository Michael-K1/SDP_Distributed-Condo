package mk1.sdp.PeerToPeer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import mk1.sdp.GRPC.HouseManagementGrpc.HouseManagementImplBase;
import mk1.sdp.GRPC.PeerMessages.*;
import mk1.sdp.PeerToPeer.Mutex.LamportClock;

import java.util.concurrent.TimeUnit;

import static mk1.sdp.misc.Common.*;


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
                print("[HOUSE "+id+"] added "+request.getId()+" to peerList.");
            }else {
                channel.shutdown();
                s="[REMOTE "+id+"] says Hello!";
            }
        }

        testTimeWaster();

        responseObserver.onNext(simpleAck(s));
        responseObserver.onCompleted();
        lamportClock.afterEvent();

    }

    @Override
    public void removeHome(SelfIntroduction request, StreamObserver<Ack> responseObserver) {


        String s;
        if(request.getId()!=id) {//if NOT self removal
            printHigh("HOUSE "+id," trying removal of  "+request.getId()+" from peerList....");
            synchronized (parent.peerList){
                if(parent.peerList.containsKey(request.getId())) {
                    parent.peerList.remove(request.getId());
                    s = "[REMOTE " + id + "] removed from peerList ";

                    printHigh("HOUSE "+id," removal of "+request.getId()+" COMPLETED!");
                }else
                    s=s= "[REMOTE " + id + "] peer "+request.getId()+" not present";
            }

        }else{
            s="[HOUSE"+id+"] self deletion completed";


        }
        testTimeWaster();

        responseObserver.onNext(simpleAck(s));
        responseObserver.onCompleted();

        lamportClock.afterEvent();
    }

    private Ack simpleAck(String text){
        synchronized (parent) {
            return Ack.newBuilder().setAck(true).setCoordinator(parent.coordinator).setMessage(text).build();
        }

    }

    /**
     * method that wastes some time(less than te a)
     */
    private void testTimeWaster(){
        try {
            Thread.sleep(9*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
