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
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Scanner;

public class Client {
	private static final String NAME  = "CLIENT";
	private static int PORT =23;
	private InetAddress serverAddress;
	private static boolean read;
	private static String file;
	private DatagramPacket sendPacket, receivePacket;
	private String path = null;
	
	private static DatagramSocket socket;
	
	public Client(String path)
	{
		this.path = path;
		try {
			this.serverAddress = InetAddress.getLocalHost();
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.out.println("Client's working directory is: " + this.path);
		System.out.println(NAME + ": Opening socket");
		
		try {
			socket = new DatagramSocket();
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
	public boolean processServerRead(){
		System.out.println("Read");
		
		BufferedOutputStream fileOutput = null;
		try {
			fileOutput = new BufferedOutputStream(new FileOutputStream((path + File.separator + file)));
		} catch (FileNotFoundException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		
		byte[] ack = {0,4,0,1};
		byte[] expectedData = {0,3,0,1};
		byte[] data = new byte[516];
		boolean finished = false;
		boolean first = true;
		InetAddress expectedAddress = null;
		int expectedPort = 0;
		while(!finished){
			
			if(first){
				
				try{
					DatagramPacket request = formRRQ();
					System.out.println("");
					System.out.println(NAME + "Sending Read Request to:");
					printPacket(request);
					socket.send(request);
				}
				catch(IOException e){
					e.printStackTrace();
					return false;
				}
				
				try{
					receivePacket = new DatagramPacket(data, data.length);
					System.out.println(NAME + ": Waiting to receive DATA from Server");
					
					socket.receive(receivePacket);
					
					System.out.println("");
					System.out.println(NAME + ": Packet received from Server");
					printPacket(receivePacket);
				}catch(IOException e){
					e.printStackTrace();
					return false;
				}
				
				if(!(verifyData(new byte[]{receivePacket.getData()[0], receivePacket.getData()[1], receivePacket.getData()[2], receivePacket.getData()[3]}, expectedData))){
					System.out.println("Invalid DATA recieved");
					sendErrorPacket((byte)4, "Illegal TFTP Operation: Invalid Data Packet", receivePacket.getAddress(), receivePacket.getPort(), socket);
					return false;
				}
				
				else{
					first = false;
					expectedAddress = receivePacket.getAddress();
					expectedPort = receivePacket.getPort();
					try {
						fileOutput.write(receivePacket.getData(), 4, receivePacket.getLength()-4);
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
						return false;
					}
					//Update Expected Data
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
						return false;
					}
				}
			}
			else{
				while(true){
					try{
						sendPacket = new DatagramPacket(ack, ack.length, expectedAddress, expectedPort);
						
						System.out.println("");
						System.out.println("Server: Sending ACK packet to:");
						printPacket(sendPacket);
						
						socket.send(sendPacket); 
					}
					catch(Exception e){
						e.printStackTrace();
						return false;
					}
					
					try{
						receivePacket = new DatagramPacket(data, data.length);
						System.out.println(NAME + ": Waiting to receive DATA from Server");
						
						socket.receive(receivePacket);
						
						System.out.println("");
						System.out.println(NAME + ": Packet received from Server");
						printPacket(receivePacket);
					}catch(IOException e){
						return false;
					}
					
					if(!validateTID(receivePacket.getAddress(), receivePacket.getPort(), expectedAddress, expectedPort)){
						System.out.println("Unknown Transfer ID");
						sendErrorPacket((byte)5, "Unknown Transfer ID", receivePacket.getAddress(), receivePacket.getPort(), socket);
						try {
							fileOutput.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						return false;
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
						if(!(verifyData(new byte[]{receivePacket.getData()[0], receivePacket.getData()[1], receivePacket.getData()[2], receivePacket.getData()[3]}, expectedData))){
							System.out.println("");
							System.out.println(NAME + ": Invalid DATA packet received.");
							sendErrorPacket((byte)4, "Illegal TFTP Operation: Invalid DATA packet", receivePacket.getAddress(), receivePacket.getPort(), socket);
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
				//Update Expected Data
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
				
				//Check if the packet received is the last packet. If so, terminate the connection
				if(receivePacket.getLength() < 516){
					try{
						sendPacket = new DatagramPacket(ack, ack.length, expectedAddress, PORT);
						
						System.out.println("");
						System.out.println("Server: Sending last ACK packet to:");
						printPacket(sendPacket);
						
						socket.send(sendPacket); 
					}
					catch(Exception e){
						e.printStackTrace();
						return false;
					}
					
					finished = true;
					try {
						fileOutput.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					return true; // we finished.
				}
			}
			
		}
		return false;
	}
	
	private ErrorExtract isErrorPacket(DatagramPacket packet){
		byte[] dataPck = packet.getData();
		if(dataPck[1] != 5){
			return new ErrorExtract(false, false, (byte)0, null);
		}
		else if(dataPck[0] != 0 && dataPck[2] != 0){
			return new ErrorExtract(false, false, (byte)0, null);
		}
		byte[] msg = new byte[dataPck.length-4];
		System.arraycopy(dataPck, 4, msg, 0, (dataPck.length-4));
		System.out.println(new String(msg));
		switch (dataPck[3]){
		case (byte)1:
			return new ErrorExtract(true, true, (byte)1, new String(msg));
		case (byte)2:
			return new ErrorExtract(true, true, (byte)2, new String(msg));
		case (byte)3:
			return new ErrorExtract(true, true, (byte)3, new String(msg));
		case (byte)4:
			return new ErrorExtract(true, true, (byte)4, new String(msg));
		case (byte)5:
			return new ErrorExtract(true, true, (byte)5, new String(msg));
		case (byte)6:
			return new ErrorExtract(true, true, (byte)6, new String(msg));
		default:
			return new ErrorExtract(false, false, (byte)0, null);
		}
	}
	
	private void printPacket (DatagramPacket packet){
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
	
	public void sendErrorPacket(byte errorCode, String errorMessage, InetAddress address, int port, DatagramSocket communicationSocket)
	{
		byte[] packetBytes = new byte[516];
		packetBytes[0] = 0;
		packetBytes[1] = 5;
		packetBytes[2] = 0;
		packetBytes[3] = errorCode;
		byte[] msg = errorMessage.getBytes();
		System.arraycopy(msg, 0, packetBytes, 4, msg.length);
		DatagramPacket errorPacket = new DatagramPacket(packetBytes, 4+msg.length, address, port);
		System.out.println("");
		System.out.println(NAME + ": Sending Error Packet to the Server");
		System.out.println("Errorcode: " + errorCode);
		printPacket(errorPacket);
		try {
			communicationSocket.send(errorPacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Boolean verifyData(byte[] received, byte[] expected){
		return Arrays.equals(received, expected);
	}
	
	private Boolean validateTID(InetAddress receivedAddress, int receivedPort, InetAddress expectedAddress, int expectedPort){
		return (receivedAddress.equals(expectedAddress) && receivedPort == expectedPort);
		
	}
	
	public boolean processServerWrite(){
		byte[] packetBytes = new byte[516];
		byte[] ack = {0,4,0,0};
		byte[] expectedACK = {0,4,0,0};
		packetBytes[0] = 0;
		packetBytes[1] = 3;
		packetBytes[2] = 0;
		packetBytes[3] = 0;
		boolean first = true;
		byte[] packetData = new byte[512];
		int bytesRead = 0;
		boolean transferComplete = false;
		InetAddress expectedAddress = null;
		int expectedPort = 0;
		BufferedInputStream fileStream;
		try {
			fileStream = new BufferedInputStream(new FileInputStream((path + File.separator + file)));
		} catch (FileNotFoundException e) {
			System.out.println("");
			System.out.println("----------ERROR----------");
			System.out.println(e.getLocalizedMessage());
			System.out.println(new Error("File Does Not Exist"));
			System.out.println("-------------------------");
			return false;
		}
		
		receivePacket = new DatagramPacket(ack, ack.length);
		sendPacket = new DatagramPacket(packetBytes, packetBytes.length);
		
		try{
			while(!transferComplete){
				if(first){
					try{
						DatagramPacket request = formWRQ();
						System.out.println("");
						System.out.println(NAME + "Sending Write Request to:");
						printPacket(request);
						socket.send(request);
					}
					catch(IOException e){
						e.printStackTrace();
						System.exit(1);
					}
					
					try{
						System.out.println("");
						System.out.println(NAME + ": Waiting for ACK");
						
						socket.receive(receivePacket);
						
						System.out.println("");
						System.out.println(NAME + ": Received packet from:");
						printPacket(receivePacket);
						
					}catch(IOException e){
						e.printStackTrace();
						System.exit(1);
					}
					
					if(!(verifyData(new byte[]{receivePacket.getData()[0], receivePacket.getData()[1], receivePacket.getData()[2], receivePacket.getData()[3]}, expectedACK))){
						System.out.println("Invalid ACK recieved");
						sendErrorPacket((byte)4, "Illegal TFTP Operation: Invalid ACK packet", receivePacket.getAddress(), receivePacket.getPort(), socket);
					}
					else{
						first = false;
						expectedAddress = receivePacket.getAddress();
						expectedPort = receivePacket.getPort();
					}
				
				}
				else{
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
						else{
							System.out.println("Block Number Error");
							System.exit(1);
						}
						//Update Expected ACK
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
							
						while(true){
							try{
								sendPacket = new DatagramPacket(packetBytes, bytesRead+4 , expectedAddress, expectedPort);
								System.out.println("");
								System.out.println(NAME + ": Sending Data Packet to:");
								printPacket(sendPacket);
								socket.send(sendPacket);
							}catch(IOException e){
								e.printStackTrace();
								System.exit(1);
							}
								
							try{
								System.out.println("");
								System.out.println(NAME + ": Waiting for ACK");
									
								socket.receive(receivePacket);
									
								System.out.println("");
								System.out.println(NAME + ": Received packet from:");
								printPacket(receivePacket);
									
							}catch(IOException e){
								e.printStackTrace();
								System.exit(1);
							}
								
							if(!validateTID(receivePacket.getAddress(), receivePacket.getPort(), expectedAddress, expectedPort)){
								System.out.println("Unknown Transfer ID");
								sendErrorPacket((byte)5, "Unknown Transfer ID", receivePacket.getAddress(), receivePacket.getPort(), socket);
								fileStream.close();
								return false;
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
									sendErrorPacket((byte)4, "Illegal TFTP Operation: Invalid ACK", receivePacket.getAddress(), receivePacket.getPort(), socket);
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
						// File Transfer Completed.
						fileStream.close();
						transferComplete = true;
					}
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
	
	private DatagramPacket formWRQ() {
		byte[] data = new byte[516];
		data[0] = 0;
		data[1] = 2;
		System.arraycopy(file.getBytes(), 0, data, 2, file.length());
		data[file.length()+2] = 0;
		System.arraycopy("octet".getBytes(), 0, data, file.length()+3, "octet".length());
		int lgth = file.length() + "octet".length() + 4;
		data[lgth-1] = 0;
		return new DatagramPacket(data, 4+file.length()+"octet".length(), serverAddress, PORT);
	}
	
	private DatagramPacket formRRQ() {
		byte[] data = new byte[516];
		data[0] = 0;
		data[1] = 1;
		System.arraycopy(file.getBytes(), 0, data, 2, file.length());
		data[file.length()+2] = 0;
		System.arraycopy("octet".getBytes(), 0, data, file.length()+3, "octet".length());
		int lgth = file.length() + "octet".length() + 4;
		data[lgth-1] = 0;
		return new DatagramPacket(data, 4+file.length()+"octet".length(), serverAddress, PORT);
	}
	public void close()
	{
		socket.close();
	}
	
	public static void main(String args[])
	{
		Scanner input = new Scanner(System.in);
		System.out.print("Enter your name: ");
		 
		String name = input.nextLine();

		System.out.println("Hello " + name + ". Please enter Client path");
		String pth = input.nextLine();
		boolean validMode = false;
		boolean shutDown = false;
		String requestType = null;
		while(!shutDown){
			while(!validMode){
				System.out.println("Please Enter the mode of operation (Read/Write). Enter Q to quit");
				requestType = input.next();
				if(requestType.equalsIgnoreCase("Q")){
					shutDown = true;
					validMode = true;
				}
				else if(requestType.equalsIgnoreCase("Read")){
					read = true;
					validMode = true;
				}
				else if(requestType.equalsIgnoreCase("Write")){
					read = false;
					validMode = true;
				}
				else{
					requestType = null;
					validMode = false;
					System.out.println("Entered request type is invalid. try again");
				}
				if(validMode){
					System.out.println("Would you like test mode(y/n)? Press Q to quit");
					String vrbse = input.nextLine();
					if(vrbse.equalsIgnoreCase("y")){
						 PORT = 23;
					}
					else if(vrbse.equalsIgnoreCase("n")){
						PORT = 69;
					}
					else{
						validMode = false;
						System.out.println("Entry is invalid. try again");
					}
				}
			}
			if(shutDown){
				break;
			}
			validMode = false;
			System.out.println("Please Enter the file to be transfered with the extension. E.g. test.txt");
			file = input.next();
			System.out.println("Client is performing a " + requestType + " operation of " + file);
			Client client = new Client(pth);
			boolean complete = false;
			if(read){
				complete = client.processServerRead();
			}
			else{
				complete = client.processServerWrite();
			}
			if(complete){
				System.out.println(NAME +": Completed Server " + requestType + " of file " + file);
			}
			System.out.println("");
		}
		System.out.println(NAME +" is Shutting Down");
		input.close();
		if(socket != null){
			socket.close();
		}
	}
}
