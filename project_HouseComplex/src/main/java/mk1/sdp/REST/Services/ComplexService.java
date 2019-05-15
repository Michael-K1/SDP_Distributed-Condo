package mk1.sdp.REST.Services;



import mk1.sdp.REST.Resources.Complex;
import mk1.sdp.REST.Resources.Home;


import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;



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

    @Path("/delete")
    @DELETE
    public Response serviceDeleteHouse(@QueryParam("id") int id){
        if(Complex.getInstance().deleteHouse(id)){
            return Response.ok().build();
        }
        return Response.status(Response.Status.NOT_MODIFIED).build();

    }
    //region HOUSE SERVICES
    @Path("/house")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetHouse(@QueryParam("id") int id){
        return Response.ok(Complex.getInstance().getHouse(id), MediaType.APPLICATION_JSON).build();
    }

    @Path("/local/stat")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetLastNLocalStat(@QueryParam("id") int id, @QueryParam("n") int n){
        return Response.ok(Complex.getInstance().getLastHomeStat(id,n),MediaType.APPLICATION_JSON).build();

    }

    @Path("/local/meanDev")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetLocalMeanDev(@QueryParam("id") int id, @QueryParam("n") int n){
        return Response.ok(Complex.getInstance().getHomeMeanDev(id,n),MediaType.APPLICATION_JSON).build();

    }

    //endregion

    //region COMPLEX SERVICES

    @Path("/global/stat")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetLastNGlobalStat(@QueryParam("n") int n){
        return Response.ok(Complex.getInstance().getLastGlobalStat(n)).build();
    }

    @Path("/global/meanDev")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetGlobalMeanDev( @QueryParam("n") int n){
        return Response.ok(Complex.getInstance().getGlobalMeanDev(n),MediaType.APPLICATION_JSON).build();

    }
    //endregion



}
