package mk1.sdp.REST;

import mk1.sdp.REST.Resources.Complex;
import mk1.sdp.REST.Resources.Measure;


import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.internal.sonar.SonarJerseyCommon;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

import java.net.URISyntaxException;
import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Scanner;


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

        client= ClientBuilder.newClient();
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
    private int printMenu(){
        int val=-1;
        do {
            print("#############################");
            print("Press -1- to obtain the list of the houses in the complex");
            print("Press -2- to obtain the list of the last statistics of a House");
            print("Press -3- to obtain the list of the last statistics of the complex");
            print("Press -4- to obtain the Mean and Standard Deviation of the last N statistics of a house");
            print("Press -5- to obtain the Mean and Standard Deviation of the last N statistics of  the complex");
            print("Press -0- to close the administrator client");
            print("#############################\n");
            val= readInput("input must be between 0 and 5");

        }while(val<0||val>5);
        return val;
    }
    private int readInput(String inputMismatch) {
        int val=-1;

        try {
            val=fromShell.nextInt();

        } catch (InputMismatchException e){
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




    //region REST QUERY
    private void getHouseList() {

        WebTarget rootPath = webTarget.path("complex");


        Response response= rootPath.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).get();
        if(!checkResponse(response)) return;

        printHigh("output from server: ");

        Complex comp= response.readEntity(Complex.class);
        print(  comp.complex.toString());
        print(  "dim "+comp.complex.size());

    }


    private void getLastLocalN() {
        int id=-1;
        int n=0;
        do{
            print("Insert the ID of the house:\n");
            id=readInput("input cannot be negative...");
        }while(id<0);

        do{
            print("Insert the number of wanted statistics :\n");
            n=readInput("input cannot be negative...");
        }while(n<0);

        //WebResource rootPath = webResource.path("/complex/local/stat?id="+id+"_n="+n);



    }

    private void getLastGlobalN() {

    }

    private void getLocalMeanDev() {

    }

    private void getGlobalMeanDev() {

    }
    //endregion


    private boolean checkResponse(@NotNull Response resp){

        if(resp.getStatus()!=200){
            printErr("failed with HTTP error code: "+resp.getStatus());
            String error=resp.readEntity(String.class);
            printErr(error);
            return false;

        }
        return true;
    }



    //easyPrint
    private void printErr(String s){ System.err.println("[ERROR]: "+s.toUpperCase()+"...");}
    private void printHigh(String s){ System.out.println("[ADMIN]: "+s.toUpperCase());}
    private void print(String s){ System.out.println(s);}


}
