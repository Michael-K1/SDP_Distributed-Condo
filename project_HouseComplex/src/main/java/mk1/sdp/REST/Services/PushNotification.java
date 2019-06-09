package mk1.sdp.REST.Services;
import static mk1.sdp.misc.Common.printHigh;

import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.glassfish.jersey.media.sse.SseBroadcaster;
import org.glassfish.jersey.media.sse.SseFeature;

import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;


@Singleton
@Path("/eventBroadcast")
public class PushNotification {
    private SseBroadcaster broadcaster=new SseBroadcaster();

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.TEXT_PLAIN)
    public void broadcastEvent( String event){          //PUBLISH: broadcast push notification to registered listener
        OutboundEvent ev= new OutboundEvent.Builder()
                .name("from-house-network")
                .mediaType(MediaType.TEXT_PLAIN_TYPE)
                .data(String.class, event)
                .build();
        broadcaster.broadcast(ev);

        printHigh("server", "broadcast event: "+event);
    }

    @GET
    @Produces(SseFeature.SERVER_SENT_EVENTS)
    public EventOutput broadcastListener(){     //SUBSCRIBE: get request to register for event notification
        EventOutput eo=new EventOutput();
        this.broadcaster.add(eo);
        printHigh("server", "new push notification subscription");
        return eo;

    }
}
