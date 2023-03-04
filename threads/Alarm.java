package nachos.threads;

import nachos.machine.*;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

    private PriorityQueue<ThreadTime> sleepThreads = new PriorityQueue<ThreadTime>();

    private class ThreadTime implements Comparable<ThreadTime> {

        KThread thread = null;
        long wakeTime = -1;

        public int compareTo(ThreadTime thr) {
            if (thr.wakeTime > this.wakeTime) {
                return -1;
            } else if (thr.wakeTime == this.wakeTime) {
                return 0;
            } else {
                return 1;
            }
        }
    }

    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p>
     * <b>Note</b>: Nachos will not function correctly with more than one alarm.
     */
    public Alarm() {
        Machine.timer().setInterruptHandler(new Runnable() {
            public void run() {
                timerInterrupt();
            }
        });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread that
     * should be run.
     */
    public void timerInterrupt() {
        long currentTime = Machine.timer().getTime(); //Store timer
        boolean intStatus = Machine.interrupt().disable(); //disable the interrupts
        ThreadTime tThread = sleepThreads.peek(); //Check first threads time

        while (tThread != null && currentTime > tThread.wakeTime) {
            System.out.println("Waking up the thread.");
            tThread.thread.ready();
            sleepThreads.poll();
            tThread = sleepThreads.peek();
        }

        Machine.interrupt().restore(intStatus);
        KThread.currentThread().yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
     * in the timer interrupt handler. The thread must be woken up (placed in
     * the scheduler ready set) during the first timer interrupt where
     *
     * <p>
     * <blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
        //for now, cheat just to get something working (busy waiting is bad)
        long wakeTime = Machine.timer().getTime() + x;
        boolean intStatus = Machine.interrupt().disable();
        ThreadTime tThread = new ThreadTime();
        tThread.wakeTime = wakeTime;

        tThread.thread = KThread.currentThread();

        sleepThreads.add(tThread);

        KThread.sleep();

        Machine.interrupt().restore(intStatus);
        while (wakeTime > Machine.timer().getTime()) {
            KThread.yield();
        }

    }

    public static void selfTest() {
        System.out.println();
        System.out.println("---Alarm Test Cases---");
        test1();
        test2();
        test3();
        System.out.println("---End of Alarm Test Cases---");
        System.out.println();
    }

    private static void test1() {
        Alarm testTime = new Alarm();
        KThread thread = new KThread();
        thread.setTarget(new Runnable() {
            public void run() {
                long time = Machine.timer().getTime();
                System.out.println("Test 1 Delay of 500 :");
                testTime.waitUntil(500);
                long waitTime = Machine.timer().getTime() - time;
                System.out.println("Test 1: Complete. Wait Time: " + waitTime);
                System.out.println();
            }
        });
        thread.fork();
        thread.join();
    }

    private static void test2() {
        Alarm testTime = new Alarm();
        KThread thread = new KThread();
        thread.setTarget(new Runnable() {
            public void run() {
                long time = Machine.timer().getTime();
                System.out.println("Test 2 Delay of 1000 :");
                testTime.waitUntil(1000);
                long waitTime = Machine.timer().getTime() - time;
                System.out.println("Test 2: Complete. Wait Time: " + waitTime);
                System.out.println();
            }
        });
        thread.fork();
        thread.join();
    }

    private static void test3() {
        Alarm testAlarm = new Alarm();
        KThread thread = new KThread();
        thread.setTarget(new Runnable() {
            public void run() {
                long time = Machine.timer().getTime();
                System.out.println("Test 3 Delay of 10 :");
                testAlarm.waitUntil(10);
                long waitTime = Machine.timer().getTime() - time;
                System.out.println("Test 3: Complete. Wait Time: " + waitTime);
            }
        });
        thread.fork();
        thread.join();
    }

}
