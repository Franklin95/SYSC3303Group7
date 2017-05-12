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
	private static final int PORT =	1025;
	private static boolean read;
	private static String file;
	private DatagramPacket sendPacket, receivePacket;
	private String path = null;
	
	private static DatagramSocket socket;
	
	public Client(String path)
	{
		this.path = path;
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
		//int port = receivePacket.getPort();
		try{
			   socket.send(formRRQ());
		}
		catch(IOException e){
			e.printStackTrace();
			System.exit(1);
		}
		BufferedOutputStream fileOutput = null;
		try {
			fileOutput = new BufferedOutputStream(new FileOutputStream((path + File.separator + file)));
		} catch (FileNotFoundException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		byte[] ack = {0,4,0,0};
		byte[] expectedDataTID = {0,3,0,0};
		byte[] data = new byte[516];
		boolean finished = false;
		int len;
		while(!finished){
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
			//Update Expected Data TID
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
				receivePacket = new DatagramPacket(data, data.length);
				socket.receive(receivePacket);
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
			try{
				socket.send(new DatagramPacket(ack, ack.length, InetAddress.getLocalHost(), PORT)); 
			}catch(IOException e){
				e.printStackTrace();
				System.exit(1);
			}
			/*---------------------------------*/
			/*          Printing what we sent  */
			System.out.println("Server: Sending ACK packet:");
			try {
				System.out.println("To host: " + InetAddress.getLocalHost());
			} catch (UnknownHostException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			System.out.println("Destination host port: " + PORT);
			System.out.println("Length: " + ack.length);
			System.out.println("Containing: ");
			for (int j=0;j<ack.length;j++) {
				 System.out.print(ack[j] + " ");
			}
			
			//-----------------------
			/**
			 * Check if we need to end this transfer connection
			 */
			if(receivePacket.getLength() < 516){
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
		return false;
	}
	
	public Boolean verifyData(byte[] received, byte[] expected){
		return Arrays.equals(received, expected);
	}
	
	public boolean processServerWrite(){
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
		try{
			   socket.send(formWRQ());
		}
		catch(IOException e){
			e.printStackTrace();
			System.exit(1);
		}
		receivePacket = new DatagramPacket(ack, ack.length);
		sendPacket = new DatagramPacket(packetBytes, packetBytes.length);
		try {
			fileStream = new BufferedInputStream(new FileInputStream((path + File.separator + file)));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		}
		try{
			while(!transferComplete){
				bytesRead = fileStream.read(packetData);
				if(bytesRead != -1){
					System.out.println("" + bytesRead + " bytes read");
					try{
						System.out.println("Waiting for ACK");
						socket.receive(receivePacket);
						if(!(verifyData(receivePacket.getData(), expectedACK))){
							System.out.println("Invalid ACK recieved");
							System.exit(1);
						}
					}catch(IOException e){
						e.printStackTrace();
						System.exit(1);
					}
					
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
					
					try{
						socket.send(new DatagramPacket(packetBytes, bytesRead+4 , InetAddress.getLocalHost(), PORT));
					}catch(IOException e){
						e.printStackTrace();
						System.exit(1);
					}
					System.out.println("" + NAME + ": Sending Data Packet:");
					System.out.println("Data: ");
					for (int k=0; k < 4+bytesRead; k++) {
						 System.out.print(packetBytes[k] + " ");
					}
					System.out.println();
				}
				else{ 
					// File Transfer Completed. Wait for last ACK
					fileStream.close();
					transferComplete = true;
					try{
						System.out.println("Awaiting last ACK");
						socket.receive(receivePacket);
					}catch(IOException e){
						e.printStackTrace();
						System.exit(1);
					}
					
					if(receivePacket.getData()[0] != 0 || receivePacket.getData()[1] != 4){
						System.out.println("Invalid ACK recieved");
						System.exit(1);
					}
					System.out.println("" + NAME + ": Last ACK Received.");
					System.out.println("Data: ");
					for (int j=0;j<receivePacket.getLength();j++) {
						 System.out.print(receivePacket.getData()[j] + " ");
					}
					System.out.println();
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
	
	private DatagramPacket formWRQ() {
		byte[] data = new byte[516];
		data[0] = 0;
		data[1] = 2;
		System.arraycopy(file.getBytes(), 0, data, 2, file.length());
		data[file.length()+2] = 0;
		System.arraycopy("octet".getBytes(), 0, data, file.length()+3, "octet".length());
		int lgth = file.length() + "octet".length() + 4;
		data[lgth-1] = 0;
		DatagramPacket packet = null;
		try{
			   packet = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), PORT);
		   }catch(IOException e){
			   e.printStackTrace();
			   System.exit(1);
		   }
		   return packet;
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
		DatagramPacket packet = null;
		try{
			   packet = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), PORT);
		   }catch(IOException e){
			   e.printStackTrace();
			   System.exit(1);
		   }
		   return packet;
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
				System.out.println(NAME +" Completed Server " + requestType);
			}
		}
		System.out.println(NAME +" is Shutting Down");
		input.close();
		socket.close();
	}
}
