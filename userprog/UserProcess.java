package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.LinkedList;
import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */

/*
I couldn't get the MIPS cross-compiler to work with windows 64 and cygwin. Thus, test files couldn't be tested.
*/
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
	int numPhysPages = Machine.processor().getNumPhysPages();
	pageTable = new TranslationEntry[numPhysPages];
	for (int i=0; i<numPhysPages; i++)
	    pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
        
        
        fileDescriptorTable = new OpenFile[16];
        processID++; //Create a new PID.
        System.out.println("New Process created with PID: " + processID);
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	// for now, just assume that virtual addresses equal physical addresses
	if (vaddr < 0 || vaddr >= memory.length)
	    return 0;

	int amount = Math.min(length, memory.length-vaddr);
	System.arraycopy(memory, vaddr, data, offset, amount);

	return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	// for now, just assume that virtual addresses equal physical addresses
	if (vaddr < 0 || vaddr >= memory.length)
	    return 0;

	int amount = Math.min(length, memory.length-vaddr);
	System.arraycopy(data, offset, memory, vaddr, amount);

	return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
	if (numPages > Machine.processor().getNumPhysPages()) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
	    return false;
	}

	// load sections
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    
	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		      + " section (" + section.getLength() + " pages)");

	    for (int i=0; i<section.getLength(); i++) {
		int vpn = section.getFirstVPN()+i;

		// for now, just assume virtual addresses=physical addresses
		section.loadPage(i, vpn);
	    }
	}
	
	return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }
    
    //Accessor method that gets the processID of the current process
    public int getPID() {
        return processID;
    }
    /*
    Task 1 file System calls : handleCreate(), handleOpen(), handleRead(), handleWrite, handleClose(), handle unlink().
    *
    *
    findEmptyFileDescriptor : iterates through the file descriptor table to find an empty index, returns -1 if none are found.
    */
    public int findEmptyFileDescriptor() {
        
        int emptyIDX = -1;
        for(int i = 0; i < 16; i++) {
            if(fileDescriptorTable[i] == null) {
                emptyIDX = i;
                break;
            }
        }
        return emptyIDX;
    }
    /*
    invalidFileDescriptor : boolean function that checks whether the index is between 0 and 15. If it returns true the FileDescriptor is invalid, if it returns false the FileDescriptor is valid
    */
    public boolean invalidFileDescriptor(int fileDescriptor) {
        //If the index given is not equal to an integer between 0 and 15 return true;
        if (fileDescriptor < 0 || fileDescriptor < 15) {
            return true;
        }
        if (findEmptyFileDescriptor() == -1) {
            return true;
        }
        return false;
    }
    
   
    /*
    * Handle the halt() system call. 
    */
    public int handleHalt() {
        //Added this for task 1, Only the process root should be able to call Machine.halt().
        if (processID != 0) {
            Lib.debug(dbgProcess, "Process is not root!");
            return -1;
        }
	Machine.halt();
	Lib.assertNotReached("Machine.halt() did not halt machine!");
	return 0;
    }
   
    /*
    handleCreate() : creates a file and returns the index of where the file was placed in the FileDescriptor
    */
    public int handleCreate(int charName) {
        if (charName < 0) {
            Lib.debug(dbgProcess,"Invalid virtual address");
            return -1;
        }
        String filename = readVirtualMemoryString(charName, 256);
        
        if (filename == null) {
            Lib.debug(dbgProcess, "Invalid file name");
            return -1;
        }
        int emptyIDX = findEmptyFileDescriptor();
        if(emptyIDX == -1) {
            Lib.debug(dbgProcess, "There are no free fileDescriptors available.");
            return -1;
        }
        OpenFile file = ThreadedKernel.fileSystem.open(filename, true);
        
        //If the file is null it was not able to be created thus throw an error.
        if(file == null) {
            Lib.debug(dbgProcess, "Cannot create file");
            return -1;
        }
        //Store the file into the fileDescriptorTable in the emptyIDX
        fileDescriptorTable[emptyIDX] = file;
        return emptyIDX; // Return where the file was stored.
    }
    
    /*
    handleOpen() : Opens the file of the file descriptor and returns the index of where the file was placed in the FileDescriptor
    */
    public int handleOpen(int charName) {
       
        //Check if the @param is invalid
        if (charName < 0) {
            Lib.debug(dbgProcess, "Invalid virtual address");
            return -1;
        }
        //Get the string name from memory 
        String fileName = readVirtualMemoryString(charName, 256);
        
        //If the fileName is null then couldn't read it from memory therefor return an Invalid filename
        if (fileName == null) {
            Lib.debug(dbgProcess, "Invalid Filename");
            return -1;
        }
        
        //Obtain an index of an available file descriptor
        int emptyIDX = findEmptyFileDescriptor();
        
        //If an empty index couldn't be found 
        if(emptyIDX == -1) {
            Lib.debug(dbgProcess, "There are no free fileDescriptors available.");
            return -1;
        }
        //Open the file from the filesystem but don't create a new file
        OpenFile file = ThreadedKernel.fileSystem.open(fileName, false);
        
        if(file == null) {
            Lib.debug(dbgProcess, "Cannot open the file");
            return -1;
        }
        
        fileDescriptorTable[emptyIDX] = file;
        return emptyIDX;
    }
        
    public int handleRead(int fileDescriptor, int bufferAddr, int count) {
        if(invalidFileDescriptor(fileDescriptor) == true) {
            return -1;
        }
        //Get the file to read
        OpenFile file = fileDescriptorTable[fileDescriptor];
        int bytesIn = 0;
        int bytesOut = 0;
        
        //If file can't be read return 0.
        if (file == null || count < 0) {
            return -1;
        }
        byte[] buffer = new byte[count];
        bytesIn = file.read(buffer, 0, count);
        
        //No bytes were read therefore return 0
        if (bytesIn == -1) {
            return -1;
        }
        
        //Write from the buffer into the virtual address 
        bytesOut = writeVirtualMemory(bufferAddr, buffer, 0, bytesIn);
        
        //return the amount of bytes read.
        return bytesOut;
    }
    
    public int handleWrite(int fileDescriptor, int bufferAddr, int count) {
        if(invalidFileDescriptor(fileDescriptor) == true) {
            return -1;
        }
        int bytesWritten = 0, returnBytes = 0;
        
        //get the corresponding file
        OpenFile file = fileDescriptorTable[fileDescriptor];
        
        //Check to see whether the file is invalid.
        if(file == null || count <  0) {
            return -1;
        }
        
        byte[] buffer = new byte[count];
        bytesWritten = readVirtualMemory(bufferAddr, buffer, 0, count);
        
        returnBytes = file.write(buffer, 0, bytesWritten);
        if(returnBytes != count) {
            return -1;
        }
        return returnBytes;
    } 
    
    public int handleClose(int fileDescriptor) {
        if (invalidFileDescriptor(fileDescriptor) == true) {
            return -1;
        }
        fileDescriptorTable[fileDescriptor].close();
        fileDescriptorTable[fileDescriptor] = null;
        return 0;
    }
    /*
    Removes a file entry from the fileSystem
    */
    
    public int handleUnlink(String name) {
        boolean suceeded = ThreadedKernel.fileSystem.remove(name);
        if(suceeded == false) {
            return -1;
        }
        return 0; //Sucessfully unlinked the account
    }
    /*
    Task 3 System calls : handleJoin(), handleExit(), handleExec()
    */
    
    /*
    * Handles the join() system call.
    */
    
    public int handleJoin(int processID, int statusAddr) {
        if (statusAddr < 0 || processID < 0) {
            return -1;
        }
        UserProcess child = null;
        
        for(int i = 0; i < childProcesses.size(); i++) {
            if (childProcesses.get(i).getPID() == processID) {
                child = childProcesses.get(i);
                break;
            }
        }
        
        if (child == null) {
            return -1;
        }
        
        child.thread.join();
        child.parentProcess = null;
        childProcesses.remove(child);
                
        
        //Get bytes from child's exit code
        byte[] exitBuffer = Lib.bytesFromInt(child.exitCode);
        
        //Write to virtual memory
        int byteCount = writeVirtualMemory(statusAddr, exitBuffer);
        
        //Check if the byte count is equal to 4
        if (byteCount != 4) {
            return 1;
        } 
        
        return -1;
    }
    
    /*
    * Handles the exit() system call.
    */
    public int handleExit(int status) {
        //If the parentProcess isn't null return the exit code as current status
        if (parentProcess != null) {
            parentProcess.exitCode = status;
        }
        //Unload the resources
        unloadSections();
        
        for(int i = 0; i < childProcesses.size(); i++) {
           //Have to make parent null then remove it from childProcesses list.
            childProcesses.get(i).parentProcess = null;
            childProcesses.removeFirst();
            
        }
        
        //If it's the last remaining process on the system, terminate the machine
        if (processID == 0) {
            Kernel.kernel.terminate();
            return 0;
        }
        else {
            UThread.finish();
        }
        
        return 0;
    }
    
    /*
    * Handles the handleExec() system call
    */
    public int handleExec(int virtualAddr, int numPages, int startVirtualAddr) {
        //Initialize the file name
        String fileName = null;
        
        //Check if params are valid
        if(virtualAddr < 0 || numPages < 0 || startVirtualAddr < 0) {
            return -1;
        }
        fileName = readVirtualMemoryString(virtualAddr, 256);
        
        //If the fileName is still null return an error.
        if (fileName == null) {
            return -1;
        }
        
        byte pageBuffer[];
        String[] pages = new String[numPages];
        for(int i = 0; i < numPages; i++) {
            pageBuffer = new byte[4];
            if (readVirtualMemory(startVirtualAddr + i * 4, pageBuffer) != pageBuffer.length) {
                return -1;
            }
            
            int address = Lib.bytesToInt(pageBuffer,0);
            String arguments = readVirtualMemoryString(address,256);
            
            if(pages[i] == null) {
                return -1;
            }
            pages[i] = arguments;
            
        }
        
        UserProcess child = UserProcess.newUserProcess();
        boolean sucessfulExecution = child.execute(fileName, pages);
        
        if(sucessfulExecution == false) {
            return -1;
        }
        else {
            childProcesses.add(child);
            return child.processID;
        }
    }
    
    
    private static final int
        syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
	case syscallHalt:
	    return handleHalt();
        case syscallExit:
            return handleExit(a0);
        case syscallExec:
            return handleExec(a0, a1, a2);
        case syscallJoin:
            return handleJoin(a0, a1);
        case syscallCreate:
            return handleCreate(a0);
        case syscallOpen:
            return handleOpen(a0);
        case syscallRead:
            return handleRead(a0,a1,a2);
        case syscallWrite:
            return handleWrite(a0,a1,a2);
        case syscallClose:
            return handleClose(a0);
        case syscallUnlink:
            if (a0 < 0) {
            return -1;
        }
        String name = readVirtualMemoryString(a0, 256);
        if(name == null) {
            return -1;
        }
        return handleUnlink(name);


	default:
	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    Lib.assertNotReached("Unknown system call!");
	}
	return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	}
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    
    //Variables for task 1    
    private OpenFile[] fileDescriptorTable;
    //Variables for task 3
    protected UserProcess parentProcess;
    protected LinkedList<UserProcess> childProcesses;
    protected UThread thread;
    protected static int processID;
    private int exitCode = 0;
}
