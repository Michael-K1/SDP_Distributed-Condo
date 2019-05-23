package mk1.sdp.misc;

import org.jetbrains.annotations.NotNull;

import javax.ws.rs.core.Response;
import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class Common {

    public static void printErr(String s){ System.err.println("[ERROR]: "+s.toUpperCase()+"...");}
    public static void printHigh(String who,String s){ System.out.println("["+who.toUpperCase()+"]: "+s.toUpperCase());}
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

    public static int readInputInteger(Scanner fromShell, String inputMismatch) {
        int val=-1;

        try {
            val=fromShell.nextInt();

        } catch(InputMismatchException e){
            printErr(inputMismatch);
            fromShell.nextLine();
        }catch(NoSuchElementException e){
            printErr("malfunction with the scanner");
            fromShell.nextLine();
        }catch(IllegalStateException e){
            printErr("scanner closed.\n attempt to reopen it...");
            fromShell=new Scanner(System.in);
            fromShell.nextLine();

        }

        return val;
    }

}
