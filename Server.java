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
	private static final int PORT = 69;
	private static boolean shutdown = false;
	private static DatagramSocket receiveSocket;
	private String path;
	private static int runningThreads = 0;
	private static Scanner input;
	private static boolean verbose = true;
	
	public Server(String path)
	{
		this.path = path;
		if(verbose){
			System.out.println("Server's working directory is: " + path);
			System.out.println(NAME + ": Opening socket");
		}
		
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
				if(verbose){
					System.out.println("---------------------------------");
					System.out.println("Waiting to Receive from Host");
				}

				//Server receives a read or write request
				receiveSocket.receive(receivePacket);
				byte[] receivedData = receivePacket.getData();
				int port = receivePacket.getPort();
				InetAddress address = receivePacket.getAddress();
				if(verbose){
					System.out.println("");
					System.out.println(NAME + ": Received packet from:");
				}
				printPacket(receivePacket);
				DataExtract requestInfo = validateAndExtractRequest(receivedData);
				if(!requestInfo.valid){
					if(verbose){
						System.out.println("");
						System.out.println(NAME + ": Invalid request. Sending error packet and terminating connection with Client");
					}
					sendErrorPacket((byte)4, "Illegal TFTP Operation: Illegal Request", address, port, new DatagramSocket());
				}
				else{
					String filename = requestInfo.fileName;
					new Thread(new Runnable() {
						@Override
						public void run() {
							runningThreads++;
							if (requestInfo.read) {
								if(verbose){
									System.out.println("Read request received");
								}
								if(read(filename, receivedData, port, address)){
									if(verbose){
										System.out.println("File Read Transfer of " + filename + " Completed");
									}
								}else{
									if(verbose){
										System.out.println("File Read Transfer of " + filename + " not successful. An error occured");
									}
								}
							}
							
							else{
								if(verbose){
									System.out.println("Write request recieved");
								}
								if(write(filename, receivedData, port, address)){
									if(verbose){
										System.out.println("File Read Transfer of " + filename + " Completed");
									}
								}else{
									if(verbose){
										System.out.println("File Read Transfer of " + filename + " not successful. An error occured");
									}
								}
							}
							runningThreads--;
						}
					}).start();
	
				}
			}
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	public boolean read(String filename, byte[] receivedData, int port, InetAddress address){
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
						if(verbose){
							System.out.println("Block Number Error");
						}
						return false;
					}
					System.arraycopy(packetData, 0, packetBytes, 4, packetData.length);
					
					while(true){
						try{
							sendPacket = new DatagramPacket(packetBytes, bytesRead+4 , address, port);
							if(verbose){
								System.out.println("");
								System.out.println(NAME + ": Sending Data Packet to:");
							}
							printPacket(sendPacket);
							transferSocket.send(sendPacket);
						}catch(IOException e){
							e.printStackTrace();
							return false;
						}
						try{
							if(verbose){
								System.out.println("Waiting for ACK");
							}
							transferSocket.receive(receivePacket);
						}catch(IOException e){
							e.printStackTrace();
							return false;
						}
						if(verbose){
							System.out.println("");
							System.out.println(NAME + ": Received Packet from:");
						}
						printPacket(receivePacket);
						
						if(!validateTID(receivePacket.getAddress(), receivePacket.getPort(), address, port)){
							System.out.println("");
							System.out.println(NAME + ": Unknown TID. Sending error packet and resuming connection with Client");
							sendErrorPacket((byte)5, "Unknown Transfer ID", receivePacket.getAddress(), receivePacket.getPort(), transferSocket);
						}
						else{
							ErrorExtract errorPacket = isErrorPacket(receivePacket);
							if(errorPacket.isError){
								System.out.println("");
								System.out.println("Received Packet is an error packet");
								System.out.println("Error code: " + errorPacket.errorCode);
								System.out.println("Error message: " + errorPacket.message);
								if(errorPacket.terminate){
									System.out.println("");
									System.out.println("Terminating the connection");
									fileStream.close();
									return false;
								}
								else{
									System.out.println("");
									System.out.println("Ignoring the error packet and continuing the transfer");
								}
							}
							else if(!(verifyData(new byte[]{receivePacket.getData()[0], receivePacket.getData()[1], receivePacket.getData()[2], receivePacket.getData()[3]}, expectedACK))){
								System.out.println("");
								System.out.println(NAME + ": Invalid ACK packet received. Sending error packet and terminating connection with Client");
								sendErrorPacket((byte)4, "Illegal TFTP Operation: Invalid ACK", receivePacket.getAddress(), receivePacket.getPort(), transferSocket);
								fileStream.close();
								return false;
							}
							else{
								break;
							}
						}
					}
				}
				else{ 
					// File Transfer Completed. Wait for last ACK
					fileStream.close();
					transferComplete = true;
				}

			}
			return transferComplete;
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return false;
		}
	}
	private ErrorExtract isErrorPacket(DatagramPacket packet){
		byte[] data = packet.getData();
		if(data[1] != 5){
			return new ErrorExtract(false, false, (byte)0, null);
		}
		else if(data[0] != 0 && data[2] != 0){
			return new ErrorExtract(false, false, (byte)0, null);
		}
		switch (data[3]){
		case (byte)1:
			return new ErrorExtract(true, true, (byte)1, new String(data, 4, data.length-4));
		case (byte)2:
			return new ErrorExtract(true, true, (byte)2, new String(data, 4, data.length-4));
		case (byte)3:
			return new ErrorExtract(true, true, (byte)3, new String(data, 4, data.length-4));
		case (byte)4:
			return new ErrorExtract(true, true, (byte)4, new String(data, 4, data.length-4));
		case (byte)5:
			return new ErrorExtract(true, true, (byte)5, new String(data, 4, data.length-4));
		case (byte)6:
			return new ErrorExtract(true, true, (byte)6, new String(data, 4, data.length-4));
		default:
			return new ErrorExtract(false, false, (byte)0, null);
		}
	}
	
	private Boolean validateTID(InetAddress receivedAddress, int receivedPort, InetAddress expectedAddress, int expectedPort){
		return (receivedAddress.equals(expectedAddress) && receivedPort == expectedPort);
		
	}
	
	public Boolean verifyData(byte[] received, byte[] expected){
		return Arrays.equals(received, expected);
	}
	public boolean write(String filename, byte[] receivedData, int port, InetAddress address){
		System.out.println("Write");
		
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
		byte[] expectedData = {0,3,0,1};
		byte[] data = new byte[516];
		boolean finished = false;
		DatagramPacket receivePacket = new DatagramPacket(data, data.length);
		DatagramPacket sendPacket = null;
		while(!finished){
			while(true){
				try{
					sendPacket = new DatagramPacket(ack, ack.length, address, port);
					System.out.println("");
					System.out.println("Server: Sending ACK packet to:");
					printPacket(sendPacket);
					transferSocket.send(sendPacket); 
				}catch(IOException e){
					e.printStackTrace();
					System.exit(1);
				}
				
				/*---------------------------------*/
				
				/**
				 * Waiting for Data Packet.
				 */
				try{
					transferSocket.receive(receivePacket);
				}catch(IOException e){
					e.printStackTrace();
					System.exit(1);
				}
				
				System.out.println("");
				System.out.println(NAME + ": Received Data packet from:");
				printPacket(receivePacket);
				
				if(!validateTID(receivePacket.getAddress(), receivePacket.getPort(), address, port)){
					System.out.println("");
					System.out.println(NAME + ": Unknown TID. Sending error packet and resuming connection with Client");
					sendErrorPacket((byte)5, "Unknown Transfer ID", receivePacket.getAddress(), receivePacket.getPort(), transferSocket);
				}
				else{
					ErrorExtract errorPacket = isErrorPacket(receivePacket);
					if(errorPacket.isError){
						System.out.println("");
						System.out.println("Received Packet is an error packet");
						System.out.println("Error code: " + errorPacket.errorCode);
						System.out.println("Error message: " + errorPacket.message);
						if(errorPacket.terminate){
							System.out.println("");
							System.out.println("Terminating the connection");
							try {
								fileOutput.close();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							return false;
						}
						else{
							System.out.println("");
							System.out.println("Ignoring the error packet and continuing the transfer");
						}
					}
					else if(!(verifyData(new byte[]{receivePacket.getData()[0], receivePacket.getData()[1], receivePacket.getData()[2], receivePacket.getData()[3]}, expectedData))){
						System.out.println("");
						System.out.println(NAME + ": Invalid DATA packet received. Sending error packet and terminating connection with Client");
						sendErrorPacket((byte)4, "Illegal TFTP Operation: Invalid DATA packet", receivePacket.getAddress(), receivePacket.getPort(), transferSocket);
						try {
							fileOutput.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						return false;
					}
					else{
						break;
					}
				}
			}
			
			try {
				fileOutput.write(receivePacket.getData(), 4, receivePacket.getLength()-4);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
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
			if(Byte.toUnsignedInt(expectedData[3]) != 255){
				expectedData[2] = 0;
				expectedData[3] = (byte) (Byte.toUnsignedInt(expectedData[3]) + 1);
				   
			}
			else if(Byte.toUnsignedInt(expectedData[2]) == 0 && Byte.toUnsignedInt(expectedData[3]) == 255){
				expectedData[2] = (byte) (Byte.toUnsignedInt(expectedData[2]) + 1) ;
				expectedData[3] = 0;
			}
			else{
				System.out.println("Block Number Error");
				System.exit(1);
			}
			
			//-----------------------
			/**
			 * Check if we need to end this transfer connection
			 */
			if(receivePacket.getLength() < 516){
				finished = true;
				
				try{
					System.out.println("");
					System.out.println("Server: Sending ACK packet to:");
					printPacket(sendPacket);
					transferSocket.send(sendPacket); 
				}catch(IOException e){
					e.printStackTrace();
					System.exit(1);
				}
				
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
	
	private void printPacket (DatagramPacket packet){
		System.out.println(NAME + ": packet:");
		System.out.println("Host: " + packet.getAddress());
		System.out.println("Port: " + packet.getPort());
		int len = packet.getLength();
		System.out.println("Length: " + len);
		System.out.println("Data: ");
		for (int j=0;j<len;j++) {
			 System.out.print(packet.getData()[j] + " ");
		}
		System.out.println("");
	}
	
	private DataExtract validateAndExtractRequest(byte[] data){
		if(data[0] != 0){
			System.out.println("First data byte is not 0");
			return new DataExtract(false, null, null);
		}
		if(data == null || data.length < 3){
			System.out.println("Packet data is empty");
			return new DataExtract(false, null, null);
		}
		
		StringBuilder builder = new StringBuilder();
		String filename = null;
		for (int i = 2; i < data.length; i++) {
			if(data[i] != 0) {
				builder.append((char) data[i]);
			} 
			else {
				filename = builder.toString();
				break;
			}
		}
		System.out.println("FileName: " + filename);
		if(filename == null){
			System.out.println("Invalid filename");
			return new DataExtract(false, null, null);
		}
		
		switch(data[1]){
		case (byte)1:
			System.out.println("RRQ received");
			return new DataExtract(true, true, filename);
		case (byte)2:
			System.out.println("WRQ received");
			return new DataExtract(true, false, filename);
		default:
			System.out.println("Invalid opcode");
			return new DataExtract(false, null, null);
			
		}
	}
	
	public void sendErrorPacket(byte errorCode, String errorMessage, InetAddress address, int port, DatagramSocket communicationSocket)
	{
		byte[] dta = new byte[516];
		dta[0] = 0;
		dta[1] = 5;
		dta[2] = 0;
		dta[3] = errorCode;
		byte[] msg = errorMessage.getBytes();
		System.out.println("Message: " + new String(msg));
		System.arraycopy(msg, 0, dta, 4, msg.length);
		DatagramPacket errorPacket = new DatagramPacket(dta, 4+msg.length, address, port);
		System.out.println("");
		System.out.println(NAME + ": Sending Error Packet to the Client");
		System.out.println("Errorcode: " + errorCode);
		printPacket(errorPacket);
		try {
			communicationSocket.send(errorPacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String args[])
	{
		input = new Scanner(System.in);
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
		
		System.out.println("Hello. Please enter server path");
		String svPath = input.nextLine();
		Server server = new Server(svPath);
		while(true){
			System.out.println("Would you like verbose mode(y/n)? Press Q to quit");
			String vrbse = input.nextLine();
			if(vrbse.equalsIgnoreCase("y")){
				verbose = true;
			}
			else{
				verbose = false;
			}
			try {
				server.run();
			}
			catch (Exception e) {
				e.printStackTrace();
				//server.close();
			}
		}
	}
}
class DataExtract{
	protected Boolean valid = false;
	protected String fileName = null;
	protected Boolean read = null;
	public DataExtract(Boolean valid, Boolean read, String fileName){
		this.valid = valid;
		this.fileName = fileName;
		this.read = read;
	}
}
class ErrorExtract{
	protected Boolean isError = false;
	protected Boolean terminate = false;
	protected byte errorCode;
	protected String message = null;
	public ErrorExtract(Boolean isError, Boolean terminate, byte errorCode, String message){
		this.isError = isError;
		this.terminate = terminate;
		this.message = message;
		this.errorCode = errorCode;
	}
}
