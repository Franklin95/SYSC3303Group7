
import java.io.IOException;
import java.net.*;
//import ErrorSimulator.ErrorSi

public class ErrorSimulator extends Thread{
	public static final int DATA_SIZE = 516;
	private boolean verbose;
	private boolean done = false; // Variable to keep track of whether the file transfer is done
	// Mode of operation entered by the user. 0 for Normal mode, 1 for lost mode, 2 for delayed mode, 3 for duplicate mode, 4 to shutdown the error simulator
	private int Selection;
	private int serverPort = 69; // the server port will be initiated to 69 and will change according to the thread needed 
	private DatagramSocket serverSocket, clientSocket, error5Socket; // socket deceleration for all three required sockets 
	private DatagramPacket sendClientPacket, receiveClientPacket, sendServerPacket , receiveServerPacket; // packet deceleration for all packets being sent and received for both client and server
	private byte clientData[]; // Stores the data received from the client
	private int clientPort; //Stores the port number used to receive the client's request
	private int clientLength; //Stores the length of the dat received from the client
	private int serverLength; // stores the length of the data received from the server
	private boolean Read = false; // This variable is true if and only if data is being read from the server
	private boolean Write = false; // This variable is true if and only if data is being written to the server
	private boolean lastPacketWrite = false; // This variable is true if and only if the error simulator receives the last data packet to be written to the server
	private boolean lastPacketRead = false; // This variable is true if and only if the error simulator receives the last packet data to be read from the server
	private boolean firstPacket = true;
	private boolean end = false; // This variable stores the return values of the various modes of operation and is true if and only if the full file transfer process between the client and the server has been completed
	private boolean errorReceived = false;
	private int packetType = 0; // Stores the type of packet to be lost, duplicated or delayed, as specified by the user
	private int packetNumber = 0; // Stores the packet number to be lost, duplicated or delayed, as specified by the user
	byte clientReply[] = new byte[DATA_SIZE]; // Store the client's reply
	byte serverReply[] = new byte[DATA_SIZE]; // // Store the server's reply
	byte serverData[] = new byte[DATA_SIZE]; // Stores the data received from the server
	byte trueLastPacket[] = new byte[2]; // Stores the correct last block number of a data transfer to make sure that the file transfer process has been fully successful
	InetAddress clientAddress;
	SocketAddress errorAddress;
	int errorPort = 55;
	private InetAddress serverAddress;
	
	public ErrorSimulator(boolean verbose, int Selection, int packetType, int packetNumber, byte[] clientData, int clientPort, int clientLength, boolean Read, boolean Write, boolean errorReceived, InetAddress serverAddress) {
		this.verbose = verbose;
		this.clientData = clientData;
		this.clientPort = clientPort;
		this.clientLength = clientLength;
		this.Selection = Selection;
		this.packetType = packetType;
		this.packetType = packetType;
		this.packetNumber = packetNumber;
		this.Read = Read;
		this.Write = Write;
		this.serverAddress= serverAddress;
		this.errorReceived = errorReceived;
		try {
			serverSocket = new DatagramSocket(); // Create a new Datagram socket for receiving data from the server.
		} // end try 
		catch (SocketException se) {
			System.err.println("SocketException: " + se.getMessage());
		} // end catch
		
		try {
			clientSocket = new DatagramSocket();
		} // end try 
		catch (SocketException se) {
			System.err.println("SocketException: " + se.getMessage());
		} // end catch
		
		if(verbose){
		System.out.println("Starting Simulation");
		}
		try {
			receiveClientPacket = new DatagramPacket(clientData, clientLength, InetAddress.getLocalHost(), clientPort);
		} catch (UnknownHostException e) {
			System.err.println("UnknownHostException: " + e.getMessage());
		}
		if(verbose){
		System.out.println("Exiting Constructor");
		}
	} // end constructor


	public void run() {
		if(verbose){
			System.out.println(" " + Selection);
		}
		// Keep looping until we are done with the data transfer.
		while (!end) {
			if (Selection == 0)
			{
				end = normalMode();
			}
			else if (Selection == 4) {
				end = simulateError4();
			}
			else if (Selection == 5) {
				end = simulateError5();
			}
		}//end while

		// If we are done with the data transfer, close sockets and return
		if(verbose){
			System.out.println("File transfer done. I am closing my client and server sockets");
		}
		serverSocket.close();
		clientSocket.close();
	} // end method


	private boolean normalMode(){
		if(verbose){
		System.out.println("Now operating in Normal data transfer mode.");
		}

		// If this is not the first iteration, i.e if you are just returning from a previous mode of operation, retrieve either a request, ACK or DATA from the client, depending on what was last sent to the client. 
		if (!firstPacket) {
			clientReceive();

		}//end if
		//if this is the initial iteration, send the initial request received from the client, to the Server
		serverSend();
		doneTransfer();
		if (done){ 
			return true;
		}
		// Check if this is not the first iteration and the request type is WRQ
		if (Write == true && !firstPacket) {
			if(verbose){
				System.out.println("Checking if this packet size...");
				printInformation(sendServerPacket);
			}
			// Check if we have written the last packet to the server.
			if(sendServerPacket.getLength() < DATA_SIZE)
				lastPacketWrite = true;	
		} // end if

		// Receive ACK or data from the server and send to the client. 
		serverReceive();
		clientSend();
		firstPacket = false;		// At this point, we are no longer in the first iteration of the data transfer. So, set this variable to false.
		// If an error was received from the server or we have completely and successfully written the client's desired data to the server, return true and exit.
		if (errorReceived || lastPacketWrite)
			return true;
		/* Check if this is not the first iteration and the request type is RRQ. If so, check if we have completely and successfully read the client's desired data from the server. If we have done this, we have to return 
		false, which will enable the program return to the begining of this method, so that the final ACK can be sent back to the server, after which we exit.*/
		if (Read == true) {
			verifyPacketSize();
			return false;
		} // end if
		return false;

	}
	public void verifyPacketSize(){
		if (!firstPacket) {
			if(verbose){
			System.out.println("Checking if the packet is less than 512 bytes:");
			}
			if (Read == true){
				printInformation(sendClientPacket);
				if(sendClientPacket.getLength() < DATA_SIZE) {
					lastPacketRead = true;
					trueLastPacket[0] = sendClientPacket.getData()[2];
					trueLastPacket[1] = sendClientPacket.getData()[3];
				}// end if
			}
			else if (Write == true){
				printInformation(sendServerPacket);
				if(sendServerPacket.getLength() < DATA_SIZE) {
					lastPacketWrite = true;	
					trueLastPacket[0] = sendServerPacket.getData()[2];
					trueLastPacket[1] = sendServerPacket.getData()[3];
				}//end if
			} // 
		}
	}

	public void doneTransfer(){
		if (Read == true){
			if (errorReceived || (lastPacketRead == true && trueLastPacket[0] == sendServerPacket.getData()[2] && trueLastPacket[1] == sendServerPacket.getData()[3])){
				done = true;
			}// end if
		}
		else if (Write == true){
			if (errorReceived || (lastPacketWrite == true && trueLastPacket[0] == sendClientPacket.getData()[2] && trueLastPacket[1] == sendClientPacket.getData()[3])){	
				done = true;	// Last packet is now sent. The thread will close
			}// end if
		}
		else{
			done = false;
		}
	}
	
	private boolean simulateError4() {
		System.out.println("------------------------------------------------");
		System.out.println("Invalid TFTP Operation Simulation (Error Code 4)");
		if (packetType == 1 || packetType == 2) { // If we are sending an RRQ or WRQ that is not properly formed
			if(verbose){
			System.out.println("Sending invalid request to Server");
			}
			clientData[1] = (byte)9;
			serverSend();
			serverReceive();
			clientSend();
			return true;
		}
		else if (Read == true) { // this is a read request
			if(verbose){
			System.out.println("Starting invalid packet send for read action");
			}
			if (packetType == 3) { // DATA packet being duplicated from the server 

				if (!firstPacket){ // Recieve request packet from client
					clientReceive();
				}
				firstPacket = false;
				serverSend(); // Send request to the server
				doneTransfer();
				if (done) return true;
				serverReceive(); // Recieve desired packet from the server 

				// Verify that this is the right data packet to be duplicated
				if (foundPacket(receiveServerPacket)) {
					if(verbose){
					System.out.println("Error simulator recieved the data to be manipulated for Error 4...");
					}
					serverData[1] = (byte) 9;
					clientSend();
					clientReceive();
					serverSend();
					return true;
				}
				clientSend();
				verifyPacketSize();
				firstPacket = false;
				return false; // Return false and continue the rest of the data transfer in normal mode
			}// end if

			else if (packetType == 4) { // Check if the user wants the client to send a duplicate ACK to the server.

				if (!firstPacket) {
					clientReceive();
				}
				// Verify that this is the right ACK packet to be duplicated
				if (foundPacket(receiveClientPacket)) {
					if(verbose){
					System.out.println("Error simulator recieved the data to be manipulated for Error 4...");
					}
					clientData[1] = (byte) 9;
					serverSend();
					serverReceive();
					clientSend();
					return true;
				}
				serverSend();
				// Check if the last acknowledge was just sent to the server
				doneTransfer();
				if (done){
					return true;
				}
				// If the last ACK has not been sent to the server, retrieve next 512 bytes of data from the server and send to the client.
				serverReceive();
				clientSend();
				// Check if we are done reading the client's desired data from the client, return false. This will take the program back to normal mode, where the last or next ACK will be sent to the server.
				verifyPacketSize();
				firstPacket = false;
				return false;
			}// end else if
		} // end read duplicate mode
		else if (Write == true) { // Check if the user specified a write request
			if(verbose){
			System.out.println("Starting invalid packet send for write action...");
			}
			if (packetType == 3) { // Check if the user specified a data duplication mode
				if (!firstPacket){
					clientReceive();
				}
				firstPacket = false;
				// Verify that this is the right DATA packet to be duplicated
				if (foundPacket(receiveClientPacket)) {
					if(verbose){
					System.out.println("Error simulator recieved the data to be manipulated for Error 4...");
					}
					clientData[1] = (byte) 9;
					serverSend();
					serverReceive();
					clientSend();
					return true;
				}
				serverSend(); // If the duplicate was found, send the duplicate data to the server. Otherwise, this still sends the data it receives to the server, without duplicating it.
				verifyPacketSize(); // Check if the sent data was the last data to be transfered.
				serverReceive(); // receive ACK from the server
				clientSend(); // Send ACK to the client
				doneTransfer(); // check if we are done with the data transfer
				if (done){ 
					return true;
				} // If we are done with the data transfer, return true.
				else {
					firstPacket = false;
					return false;
				}// This will take the program back to normal mode, if not done, where the last or next DATA will be sent to the server normally, without duplication.
			}// end if

			else if (packetType == 4) { // Check if the user specified a data duplication mode

				if (!firstPacket) { // if this is not the first packet to be sent, recieve next data or ACK from the client. If this is the first data transfer, skip this loop and sent request to the server.
					clientReceive();
				} // end if

				serverSend(); // Send the client's packet to the server
				verifyPacketSize(); // Check if we have sent the last packet to the client, if this is not the first data transfer stage
				serverReceive(); // Retrieve an ACK from the server
				// Verify that this is the right ACK packet to be duplicated
				if (foundPacket(receiveServerPacket)) {
					if(verbose){
					System.out.println("Error simulator recieved the data to be manipulated for Error 4...");
					}
					serverData[1] = (byte) 9;
					clientSend();
					clientReceive();
					serverSend();
					return true;
				}
				clientSend();
				doneTransfer();
				if (done){
					return true;
				}
				else{
					firstPacket = false;
					return false;
				}
			}// end else if
		}// End write ACK duplication mode
		return true;
	}
	
	private boolean simulateError5() {
		DatagramPacket errorPacket;
		System.out.println("------------------------------------------------");
		System.out.println("Invalid TFTP Operation Simulation (Error Code 5)");
		/*if (packetType == 1 || packetType == 2) { // If we are sending an RRQ or WRQ that is not properly formed
			if(verbose){
			System.out.println("Sending invalid request to Server");
			}
			clientData[1] = (byte)9;
			serverSend();
			serverReceive();
			clientSend();
			return true;
		}*/
		if (Read == true) { // this is a read request
			if(verbose){
			System.out.println("Starting invalid packet send for read action");
			}
			if (packetType == 3) { // DATA packet being duplicated from the server 

				if (!firstPacket){ // Receive request packet from client
					clientReceive();
				}
				firstPacket = false;
				serverSend(); // Send request to the server
				doneTransfer();
				if (done) return true;
				serverReceive(); // Receive desired packet from the server 

				// Verify that this is the right data packet to be duplicated
				if (foundPacket(receiveServerPacket)) {
					if(verbose){
					System.out.println("Error simulator recieved the data to be manipulated for Error 5...");
					}
					try {
						error5Socket = new DatagramSocket(); // Create a new Datagram socket for receiving data from the server.
						errorPacket = new DatagramPacket(serverData, serverLength, clientAddress, clientPort);
						if (verbose){
							System.out.println("Sending to client using new Socket");
							printInformation(errorPacket);
						}
						error5Socket.send(errorPacket);
						if (verbose){
							System.out.println("I have sent the packet to the client");
						}
						byte[] b = new byte[516];
						DatagramPacket receive = new DatagramPacket(b, 516);
						if (verbose){
							System.out.println("I am trying to receive a response packet from the client");
						}
						error5Socket.receive(receive);
						if (verbose){
							System.out.println("Response packet received from the client");
							printInformation(receive);
						}
						receive.setPort(serverPort);
						receive.setAddress(serverAddress);
						if (verbose){
							System.out.println("Sending Error Packet to server");
						}
						serverSocket.send(receive);
						
						/*b = new byte[516];
						receive = new DatagramPacket(b, 516);
						error5Socket.receive(receive);*/
						if (verbose){
							System.out.println("Closing error socket");
						}
						error5Socket.close();
					} // end try 
					catch (Exception e) {
						System.err.println("SocketException: " + e.getMessage());
					} // end catch
					Selection = 0;
					return true;
				}
				clientSend();
				verifyPacketSize();
				firstPacket = false;
				return false; // Return false and continue the rest of the data transfer in normal mode
			}// end if

			else if (packetType == 4) { // Check if the user wants the client to send a duplicate ACK to the server.

				if (!firstPacket) {
					clientReceive();
				}
				firstPacket = false;
				// Verify that this is the right ACK packet to be duplicated
				if (foundPacket(receiveClientPacket)) {
					if(verbose){
					System.out.println("Error simulator recieved the data to be manipulated for Error 4...");
					}
					try {
						error5Socket = new DatagramSocket(); // Create a new Datagram socket for receiving data from the server.
						errorPacket = new DatagramPacket(clientData, clientLength, serverAddress, serverPort);
						if (verbose){
							System.out.println("Sending to server using error Socket");
							printInformation(errorPacket);
						}
						error5Socket.send(errorPacket);
						if(verbose){
							System.out.println("I have sent the packet to the server");
						}
						
						byte[] b = new byte[516];
						DatagramPacket receive = new DatagramPacket(b, 516);
						if(verbose){
							System.out.println("Attempting to receive from the server");
						}
						error5Socket.receive(receive);
						
						if (verbose){
							System.out.println("Paket received from the server");
							printInformation(receive);
						}
						receive.setPort(clientPort);
						receive.setAddress(clientAddress);
						if(verbose){
						System.out.println("Sending Error Packet to client");
						}
						clientSocket.send(receive);
						if (verbose){
							System.out.println("Packet sent to the client");
							printInformation(errorPacket);
						}
						
						/*b = new byte[516];
						receive = new DatagramPacket(b, 516);
						System.out.println("waiting to receive from server");
						serverSocket.receive(receive);
						System.out.println("Received");*/
						if(verbose){
							System.out.println("I am closing the error socket I created");
						}
						error5Socket.close();
					} // end try 
					catch (Exception e) {
						System.err.println("SocketException: " + e.getMessage());
					} // end catch
					Selection = 0;
					return true;
				}
				serverSend();
				// Check if the last acknowledge was just sent to the server
				doneTransfer();
				if (done){
					return true;
				}
				// If the last ACK has not been sent to the server, retrieve next 512 bytes of data from the server and send to the client.
				serverReceive();
				clientSend();
				// Check if we are done reading the client's desired data from the client, return false. This will take the program back to normal mode, where the last or next ACK will be sent to the server.
				verifyPacketSize();
				firstPacket = false;
				return false;
			}// end else if
		} // end read duplicate mode
		else if (Write == true) { // Check if the user specified a write request
			if(verbose){
			System.out.println("Starting invalid packet send for write action...");
			}
			if (packetType == 3) { // Check if the user specified a data duplication mode
				if (!firstPacket) {
					clientReceive();
				}
				firstPacket = false;
				// Verify that this is the right ACK packet to be duplicated
				if (foundPacket(receiveClientPacket)) {
					if(verbose){
					System.out.println("Error simulator recieved the data to be manipulated for Error 4...");
					}
					try {
						error5Socket = new DatagramSocket(); // Create a new Datagram socket for receiving data from the server.
						errorPacket = new DatagramPacket(clientData, clientLength, serverAddress, serverPort);
						if (verbose){
							System.out.println("Sending to server using error Socket");
							printInformation(errorPacket);
						}
						error5Socket.send(errorPacket);
						if(verbose){
							System.out.println("I have sent the packet to the server");
						}
						
						byte[] b = new byte[516];
						DatagramPacket receive = new DatagramPacket(b, 516);
						if(verbose){
							System.out.println("Attempting to receive from the server");
						}
						error5Socket.receive(receive);
						
						if (verbose){
							System.out.println("Paket received from the server");
							printInformation(receive);
						}
						receive.setPort(clientPort);
						receive.setAddress(clientAddress);
						if(verbose){
						System.out.println("Sending Error Packet to client");
						}
						clientSocket.send(receive);
						if (verbose){
							System.out.println("Packet sent to the client");
							printInformation(errorPacket);
						}
						
						/*b = new byte[516];
						receive = new DatagramPacket(b, 516);
						System.out.println("waiting to receive from server");
						serverSocket.receive(receive);
						System.out.println("Received");*/
						if(verbose){
							System.out.println("I am closing the error socket I created");
						}
						error5Socket.close();
					} // end try 
					catch (Exception e) {
						System.err.println("SocketException: " + e.getMessage());
					} // end catch
					Selection = 0;
					return true;
				}
				serverSend(); // If the duplicate was found, send the duplicate data to the server. Otherwise, this still sends the data it receives to the server, without duplicating it.
				verifyPacketSize(); // Check if the sent data was the last data to be transfered.
				serverReceive(); // receive ACK from the server
				clientSend(); // Send ACK to the client
				doneTransfer(); // check if we are done with the data transfer
				if (done){ 
					return true;
				} // If we are done with the data transfer, return true.
				else {
					firstPacket = false;
					return false;
				}// This will take the program back to normal mode, if not done, where the last or next DATA will be sent to the server normally, without duplication.
			}// end if

			else if (packetType == 4) { // Check if the user specified a data duplication mode

				if (!firstPacket){ // Receive request packet from client
					clientReceive();
				}
				firstPacket = false;
				serverSend(); // Send request to the server
				doneTransfer();
				if (done) return true;
				serverReceive(); // Receive desired packet from the server 

				// Verify that this is the right data packet to be duplicated
				if (foundPacket(receiveServerPacket)) {
					if(verbose){
					System.out.println("Error simulator recieved the data to be manipulated for Error 5...");
					}
					try {
						error5Socket = new DatagramSocket(); // Create a new Datagram socket for receiving data from the server.
						errorPacket = new DatagramPacket(serverData, serverLength, clientAddress, clientPort);
						if (verbose){
							System.out.println("Sending to client using new Socket");
							printInformation(errorPacket);
						}
						error5Socket.send(errorPacket);
						if (verbose){
							System.out.println("I have sent the packet to the client");
						}
						byte[] b = new byte[516];
						DatagramPacket receive = new DatagramPacket(b, 516);
						if (verbose){
							System.out.println("I am trying to receive a response packet from the client");
						}
						error5Socket.receive(receive);
						if (verbose){
							System.out.println("Response packet received from the client");
							printInformation(receive);
						}
						receive.setPort(serverPort);
						receive.setAddress(serverAddress);
						if (verbose){
							System.out.println("Sending Error Packet to server");
						}
						serverSocket.send(receive);
						
						/*b = new byte[516];
						receive = new DatagramPacket(b, 516);
						error5Socket.receive(receive);*/
						if (verbose){
							System.out.println("Closing error socket");
						}
						error5Socket.close();
					} // end try 
					catch (Exception e) {
						System.err.println("SocketException: " + e.getMessage());
					} // end catch
					Selection = 0;
					
					clientSend();
					doneTransfer();
					return true;
				}// end if
			}// End write ACK error 5 mode
		}
		return true;
	}

	private void clientReceive() {
		if(verbose){
		System.out.println("Waiting to receive packet from client");
		}

		receiveClientPacket = new DatagramPacket(clientReply, clientReply.length);
		try { // wait to receive client packet
			clientSocket.receive(receiveClientPacket);
		}//end try 
		catch (IOException ie) {
			System.err.println("IOException error: " + ie.getMessage());
		}//end catch
		if (receiveClientPacket.getData()[1] == (byte)5){
			errorReceived = true;
		}
		if(verbose){
		System.out.println("ConnectionManagerESim: Received packet from client");
		printInformation(receiveClientPacket);
		}
		// updating the data and length in the packet being sent to the server
		clientData = receiveClientPacket.getData();
		clientLength = receiveClientPacket.getLength();
		clientAddress = receiveClientPacket.getAddress();
		System.out.println("" + clientAddress);
	}
	
	/*private void error5ClientReceive() {
		if(verbose){
		System.out.println("Waiting to receive packet from client");
		}
		receiveClientPacket = new DatagramPacket(clientReply, clientReply.length);
		try { // wait to receive client packet
			error5ClientSocket.receive(receiveClientPacket);
		}//end try 
		catch (IOException ie) {
			System.err.println("IOException error: " + ie.getMessage());
		}//end catch
		if (receiveClientPacket.getData()[1] == (byte)5){
			errorReceived = true;
		}
		if(verbose){
		System.out.println("ConnectionManagerESim: Received packet from client");
		printInformation(receiveClientPacket);
		}
		// updating the data and length in the packet being sent to the server
		clientData = receiveClientPacket.getData();
		clientLength = receiveClientPacket.getLength();
		clientAddress = receiveClientPacket.getAddress();
	}*/

	private void clientSend(){
		if(verbose){
		System.out.println("Sending packet to the Client");
		}

		// prepare the new send packet to the client
		try {
			sendClientPacket = new DatagramPacket(serverData, serverLength, InetAddress.getLocalHost(), clientPort);
		} // end try
		catch (UnknownHostException uhe) {
			uhe.printStackTrace();
			System.exit(1);
		} // end catch

		// send the packet to the client via the send socket 
		try {
			clientSocket.send(sendClientPacket);

		} // end try 
		catch (IOException ioe) {
			System.err.println("Unknown IO exception error: " + ioe.getMessage());
		} // end catch
		
		if(verbose){
		// print confirmation message that the packet has been sent to the client
		System.out.println("ConnectionManagerESim: response packet sent to client");
		// print out information about the packet being sent to the client
		printInformation(sendClientPacket);
		}
	}
	
	/*private void error5ClientSend(){
		if(verbose){
		System.out.println("Sending packet to the Client");
		}
		// prepare the new send packet to the client
		try {
			sendClientPacket = new DatagramPacket(serverData, serverLength, InetAddress.getLocalHost(), clientPort);
		} // end try
		catch (UnknownHostException uhe) {
			uhe.printStackTrace();
			System.exit(1);
		} // end catch
		// send the packet to the client via the send socket 
		try {
			error5ClientSocket.send(sendClientPacket);
		} // end try 
		catch (IOException ioe) {
			System.err.println("Unknown IO exception error: " + ioe.getMessage());
		} // end catch
		
		if(verbose){
		// print confirmation message that the packet has been sent to the client
		System.out.println("ConnectionManagerESim: response packet sent to client");
		// print out information about the packet being sent to the client
		printInformation(sendClientPacket);
		}
	}*/

	private void serverReceive(){
		if(verbose){
		System.out.println("Receiving Packet from Server");
		}

		receiveServerPacket = new DatagramPacket(serverReply, serverReply.length);

		// block until you receive a packet from the server
		try {
			System.out.println("Hi");
			serverSocket.receive(receiveServerPacket);
			System.out.println("Hello");
		} // end try 
		catch (IOException ioe) {
			System.err.println("Unknown IO exception error: " + ioe.getMessage());
		} // end catch
		
		if(verbose){
			System.out.println("Received Server Packet");
			// print out information about the packet received from the server if verbose
			printInformation(receiveServerPacket);
		}
		if (receiveServerPacket.getData()[1] == (byte)5)
			errorReceived = true;
		serverData = receiveServerPacket.getData();
		serverLength = receiveServerPacket.getLength();
		// set the serverPort to the port we have just received it from (meaning to the Server Thread that will deal with this request
		serverPort = receiveServerPacket.getPort();

	}

	private void serverSend() {
		if(verbose){
		System.out.println("Sending Packet to the Server");
		System.out.print(" " + clientData);
		}
		// prepare the new send packet to the server
		try {
			sendServerPacket = new DatagramPacket(clientData, clientLength, serverAddress, serverPort);
		} // end try 
		catch (Exception e) {
			System.err.println("exception error: " + e.getMessage());
		} // end catch

		// send the packet to the server via the send/receive socket to server port
		try {
			serverSocket.send(sendServerPacket);
		} // end try 
		catch (IOException ioe) {
			System.err.println("Unknown IO exception error: " + ioe.getMessage());
		} // end catch
		
		if(verbose){
		System.out.println("Packet sent to server");
		printInformation(sendServerPacket);
		}
	}
	
	/*private void error5ServerSend() {
		if(verbose){
		System.out.println("Sending Packet to the Server");
		System.out.print(" " + clientData);
		}
		// prepare the new send packet to the server
		try {
			sendServerPacket = new DatagramPacket(clientData, clientLength, InetAddress.getLocalHost(), serverPort);
		} // end try 
		catch (UnknownHostException uhe) {
			System.err.println("Unknown host exception error: " + uhe.getMessage());
		} // end catch
		// send the packet to the server via the send/receive socket to server port
		try {
			error5ServerSocket.send(sendServerPacket);
		} // end try 
		catch (IOException ioe) {
			System.err.println("Unknown IO exception error: " + ioe.getMessage());
		} // end catch
		
		if(verbose){
		System.out.println("Packet sent to server");
		printInformation(sendServerPacket);
		}
	}*/

	private boolean foundPacket(DatagramPacket p) {
		int type;
		byte blk[] =new byte[2];
		// set type, to the type of packet it is based on its second byte
		type = (int)(p.getData()[1]);
		if(verbose){
		System.out.println("ConnectionManagerESim: packet type is " + type);
		}
		// Create a new byte that stores the block number in the right 2 byte format
		blk[0] = (byte)((packetNumber - (packetNumber % 256)) /256);
		// the low byte
		blk[1] = (byte)(packetNumber % 256);

		// the packet's block number that we are checking
		byte blockReceived[] = {p.getData()[2], p.getData()[3]};
		if(verbose){
		System.out.println("Block number received: " + Integer.toHexString(blockReceived[0]) + "" + Integer.toHexString(blockReceived[1]));
		}
		// check if it is the right packet type
		if (type ==  packetType) {
			if (blk[0] == blockReceived[0] && blk[1] == blockReceived[1]){
				if(verbose){
				System.out.println("Error simulator has the right block number.");
				}
				return true;
			} // end if
		} // end if
		return false;
	}// end method


	
	private void printInformation(DatagramPacket p) {
		
		// print out the information on the packet
		System.out.println("PACKET INFORMATION");
		System.out.println("Host: " + p.getAddress());
		System.out.println("Host port: " + p.getPort());
		System.out.println("Containing the following \nString: " + new String(p.getData()));
		System.out.println("Length of packet: " + p.getLength());
		System.out.println("Bytes: ");
		for (int i = 0; i < p.getLength(); i++) {
			System.out.print(Integer.toHexString(p.getData()[i]));
		}
		System.out.println("\n******************************************************");
		System.out.println("\n\n");
	} // end method 
}
