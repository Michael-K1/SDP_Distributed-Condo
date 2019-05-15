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

        if (pair == null)return Response.status(Response.Status.PARTIAL_CONTENT).build();

        Pair<Integer,Double> p = checkWellFormedPair(pair);
        if(p==null)return Response.status(Response.Status.BAD_REQUEST).build();;

        if(Complex.getInstance().addLocalStat(id,p)){
            return Response.ok().build();
        }
        return Response.status(Response.Status.BAD_REQUEST).build();
    }

    //endregion

    //region COMPLEX SERVICES

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
        return Response.ok(Complex.getInstance().getGlobalMeanDev(n),MediaType.APPLICATION_JSON).build();

    }

    @Path("/global/add")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response serviceAddGlobalStat(Pair pair){
        if(pair==null) return Response.status(Response.Status.PARTIAL_CONTENT).build();


        Pair<Integer,Double> p= checkWellFormedPair(pair);
        if(p==null)return Response.status(Response.Status.BAD_REQUEST).build();;

        if(Complex.getInstance().addGlobalStat(p)){
            return Response.ok().build();
        }
        return Response.status(Response.Status.NOT_ACCEPTABLE).build();
    }
    //endregion

    private Pair<Integer,Double> checkWellFormedPair(Pair pair){    //checks if the input pair is well formed

         if(pair.first instanceof Integer && pair.second instanceof Double){
             int v1=(Integer) pair.first;
             double v2=(Double)pair.second;
             return new Pair<>(v1,v2);
         }
         return null;
    }

    private Pair<Response,Home> checkHousePresent(int id){  //check if the house is present
        Home home = Complex.getInstance().getHouse(id);
        if(home ==null)return new Pair<>(Response.status(Response.Status.NOT_FOUND.getStatusCode(),"There is no house with ID="+id).build(),null);

        return new Pair<>(null,home);
    }


}
