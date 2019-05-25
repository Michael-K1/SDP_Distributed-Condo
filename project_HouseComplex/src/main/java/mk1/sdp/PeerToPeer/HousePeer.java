package mk1.sdp.PeerToPeer;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import mk1.sdp.PeerToPeer.Mutex.LamportClock;
import mk1.sdp.REST.RESTServer;
import mk1.sdp.REST.Resources.Home;
import mk1.sdp.misc.Common;
import mk1.sdp.misc.Pair;

import static mk1.sdp.misc.Common.*;


import org.glassfish.jersey.client.ClientConfig;
import simulation_src_2019.SmartMeterSimulator;


import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class HousePeer {
    public final int ID;
    public final String host;
    public final int port;
    public final String hostServer;
    public int coordinator=-1;

    private Scanner fromShell;
    private Client client;
    private WebTarget webTarget;
    private PeerServer listener;

    public final Hashtable<Integer, ManagedChannel> peerList ;
    public SmartMeterSimulator simulator;
    public final LamportClock lamportClock;
    private final MessageDispatcher mexDispatcher;

    public  static void main (String[] args){
        Random rand=new Random(System.nanoTime());
        int id=rand.nextInt(50)+1;
        int port=rand.nextInt((65535 - 49152) + 1) + 49152; //only using free ports
        String host= "localhost";

        HousePeer peer =  new HousePeer(id, host, port, "http://"+RESTServer.HOST+":"+RESTServer.PORT+"/");

        peer.start();

    }

    private HousePeer(int ID,String host,int port, String hostServer){
        this.ID=ID;
        this.host=host;
        this.port=port;
        this.hostServer=hostServer;
        peerList = new Hashtable<>();
        mexDispatcher=new MessageDispatcher(this);
        lamportClock = new LamportClock(ID);

        ClientConfig c=new ClientConfig();
        client= ClientBuilder.newClient(c);
        webTarget=client.target(getBaseURI());//todo get input da tastiera o args
    }

    private void start() {
        if (!registerToServer()) {
            client.close();
            return;
        }

        fromShell = new Scanner(System.in);


        simulator = new SmartMeterSimulator(new SlidingBuffer(this, 24, 0.5f));
        listener=new PeerServer(this);

        simulator.start();

        new Thread(listener).start();

        menu();

    }

    private void menu() {
        boolean isDeleted = false;

        while (!isDeleted){
            int val=-1;
            do{

                print("\n##########################################################");
                print("Press -1- to require a boost");
                print("Press -0- to exit the Complex");
                print("\n##########################################################");
                val= readInputInteger(fromShell, "input must be 0 or 1");

            }while(val!=0 && val!=1);

            if(val==0)
                isDeleted=deleteHouse();

            if(val==1){
                //todo chiedere il boost
            }
        }

    }

    //region REST INTERFACE
    private boolean registerToServer(int ...retries){
        WebTarget wt=webTarget.path("/complex/add");
        Response resp=null;

        try {
             resp = wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).post(Entity.entity(new Home(ID, host, port), MediaType.APPLICATION_JSON_TYPE));
        }catch (ProcessingException p){
            if(retries.length==0){
                Common.printErr("Connection refused by server.\tretrying...");
                return registerToServer(1);
            }
            if (retries[0]<=5) {
                Common.printErr("attempt "+retries[0]+". Connection refused by server.\tretrying...");
                return registerToServer(retries[0]+1);
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

        Home[] h=resp.readEntity(Home[].class);
        resp.close();

        print("House registered:"+h.length );
        if(h.length==0||(h.length==1 && h[0].HomeID==this.ID)){
            coordinator=this.ID;
            return true;
        }


        addPeers(h);

        print("House "+ID+" registered SUCCESSFULLY!");

        return true;
    }

    private void addPeers(Home[] h) {

        for(Home x :h){
            ManagedChannel channel = ManagedChannelBuilder.forAddress(x.address, x.listeningPort).usePlaintext(true).build();

            peerList.put(x.HomeID,channel);
        }

        mexDispatcher.addSelfToPeers();
        lamportClock.afterEvent();
    }

    private boolean deleteHouse(int ...tries){
        WebTarget wt = webTarget.path("/complex/delete").queryParam("id",ID);
        Response resp=null;

        try {
            resp = wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).delete();
        }catch (ProcessingException p){
            if(tries.length==0){
                Common.printErr("Connection refused by server.\tretrying...");
                return deleteHouse(1);
            }
            if (tries[0]<=5) {
                Common.printErr("attempt "+tries[0]+". Connection refused by server.\tretrying...");
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
        fromShell.close();
        simulator.stopMeGently();
        dropConnections();
        print("House "+ID+" successfully deleted!");


        return true;
    }

    private void dropConnections() {
        if(peerList.isEmpty()) {
            listener.stop();
            client.close();
            return;
        }

        ArrayList<ManagedChannel> copy =null;

        synchronized (peerList){
            copy=new ArrayList<>(peerList.values());
        }

        mexDispatcher.removeSelfFromPeers(copy);

        for(ManagedChannel x:copy){
            try{
                x.shutdown().awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                printErr("interrupted while shutdown house "+ID);
            }
        }

        listener.stop();
        client.close();
    }

    private URI getBaseURI() {
        URI uri=null;

        try {
            uri = new URI("http://"+ RESTServer.HOST+":"+RESTServer.PORT+"/");
        }catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return uri;
    }

    public void printMeasure(Pair<Long,Double> measure){
        print(measure.left+" "+measure.right);
    }
    //endregion
}
