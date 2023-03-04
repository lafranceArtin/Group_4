package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;
/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
	this.conditionLock = conditionLock;
        sleepingQueue = new LinkedList<>();      
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        Lib.assertTrue(KThread.currentThread().getStatus() != 4);
        
        boolean intStatus = Machine.interrupt().disable(); //disable interrupts
        
        sleepingQueue.add(KThread.currentThread()); //add the current thread to the sleepingQueue
        
        conditionLock.release(); //release the lock
        
        KThread.sleep();    //put the current thread to sleep
        Machine.interrupt().restore(intStatus); //Restore the previous interrupts
        
        conditionLock.acquire(); //re-acquire the lock        
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        boolean intStatus = Machine.interrupt().disable(); //Disable the interrupts
       
        //If the queue is not empty remove the first thread from the wait queue and set its state to ready
        if(!sleepingQueue.isEmpty()) {
            (sleepingQueue.removeFirst()).ready(); 
        }
        //If wake() is called but there is no threads in the waiting queue return error message.
        else {
            System.out.println("ERROR: There is no threads in the waiting queue.");
        }
        
        Machine.interrupt().restore(intStatus); //Restore previous interrupts
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        //While the queue is not empty wake all the threads.
        while(!sleepingQueue.isEmpty()) {
            wake(); //call wake() on all threads
        }
        
        //If there's no elements in the queue and wakeAll is invoked then return an error message
        if(sleepingQueue.isEmpty()) {
            System.out.println("ERROR: There is no threads in the waiting queue.");
        }
    }

    //Lock and sleep queue
    private Lock conditionLock;
    private LinkedList<KThread> sleepingQueue;
    
    
    public static void selfTest() {
        System.out.println("---Condition2 Test Cases---");
        Test1();
        Test2();
        Test3();
        System.out.println("---End of Condition2 Test Cases---"); 
    }
    
    //Test case 1 : Put a thread to sleep and then gets another thread to wake it up.
    public static void Test1() {
        Lock testLock = new Lock();
        Condition2 testCond = new Condition2(testLock);
        
        KThread sleep1 = new KThread(new Runnable(){
            public void run() {
                testLock.acquire();
                System.out.println("--Starting Test1--");
                System.out.println("Putting thread to sleep...");
                testCond.sleep();
                System.out.println("Thread woke up!");
                testLock.release();
            }
        });
       
         KThread wake1 = new KThread(new Runnable(){
            public void run() {
                testLock.acquire();
                System.out.println("Waking the sleeping thread up...");
                testCond.wake();
                testLock.release();
            }
        });
        sleep1.fork();
        wake1.fork();
        wake1.join();
        sleep1.join();
        System.out.println("Test 1 has been completed.");
        System.out.println("--End of Test1--");
    }
    
    public static void Test2() {
        
        System.out.println("--Starting Test2--");
        Lock testLock = new Lock();
        Condition2 testCond = new Condition2(testLock);
        
        KThread sleep1 = new KThread(new Runnable(){
            public void run() {
                testLock.acquire();
                System.out.println("Putting thread1 to sleep...");
                testCond.sleep();
                System.out.println("thread1 woke up!");
                testLock.release();
            }
        });
        
        KThread sleep2 = new KThread(new Runnable(){
            public void run() {
                testLock.acquire();
                System.out.println("Putting thread2 to sleep...");
                testCond.sleep();
                System.out.println("thread2 woke up!");
                testLock.release();
            }
        });
        
        KThread sleep3 = new KThread(new Runnable(){
            public void run() {
                testLock.acquire();
                System.out.println("Putting thread3 to sleep...");
                testCond.sleep();
                System.out.println("thread3 woke up!");
                testLock.release();
            }
        });
        
        KThread wake = new KThread(new Runnable(){
            public void run() {
                testLock.acquire();
                System.out.println("wakeAll : Waking up all the threads...");
                testCond.wakeAll();
                testLock.release();
            }
        });
        
        sleep1.fork();
        sleep2.fork();
        sleep3.fork();
        wake.fork();
        wake.join();
        System.out.println("Test 2 has been completed.");
        System.out.println("--End of Test2--");
    }
    
    public static void Test3() {
        System.out.println("--Starting Test3--");
        Lock testLock = new Lock();
        Condition2 testCond = new Condition2(testLock);
        
        KThread thread1 = new KThread(new Runnable() {
            public void run() {
                testLock.acquire();
                System.out.println("Waking up the thread using wake() method.");
                testCond.wake();
                System.out.println("Waking up the thread using wakeAll() method");
                testCond.wakeAll();
                testLock.release();
            }
        });
        
        thread1.fork();
        thread1.join();
        System.out.println("Test 3 has been completed.");
        System.out.println("--End of Test3--");
    }
}