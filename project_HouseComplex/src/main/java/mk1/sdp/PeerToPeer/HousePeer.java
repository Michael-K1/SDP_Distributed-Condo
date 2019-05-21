package mk1.sdp.PeerToPeer;

import mk1.sdp.REST.Resources.Home;
import mk1.sdp.misc.Pair;
import mk1.sdp.misc.EasyPrinter;

import org.glassfish.jersey.client.ClientConfig;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.Hashtable;


public class HousePeer {
    public final int ID;
    public final String host;
    public final int port;
    public final String hostServer;
    private Client client;
    private WebTarget webTarget;

    public Hashtable<Integer, Pair<?,?>> peerList ; //TODO decidere da cosa è composta la coppia, se da channel(GRPC) e/o socket(solo protobuf)


    public  static void main (String[] args){
        HousePeer peer =  new HousePeer(2, "localhost", 5050, "http://localhost:1337");

       if( peer.registerToServer())
          EasyPrinter.print("sono dentro");
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

    private boolean registerToServer(){
        WebTarget wt=webTarget.path("/complex/add");
        Response resp=wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).post(Entity.entity(new Home(ID, host, port), MediaType.APPLICATION_JSON_TYPE));

        if(responseHasError(resp)) {    //if failed close everything
            resp.close();
            return false;

        }

        Home[] h=resp.readEntity(Home[].class);

        EasyPrinter.print("case:"+h.length );
        for(Home x :h){
            EasyPrinter.print(x.HomeID+"");
        }

        
        resp.close();
        return true;



    }

    private URI getBaseURI() {
        URI uri=null;

        try {
            uri = new URI("http://localhost:1337/");
        }catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return uri;
    }

    private void closeConnection(){
        client.close();
    }

    private boolean responseHasError(@NotNull Response resp){

        if(resp.getStatus()!=200){
            EasyPrinter.printErr("failed with HTTP error code: "+resp.getStatus());
            String error=resp.readEntity(String.class);
            EasyPrinter.printErr(error);
            return true;

        }
        return false;
    }


}
