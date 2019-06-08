package mk1.sdp.REST;


import mk1.sdp.REST.Resources.Complex;
import mk1.sdp.REST.Resources.Home;
import mk1.sdp.misc.Pair;

import org.glassfish.jersey.client.ClientConfig;


import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


import java.net.URI;

import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static mk1.sdp.misc.Common.*;
//https://jersey.github.io/apidocs/2.25.1/jersey/index.html

public class Administrator {
    private Client client=null;
    private WebTarget webTarget;
    private Scanner fromShell;

    public static void main (String[] args){
        Administrator admin =new Administrator();

        admin.Menu();
    }

    private Administrator(){
        fromShell=new Scanner(System.in);

        ClientConfig c=new ClientConfig();
        client= ClientBuilder.newClient(c);
        webTarget=client.target(getBaseURI());
    }

    private URI getBaseURI() {
        URI uri=null;

        try {
            uri = new URI("http://"+RESTServer.HOST+":"+RESTServer.PORT+"/");
        }catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return uri;
    }

    private void Menu(){
        int choice=0;

        while(true){
            choice=printMenu();

            switch (choice){
                case 1: getHouseList();
                    break;
                case 2: getLastLocalN();
                    break;
                case 3: getLastGlobalN();
                    break;
                case 4: getLocalMeanDev();
                    break;
                case 5: getGlobalMeanDev();
                    break;
                default:
                        printHigh("admin","closing the client...");
                        fromShell.close();      //closing the input stream before leaving
                        return;
            }

        }
    }

    //region REST QUERY
    private void getHouseList() {

        WebTarget wt = webTarget.path("complex");

//        Response response= wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).get();
        Response response= obtainResponse(wt);

        if(response==null)return;

        if(responseHasError(response)) return;

        printHigh("admin","output from server: ");

        Complex comp= response.readEntity(Complex.class);

        print(  "Houses in Complex: "+comp.complex.size());

        for(Home h: comp.complex.values()){
            print("HomeID:"+h.HomeID+"\n\t Host: "+h.address+"\n\t Port: "+h.listeningPort);
        }
        response.close();

    }

    private void getLastLocalN() {
        Pair<Integer, Integer> p = askParam(true);

        WebTarget wt=webTarget.path("/complex/house/stat").queryParam("id", p.left).queryParam("n", p.right);
        Response response= obtainResponse(wt);
        if(response==null)return;

        if(responseHasError(response)) return;

        printStatistics(response,p.left);

        response.close();
    }

    private void getLastGlobalN() {
        Pair<Integer, Integer> p = askParam(false);

        WebTarget wt=webTarget.path("/complex/global/stat").queryParam("n", p.right);

        Response response= obtainResponse(wt);
        if(response==null)return;


        if(responseHasError(response)) return;
        printStatistics(response,p.left);
        response.close();
    }

    private void getLocalMeanDev() {
        Pair<Integer, Integer> p = askParam(true);

        WebTarget wt=webTarget.path("/complex/house/meanDev").queryParam("id", p.left).queryParam("n", p.right);
        Response response= obtainResponse(wt);
        if(response==null)return;

        if(responseHasError(response)) return;

        printMeanDev(response,p.left);
        response.close();
    }

    private void getGlobalMeanDev() {
        Pair<Integer, Integer> p = askParam(false);

        WebTarget wt=webTarget.path("/complex/global/meanDev").queryParam("n", p.right);
        Response response= obtainResponse(wt);
        if(response==null)return;

        if(responseHasError(response)) return;

        printMeanDev(response,p.left);
        response.close();
    }

    //endregion



    private Pair<Integer,Integer> askParam(boolean isHouse) {
        int id = -1;
        int n = 0;

        if(isHouse) {

            do {
                print("Insert the HomeID of the house:\n");
                id = readInputInteger(fromShell,"input must be of positive digit");
            } while (id < 0);
        }

        do {
            print("Insert the number of wanted statistics :\n");
            n = readInputInteger(fromShell,"input must be of positive digit");
        } while (n < 0);
        return Pair.of(id,n);
    }

    private Response obtainResponse(WebTarget wt, int ...retries){
        Response response=null;
        try {
            response = wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).get();
        }catch(ProcessingException e){
            if(retries.length==0){
                printErr("Connection refused by server.\tretrying...");
                timeWaster(5);
                return obtainResponse(wt,1);
            }
            if (retries[0]<5) {
                printErr("Connection refused by server.\tretrying...");
                timeWaster(5);
                return obtainResponse(wt,retries[0]+1);
            }
            else {
                printErr("unable to connect to server.\n try again later...");
                return null;
            }
        }
        return response;
    }


    //region PrettyPrint

    private int printMenu(){
        int val=-1;
        do {
            print("##########################################################\n"+
                    "Press -1- to obtain the list of the houses in the complex\n"+
                    "Press -2- to obtain the list of the last statistics of a House\n"+
                    "Press -3- to obtain the list of the last statistics of the complex\n"+
                    "Press -4- to obtain the Mean and Standard Deviation of the last N statistics of a house\n"+
                    "Press -5- to obtain the Mean and Standard Deviation of the last N statistics of  the complex\n"+
                    "Press -0- to close the administrator client\n"+
                    "##########################################################\n");
            val= readInputInteger(fromShell,"input must be between 0 and 5");

        }while(val<0||val>5);
        return val;
    }

    private void printStatistics(Response resp, int id){
        String pretty=    id==-1?"Complex :":"House "+id+":";
        String prettyErr= id==-1?"Complex":"House "+id;

        Pair[] mes=resp.readEntity(Pair[].class);
        if(mes==null || mes.length<1){
            print(prettyErr+" hasn't any statistics so far...");
            return;
        }

        printHigh("admin","output from server: ");
        print("Last "+mes.length+" statistics of "+pretty);

        for(Pair<Long,Double> m:convertPairs(mes)){
            printMeasure(pretty, m);
        }

    }

    private void printMeanDev(Response response, int id){
        String pretty=    id==-1?"the Complex":"House "+id;
        String prettyErr= id==-1?"The Complex":"The House "+id;

        Pair meanDev = response.readEntity(Pair.class);


        if (meanDev==null || !(meanDev.left instanceof Double) || !(meanDev.right instanceof Double)){
            print(prettyErr + " hasn't any statistics for the calculation yet...");
            return;
        }

        printHigh("admin","output from server: ");
        print("The mean and standard deviation of "+pretty+" are:"
                +"\n\t Mean= "+meanDev.left
                +"\n\t StdDev= "+meanDev.right);

    }
    //endregion

    private List<Pair<Long,Double>> convertPairs(Pair[] origin){
        List<Pair<Long,Double>> tmp=new ArrayList<>();

        for (Pair x:origin) {

            long x1=(Long)x.left;
            Double x2=(Double)x.right;

            tmp.add(Pair.of(x1, x2));

        }
        return tmp;

    }

}