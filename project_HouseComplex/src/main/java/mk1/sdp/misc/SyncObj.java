package mk1.sdp.misc;

import static mk1.sdp.misc.Common.printErr;

public class SyncObj {

    private static SyncObj syncer=null;
    public static synchronized SyncObj getInstance(){
        if(syncer==null)
            syncer=new SyncObj();
        return syncer;

    }

    public synchronized void waiter(){
        try {
            this.wait();
        } catch (InterruptedException e) {printErr("while waiting for boost");
        }
    }

    public synchronized void waiter(long sec){
        try {
            this.wait(sec*1000);
        } catch (InterruptedException e) {printErr("while waiting for boost");
        }
    }
    public synchronized void notifier(){
        this.notifyAll();
    }
}
