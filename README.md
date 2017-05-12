# SYSC3303Group7
To run the test, create two folders, one for the client and one for the server
the server folder/directory is the forlder where the server will look for and store its files. Same applies to the client
When you run the server, it will ask for certain things, including the path of its directory. Just go to the directory or folder you created for the server and copy and paste the path for the folder
When you run the client, it will ask for certain things, including the path of its directory. Just go to the directory or folder you created for the client and copy and paste the path for the folder
The user prompt on the client will also ask for the mode of operation. If you want to read a file from the server, please indicate "read" (without the quotation marks). If you want to write a file from the server, please indicate "write" (without the quotation marks).
MAKE SURE YOU RUN THE SERVER, THEN THE INTERMEDIATE HOST, AND FINALLY THE CLIENT
When you indicate that you want to read from the server, the server will go to the directory whose path you specified initially and try to find the file. If the file is found, the server transfers the data 512 bytes at a time  until the transfer is done.
When you indicate that you want to write to the server, the client will go to the directory whose path you specified initially and try to find the file. If the file is found, the client transfers the data 512 bytes at a time  until the transfer is done.
For each data transfer, the receiver has to reply with an appropriate ACK.
PLEASE NOTE: When writing to the server, the client first sends a WRQ packet to the server. Upon receiving and validating this request, the server replies an ACK with block number 0. The client, upon receiving and validating this ACK, starts the data transfer by sending data block 1...and so on
ALSO NOTE: When reading the server, the client first sends a RRQ packet to the server. Upon receiving and validating this request, THE SERVER REPLIES WITH DATA BLOCK 1 and the file transfer continues untill completed.
FOR NOW, the Intermediate host just passes on the packets and does no error simulation
FOR ITERATION 2, the error simulator should ask the user for the error to be simulated, the data block to be manipulated, and the data type to be manipulated.
The error simulator, upon receiving the data type and data block to be manipulated, and changes it, depending on the error type being simulated. FOR EXAMPLE, if the user specifies that he/she wants to simulate an invalid TID error on DATA block 5, the error simulator keeps checking the block number of eack data packet it receives and if it received DATA block 5, it should change its TID and send manipulated packet to the appropriate recipient. The side (Client/Server) that receives the manipulated packet should be able to detect this and send the appropriate error packet to the sender and should know whether or not to terminate the connection.
