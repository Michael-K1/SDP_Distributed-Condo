package mk1.sdp.PeerToPeer;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

import static mk1.sdp.misc.Common.*;

public class PeerServer implements Runnable{
    private final int id;
    private final String address;
    private final int port;
    private final Server server;

    public PeerServer(HousePeer parent){
        this.id=parent.ID;
        this.address=parent.host;
        this.port=parent.port;
        server= ServerBuilder.forPort(this.port)/*.addService()*/.build();  //todo creare il servizio grpc
    }

    @Override
    public void run() {
        this.startServer();
        this.blockUntilShutdown();
    }

    private void startServer(){
        try {
            server.start();
            print("[HOUSE "+id+"] starting server...".toUpperCase());
            Runtime.getRuntime().addShutdownHook(new Thread(){
                @Override
                public void run(){
                    System.err.println("shutting down gRPC server since JVM is shutting down...");
                    PeerServer.this.stop();

                }
            });
        } catch (IOException e) {
            printErr("while starting the server on port "+port+"...");
            e.printStackTrace();
        }
    }

    private void blockUntilShutdown(){
        if(server!=null){
            try {
                server.awaitTermination();
            } catch (InterruptedException e) {
                printErr("while waiting for termination");
                e.printStackTrace();
            }
        }
    }

    public void stop(){
        if(server!=null )
            server.shutdown();

    }
}
