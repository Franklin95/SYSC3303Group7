import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Scanner;

public class Server {
	private static final String NAME  = "SERVER";
	private static final int PORT = 1069;
	private boolean shutdown = false;
	private DatagramSocket receiveSocket;
	private String path;
	private int runningThreads = 0;
	private static Scanner input;
	
	public Server(String path)
	{
		this.path = path;
		System.out.println("Server's working directory is: " + path);
		System.out.println(NAME + ": Opening socket");
		
		try {
			receiveSocket = new DatagramSocket(PORT);
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
	
	public void run() throws Exception {
		new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) { // run till shutdown requested
					if(!shutdown) {
						System.out.println("Enter q to shutdown the Server");
						String request = input.nextLine().toLowerCase();
						if (request.equals("q")) {
							input.close();
							shutdown = true;
						}

					} 
					/*Do not exit until all running threads have finished executing*/
					else if (shutdown && runningThreads == 0) {
						receiveSocket.close();
						System.out.println("Server has shut down");
						System.exit(0);
					}
				}
			}
		}).start();
		byte[] data = new byte[516];
		DatagramPacket receivePacket = new DatagramPacket(data, data.length);
		try {
			while (true) {
				System.out.println("---------------------------------");
				System.out.println("Waiting to Receive from Host");

				//Server receives a read or write request
				receiveSocket.receive(receivePacket);
				byte[] receivedData = receivePacket.getData();
				int port = receivePacket.getPort();
				InetAddress address = receivePacket.getAddress();
				int length = receivePacket.getLength();
				int n;
				for(n = 2; receivedData[n] != 0; n++){}
				String filename = new String(receivedData, 2, n-2);
				System.out.println(filename);
				System.out.println("--------------------------------------------");
				System.out.println("Packet Opcode: " + receivedData[1]);
				switch(receivedData[1]) {
				case (1): System.out.println("Packet Type: RRQ"); break;
				case (2): System.out.println("Packet Type: WRQ"); break;
				case (3): System.out.println("Packet Type: DATA"); break;
				case (4): System.out.println("Packet Type: ACK"); break;
				}
				
				System.out.print("Packet Contents: ");
				for (int i = 0; i < receivedData.length; i++)  {
					System.out.print(receivedData[i] + " ");
				}
				System.out.println();
				System.out.println("--------------------------------------------");
				System.out.println("Received a packet.");
				//processRequest(receivePacket);
				new Thread(new Runnable() {
					@Override
					public void run() {
						runningThreads++;
						System.out.println("New thread created.");
						//runningThreads++;
						if (receivedData[1] == 1) {
							System.out.println("Read request received");
							if(read(filename, receivedData, port, address, length)){
								System.out.println("A thread has completed execution.");
							}else{
								System.out.println("A thread has not completed execution.");
							}
						}
						
						else if (receivedData[1] == 2) {
							System.out.println("Write request recieved");
							if(write(filename, receivedData, port, address, length)){
								System.out.println("A thread has completed execution.");
							}else{
								System.out.println("A thread has not completed execution.");
							}
						} 
						else {
							System.out.println("ERR");
						}
						runningThreads--;
					}
				}).start();

			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public boolean read(String filename, byte[] receivedData, int port, InetAddress address, int length){
		DatagramSocket transferSocket = null;
		try {
			transferSocket = new DatagramSocket();
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		byte[] packetBytes = new byte[516];
		byte[] ack = {0,4,0,0};
		byte[] expectedACK = {0,4,0,0};
		packetBytes[0] = 0;
		packetBytes[1] = 3;
		packetBytes[2] = 0;
		packetBytes[3] = 0;
		byte[] packetData = new byte[512];
		int bytesRead = 0;
		boolean transferComplete = false;
		BufferedInputStream fileStream;
		DatagramPacket sendPacket = new DatagramPacket(packetBytes, packetBytes.length);
		DatagramPacket receivePacket = new DatagramPacket(ack, ack.length);
		try {
			fileStream = new BufferedInputStream(new FileInputStream(path + File.separator + filename));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false; 
		}
		try{
			while(!transferComplete){
				bytesRead = fileStream.read(packetData);
				if(bytesRead != -1){
					System.out.println("" + bytesRead + " bytes read");
					//Update Block Number
					if(Byte.toUnsignedInt(sendPacket.getData()[3]) != 255){
						packetBytes[2] = 0;
						packetBytes[3] = (byte) (Byte.toUnsignedInt(sendPacket.getData()[3]) + 1);
						   
					}
					else if(Byte.toUnsignedInt(sendPacket.getData()[2]) == 0 && Byte.toUnsignedInt(sendPacket.getData()[3]) == 255){
						packetBytes[2] = (byte) (Byte.toUnsignedInt(sendPacket.getData()[2]) + 1) ;
						packetBytes[3] = 0;
					}
					if(Byte.toUnsignedInt(expectedACK[3]) != 255){
						expectedACK[2] = 0;
						expectedACK[3] = (byte) (Byte.toUnsignedInt(expectedACK[3]) + 1);
						   
					}
					else if(Byte.toUnsignedInt(expectedACK[2]) == 0 && Byte.toUnsignedInt(expectedACK[3]) == 255){
						expectedACK[2] = (byte) (Byte.toUnsignedInt(expectedACK[2]) + 1) ;
						expectedACK[3] = 0;
					}
					else{
						System.out.println("Block Number Error");
						System.exit(1);
					}
					System.arraycopy(packetData, 0, packetBytes, 4, packetData.length);
					
					System.out.println("" + NAME + ": Sending Data Packet:");
					System.out.println("Data: ");
					for (int k=0; k < 4+bytesRead; k++) {
						 System.out.print(packetBytes[k] + " ");
					}
					System.out.println();
					try{
						transferSocket.send(new DatagramPacket(packetBytes, bytesRead+4 , address, port));
					}catch(IOException e){
						e.printStackTrace();
						System.exit(1);
					}
					try{
						System.out.println("Waiting for ACK");
						transferSocket.receive(receivePacket);
						if(!(verifyData(receivePacket.getData(), expectedACK))){
							System.out.println("Invalid ACK recieved");
							System.exit(1);
						}
						System.out.println("" + NAME + ": Received ACK Packet:");
						System.out.println("ACK: ");
						for (int k=0; k < receivePacket.getData().length; k++) {
							 System.out.print(receivePacket.getData()[k] + " ");
						}
						System.out.println();
					}catch(IOException e){
						e.printStackTrace();
						System.exit(1);
					}
				}
				else{ 
					// File Transfer Completed. Wait for last ACK
					fileStream.close();
					transferComplete = true;
				}

			}
			return true;
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return false;
	}
	public Boolean verifyData(byte[] received, byte[] expected){
		return Arrays.equals(received, expected);
	}
	public boolean write(String filename, byte[] receivedData, int port, InetAddress address, int length){
		System.out.println("Write");
		//InetAddress address = receivePacket.getAddress();
		System.out.println("ERR");
		//int port = receivePacket.getPort();
		DatagramSocket transferSocket = null;
		try {
			transferSocket = new DatagramSocket();
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		BufferedOutputStream fileOutput = null;
		try {
			fileOutput = new BufferedOutputStream(new FileOutputStream(path + File.separator + filename));
		} catch (FileNotFoundException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		byte[] ack = {0,4,0,0};
		byte[] expectedDataTID = {0,3,0,0};
		byte[] data = new byte[516];
		boolean finished = false;
		int len;
		DatagramPacket receivePacket = new DatagramPacket(data, data.length);
		while(!finished){
			try{
				transferSocket.send(new DatagramPacket(ack, ack.length, address, port)); 
			}catch(IOException e){
				e.printStackTrace();
				System.exit(1);
			}
			/*---------------------------------*/
			/*          Printing what we sent  */
			System.out.println("Server: Sending ACK packet:");
			System.out.println("To host: " + address);
			System.out.println("Destination host port: " + port);
			System.out.println("Length: " + length);
			System.out.println("Containing: ");
			for (int j=0;j<ack.length;j++) {
				 System.out.print(ack[j] + " ");
			}
			
			//Update ACK Number
			if(Byte.toUnsignedInt(ack[3]) != 255){
				ack[2] = 0;
				ack[3] = (byte) (Byte.toUnsignedInt(ack[3]) + 1);
				   
			}
			else if(Byte.toUnsignedInt(ack[2]) == 0 && Byte.toUnsignedInt(ack[3]) == 255){
				ack[2] = (byte) (Byte.toUnsignedInt(ack[2]) + 1) ;
				ack[3] = 0;
			}
			else{
				System.out.println("Block Number Error");
				System.exit(1);
			}
			if(Byte.toUnsignedInt(expectedDataTID[3]) != 255){
				expectedDataTID[2] = 0;
				expectedDataTID[3] = (byte) (Byte.toUnsignedInt(expectedDataTID[3]) + 1);
				   
			}
			else if(Byte.toUnsignedInt(expectedDataTID[2]) == 0 && Byte.toUnsignedInt(expectedDataTID[3]) == 255){
				expectedDataTID[2] = (byte) (Byte.toUnsignedInt(expectedDataTID[2]) + 1) ;
				expectedDataTID[3] = 0;
			}
			else{
				System.out.println("Block Number Error");
				System.exit(1);
			}
			
			/*---------------------------------*/
			
			/**
			 * Waiting for Data Packet.
			 */
			try{
				transferSocket.receive(receivePacket);
				if(!(verifyData(new byte[]{receivePacket.getData()[0], receivePacket.getData()[1], receivePacket.getData()[2], receivePacket.getData()[3]}, expectedDataTID))){
					System.out.println("Invalid ACK recieved");
					System.exit(1);
				}
			}catch(IOException e){
				e.printStackTrace();
				System.exit(1);
			}
			/*--------------------------------*/
			/*      Print what we received      */
			System.out.println("Server: Received Data packet:");
			System.out.println("To host: " + receivePacket.getAddress());
			System.out.println("Destination host port: " + receivePacket.getPort());
			len = receivePacket.getLength();
			System.out.println("Length: " + len);
			System.out.println("Containing: ");
			for (int j=0;j<len;j++) {
				 System.out.print(receivePacket.getData()[j] + " ");
			}
			/*----------------------------------*/
			// Writing it to file now.
			
			try {
				fileOutput.write(receivePacket.getData(), 4, receivePacket.getLength()-4);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			//-----------------------
			/**
			 * Check if we need to end this transfer connection
			 */
			if(receivePacket.getLength() < 516){
				finished = true;
				// and send the LAST ACK of the transfer.----//
				
				try{
					// receivePacket going to send "Ack's"
					receivePacket.setData(ack);
					receivePacket.setLength(ack.length);
					transferSocket.send(receivePacket); 
				}catch(IOException e){
					e.printStackTrace();
					System.exit(1);
				}
				/*---------------------------------*/
				/*          Printing what we sent  */
				System.out.println("Server: Sending ACK packet:");
				System.out.println("To host: " + receivePacket.getAddress());
				System.out.println("Destination host port: " + receivePacket.getPort());
				len = receivePacket.getLength();
				System.out.println("Length: " + len);
				System.out.println("Containing: ");
				for (int j=0;j<len;j++) {
					 System.out.print(receivePacket.getData()[j] + " ");
				}
				//------------Last ACK sent------------------//
				try {
					fileOutput.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				return true; // we finished.
			}
			
		}
		return false;
	}
	/*public void close()
	{
		socket.close();
	}*/
	
	public static void main(String args[])
	{
		input = new Scanner(System.in);
		System.out.print("Enter your name: ");
		 
		String name = input.nextLine();

		System.out.println("Hello " + name + ". Please enter server path");
		
		String svPath = input.nextLine();
		Server server = new Server(svPath);
		try {
			server.run();
		}
		catch (Exception e) {
			e.printStackTrace();
			//server.close();
		}
	}
}
