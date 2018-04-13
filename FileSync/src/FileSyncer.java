import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Gregory Abellanosa <gregoryabellanosa@gmail.com>
 * @author Caren Briones <carenpbriones@gmail.com>
 */
public class FileSyncer
{
	public static void main(String args[]) throws Exception 
	{
		Scanner scan = new Scanner(System.in);
		
		// Create hash table for files and their update times
		final Hashtable <String, Integer> hash = new Hashtable<String, Integer>();
		System.out.println("Enter your file directory: ");
		String fileDirectory = scan.next();
		
		int choice = 1;
		
		while (choice >= 1 && choice <= 3) {
			System.out.println("What would you like to do?");
			displayMainMenu();
			
			choice = scan.nextInt();
			
			switch (choice) 
			{
				case 1: // Displays IP address of computers on the same network
					getNetworkIPs();
					
					// Delay from going back into while loop to finish looking for clients
					TimeUnit.SECONDS.sleep(3);
					break;
					
				case 2: // Receives files
					createHashtable(fileDirectory, hash);
					
					ServerSocket server = new ServerSocket(1717);
					server.setReuseAddress(true);
					
					System.out.println("Waiting for request");
					
					// ServerSocket accepts request and creates a socket
					Socket socket = server.accept();
					socket.setReuseAddress(true); // To avoid any port connection issues
					System.out.println("Now connected with " + socket.getInetAddress().toString());
					
					// Sends out this computer's hash table to the other client
					ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
					oos.writeObject(hash);
					
					// Obtains the other client's hash table to compare the two sets of files
					InputStream is = socket.getInputStream();
					ObjectInputStream ois = new ObjectInputStream(is);
					Hashtable <String, Integer> hash2 = (Hashtable <String, Integer>) ois.readObject();
					
					// Determines how many files this client is missing from the other client
					int fileCount = 0;
					Set<String> keys = hash2.keySet();
					for(String key: keys) {
						if(!hash.containsKey(key))
						{
							// Adds other client's file and update time to this client's table
							hash.put(key, hash2.get(key));
							fileCount += 1;
						}
						else if(hash.containsKey(key) && hash2.containsKey(key) && 
								hash.get(key) != hash2.get(key))
						{
							// If the other client's file was more recently updated, delete to be rewritten
							if(hash2.get(key) > hash.get(key))
							{
								fileCount += 1;
								File file = new File(key);
								file.delete();
							}
						}
					}
					System.out.println(fileCount);
					
					// Input stream to obtain the file's contents
					DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
					
					for (int i = 0; i < fileCount; i++)
					{
						receiveFile(in);
					}
					
					server.close();
					socket.close();
					break;
					
				case 3: // Connecting to a client, sends files
					createHashtable(fileDirectory, hash);
					
					System.out.print("Enter the IP address you want to connect to: ");
					String address = scan.next();
					
					// Creates a socket at the given IP address on port 1717
					Socket sock = new Socket(address, 1717);
					sock.setReuseAddress(true);
					
					// Input streams to obtain the other client's hash table
					InputStream iStream = sock.getInputStream();
					ObjectInputStream oIStream = new ObjectInputStream(iStream);
					Hashtable <String, Integer> otherTable = (Hashtable <String, Integer>) oIStream.readObject();
					
					ObjectOutputStream oOut = new ObjectOutputStream(sock.getOutputStream());
					oOut.writeObject(hash);
					
					// Data streams to read and write data to/from files
					DataOutputStream dOut = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream())); 
					DataInputStream dIn = new DataInputStream(new BufferedInputStream(sock.getInputStream()));

					Set<String> keys1 = hash.keySet();
					for(String key: keys1) {
						if(!otherTable.containsKey(key)) // If the other client is missing files from this client's table
						{	
							sendFile(dOut, key);
						} 
						// If they have the same file name but different update times
						else if (otherTable.containsKey(key) && otherTable.get(key) != hash.get(key))
						{
							// If the file on this client is more recent, send the file
							if (hash.get(key) > otherTable.get(key))
							{
								sendFile(dOut, key);
							} 
						}
					}
					System.out.println();
					sock.close();
					break;
				default:
					break;
			}
		}
		scan.close();
	}
	
	/**
	 * Displays the menu options for the user
	 */
	public static void displayMainMenu() 
	{
		System.out.println("1.) Scan network for clients");
		System.out.println("2.) Wait for connections");
		System.out.println("3.) Connect to another client");
		System.out.println("4.) Disconnect");
	}
	
	/**
	 * Displays the IPs of the other computers on the same network
	 * @throws ConnectException
	 */
	public static void getNetworkIPs() throws ConnectException{
	    final byte[] ip;
	    
	    try {
	    	// Stores IP address of this computer in the array
	        ip = InetAddress.getLocalHost().getAddress();
	    } catch (Exception e) {
	    	e.printStackTrace();
	        return;
	    }
	    
	    // Checks each IP address, replacing fourth set of numbers 
	    // in this computer's IP address with values from 1 - 255
	    for(int i = 1; i < 255; i++) {
	        final int j = i;
	        new Thread(new Runnable() {
	            public void run() {
	                try {
	                	// Checks if this IP address is reachable
	                    ip[3] = (byte) j;
	                    InetAddress address = InetAddress.getByAddress(ip);
	                    String output = address.toString().substring(1);
	                    
	                    // Prints out IP address only if it is reachable
	                    if (address.isReachable(1717)) {
	                        System.out.println(output + " is on the network");
	                    }
	                } catch (Exception e) {
//	                    e.printStackTrace();
	                }
	            }
	        }).start(); 
	    }
	}
	
	/**
	 * Creates a Hashtable of key, value pairs (file name, most recent modification time)
	 * given a file directory String.
	 * @param fileDirectory the directory of the files to be made into a hash table
	 * @param hash Hashtable to be created/modified
	 */
	public static void createHashtable(String fileDirectory, Hashtable <String, Integer> hash)
	{
		File directory = new File(fileDirectory);
		File[] files = directory.listFiles();
		
		// Adds file names and their corresponding update times into the hash table
		for (int i = 0; i < files.length; i++)
		{
			String fileName = files[i].getName();
			
			// Gets files in this project folder, excludes source files and compiled files
			if(!fileName.startsWith(".") && !fileName.equals("src") && !fileName.equals("bin")) {
				hash.put(files[i].getName(), (int) files[i].lastModified());
			}
		}
		
		// Displays Hashtable of key and value pairs
		System.out.println(hash + "\n");
	}
	
	/**
	 * Sends a file via a DataOutputStream
	 * @param dOut output stream used to write the file
	 * @param key name of File to be written
	 * @throws IOException 
	 */
	public static void sendFile(DataOutputStream dOut, String key) throws IOException{
		// Writes file name to stream
		System.out.println(key);
		dOut.writeUTF(key);
		dOut.flush();
		
		// Writes file size to stream
		File file = new File(key);
		long length = file.length();
		dOut.writeUTF(Long.toString(length));
		dOut.flush();
		
		FileInputStream fin = new FileInputStream(file);
		
		// Writes data to file
		int count;
		byte[] buffer = new byte[(int) length];
		// read method returns number of bytes read, used to know how many bytes to write
		
		while ((count = fin.read(buffer)) != -1){
			dOut.write(buffer, 0, count);
			dOut.flush();
		}
	}
	
	/**
	 * Receives a file from another client and writes it to this client
	 * @param in input stream that reads the file
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public static void receiveFile(DataInputStream in) throws NumberFormatException, IOException
	{
		// Obtains the file's name and size from the other client
		String fileName = in.readUTF();
		System.out.print("file name: ");
		System.out.println(fileName);
		long size = Long.parseLong(in.readUTF());
		System.out.println(size + "\n");
		
		// Byte array to store file's content as bytes
		byte[] bytes = new byte[1024];
		
		// File output stream to write the bytes to the specified file
		FileOutputStream fos = new FileOutputStream(fileName);
		int n;
		
		// While there is still content to write to the file, write data to the file
		while (size > 0 && (n = in.read(bytes, 0, (int)Math.min(bytes.length, size))) != -1)
		{
			fos.write(bytes, 0, n);
			size -= n;
		}
		fos.close();
	}
}