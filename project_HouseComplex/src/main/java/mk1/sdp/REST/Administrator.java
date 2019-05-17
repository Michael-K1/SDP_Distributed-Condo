package mk1.sdp.REST;


import mk1.sdp.REST.Resources.Complex;
import mk1.sdp.REST.Resources.Home;
import mk1.sdp.misc.Pair;

import org.glassfish.jersey.client.ClientConfig;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.net.URI;

import java.net.URISyntaxException;
import java.util.*;
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
            uri = new URI("http://localhost:1337/");
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
                default:printHigh("closing the client...");
                        fromShell.close();      //closing the input stream before leaving
                        return;
            }

        }
    }

    //region REST QUERY
    private void getHouseList() {

        WebTarget wt = webTarget.path("complex");

        Response response= wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).get();
        if(responseHasError(response)) return;

        printHigh("output from server: ");

        Complex comp= response.readEntity(Complex.class);

        print(  "Houses in Complex: "+comp.complex.size());

        for(Home h: comp.complex.values()){
            print("ID:"+h.HomeID+"\n\t Host: "+h.address+"\n\t Port: "+h.listeningPort);
        }
        response.close();

    }

    private void getLastLocalN() {
        Pair<Integer, Integer> p = askParam(true);


        WebTarget wt=webTarget.path("/complex/house/stat").queryParam("id", p.first).queryParam("n", p.second);
        Response response = wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).get();
        if(responseHasError(response)) return;

        printStatistics(response,p.first);

        response.close();
    }

    private void getLastGlobalN() {
        Pair<Integer, Integer> p = askParam(false);

        WebTarget wt=webTarget.path("/complex/global/stat").queryParam("n", p.second);
        Response response = wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).get();

        if(responseHasError(response)) return;
        printStatistics(response,p.first);
        response.close();
    }

    private void getLocalMeanDev() {
        Pair<Integer, Integer> p = askParam(true);

        WebTarget wt=webTarget.path("/complex/house/meanDev").queryParam("id", p.first).queryParam("n", p.second);
        Response response = wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).get();

        if(responseHasError(response)) return;

        printMeanDev(response,p.first);
        response.close();
    }

    private void getGlobalMeanDev() {
        Pair<Integer, Integer> p = askParam(false);

        WebTarget wt=webTarget.path("/complex/global/meanDev").queryParam("n", p.second);
        Response response = wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).get();

        if(responseHasError(response)) return;

        printMeanDev(response,p.first);
        response.close();
    }

    //endregion

    private int readInput(String inputMismatch) {
        int val=-1;

        try {
            val=fromShell.nextInt();

        } catch(InputMismatchException e){
            printErr(inputMismatch);
            fromShell.nextLine();
        }catch(NoSuchElementException e){
            printErr("malfunction with the scanner");
            fromShell.nextLine();
        }catch(IllegalStateException e){
            printErr("scanner closed.\n attempt to reopen it...");
            fromShell=new Scanner(System.in);
            fromShell.nextLine();

        }

        return val;
    }

    private Pair<Integer,Integer> askParam(boolean isHouse) {
        int id = -1;
        int n = 0;

        if(isHouse) {

            do {
                print("Insert the ID of the house:\n");
                id = readInput("input cannot be negative...");
            } while (id < 0);
        }

        do {
            print("Insert the number of wanted statistics :\n");
            n = readInput("input cannot be negative...");
        } while (n < 0);
        return new Pair<>(id,n);
    }

    private boolean responseHasError(@NotNull Response resp){

        if(resp.getStatus()!=200){
            printErr("failed with HTTP error code: "+resp.getStatus());
            String error=resp.readEntity(String.class);
            printErr(error);
            return true;

        }
        return false;
    }


    //region PrettyPrint

    private int printMenu(){
        int val=-1;
        do {
            print("\n##########################################################");
            print("Press -1- to obtain the list of the houses in the complex");
            print("Press -2- to obtain the list of the last statistics of a House");
            print("Press -3- to obtain the list of the last statistics of the complex");
            print("Press -4- to obtain the Mean and Standard Deviation of the last N statistics of a house");
            print("Press -5- to obtain the Mean and Standard Deviation of the last N statistics of  the complex");
            print("Press -0- to close the administrator client");
            print("##########################################################\n");
            val= readInput("input must be between 0 and 5");

        }while(val<0||val>5);
        return val;
    }

    private void printStatistics(Response resp, int id){
        String pretty=    id==-1?"the Complex :":"House "+id+":";
        String prettyErr= id==-1?"The Complex":"Thh House "+id;

        Pair[] mes=resp.readEntity(Pair[].class);
        if(mes==null || mes.length<1){
            print(prettyErr+" hasn't any statistics so far...");
            return;
        }

        printHigh("output from server: ");
        print("Last "+mes.length+" statistics of "+pretty);
        for(Pair m:mes){
            print("Time: "+m.first+" --> "+m.second+" kW"); //todo pretty print time
        }

    }

    private void printMeanDev(Response response, int id){
        String pretty=    id==-1?"the Complex":"House "+id;
        String prettyErr= id==-1?"The Complex":"The House "+id;

        Pair meanDev = response.readEntity(Pair.class);


        if (meanDev==null || !(meanDev.first instanceof Double) || !(meanDev.second instanceof Double)){
            print(prettyErr + " hasn't any statistics for the calculation yet...");
            return;
        }

        printHigh("output from server: ");
        print("The mean and standard deviation of "+pretty+" are:"
                +"\n\t Mean= "+meanDev.first
                +"\n\t StdDev= "+meanDev.second);

    }
    //endregion

    //region easyPrint
    private void printErr(String s){ System.err.println("[ERROR]: "+s.toUpperCase()+"...");}
    private void printHigh(String s){ System.out.println("[ADMIN]: "+s.toUpperCase());}
    private void print(String s){ System.out.println(s);}
    //endregion
}