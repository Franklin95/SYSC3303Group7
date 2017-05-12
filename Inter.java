import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class Inter {
	private DatagramPacket sendPacket, receivePacket;
	private DatagramSocket receiveSocket, sendSocket, sendReceiveSocket;
	
	public Inter()
	{
	   try {
	      // Construct a datagram socket and bind it to port 23
	      // on the local host machine. This socket will be used to
	      // receive UDP Datagram packets from clients.
	      receiveSocket = new DatagramSocket(1025);
	      // Construct a datagram socket and bind it to any available
	      // port on the local host machine. This socket will be used to
	      // send and receive UDP Datagram packets from the server.
	      sendReceiveSocket = new DatagramSocket();
	   } catch (SocketException se) {
	      se.printStackTrace();
	      System.exit(1);
	   }
	}

	public void run()
	{

	   byte[] data;
	   
	   int clientPort, serverPort=0, j=0, len;

	   for(;;) { // loop forever
	      // Construct a DatagramPacket for receiving packets up
	      // to 516 bytes long (the length of the byte array).
	      
	      data = new byte[516];
	      receivePacket = new DatagramPacket(data, data.length);

	      System.out.println("Simulator: Waiting for packet from Client.");
	      // Block until a datagram packet is received from receiveSocket.
	      try {
	         receiveSocket.receive(receivePacket);
	      } catch (IOException e) {
	         e.printStackTrace();
	         System.exit(1);
	      }

	      // Process the received datagram.
	      System.out.println("Simulator: Packet received from Client:");
	      System.out.println("From host: " + receivePacket.getAddress());
	      clientPort = receivePacket.getPort();
	      System.out.println("Host port: " + clientPort);
	      len = receivePacket.getLength();
	      System.out.println("Length: " + len);
	      System.out.println("Data bytes: " );
	      
	      // print the bytes
	      for (j=0;j<len;j++) {
	    	  System.out.print(data[j] + " ");
	      }

	      // Form a String from the byte array, and print the string.
	      System.out.println("Text: " );
	      String received = new String(data,0,len);
	      
	      // Now pass it on to the server (to port 69)
	      // Construct a datagram packet that is to be sent to a specified port
	      // on a specified host.
	      // The arguments are:
	      //  msg - the message contained in the packet (the byte array)
	      //  the length we care about - k+1
	      //  InetAddress.getLocalHost() - the Internet address of the
	      //     destination host.
	      //     In this example, we want the destination to be the same as
	      //     the source (i.e., we want to run the client and server on the
	      //     same computer). InetAddress.getLocalHost() returns the Internet
	      //     address of the local host.
	      //  69 - the destination port number on the destination host.
	      if (data[1]==1||data[1]==2)//WRQ or RRQ
	      {
	    	  //If we receive a request, send the packet via port 69
	    	  sendPacket = new DatagramPacket(data, len, receivePacket.getAddress(), 1069);
	      }
	      else if ((data[1]==3||data[1]==4)&&serverPort!=0)//DATA or ACK
	      {
	    	  //If we get an DATA or ACK packet, send the packet to the server via the transfer port created by the server 
	    	  sendPacket = new DatagramPacket(data, len, receivePacket.getAddress(), serverPort);
	      }
	     
	      System.out.println("Simulator: sending packet to the Server.");
	      System.out.println("To host: " + sendPacket.getAddress());
	      System.out.println("Destination host port: " + sendPacket.getPort());
	      len = sendPacket.getLength();
	      System.out.println("Length: " + len);
	      System.out.println("Containing: ");
	      for (j=0;j<len;j++) {
	    	  System.out.print(data[j] + " ");
	      }
	      System.out.println(" ");

	      try {
	         sendReceiveSocket.send(sendPacket);
	      } catch (IOException e) {
	         e.printStackTrace();
	         System.exit(1);
	      }
	      
	      // Construct a DatagramPacket for receiving packets up
	      // to 516 bytes long (the length of the byte array).

	      data = new byte[516];
	      receivePacket = new DatagramPacket(data, data.length);

	      System.out.println("Simulator: Waiting for packet from the Server.");
	      try {
	    	  //receive server reply
	         sendReceiveSocket.receive(receivePacket);
	      } catch(IOException e) {
	         e.printStackTrace();
	         System.exit(1);
	      }

	      // Process the received datagram.
	      System.out.println("Simulator: Packet received:");
	      System.out.println("From host: " + receivePacket.getAddress());
	      System.out.println("Host port: " + receivePacket.getPort());
	      len = receivePacket.getLength();
	      serverPort=receivePacket.getPort();
	      System.out.println("Length: " + len);
	      System.out.println("Containing: ");
	      for (j=0;j<len;j++) {
	         System.out.print(data[j] + " ");
	      }
	      System.out.println(" ");

	      // Construct a datagram packet that is to be sent to a specified port
	      // on a specified host.
	      // The arguments are:
	      //  data - the packet data (a byte array). This is the response.
	      //  receivePacket.getLength() - the length of the packet data.
	      //     This is the length of the msg we just created.
	      //  receivePacket.getAddress() - the Internet address of the
	      //     destination host. Since we want to send a packet back to the
	      //     client, we extract the address of the machine where the
	      //     client is running from the datagram that was sent to us by
	      //     the client.
	      //  receivePacket.getPort() - the destination port number on the
	      //     destination host where the client is running. The client
	      //     sends and receives datagrams through the same socket/port,
	      //     so we extract the port that the client used to send us the
	      //     datagram, and use that as the destination port for the TFTP
	      //     packet.
	      
	      sendPacket = new DatagramPacket(data, receivePacket.getLength(),
	                            receivePacket.getAddress(), clientPort);

	      System.out.println( "Simulator: Sending server reply to Client:");
	      System.out.println("To host: " + sendPacket.getAddress());
	      System.out.println("Destination host port: " + sendPacket.getPort());
	      len = sendPacket.getLength();
	      System.out.println("Length: " + len);
	      System.out.println("Containing: ");
	      for (j=0;j<len;j++) {
	    	  System.out.print(data[j] + " ");
	      }

	      // Send the datagram packet to the client via a new socket.

	      try {
	         // Construct a new datagram socket and bind it to any port
	         // on the local host machine. This socket will be used to
	         // send UDP Datagram packets.
	         sendSocket = new DatagramSocket();
	      } catch (SocketException se) {
	         se.printStackTrace();
	         System.exit(1);
	      }

	      try {
	         sendSocket.send(sendPacket);
	      } catch (IOException e) {
	         e.printStackTrace();
	         System.exit(1);
	      }

	      System.out.println("Simulator: packet sent using port " + sendSocket.getLocalPort());
	      System.out.println();

	      // We're finished with this socket, so close it.
	      sendSocket.close();
	   } // end of loop

	}

	public static void main( String args[] )
	{
	   Inter s = new Inter();
	   s.run();
	}
}
