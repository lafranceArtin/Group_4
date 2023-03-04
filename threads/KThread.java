package nachos.threads;

import nachos.machine.*;
import java.util.ArrayList;

/**
 * A KThread is a thread that can be used to execute Nachos kernel code. Nachos
 * allows multiple threads to run concurrently.
 *
 * To create a new thread of execution, first declare a class that implements
 * the <tt>Runnable</tt> interface. That class then implements the <tt>run</tt>
 * method. An instance of the class can then be allocated, passed as an argument
 * when creating <tt>KThread</tt>, and forked. For example, a thread that
 * computes pi could be written as follows:
 *
 * <p>
 * <blockquote><pre>
 * class PiRun implements Runnable {
 *     public void run() {
 *         // compute pi
 *         ...
 *     }
 * }
 * </pre></blockquote>
 * <p>
 * The following code would then create a thread and start it running:
 *
 * <p>
 * <blockquote><pre>
 * PiRun p = new PiRun();
 * new KThread(p).fork();
 * </pre></blockquote>
 */
public class KThread {

    /**
     * Get the current thread.
     *
     * @return	the current thread.
     */
    public static KThread currentThread() {
        Lib.assertTrue(currentThread != null);
        return currentThread;
    }

    /**
     * Allocate a new <tt>KThread</tt>. If this is the first <tt>KThread</tt>,
     * create an idle thread as well.
     */
    public KThread() {
        if (currentThread != null) {
            tcb = new TCB();
        } else {
            readyQueue = ThreadedKernel.scheduler.newThreadQueue(false);
            readyQueue.acquire(this);

            currentThread = this;
            tcb = TCB.currentTCB();
            name = "main";
            restoreState();

            createIdleThread();
        }
    }

    /**
     * Allocate a new KThread.
     *
     * @param	target	the object whose <tt>run</tt> method is called.
     */
    public KThread(Runnable target) {
        this();
        this.target = target;
    }

    /**
     * Set the target of this thread.
     *
     * @param	target	the object whose <tt>run</tt> method is called.
     * @return	this thread.
     */
    public KThread setTarget(Runnable target) {
        Lib.assertTrue(status == statusNew);

        this.target = target;
        return this;
    }

    /**
     * Set the name of this thread. This name is used for debugging purposes
     * only.
     *
     * @param	name	the name to give to this thread.
     * @return	this thread.
     */
    public KThread setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Get the name of this thread. This name is used for debugging purposes
     * only.
     *
     * @return	the name given to this thread.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the full name of this thread. This includes its name along with its
     * numerical ID. This name is used for debugging purposes only.
     *
     * @return	the full name given to this thread.
     */
    public String toString() {
        return (name + " (#" + id + ")");
    }

    /**
     * Deterministically and consistently compare this thread to another thread.
     */
    public int compareTo(Object o) {
        KThread thread = (KThread) o;

        if (id < thread.id) {
            return -1;
        } else if (id > thread.id) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Causes this thread to begin execution. The result is that two threads are
     * running concurrently: the current thread (which returns from the call to
     * the <tt>fork</tt> method) and the other thread (which executes its
     * target's <tt>run</tt> method).
     */
    public void fork() {
        Lib.assertTrue(status == statusNew);
        Lib.assertTrue(target != null);

        Lib.debug(dbgThread,
                "Forking thread: " + toString() + " Runnable: " + target);

        boolean intStatus = Machine.interrupt().disable();

        tcb.start(new Runnable() {
            public void run() {
                runThread();
            }
        });

        ready();

        Machine.interrupt().restore(intStatus);
    }

    private void runThread() {
        begin();
        target.run();
        finish();
    }

    private void begin() {
        Lib.debug(dbgThread, "Beginning thread: " + toString());

        Lib.assertTrue(this == currentThread);

        restoreState();

        Machine.interrupt().enable();
    }

    /**
     * Finish the current thread and schedule it to be destroyed when it is safe
     * to do so. This method is automatically called when a thread's
     * <tt>run</tt> method returns, but it may also be called directly.
     *
     * The current thread cannot be immediately destroyed because its stack and
     * other execution state are still in use. Instead, this thread will be
     * destroyed automatically by the next thread to run, when it is safe to
     * delete this thread.
     */
    public static void finish() {
        Lib.debug(dbgThread, "Finishing thread: " + currentThread.toString());

        Machine.interrupt().disable();

        Machine.autoGrader().finishingCurrentThread();

        // ensures thread that it doesn't cause any deadlock by joining itself
        if (currentThread.join) {
            currentThread.wQueue.nextThread().ready();
        }

        Lib.assertTrue(toBeDestroyed == null);
        toBeDestroyed = currentThread;

        currentThread.status = statusFinished;

        sleep();
    }

    /**
     * Relinquish the CPU if any other thread is ready to run. If so, put the
     * current thread on the ready queue, so that it will eventually be
     * rescheuled.
     *
     * <p>
     * Returns immediately if no other thread is ready to run. Otherwise returns
     * when the current thread is chosen to run again by
     * <tt>readyQueue.nextThread()</tt>.
     *
     * <p>
     * Interrupts are disabled, so that the current thread can atomically add
     * itself to the ready queue and switch to the next thread. On return,
     * restores interrupts to the previous state, in case <tt>yield()</tt> was
     * called with interrupts disabled.
     */
    public static void yield() {
        Lib.debug(dbgThread, "Yielding thread: " + currentThread.toString());

        Lib.assertTrue(currentThread.status == statusRunning);

        boolean intStatus = Machine.interrupt().disable();

        currentThread.ready();

        runNextThread();

        Machine.interrupt().restore(intStatus);
    }

    /**
     * Relinquish the CPU, because the current thread has either finished or it
     * is blocked. This thread must be the current thread.
     *
     * <p>
     * If the current thread is blocked (on a synchronization primitive, i.e. a
     * <tt>Semaphore</tt>, <tt>Lock</tt>, or <tt>Condition</tt>), eventually
     * some thread will wake this thread up, putting it back on the ready queue
     * so that it can be rescheduled. Otherwise, <tt>finish()</tt> should have
     * scheduled this thread to be destroyed by the next thread to run.
     */
    public static void sleep() {
        Lib.debug(dbgThread, "Sleeping thread: " + currentThread.toString());

        Lib.assertTrue(Machine.interrupt().disabled());

        if (currentThread.status != statusFinished) {
            currentThread.status = statusBlocked;
        }

        runNextThread();
    }

    /**
     * Moves this thread to the ready state and adds this to the scheduler's
     * ready queue.
     */
    public void ready() {
        Lib.debug(dbgThread, "Ready thread: " + toString());

        Lib.assertTrue(Machine.interrupt().disabled());
        Lib.assertTrue(status != statusReady);

        status = statusReady;
        if (this != idleThread) {
            readyQueue.waitForAccess(this);
        }

        Machine.autoGrader().readyThread(this);
    }

    /**
     * Waits for this thread to finish. If this thread is already finished,
     * return immediately. This method must only be called once; the second call
     * is not guaranteed to return. This thread must not be the current thread.
     */
    public void join() {

        // Previous Calls
        Lib.debug(dbgThread, "Joining to thread: " + toString());
        Lib.assertTrue(this != currentThread);

        // Ensure thread that has already been joined is completed 
        if (this.status == statusFinished) {
            return;
        } else if (currentThread.callID.contains(this.id)) {
            return;
        }

        // Disable interrupts (it gets restored later)
        boolean nStatus = Machine.interrupt().disable();

        // Ensure wait queue is not initialized + Create queue for waiting threads
        if (this.wQueue == null) {
            this.wQueue = ThreadedKernel.scheduler.newThreadQueue(false);
            this.wQueue.acquire(this);
        }

        // Ensure thread has joiend 
        this.callID.add(currentThread.id);
        this.join = true;
        for (int j = 0; j < currentThread.callID.size(); j++) {
            this.callID.add(currentThread.callID.get(j));
        }

        // Add thread to wait queue
        this.wQueue.waitForAccess(KThread.currentThread());

        // Set currentThread on waitList to be awoken later
        KThread.sleep();
        Machine.interrupt().restore(nStatus);
    }
    // Set thread to sleep and restore teh machine status stored earlier

    /**
     * Create the idle thread. Whenever there are no threads ready to be run,
     * and <tt>runNextThread()</tt> is called, it will run the idle thread. The
     * idle thread must never block, and it will only be allowed to run when all
     * other threads are blocked.
     *
     * <p>
     * Note that <tt>ready()</tt> never adds the idle thread to the ready set.
     */
    private static void createIdleThread() {
        Lib.assertTrue(idleThread == null);

        idleThread = new KThread(new Runnable() {
            public void run() {
                while (true) {
                    yield();
                }
            }
        });
        idleThread.setName("idle");
        Machine.autoGrader().setIdleThread(idleThread);

        idleThread.fork();
    }

    /**
     * Determine the next thread to run, then dispatch the CPU to the thread
     * using <tt>run()</tt>.
     */
    private static void runNextThread() {
        KThread nextThread = readyQueue.nextThread();
        if (nextThread == null) {
            nextThread = idleThread;
        }

        nextThread.run();
    }

    /**
     * Dispatch the CPU to this thread. Save the state of the current thread,
     * switch to the new thread by calling <tt>TCB.contextSwitch()</tt>, and
     * load the state of the new thread. The new thread becomes the current
     * thread.
     *
     * <p>
     * If the new thread and the old thread are the same, this method must still
     * call <tt>saveState()</tt>, <tt>contextSwitch()</tt>, and
     * <tt>restoreState()</tt>.
     *
     * <p>
     * The state of the previously running thread must already have been changed
     * from running to blocked or ready (depending on whether the thread is
     * sleeping or yielding).
     *
     * @param	finishing	<tt>true</tt> if the current thread is finished, and
     * should be destroyed by the new thread.
     */
    private void run() {
        Lib.assertTrue(Machine.interrupt().disabled());

        Machine.yield();

        currentThread.saveState();

        Lib.debug(dbgThread, "Switching from: " + currentThread.toString()
                + " to: " + toString());

        currentThread = this;

        tcb.contextSwitch();

        currentThread.restoreState();
    }

    /**
     * Prepare this thread to be run. Set <tt>status</tt> to
     * <tt>statusRunning</tt> and check <tt>toBeDestroyed</tt>.
     */
    protected void restoreState() {
        Lib.debug(dbgThread, "Running thread: " + currentThread.toString());

        Lib.assertTrue(Machine.interrupt().disabled());
        Lib.assertTrue(this == currentThread);
        Lib.assertTrue(tcb == TCB.currentTCB());

        Machine.autoGrader().runningThread(this);

        status = statusRunning;

        if (toBeDestroyed != null) {
            toBeDestroyed.tcb.destroy();
            toBeDestroyed.tcb = null;
            toBeDestroyed = null;
        }
    }

    /**
     * Prepare this thread to give up the processor. Kernel threads do not need
     * to do anything here.
     */
    protected void saveState() {
        Lib.assertTrue(Machine.interrupt().disabled());
        Lib.assertTrue(this == currentThread);
    }

    private static class PingTest implements Runnable {

        PingTest(int which) {
            this.which = which;
        }

        public void run() {
            for (int i = 0; i < 5; i++) {
                System.out.println("*** thread " + which + " looped "
                        + i + " times");
                currentThread.yield();
            }
        }

        private int which;
    }

    /**
     * Test #1 whether this module is working.
     */
    public static void selfTest() {
        Lib.debug(dbgThread, "Enter KThread.selfTest");

        new KThread(new PingTest(1)).setName("Forked").fork();
        new PingTest(0).run();

        // Starting Call
        System.out.println("------//------ KThread Self Tests [Starting] -----//--------");

        selfJoiningTest();
        joinCompleteThreadTest();
        contraryJoinerTest();
        multiThreadJoinTest();
        System.out.println("-----//----- All KThread Self Tests [Completed] -----//-----");

    }

    /**
     * Test #1: to ensure a thread cannot join with itself (might cause
     * deadlock)
     */
    public static void selfJoiningTest() {
        // Create Dummy Thread
        KThread joiner = new KThread();
        joiner.setName("Joiner");

        System.out.println("Self Join Test: Started");

        // Has thread (try to) join to iself
        joiner.setTarget(new Runnable() {
            public void run() {
                String result = "didn't successfully jion";
                try {
                    System.out.println("Self-Join Test: Joining self");
                    joiner.join();
                } catch (Error e) {
                    result = "succcesful";
                }

                // Final Result Call
                System.out.println("Self Join Test: Completed " + result);
            }
        });
        joiner.fork();
        joiner.join();
    }

    /**
     * Test #2: to check if Thread can join to itself through a separate thread
     */
    public static void contraryJoinerTest() {
        System.out.println("Contrarian Join Test.. In Progress");

        // Create Threads (2) to join
        KThread thread1 = new KThread();
        thread1.setName("Thread 1");
        KThread thread2 = new KThread();
        thread2.setName("Thread 2");

        // First thread starts the Second thread then joins the second thread
        thread1.setTarget(new Runnable() {
            public void run() {
                System.out.println("Contrarian Join Test: Join Thread 2 from Thread 1");
                thread2.fork(); // fork
                thread2.join(); // join

            }
        });

        // Second thread to join the First thread (no need for fork as it's already in process)
        thread2.setTarget(new Runnable() {
            public void run() {
                System.out.println("Contrarian Join Test: Join Thread 1 from Thread 2");
                thread1.join(); // only join

            }
        });

        // Start Nested Join
        thread1.fork();
        thread1.join();
        System.out.println("Contrarian Join Test: Completed & Successful");
    }

    /**
     * Test #3: if a thread is able to join another thread that has already been
     * completed
     */
    public static void joinCompleteThreadTest() {
        // Create new threads (2) that will join after being completed
        System.out.println("Join Completed Thread Test: Starting");

        KThread finished = new KThread();
        finished.setName("Thread 1");

        KThread joiner = new KThread();
        joiner.setName("Thread 2");

        // Start & Finish Thread
        finished.setTarget(new Runnable() {
            public void run() {
                System.out.println("Join Completed Thread Test: Thread is Completed");
            }
        });

        // Join the Completed thread from the First thread 
        joiner.setTarget(new Runnable() {
            public void run() {
                System.out.println("Join Completed Thread Test: Preparing to join finished thread");
                finished.join();
            }
        });

        // Start thread and join the finished threads
        finished.fork();
        joiner.fork();
        joiner.join();

        // Final Output 
        System.out.println("Join Completed Thread Test: Completed & Successful");
    }

    /**
     * Create multiple threads that will be joined and multiple threads to join
     * this will act as a performance test to see how it is able to handle
     * multiple threads being joined simitaneously
     */
    public static void multiThreadJoinTest() {
        System.out.println("Multiple Threads Joining Test... IN PROGRESS");

        // Create 5 threads (will join other threads)
        KThread thread1 = new KThread();
        KThread thread2 = new KThread();
        KThread thread3 = new KThread();
        KThread thread4 = new KThread();
        KThread thread5 = new KThread();
        thread1.setName("Initial Thread 1");
        thread2.setName("Initial Thread 2");
        thread3.setName("Initial Thread 3");
        thread4.setName("Initial Thread 4");
        thread5.setName("Initial Thread 5");

        // Create 5 additional threads (will be joined)
        KThread joinThread1 = new KThread();
        KThread joinThread2 = new KThread();
        KThread joinThread3 = new KThread();
        KThread joinThread4 = new KThread();
        KThread joinThread5 = new KThread();
        joinThread1.setName("Join Thread 1");
        joinThread2.setName("Join Thread 2");
        joinThread3.setName("Join Thread 3");
        joinThread4.setName("Join Thread 4");
        joinThread5.setName("Join Thread 5");

        // Join the 5 Threads (same process 5 times)
        // FIRST PROCESS
        thread1.setTarget(new Runnable() {
            public void run() {
                System.out.println("Multiple Threads Join Test: " + thread1.getName() + " joining " + joinThread1.getName());
                joinThread1.fork();
                joinThread1.join();
            }
        });

        // SECOND PROCESS
        thread2.setTarget(new Runnable() {
            public void run() {
                System.out.println("Multiple Threads Join Test: " + thread2.getName() + " joining " + joinThread2.getName());
                joinThread2.fork();
                joinThread2.join();
            }
        });

        // THIRD PROCESS
        thread3.setTarget(new Runnable() {
            public void run() {
                System.out.println("Multiple Threads Join Test: " + thread3.getName() + " joining " + joinThread3.getName());
                joinThread3.fork();
                joinThread3.join();
            }
        });

        // FOURTH PROCESS
        thread4.setTarget(new Runnable() {
            public void run() {
                System.out.println("Multiple Threads Join Test: " + thread4.getName() + " joining " + joinThread4.getName());
                joinThread4.fork();
                joinThread4.join();
            }
        });

        // FIFTH PROCESS
        thread5.setTarget(new Runnable() {
            public void run() {
                System.out.println("Multiple Threads Join Test: " + thread5.getName() + " joining " + joinThread5.getName());
                joinThread5.fork();
                joinThread5.join();
            }
        });

        // Print what the Multi-Threaded are Running (5 processes)
        joinThread1.setTarget(new Runnable() {
            public void run() {
                System.out.println("Multiple Threads Join Test: " + joinThread1.getName() + " running");
            }
        });
        joinThread2.setTarget(new Runnable() {
            public void run() {
                System.out.println("Multiple Threads Join Test: " + joinThread2.getName() + " running");
            }
        });
        joinThread3.setTarget(new Runnable() {
            public void run() {
                System.out.println("Multiple Threads Join Test: " + joinThread3.getName() + " running");
            }
        });
        joinThread4.setTarget(new Runnable() {
            public void run() {
                System.out.println("Multiple Threads Join Test: " + joinThread4.getName() + " running");
            }
        });
        joinThread5.setTarget(new Runnable() {
            public void run() {
                System.out.println("Multiple Threads Join Test: " + joinThread5.getName() + " running");
            }
        });

        // Final Calls: Multi-Thread & Multi-Join Process
        thread1.fork();
        thread1.join();
        thread2.fork();
        thread2.join();
        thread3.fork();
        thread3.join();
        thread4.fork();
        thread4.join();
        thread5.fork();
        thread5.join();
        System.out.println("Multiple Threads Join Test: Completed & Successful");
    }

    private static final char dbgThread = 't';

    /**
     * Additional state used by schedulers.
     *
     * @see	nachos.threads.PriorityScheduler.ThreadState
     */
    public Object schedulingState = null;

    private static final int statusNew = 0;
    private static final int statusReady = 1;
    private static final int statusRunning = 2;
    private static final int statusBlocked = 3;
    private static final int statusFinished = 4;

    /**
     * The status of this thread. A thread can either be new (not yet forked),
     * ready (on the ready queue but not running), running, or blocked (not on
     * the ready queue and not running).
     */
    private int status = statusNew;
    private String name = "(unnamed thread)";
    private Runnable target;
    private TCB tcb;

    /**
     * Unique identifer for this thread. Used to deterministically compare
     * threads.
     */
    private int id = numCreated++;
    /**
     * Number of times the KThread constructor was called.
     */
    private static int numCreated = 0;

    private ThreadQueue wQueue = null;
    private ArrayList<Integer> callID = new ArrayList<Integer>();
    private boolean join = false;

    private static ThreadQueue readyQueue = null;
    private static KThread currentThread = null;
    private static KThread toBeDestroyed = null;
    private static KThread idleThread = null;
}
