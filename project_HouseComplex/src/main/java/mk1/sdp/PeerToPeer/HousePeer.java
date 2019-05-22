package mk1.sdp.PeerToPeer;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import mk1.sdp.REST.RESTServer;
import mk1.sdp.REST.Resources.Home;
import mk1.sdp.misc.Pair;
import mk1.sdp.misc.Common;
import static mk1.sdp.misc.Common.responseHasError;

import org.glassfish.jersey.client.ClientConfig;


import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.Hashtable;
import java.util.Random;

public class HousePeer {
    public final int ID;
    public final String host;
    public final int port;
    public final String hostServer;
    private Client client;
    public WebTarget webTarget;

    public Hashtable<Integer, ManagedChannel> peerList ; //TODO decidere da cosa è composta la coppia, se da channel(GRPC) e/o socket(solo protobuf)


    public  static void main (String[] args){
        Random rand=new Random(System.nanoTime());
        int id=rand.nextInt(50)+1;
        int port=rand.nextInt(64511)+1024;  //to avoid the first 1024
        String host= "localhost";
        HousePeer peer =  new HousePeer(id, host, port, "http://localhost:1337");

       if( peer.registerToServer())
          Common.print("sono dentro");
       else{
           peer.closeConnection();
       }
    }

    private HousePeer(int ID,String host,int port, String hostServer){
        this.ID=ID;
        this.host=host;
        this.port=port;
        this.hostServer=hostServer;
        ClientConfig c=new ClientConfig();
        client= ClientBuilder.newClient(c);
        webTarget=client.target(getBaseURI());//todo get input da tastiera o args
    }

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
            if (retries[0]<5) {
                Common.printErr("Connection refused by server.\tretrying...");
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

        Common.print("case:"+h.length );
        for(Home x :h){
            ManagedChannel channel = ManagedChannelBuilder.forAddress(x.address, x.listeningPort).usePlaintext(true).build();
            peerList.put(x.HomeID,channel);
        }

        resp.close();

        Common.print("House "+ID+" registered SUCCESSFULLY!");

        return true;
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

    public void closeConnection(){
        client.close();
    }




}
