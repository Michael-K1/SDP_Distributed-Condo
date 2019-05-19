package mk1.sdp.REST.Services;



import mk1.sdp.REST.Resources.Complex;
import mk1.sdp.REST.Resources.Home;
import mk1.sdp.misc.Pair;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

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

        return Response.status(Response.Status.CONFLICT).entity("there is already an house with ID="+h.HomeID).build();
    }

    @Path("/delete")
    @DELETE
    public Response serviceDeleteHouse(@QueryParam("id") int id){
        if(Complex.getInstance().deleteHouse(id)){
            return Response.ok().build();
        }
        return Response.status(Response.Status.NOT_MODIFIED).entity("the house with ID="+id+" has not been removed").build();

    }

    //region HOUSE SERVICES
    @Path("/house")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetHouse(@QueryParam("id") int id){
        Pair<Response, Home> resp = checkHousePresent(id);
        if(resp.first!=null) return resp.first;

        return Response.ok(resp.second, MediaType.APPLICATION_JSON).build();
    }

    @Path("/house/stat")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetLastNLocalStat(@QueryParam("id") int id, @QueryParam("n") int n){

        Pair<Response, Home> resp = checkHousePresent(id);
        if(resp.first!=null) return resp.first;

        List<Pair<Integer, Double>> list = Complex.getInstance().getLastLocalStat(id, n);

        return Response.ok(list,MediaType.APPLICATION_JSON).build();

    }

    @Path("/house/meanDev")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetLocalMeanDev(@QueryParam("id") int id, @QueryParam("n") int n){
        Pair<Response, Home> resp = checkHousePresent(id);
        if(resp.first!=null) return resp.first;

        return Response.ok(Complex.getInstance().getLocalMeanDev(id,n),MediaType.APPLICATION_JSON).build();

    }

    @Path("/house/add")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response serviceAddLocalStat(@QueryParam("id") int id, Pair pair){
        Pair<Response, Home> resp = checkHousePresent(id);
        if(resp.first!=null) return resp.first;

        Pair<Response,Pair<Integer,Double>> responsePairPair =checkWellFormedPair(pair);
        if(responsePairPair.first!=null) return responsePairPair.first;

        if(Complex.getInstance().addLocalStat(id,responsePairPair.second)){
            return Response.ok().build();
        }
        return Response.status(Response.Status.NOT_ACCEPTABLE).entity("failed to add to the statistics of house with id ="+id).build();
    }

    //endregion

    //region COMPLEX SERVICES   (GLOBAL)
    @Path("/global/add")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response serviceAddGlobalStat(Pair pair){
        Pair<Response,Pair<Integer,Double>> responsePairPair =checkWellFormedPair(pair);
        if(responsePairPair.first!=null) return responsePairPair.first;

        if(Complex.getInstance().addGlobalStat(responsePairPair.second)){
            return Response.ok().build();
        }
        return Response.status(Response.Status.NOT_ACCEPTABLE).entity("failed to add to global statistics").build();
    }
    @Path("/global/stat")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetLastNGlobalStat(@QueryParam("n") int n){  //worst case returns an empty list
        ArrayList<Pair<Integer, Double>> list = Complex.getInstance().getLastGlobalStat(n);

        return Response.ok(list,MediaType.APPLICATION_JSON).build();
    }

    @Path("/global/meanDev")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceGetGlobalMeanDev( @QueryParam("n") int n){
        Pair<Double, Double> res = Complex.getInstance().getGlobalMeanDev(n);

        if (res==null)Response.status(Response.Status.PRECONDITION_FAILED).entity("there are no global statistics ").build();

        return Response.ok(res,MediaType.APPLICATION_JSON).build();

    }


    //endregion

    private Pair<Response,Pair<Integer,Double>> checkWellFormedPair(Pair pair){    //checks if the input pair is well formed
        Response resp;
        if(pair==null) {
            resp = Response.status(Response.Status.PARTIAL_CONTENT).entity("request is empty").build();
            return new Pair<>(resp,null);
        }

        if(pair.first instanceof Integer && pair.second instanceof Double){
            int v1=(Integer) pair.first;
            double v2=(Double)pair.second;
            return new Pair<>(null,new Pair<>(v1,v2));
        }

         resp=Response.status(Response.Status.BAD_REQUEST).entity("the types of the values given are incorrect").build();;
         return new Pair<>(resp,null);
    }

    private Pair<Response,Home> checkHousePresent(int id){  //check if the house is present
        Home home = Complex.getInstance().getHouse(id);

        if(home ==null)return Pair.of(Response.status(Response.Status.NOT_FOUND).entity("There is no house with ID="+id).build(),null);

        return Pair.of(null,home);
    }


}
