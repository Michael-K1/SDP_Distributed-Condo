package mk1.sdp.REST;

import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;


public class RESTServer {

    public static final String HOST = "localhost";
    public static final int PORT = 1337;


    public static void main(String[] args)  {
        HttpServer server = createServerREST("http://"+HOST+":"+PORT+"/");
        server.start();

        System.out.println("Server running!");
        System.out.println("Server started on: http://"+HOST+":"+PORT);

        System.out.println("Hit return to stop...");

        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("error while reading from standard input of the server REST...\n".toUpperCase()+e.getMessage());

        }

        System.out.println("Stopping server");
        server.stop(0);
        System.out.println("Server stopped");
    }

    private static HttpServer createServerREST(String address){
       HttpServer server=null;
        try {
           server= HttpServerFactory.create(address);
        } catch (IOException e) {
            System.err.println("error while creating the server rest...\n".toUpperCase()+e.getMessage());
            e.printStackTrace();
        }
        return server;
    }
}
