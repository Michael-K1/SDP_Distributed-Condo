package mk1.sdp.misc;

import org.jetbrains.annotations.NotNull;

import javax.ws.rs.core.Response;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Common {
    private static Timestamp time =new Timestamp(System.currentTimeMillis());
    private static SimpleDateFormat formatter=new SimpleDateFormat("dd/MM/yyyy hh:mm:ss a");

    public static void printErr(String s){ printRED("[ERROR]: "+s+"...");}
    public static void printRED(String s){
        System.err.println(s.toUpperCase());
    }
    public static void printHigh(String who,String s){ System.out.println("["+who.toUpperCase()+"]: "+s.toUpperCase());}
    public static void print(String s){ System.out.println(s);}
    public static void printMeasure(String s,Pair<Long,Double> m){
        time.setTime(m.left);
        print(s+" "+formatter.format(time)+"\t--->\t"+m.right+"\tkW");

    }

    public static boolean responseHasError(@NotNull Response resp){

        if(resp.getStatus()!=200){
            printErr("failed with HTTP error code: "+resp.getStatus());
            String error=resp.readEntity(String.class);
            printErr(error);
            return true;

        }
        return false;
    }

    public static int readInputInteger(@NotNull Scanner fromShell, String inputMismatch) {
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
            print("press Return to continue...");
            fromShell.nextLine();

        }

        return val;
    }


    public static void testTimeWaster(int sec){

        printRED("TEST: waiting for "+sec+" sec: started");
        timeWaster(sec);
        printRED("TEST: waiting for "+sec+" sec: finished");

    }

    /**
     * method that wastes some time(less than deadline)
     * @param sec =seconds to be wasted
     */
    public static void timeWaster(int sec){
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException e) {
            printErr("while waiting");
            e.printStackTrace();
        }
    }
}
