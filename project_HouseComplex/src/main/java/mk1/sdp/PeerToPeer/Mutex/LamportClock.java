package mk1.sdp.PeerToPeer.Mutex;

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

    public boolean after(Pair<Integer,Integer> otherClockID){
        if(clock>otherClockID.left)
            return true;
        else if(clock<otherClockID.left){
            return false;

        }else{
            return this.id > otherClockID.right;    //if > then this clock is after the received one
        }
    }



    @Override
    public String toString(){
        return id+" "+clock;
    }
}
