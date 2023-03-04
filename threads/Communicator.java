package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {

	 //Counters for number of speakers
	 private int speakerNum;
	 private int listenerNum;
         private int msg; //variable for message
	 
         //
         private Lock lock;
	 private Condition condSpeak;
	 private Condition condListen;
	 private boolean ISinboxFull;
    /**
     * Allocate a new communicator.
     */

    public Communicator()
	{
		speakerNum = 0;
		listenerNum = 0;
                
                ISinboxFull = false;
		lock = new Lock();
		condSpeak = new Condition(lock);
		condListen = new Condition(lock);
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word)
	{
                
                //If the lock isn't currently held by the thread acquire the lock
		if(!lock.isHeldByCurrentThread()){ 
                    lock.acquire(); 
                }
                //Increment number of speakers by 1 because a new speaker is created
		speakerNum = speakerNum + 1;
                
                //If the inbox is full or number of listeners is 0. Speaker sleeps.
            while((ISinboxFull || listenerNum == 0)){
                condSpeak.sleep();

             }

		
            //Wake up thread and decrement number of speaker as message has been spoken
            msg = word;
            ISinboxFull = true;   
            condListen.wake();  
            speakerNum = speakerNum - 1;
            lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */
    public int listen()
	{
		if(!lock.isHeldByCurrentThread()){ lock.acquire(); }
		condSpeak.wakeAll();
		listenerNum = listenerNum + 1;

                
		while(!ISinboxFull){ 
                    condListen.sleep(); 
                }      
			

		int word = msg;        
		ISinboxFull = false;
		listenerNum = listenerNum - 1;

		condSpeak.wakeAll();   
		lock.release();
		return word;
	}

	public static void selfTest()
	{
           System.out.println("---Communicator Test case---");
           test1();
           test2();
           test3();
           System.out.println("---End of Communicator Test case---");
        }
        public static void test2() {
            System.out.println("--Test2 : Different communicators--");
            Communicator testCase1 = new Communicator();
            Communicator testCase2 = new Communicator();
            KThread speak = new KThread(new Speak(testCase1,19));
            KThread speak1 = new KThread(new Speak(testCase2,29));
            System.out.println("Creating speaker thread for Communicator A (Speaker said 19)");
            System.out.println("Creating speaker thread for Communicator B (Speaker said 29)");

            speak.fork();
            speak1.fork();
            KThread listen1 = new KThread(new Hear(testCase1));
            KThread listen2 = new KThread(new Hear(testCase2));
            
            listen1.fork();
            listen2.fork();
            listen1.join();
            listen2.join();
            System.out.println("--End of Test2--");
        }
        
        public static void test1() {
            System.out.println("--Test1 : 1 Speaker and 1 Listener--");
            Communicator testCase = new Communicator();
            KThread speak = new KThread(new Speak(testCase, 34));
            System.out.println("Creating speaker thread...");
            speak.fork();
            System.out.println("Won't output until a listener thread is created...");
            KThread list= new KThread(new Hear(testCase));
            
            list.fork();
            list.join();
            System.out.println("--End of Test1--");
        }
        
        public static void test3() {
            System.out.println("--Test3 : Multiple speakers and Multiple listeners--");
            Communicator testCase = new Communicator();
            KThread speak = new KThread(new Speak(testCase, 98));
            KThread speak1 = new KThread(new Speak(testCase, 4));
            KThread speak2 = new KThread(new Speak(testCase,23));

            speak.fork();
            speak1.fork();
            speak2.fork();
            
            System.out.println("Speaker threads created (Speaker 1 : 98) (Speaker 2 : 4) (Speaker 3 : 23)");
            
            KThread listen1 = new KThread(new Hear(testCase));
            KThread listen2 = new KThread (new Hear(testCase));
            KThread listen3 = new KThread(new Hear(testCase));
            listen1.fork();
            listen1.join();
            listen2.fork();
            listen2.join();
            listen3.fork();
            listen3.join();
            System.out.println("Test3 successful");
            System.out.println("--End of Test3--");
            
        } 

	

	private static class Hear implements Runnable
	{
            	Communicator communicator;
		int MSG;

		public Hear(Communicator communicator)
		{
			MSG = 0;
			this.communicator = communicator;
		}

		public void run()
		{
                        MSG = communicator.listen();
			System.out.println("Listener heard: " + MSG);
		}
	}
        
        private static class Speak implements Runnable
	{
		int MSG;
		Communicator communicator;
                
		public Speak(Communicator communicator, int word)
		{
			MSG = word;
			this.communicator = communicator;
		}

		public void run()
		{
			
			communicator.speak(MSG);
                        System.out.println("Speaker spoke: " + MSG);
		}
	}
        
}