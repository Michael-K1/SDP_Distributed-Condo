package mk1.sdp.REST.Services;



import mk1.sdp.REST.Resources.Complex;
import mk1.sdp.REST.Resources.Home;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.Provider;


@Path("/complex")
public class ComplexService {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetHouseComplex(){
        return Response.ok(Complex.getInstance(),  MediaType.APPLICATION_JSON).build();
    }

    @Path("/add")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceAddHouse(Home h){
        if(Complex.getInstance().addHouse(h))
            return Response.ok(Complex.getInstance().complex,  MediaType.APPLICATION_JSON).build();

        return Response.status(Response.Status.CONFLICT).build();
    }

    @Path("/delete?{id}")
    @DELETE
    public Response serviceDeleteHouse(@PathParam("id") int id){
        if(Complex.getInstance().deleteHouse(id)){
            return Response.ok().build();
        }
        return Response.status(Response.Status.NOT_MODIFIED).build();

    }
    //region HOUSE SERVICES
    @Path("/house?{id}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetHouse(@PathParam("id") int id){
        return Response.ok(Complex.getInstance().getHouse(id), MediaType.APPLICATION_JSON).build();
    }

    @Path("/local/stat?id={id}_n={n}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetLastNLocalStat(@PathParam("id") int id, @PathParam("n") int n){
        return Response.ok(Complex.getInstance().getLastHomeStat(id,n),MediaType.APPLICATION_JSON).build();

    }

    @Path("/local/meanDev?id={id}_n={n}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetLocalMeanDev(@PathParam("id") int id, @PathParam("n") int n){
        return Response.ok(Complex.getInstance().getHomeMeanDev(id,n),MediaType.APPLICATION_JSON).build();

    }

    //endregion

    //region COMPLEX SERVICES

    @Path("/global/stat?n={n}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetLastNGlobalStat(@PathParam("n") int n){
        return Response.ok(Complex.getInstance().getLastGlobalStat(n)).build();
    }

    @Path("/global/meanDev?n={n}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetGlobalMeanDev( @PathParam("n") int n){
        return Response.ok(Complex.getInstance().getGlobalMeanDev(n),MediaType.APPLICATION_JSON).build();

    }
    //endregion



}
