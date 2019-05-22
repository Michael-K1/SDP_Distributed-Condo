package mk1.sdp.misc;

import org.jetbrains.annotations.NotNull;

import javax.ws.rs.core.Response;

public class Common {

    public static void printErr(String s){ System.err.println("[ERROR]: "+s.toUpperCase()+"...");}
    public static void printHigh(String s){ System.out.println("[ADMIN]: "+s.toUpperCase());}
    public static void print(String s){ System.out.println(s);}

    public static boolean responseHasError(@NotNull Response resp){

        if(resp.getStatus()!=200){
            printErr("failed with HTTP error code: "+resp.getStatus());
            String error=resp.readEntity(String.class);
            printErr(error);
            return true;

        }
        return false;
    }



}
