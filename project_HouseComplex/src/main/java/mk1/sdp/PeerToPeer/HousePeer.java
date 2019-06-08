package mk1.sdp.PeerToPeer;

import simulation_src_2019.SmartMeterSimulator;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import mk1.sdp.PeerToPeer.Mutex.LamportClock;
import mk1.sdp.REST.RESTServer;
import mk1.sdp.REST.Resources.Home;
import mk1.sdp.misc.Common;
import mk1.sdp.misc.Pair;

import static mk1.sdp.misc.Common.*;

import org.glassfish.jersey.client.ClientConfig;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class HousePeer {
    public final int HomeID;
    public final String host;
    public final int port;
    public final String hostServer;
    private int coordinator=-1;

    private Scanner fromShell;
    private Client client;
    private WebTarget serverREST;
    private PeerServer listener;

    public final Hashtable<Integer, ManagedChannel> peerList ;
    private SmartMeterSimulator simulator;
    private final MessageDispatcher mexDispatcher;

    public final LamportClock lamportClock;

    public  static void main (String[] args){
        Random rand=new Random(System.nanoTime());
        int id=rand.nextInt(50)+1;
        int port=rand.nextInt((65535 - 49152) + 1) + 49152; //only using free ports
        String host= "localhost";

        HousePeer peer =  new HousePeer(id, host, port, "http://"+RESTServer.HOST+":"+RESTServer.PORT+"/");

        peer.start();

    }

    private HousePeer(int ID,String host,int port, String hostServer){
        this.HomeID =ID;
        this.host=host;
        this.port=port;
        this.hostServer=hostServer;
        peerList = new Hashtable<>();
        lamportClock = new LamportClock(HomeID);

        ClientConfig c=new ClientConfig();
        client= ClientBuilder.newClient(c);
        serverREST =client.target(getBaseURI());
        mexDispatcher=new MessageDispatcher(this,serverREST);
    }

    private void start() {
        if (!registerToServer()) {
            client.close();
            return;
        }

        fromShell = new Scanner(System.in);
        simulator = new SmartMeterSimulator(new SlidingBuffer(this, 24, 0.5f));
        listener  = new PeerServer(this);

        simulator.start();

        new Thread(listener).start();

        menu();
    }

    private void menu() {
        boolean isDeleted = false;
        printMenu();
        while (!isDeleted){
            int choice=-1;
            do{
                choice= readInputInteger(fromShell, "input must be between 0 and 3");

            }while(choice<0 || choice>4);

            switch (choice){
                case 0: isDeleted=deleteHouse();
                    break;
                case 1:mexDispatcher.ricartAgrawala(getFullPeerListCopy());
                    break;
                case 2: if (isCoordinator())
                            print("I am the Coordinator!");
                        else
                            print("Coordinator is: "+coordinator);
                    break;
                case 3: Set<Integer> keys;
                        synchronized (peerList) {
                             keys = peerList.keySet();
                        }
                        for(int x:keys){
                            print("House: "+x);
                        }
                    break;
                case 4: printMenu();
                    break;
            }
        }
    }


    private void printMenu(){
        print("\n##########################################################\n"+
                "Press -1- to require a boost\n"+
                "Press -2- to show the coordinator\n"+
                "Press -3- to show the house registered in the complex\n"+
                "Press -4- to print this menu\n"+
                "Press -0- to exit the Complex\n"+
                "##########################################################\n"
        );
    }

    //region APPLICATION START
    private boolean registerToServer(){
        WebTarget wt= serverREST.path("/complex/add");
        Response resp= tryConnection(wt,true);

        if(resp==null) return false;

        if(responseHasError(resp)) {    //if failed close everything
            resp.close();
            return false;
        }

        Home[] h=resp.readEntity(Home[].class);
        resp.close();

        if(h.length==0||(h.length==1 && h[0].HomeID==this.HomeID)){
            coordinator=this.HomeID;
        }

        addPeers(h);

        print("House registered in complex:"+h.length );
        print("House "+ HomeID +" registered SUCCESSFULLY!");

        return true;
    }

    private void addPeers(Home[] h) {
        for(Home x :h){
            ManagedChannel channel = ManagedChannelBuilder.forAddress(x.address, x.listeningPort).usePlaintext(true).build();

            peerList.put(x.HomeID,channel);
        }

        mexDispatcher.addSelfToPeers(getFullPeerListCopy());
        lamportClock.afterEvent();
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

    //endregion
    //region APPLICATION END

    private boolean deleteHouse(int ...tries){
        WebTarget wt = serverREST.path("/complex/delete").queryParam("id", HomeID);
        Response resp=tryConnection(wt,false );

        if(resp==null) return false;

        if(responseHasError(resp)) {    //if failed close everything
            resp.close();
            return false;

        }
        resp.close();
        fromShell.close();
        simulator.stopMeGently();
        dropConnections();
        print("House "+ HomeID +" successfully deleted!");


        return true;
    }
    private void dropConnections() {
        if(peerList.isEmpty()) {
            listener.stop();
            client.close();
            return;
        }

        List<ManagedChannel> copy = getFullPeerListCopy();

        synchronized (peerList){
            peerList.clear();
        }

        mexDispatcher.removeSelfFromPeers(copy);

        for(ManagedChannel chan:copy){
            try{
                if(!chan.isShutdown())
                    chan.shutdown().awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                printErr("interrupted while shutdown house "+ HomeID);
            }
        }

        listener.stop();
        client.close();
    }

    //endregion

    private Response tryConnection(WebTarget wt,boolean registration, int ...retries){
        Response resp=null;
        try {
            if(registration)
                resp = wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).post(Entity.entity(new Home(HomeID, host, port), MediaType.APPLICATION_JSON_TYPE));
            else//remotion
                resp = wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).delete();
        }catch (ProcessingException p){
            if(retries.length==0){
                Common.printErr("Connection refused by server.\tretrying...");
                return tryConnection(wt,registration,1);
            }
            if (retries[0]<=5) {
                Common.printErr("attempt "+retries[0]+". Connection refused by server.\tretrying...");
                return tryConnection(wt,registration,retries[0]+1);
            }
            else {
                Common.printErr("unable to connect to server.\n Closing program...");
                return null;
            }
        }
        return resp;
    }

    public void broadcastLocalStat(Pair<Long,Double> measure){
        mexDispatcher.sendToPeer(getFullPeerListCopy(), measure);
    }

    //region GETTER/SETTER
    public int getCoordinator() {
        return coordinator;
    }

    public synchronized void setCoordinator(int coord) {
        if(this.coordinator==coord)return;

        this.coordinator = coord;
    }

    public MessageDispatcher getMexDispatcher() {
        return mexDispatcher;
    }
    public SmartMeterSimulator getSimulator(){
        return this.simulator;
    }
    public synchronized boolean isCoordinator(){
        return coordinator==this.HomeID;
    }
    public synchronized boolean isCoordinator(int id){
        if(coordinator==-1)return false;
        return coordinator==id;
    }

    public  List<ManagedChannel> getFullPeerListCopy(){
        List<ManagedChannel> copy;
        synchronized (peerList){
             copy = new ArrayList<>(peerList.values());
        }
        return copy;
    }

    public List<ManagedChannel> getGTPeerListCopy() {   //greater id then this.is
        List<ManagedChannel> tmp=new ArrayList<>();
        synchronized (peerList){
            for (Integer key : peerList.keySet()) {
                if(key>this.HomeID)
                    tmp.add(peerList.get(key));
            }
        }
        return tmp;
    }

    public List<ManagedChannel> getSetPeerList(List<Integer> queue){
        List<ManagedChannel> copy=new ArrayList<>(queue.size());
        synchronized (peerList){
            for (Integer key : queue) {
                if(peerList.containsKey(key))
                    copy.add(peerList.get(key));
            }
        }

        return copy;
    }
    //endregion
}
