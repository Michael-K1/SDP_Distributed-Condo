package mk1.sdp.PeerToPeer;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static mk1.sdp.misc.Common.*;

public class PeerServer implements Runnable{
    private final int id;
    private final String address;
    private final int port;
    private final Server server;
    private final HousePeer parent;

    public PeerServer(HousePeer parent){
        this.id=parent.ID;
        this.address=parent.host;
        this.port=parent.port;
        this.parent=parent;
        server= ServerBuilder.forPort(this.port).addService(new HouseManagementService(parent)).build();
    }

    @Override
    public void run() {
        this.startServer();
        this.blockUntilShutdown();
    }

    private void startServer(){
        try {
            server.start();
            printHigh("[HOUSE "+id+"]"," starting server...");
            Runtime.getRuntime().addShutdownHook(new Thread(){
                @Override
                public void run(){
                    System.err.println("[HOUSE "+ id +"]shutting down gRPC server since JVM is shutting down...".toUpperCase());
                    PeerServer.this.stop();

                }
            });
        } catch (IOException e) {
            printErr("while starting the server on port "+port+"...");
            e.printStackTrace();
        }

        parent.lamportClock.afterEvent();
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
        if(server!=null ) {
            try {
                server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                //e.printStackTrace();
                printErr("while waiting for termination");
            }
        }

    }
}
