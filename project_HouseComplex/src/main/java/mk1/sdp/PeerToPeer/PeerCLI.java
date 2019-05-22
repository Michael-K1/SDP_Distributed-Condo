package mk1.sdp.PeerToPeer;

import mk1.sdp.misc.Common;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Scanner;

import static mk1.sdp.misc.Common.print;
import static mk1.sdp.misc.Common.readInputInteger;
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

        boolean isDeleted = false;
        while (!isDeleted){
            int val=-1;
            do{

                print("\n##########################################################");
                print("Press -1- to require a boost");
                print("Press -0- to exit the Complex");
                print("\n##########################################################");
                val= readInputInteger(fromShell, "input must be 0 or 1");

            }while(val!=0 || val!=1);

            if(val==0)
                isDeleted=deleteHouse();

            if(val==1){
                //todo chiedere il boost al parent
            }
        }

        print("Farewell!");
        //  parent.chiudi tutto
    }

    private boolean deleteHouse(int ...tries){
        WebTarget wt = parent.webTarget.path("/complex/delete").queryParam("id",parent.ID);
        Response resp=null;

        try {
            resp = wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).delete();
        }catch (ProcessingException p){
            if(tries.length==0){
                Common.printErr("Connection refused by server.\tretrying...");
                 return deleteHouse(1);
            }
            if (tries[0]<5) {
                Common.printErr("Connection refused by server.\tretrying...");
                return deleteHouse(tries[0]+1);
            }
            else {
                Common.printErr("unable to connect to server.\n Closing program...");
                return false;
            }
        }

        if(responseHasError(resp)) {    //if failed close everything
            resp.close();
            return false;

        }
        resp.close();
        print("House "+parent.ID+" successfully deleted!");
        return true;
    }
}
