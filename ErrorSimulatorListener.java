
import java.io.IOException;
import java.net.*;
import java.util.Scanner;
public class ErrorSimulatorListener{
    public static final int DATA_SIZE = 516;
	// Mode of operation entered by the user. 0 for Normal mode, 1 for lost mode, 2 for delayed mode, 3 for duplicate mode, 4 to shutdown the error simulator
	private static int Selection;
	private boolean verbose;
	private static DatagramSocket clientSocket; // socket deceleration for client
	private DatagramPacket receiveClientPacket; // packet deceleration for all packets being sent and received for both client and server
	private byte clientData[]; // Stores the data received from the client
	private int clientPort; //Stores the port number used to receive the client's request
	private int clientLength; //Stores the length of the data received from the client
	private boolean Read = false; // This variable is true if and only if data is being read from the server
	private boolean Write = false; // This variable is true if and only if data is being written to the server
	private boolean errorReceived = false;
	private int packetType = 0; // Stores the type of packet to be lost, duplicated or delayed, as specified by the user
	private int packetNumber = 0; // Stores the packet number to be lost, duplicated or delayed, as specified by the user
	private static InetAddress serverAddress;

public ErrorSimulatorListener(boolean verbose) {
    this.verbose = verbose;
    clientData = new byte[DATA_SIZE]; // Create a new array of 516 bytes for client data storage
	try{
		clientSocket = new DatagramSocket(1025); // Create a new Datagram socket for receiving data from the client. Here we bind the socket to port 23, which is the well-known port for the error simulator
	} catch (SocketException e){
		System.err.println("SocketException: " + e.getMessage());
	}
	receiveClientPacket = new DatagramPacket(clientData, clientData.length); // Create a new Datagram packet, which stores the client's data.
}

public void receiveClientRequest(){
    if (Selection == 0) {
			System.out.println("Operating Error Simulator in Normal Mode, as specified by the user");
	}
    
		else if (Selection == 4) {
			@SuppressWarnings("resource")
			Scanner input = new Scanner(System.in);
			System.out.println("Operating Error Simulator to generate Error 4, as specified by the user");
			System.out.println("Enter packet type to be manipulated: 1 for RRQ; 2 for WRQ; 3 for DATA; 4 for ACK");
			packetType = input.nextInt();

			// If packet type to be duplicated is DATA or ACK, get the speific packet number
			if (packetType == 3 || packetType == 4){
				System.out.println("Which packet do you want to manipulated: ");
				packetNumber = input.nextInt();
			}

			// If the packet to be duplicated is a request, we will just duplicated the first packet
			else {
				packetNumber = 1;
			}
		}
    
		else if (Selection == 5) {
			@SuppressWarnings("resource")
			Scanner input = new Scanner(System.in);
			System.out.println("Operating Error Simulator to generate Error 5, as specified by the user");
			System.out.println("Enter packet type whose TID is to be changed: 1 for RRQ; 2 for WRQ; 3 for DATA; 4 for ACK");
			packetType = input.nextInt();

			// If packet type to be duplicated is DATA or ACK, get the speific packet number
			if (packetType == 3 || packetType == 4){
				System.out.println("Which packet do you want to manipulate its TID: ");
				packetNumber = input.nextInt();
			}

			// If the packet to be duplicated is a request, we will just duplicated the first packet
			else {
				packetNumber = 1;
			}
		}

		
		clientData = new byte[DATA_SIZE]; // Create a new array of 516 bytes for client data storage

		receiveClientPacket = new DatagramPacket(clientData, clientData.length); // Create a new Datagram packet, which stores the client's data.
		
		System.out.println("Waiting for request from the client...");

		try { // Try recieving client request if available. This blocks until a packet is received from the client
			clientSocket.receive(receiveClientPacket);
		} // end try 
		catch (IOException ie) {
			System.err.println("IOException error: " + ie.getMessage());
        }
		System.out.println("Recieved client's request");
        if(verbose){
		printInformation(receiveClientPacket); // Print the information of the request received from the client
        }
		verifyTransferType(receiveClientPacket); // verify whether the request is a read or write or an error packet
		clientPort = receiveClientPacket.getPort(); // store the port number used by the client to send the request
		clientLength = receiveClientPacket.getLength(); // store the length of the packet received from the client
        Thread connectionmanager = new ErrorSimulator(verbose, Selection, packetType, packetNumber, clientData, clientPort, clientLength, Read, Write, errorReceived, serverAddress);
		connectionmanager.start();

		while (connectionmanager.getState() != Thread.State.TERMINATED) {

		}
		
}

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
		} // end forloop
		System.out.println("\n******************************************************");
		System.out.println("\n\n");
	} // end method 


	public void verifyTransferType (DatagramPacket p){
		System.out.println ("Verifying transfer type");
		if(p.getData()[0] != (byte) 0){
			System.exit(1);
		}
		if(p.getData()[1] == (byte) 1 ){
			Read = true;
		}
		else if(p.getData()[1] == (byte) 2 ){
			Write = true;
		}
        else{
            errorReceived = true;
        }
		
	}
    public static void main(String[] args) {
			// the scanner to receive a user input
			Scanner in = new Scanner(System.in);

			// will hold the value if a valid choice has been entered
			boolean validEntry = false;
			boolean shutdown = false;

			while (!shutdown) { 
				while (!validEntry) {

					// print out information for the user depending on the mode of run they want to use
					System.out.println("Hello World! Enter the mode operation you would like today. 0 for Normal mode,\n 4 to simulate an Error 4 transfer,\n 5 to simulate an Error 5 transfer,\n 9 to shutdown the error simulator\n");
					System.out.println("Please enter a mode for the Error Simulator to start in:");

					Selection = in.nextInt();
					// check if a valid choice has been entered
					if (Selection == 0 ||  Selection == 4 || Selection == 5 || Selection ==9) {
						validEntry = true;
					}
					else {
						System.out.println("It seems like your input was invalid **Better luck next time**. Please feel free try again!");
						validEntry = false;
					}
				} // end while
		   		try {
					serverAddress = InetAddress.getLocalHost();
				} catch (UnknownHostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                if (Selection == 9){
                    shutdown = true; // if user input is 4, Shutdown the Error simulator 
                }

				
				ErrorSimulatorListener esim = new ErrorSimulatorListener(true);

				esim.receiveClientRequest();
				validEntry = false; // reset valid entry to false, so user can do another data transfer
                clientSocket.close();
			}// end while	

			// close the Scanner
			in.close();
			System.out.println ("My job is done. I am shutting down...GOODBYE FOREVER **Starts Crying** I...Caa...Can't...Bre...athe...Goo...d...Bye");
			System.exit(0);
		} // end method
	} // end class
