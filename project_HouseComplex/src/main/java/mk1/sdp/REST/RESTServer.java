package mk1.sdp.REST;

import mk1.sdp.REST.Services.ComplexService;
import mk1.sdp.REST.Services.PushNotificationService;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.URI;


public class RESTServer {

    public static final String HOST = "localhost";
    public static final int PORT = 9421;


    public static void main(String[] args)  {
        HttpServer server = createServerREST("http://"+HOST+":"+PORT+"/");
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

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
        server.shutdown();
        System.out.println("Server stopped");
    }

    private static HttpServer createServerREST(String address){
        HttpServer server=null;
        server =GrizzlyHttpServerFactory.createHttpServer(URI.create(address),new ResourceConfig(ComplexService.class, PushNotificationService.class));

        return server;
    }
}
