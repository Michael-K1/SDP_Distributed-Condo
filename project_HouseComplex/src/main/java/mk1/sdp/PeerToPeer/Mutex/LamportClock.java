package mk1.sdp.PeerToPeer.Mutex;

import mk1.sdp.GRPC.PeerMessages;
import mk1.sdp.misc.Pair;

public class LamportClock {
    private int deltaTime=1;
    private int id;
    private int clock=0;

    public LamportClock(int id){
        this.id=id;

    }

    public synchronized void afterEvent(){
        clock+=deltaTime;
    }

    public synchronized int peekClock(){
        return clock;
    }
    private synchronized void updateClock(int newClock){
        clock=newClock;
    }

    public boolean before(Pair<Integer,Integer> otherIDClock){   //left=id right=clock
        return  clock==otherIDClock.right? this.id <= otherIDClock.left: clock<otherIDClock.right;
    }

    /**
     *  checks for each answer received if this clock is not left too far behind
     * @param ack= the ack received from a peer
     */
    public synchronized void checkLamport(PeerMessages.Ack ack){
        Pair<Integer,Integer> otherClock=Pair.of(ack.getSender(),ack.getLamportTimestamp());
        if(this.before(otherClock)){
            this.updateClock(ack.getLamportTimestamp()+1);
        }
        //printHigh("house "+id,"Lamport clock: "+this.peekClock());
    }

    @Override
    public String toString(){
        return id+" "+clock;
    }
}
