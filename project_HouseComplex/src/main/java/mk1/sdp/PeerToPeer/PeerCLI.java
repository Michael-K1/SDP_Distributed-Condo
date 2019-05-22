package mk1.sdp.PeerToPeer;

import mk1.sdp.misc.Common;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Scanner;

import static mk1.sdp.misc.Common.print;
import static mk1.sdp.misc.Common.responseHasError;

public class PeerCLI implements Runnable{
    private Scanner fromShell;
    private HousePeer parent;
    public PeerCLI(HousePeer h){
        fromShell=new Scanner(System.in);
        //parent.closeConnection();
    }
    @Override
    public void run() {
        while (true){

        }
    }

    private boolean deleteHouse(int ...tryes){
        WebTarget wt = parent.webTarget.path("/complex/delete").queryParam("id",parent.ID);
        Response resp=null;

        try {
            resp = wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).delete();
        }catch (ProcessingException p){
            if(tryes.length==0){
                Common.printErr("Connection refused by server.\tretrying...");
                 return deleteHouse(1);
            }
            if (tryes[0]<5) {
                Common.printErr("Connection refused by server.\tretrying...");
                return deleteHouse(tryes[0]+1);
            }
            else {
                Common.printErr("unable to connect to server.\n Closing program...");
                return false;
            }
        }

        if(responseHasError(resp)) return false;

        print("House "+parent.ID+" successfully deleted!");
        return true;
    }
}
