package mk1.sdp.PeerToPeer;

import io.grpc.ManagedChannel;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import mk1.sdp.GRPC.HouseManagementGrpc;
import mk1.sdp.GRPC.PeerMessages.*;

import mk1.sdp.PeerToPeer.Mutex.LamportClock;
import mk1.sdp.misc.Pair;
import mk1.sdp.PeerToPeer.Mutex.SyncObj;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static mk1.sdp.misc.Common.*;

class MessageDispatcher {
    private final int id;
    private final HousePeer parent;
    private final String address;
    private final int port;
    private final WebTarget toLocalStat;
    private final WebTarget toGlobalStat;

    //mutex
    private final LamportClock lampClock;

    private boolean usingBoost;

    private boolean askingBoost;


    MessageDispatcher(HousePeer parent, WebTarget server){
        this.parent=parent;
        synchronized (this.parent){
            this.id=parent.HomeID;
            this.address=parent.host;
            this.port=parent.port;
            this.lampClock=parent.lamportClock;
        }

        toLocalStat = server.path("/complex/house/add").queryParam("id", id);
        toGlobalStat = server.path("/complex/global/add");

        setUsingBoost(false);
    }

    //region NETWORK MESSAGES
    void addSelfToPeers(List<ManagedChannel> copy){
        SelfIntroduction selfIntro = SelfIntroduction.newBuilder().setId(id).setAddress(address).setPort(port).build();

        final  Pair<Integer, Integer> p = Pair.of(0,-1);

        StreamObserver<Ack> respObs=new StreamObserver<Ack>() {

            @Override
            public void onNext(Ack ack) {

                print(ack.getMessage());
                int coord=ack.getCoordinator();
                synchronized (p){
                    if(coord!=-1 && p.right!=coord){
                        p.right=coord;
                    }
                }

                lampClock.checkLamport(ack);
            }

            @Override
            public void onError(Throwable throwable) {
                StatusRuntimeException t=(StatusRuntimeException)throwable;
                if(t.getStatus().isOk()) return;
                printErr("Async self introduction "+ t.getMessage()+ " status= "+t.getStatus());
                //throwable.getCause().printStackTrace();
                if(t.getStatus().getCode()== Status.Code.DEADLINE_EXCEEDED){
                    printErr("deadline problem detected during self introduction");
                }
            }

            @Override
            public void onCompleted() {
                synchronized (p){
                    p.left+=1;
                    print("[HOUSE "+id+"] successful introduction: "+p.left+"/"+copy.size());

                    if(p.right!=-1){
                        synchronized (parent){
                            if(parent.getCoordinator() !=p.right)
                                parent.setCoordinator(p.right);
                        }
                    }
                }


            }
        };

        copy.stream().parallel().forEach(chan -> HouseManagementGrpc.newStub(chan).addHome(selfIntro, respObs));
//        copy.stream().parallel().forEach(chan -> HouseManagementGrpc.newStub(chan).withDeadlineAfter(5, TimeUnit.SECONDS).addHome(selfIntro, respObs));

        lampClock.afterEvent();
    }

    void removeSelfFromPeers(List<ManagedChannel> copy){
        SelfIntroduction selfIntro = SelfIntroduction.newBuilder().setId(id).build();

        final int[] count = {0};
        StreamObserver<Ack> respObs=new StreamObserver<Ack>() {
            @Override
            public void onNext(Ack ack) {
                if(ack.getAck())
                    print(ack.getMessage());
                lampClock.checkLamport(ack);
            }

            @Override
            public void onError(Throwable throwable) {
                StatusRuntimeException t=(StatusRuntimeException)throwable;
                if(t.getStatus().isOk()) return;
                printErr("Async self deletion "+ t.getMessage()+" status "+t.getStatus());
                // throwable.printStackTrace();
                if(t.getMessage().toUpperCase().matches("(.*)DEADLINE_EXCEEDED(.*)")){
                    printErr("deadline problem detected while removing self");
                }

            }

            @Override
            public void onCompleted() {
                synchronized (MessageDispatcher.this) {
                    count[0] += 1;
                    print("[HOUSE "+id+"] successful removal: "+count[0]+"/"+copy.size());
                }

            }
        };

        SyncObj.getInstance().notifier();     //to notify whomever is waiting form "this" in the boost queue

        copy.stream().parallel().forEach(chan->HouseManagementGrpc.newStub(chan).removeHome(selfIntro, respObs));
//        copy.stream().parallel().forEach(chan->HouseManagementGrpc.newStub(chan).withDeadlineAfter(5, TimeUnit.SECONDS).removeHome(selfIntro, respObs));
    }

    void sendGlobalStatistics(Pair<Long, Double> measure){
        if (!parent.isCoordinator())return;

        sendToServer(toGlobalStat, measure);

        List<ManagedChannel> copy=parent.getFullPeerListCopy();

        Measure globalMean= Measure.newBuilder().setMeasurement(measure.right).setTimeStamp(measure.left).build();

        StreamObserver<Ack> respObs=new StreamObserver<Ack>() {
            @Override
            public void onNext(Ack ack) {
               lampClock.checkLamport(ack);

            }

            @Override
            public void onError(Throwable throwable) {
                StatusRuntimeException t=(StatusRuntimeException)throwable;
                if(t.getStatus().isOk()) return;
                printErr(" during global mean deliverance "+t.getStatus());

            }

            @Override
            public void onCompleted() {

            }
        };

        copy.stream().parallel().forEach(chan->HouseManagementGrpc.newStub(chan).sendGlobalMean(globalMean,respObs));
        lampClock.afterEvent();
    }

    void sendToPeer(List<ManagedChannel> copy, Pair<Long, Double> measure) {

        sendToServer(toLocalStat, measure);

        Measure newMean= Measure.newBuilder().setSenderID(id).setTimeStamp(measure.left).setMeasurement(measure.right).build();

        final Pair<Integer, Boolean> respVal = Pair.of(0, false);

        StreamObserver<Ack> respObs=new StreamObserver<Ack>() {
            @Override
            public void onNext(Ack ack) {
                lampClock.checkLamport(ack);

                synchronized (respVal){
                    int x=ack.getCoordinator();
                    if (parent.isCoordinator(x) && !respVal.right){
                        respVal.right=true;                          //the coordinator has answered
                    }
                }
            }

            @Override
            public void onError(Throwable throwable) {
                StatusRuntimeException t=(StatusRuntimeException)throwable;
                synchronized (respVal){
                    respVal.left+=1;
                }
                if(t.getStatus().isOk()) return;
                printErr("during peer broadcast "+t.getStatus());

                if(t.getMessage().toUpperCase().matches("(.*)DEADLINE_EXCEEDED(.*)")){
                    printErr("deadline problem detected");
                }
            }

            @Override
            public void onCompleted() {
                synchronized (respVal){
                    respVal.left+=1;

                    if(respVal.left==copy.size())
                        if(!respVal.right){

                            printErr("election needed");
                            startElection();

                        }

                }
            }
        };
        copy.stream().parallel().forEach(chan-> HouseManagementGrpc.newStub(chan).sendMeasure(newMean, respObs));
//        copy.stream().parallel().forEach(chan-> HouseManagementGrpc.newStub(chan).withDeadlineAfter(5, TimeUnit.SECONDS).sendMeasure(newMean, respObs));

        lampClock.afterEvent();
    }

    /**
     * REST request to server
     * @param wt = target of the request (MUST HAVE THE CORRECT PATH)
     * @param measure = measure to be sent to the server
     * @return true if it was NOT able to send a request to the server
     */
    private boolean sendToServer(WebTarget wt, Pair<Long, Double> measure, int ...retries) {
        Response resp=null;
        try {
            print(wt.getUri().getPath());
             resp = wt.request(MediaType.APPLICATION_JSON).header("content-type", MediaType.APPLICATION_JSON).put(Entity.entity(measure, MediaType.APPLICATION_JSON_TYPE));
        }catch (ProcessingException e){
            if(retries.length==0){
                printErr("server unreachable.\tretrying...");
                timeWaster(5);
                return sendToServer(wt,measure,1);
            }
            if (retries[0]<=5) {
                printErr("attempt "+retries[0]+". server unreachable.\tretrying...");
                timeWaster(5);
                return sendToServer(wt,measure,retries[0]+1);
            }
            else {
                printErr("unable to connect to server.");
                return true;
            }

        }catch(IllegalStateException e){
            printErr("connection to server closed");
        }
        if(resp==null) return true;
        return responseHasError(resp);
    }

    //region ELECTION HANDLING
    public void startElection(){
        List<ManagedChannel> copy = parent.getGTPeerListCopy();
        if(copy.size()==0){
            becomeCoordinator();//I am new coordinator
            return;
        }
        Coordinator coord= Coordinator.newBuilder().setCoordinatorID(id).build();

        StreamObserver<Ack> respObs=new StreamObserver<Ack>() {
            @Override
            public void onNext(Ack ack) {
                printHigh("house "+id, "asked to be coordinator to "+ack.getMessage());
                lampClock.checkLamport(ack);
            }

            @Override
            public void onError(Throwable throwable) {
                StatusRuntimeException t=(StatusRuntimeException)throwable;
                if(t.getStatus().isOk()) return;
                printErr("during start election " +t.getStatus());


                if(t.getStatus().equals(Status.DEADLINE_EXCEEDED)){ //only happens when a message is sent to the previous coordinator, if it hasn't reappeared yet
                    printErr("deadline problem detected: previous coordinator unreachable");
                    synchronized (parent){
                        if(parent.getCoordinator()==-1) //if the coordinator hasn't been chosen yet
                            startElection();
                    }
                }
            }

            @Override
            public void onCompleted() {
                printHigh("house "+id, "answer received.\n\tanother coordinator is being selected");

            }
        };
        copy.stream().parallel().forEach(chan->HouseManagementGrpc.newStub(chan).withDeadlineAfter(5, TimeUnit.SECONDS).election(coord, respObs));
        lampClock.afterEvent();
    }

    private void becomeCoordinator() {
        Coordinator coord= Coordinator.newBuilder().setCoordinatorID(id).build();
        List<ManagedChannel> copy=parent.getFullPeerListCopy();

        final Pair<Integer, Integer> respVal = Pair.of(0, 0);

        StreamObserver<Ack> respObs=new StreamObserver<Ack>() {
            @Override
            public void onNext(Ack ack) {
                print(ack.getMessage());
                lampClock.checkLamport(ack);
            }

            @Override
            public void onError(Throwable throwable) {
                StatusRuntimeException t=(StatusRuntimeException)throwable;
                if(t.getStatus().isOk()) return;
                printErr("during \"become coordinator\" "+t.getStatus());
            }

            @Override
            public void onCompleted() {
                    synchronized (respVal){
                        respVal.left+=1;
                        print("[HOUSE "+id+"] houses notified: "+respVal.left+"/"+copy.size());
                    }
            }
        };

        copy.stream().parallel().forEach(chan->HouseManagementGrpc.newStub(chan).newCoordinator(coord, respObs));
        lampClock.afterEvent();
    }
    //endregion

    //region BOOST HANDLING
    void ricartAgrawala(List<ManagedChannel> copy) {

        if(isInBoost()) {
            printErr("already in boost mode");
            return;
        }

        RequestBoost request=RequestBoost.newBuilder().setRequester(id).setLamportTimestamp(lampClock.peekClock()).build();

        final Pair<Integer, Object> responses = Pair.of(0, null); //left=number of answers, right= number of permission denied

        StreamObserver<Ack> respObs= new StreamObserver<Ack>() {
            @Override
            public void onNext(Ack ack) {
                lampClock.checkLamport(ack);

                print(ack.getMessage());
            }

            @Override
            public void onError(Throwable throwable) {
                StatusRuntimeException t=(StatusRuntimeException)throwable;
                if(t.getStatus().isOk()) return;
                printErr("error in ricart-Agrawala "+t.getMessage()+ " status "+t.getStatus());

            }

            @Override
            public void onCompleted() {
                synchronized (responses) {
                    responses.left = responses.left + 1;
                    printHigh("house " + id, "answers received " + responses.left + "/" + copy.size());
                    if(responses.left>=copy.size()-1 && !isInBoost()){
                        setAskingBoost(false);
                        boost();
                    }
                }


            }
        };
        printHigh("house "+id,"asking the network the permission to boost");
        setAskingBoost(true);

        copy.stream().parallel().forEach(chan->HouseManagementGrpc.newStub(chan).boostRequest(request, respObs));

        lampClock.afterEvent();
    }



    private synchronized void boost(){
        if(isInBoost()) return;
        setUsingBoost(true);
        Runnable runner = () -> {

            try {
                printHigh("house "+id,"starting boost");
                parent.getSimulator().boost();

                testTimeWaster(10);       // for test
            } catch (InterruptedException e) {
                printErr("interrupted while boosting");
            }finally {
                setUsingBoost(false);
                printHigh("house "+id,"boost completed!");
                SyncObj.getInstance().notifier();

            }
        };

        new Thread(runner).start();
    }


    //endregion
    //endregion

    //region GETTER/SETTER
    private synchronized void setUsingBoost(boolean usingBoost) {
        this.usingBoost = usingBoost;
    }

    synchronized boolean isInBoost(){
        return usingBoost;
    }

    boolean isAskingBoost() {
        return askingBoost;
    }

    private synchronized void setAskingBoost(boolean askingBoost) {
        this.askingBoost = askingBoost;
    }

    //endregion
}