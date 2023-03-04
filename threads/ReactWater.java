/*
ReactWater.java is a class used to test that the synchronization of Condition2 is working.
Author: Artin Lafrance
*/

package nachos.threads;
import nachos.machine.*;
public class ReactWater {
    
    //Counters for H and O
    private static int hydrogenNum;
    private static int oxygenNum;
    
    //Condition and Lock Variables
    Condition2 hydrogenCondition;
    Condition2 oxygenCondition;
    private Lock RWLock;

    
    //Constructor that initializes the counters to 0 and initializes the Locks and Conditions
    public ReactWater() {
        hydrogenNum = 0;
        oxygenNum = 0;
        
        RWLock = new Lock();
        oxygenCondition = new Condition2(RWLock);
        hydrogenCondition = new Condition2(RWLock);
    } 
    
    public void hReady() {
        RWLock.acquire();
        ++hydrogenNum;
        if (hydrogenNum % 2 == 0 && oxygenNum > 0) {
        Makewater();
         } else {
            hydrogenCondition.sleep();
        }
        RWLock.release();
    }
    
    public void oReady() {
        RWLock.acquire();
        ++oxygenNum;
        if (hydrogenNum % 2 == 0 && oxygenNum > 0) {
           Makewater();
        } else {
           oxygenCondition.sleep();
        }
        RWLock.release();

    }
    
    public void Makewater() {
        //If theres 2 hydrogen and 1 oxygen thread. Wake the threads and create the water.
        
            hydrogenCondition.wake();
            oxygenCondition.wake();
            
            //Decrement the counters
            hydrogenNum = hydrogenNum - 2;
            oxygenNum = oxygenNum - 1;
            
            
            System.out.println("Water was made.");
        
    }
    
    //Tests cases for ReactWater
    public synchronized static  void selfTest() {
         System.out.println("---ReactWater Test Cases---");
         ReactWater react = new ReactWater();
         KThread H1 = new KThread(new Runnable(){
             public void run() {
                 System.out.println("--Starting Test1--");
                 System.out.println("Testing with 1 hydrogen that a reaction does not occur.");
                 react.hReady();
                 System.out.println("--End of Test1--");
             }
         });
         H1.fork();
        
         
        KThread H2 = new KThread(new Runnable() {
            public void run() {
                System.out.println("--Starting Test2--");
                System.out.println("Testing with 2 hydrogen that a reaction does not occur.");
                react.hReady();
                System.out.println("--End of Test2--");
             }
         });
         H2.fork();
         
        KThread O1 = new KThread(new Runnable() {
            public void run() {
                System.out.println("--Starting Test3--");
                System.out.println("Testing with 2 hydrogen and 1 oxygen that a reaction does occur.");
                react.oReady();
                System.out.println("--End of Test3--");
            }
        });
        O1.fork();
        O1.join();
         System.out.println("---End of ReactWater Test Cases---");
    }
}
