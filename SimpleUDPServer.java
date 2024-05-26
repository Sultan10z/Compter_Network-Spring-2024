import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class SimpleUDPServer {

    
    private static String completeEmail;
    private static final String EMAIL_FILE = "emails.txt"; // File containing valid email addresses
    private static final String EMAILS_DIRECTORY = "emails"; // Base directory to save email files
    private static final String MAPPING_FILE = "mapping.txt"; // File containing mappings from email addresses to receiver client hostnames

    private static final int BUFFER_SIZE = 1024;

    // Map to keep track of client directories
    private static Map<String, String> emailToReceiverMapping = new HashMap<>();
    Map<InetAddress, List<Integer>> seqNumList=new HashMap<>();
    private static Map<InetAddress, Integer> seqNumMap = new HashMap<>(); // Map to store sequence numbers for each client
    private static Map<String, Integer> seqNumMapemail = new HashMap<>(); // Map to store sequence numbers for each client that uses email

    private static List<Client> clientList = new ArrayList<>(); // List to store information about connected clients
    private static boolean acked=false;
    private static boolean acked1=false;

    static Map<Integer, StringBuilder> emailFragmentsMap = new HashMap<>();

    public SimpleUDPServer() {


    }
// Define a class for receiving acknowledgments
class AckReceiver extends Thread {
    private DatagramSocket serverSocket;
    private boolean acked;
    private int seqNum;

    public AckReceiver(DatagramSocket serverSocket) {
        this.serverSocket = serverSocket;
        this.acked = false;
        this.seqNum = -1; // Initialize to an invalid value
    }

    public boolean isAcked() {
        return acked;
    }

    public int getSeqNum() {
        return seqNum;
    }

    @Override
    public void run() {
        try {
            byte[] ackData = new byte[1024];
            DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);
            serverSocket.receive(ackPacket);
    
            String ackMessage = new String(ackPacket.getData(), 0, ackPacket.getLength());
    
            if (ackMessage.contains("ACK")) {
                acked=true;
                // Extract and store the sequence number
                seqNum = extractSeqNum(ackMessage);

            }
        } catch (IOException e) {
            // Handle IO exception
        }
    }
}

    private class ServerThread implements Runnable {

    public void run() {
        try (FileWriter fileWriter = new FileWriter(MAPPING_FILE)) {
            fileWriter.write("");
            fileWriter.close();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        DatagramSocket serverSocket = null;

        // Load valid email addresses from file
        List<String> validEmails = loadValidEmails();

        // Load email-to-receiver mappings from file
        loadEmailToReceiverMapping();


        try {
            // Create a DatagramSocket bound to port 25
            serverSocket = new DatagramSocket(25);
            byte[] receiveData = new byte[1024];

            // Display server initialization message
            System.out.println("Mail Server Starting at host: " + InetAddress.getLocalHost().getHostName());
            System.out.println("Waiting to be contacted for transferring Mail...\n\n");

            String currentReceiver = "";
            boolean hasFragmentEnd = false;

            // Now handshake completed with two clients, continue with email communication...
            // Listen for incoming requests indefinitely
            while (true) {
                // Listen for incoming requests indefinitely
                // Receive request packet
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);

                // Extract message from received packet
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());

                if (message.startsWith("mapping:")) {
                    String mapping = message;

                    // Extract client type from SYN message
                    String[] parts = mapping.split(":");
                    String receiveremail = parts[1]; // Assuming client email is the third part after splitting by :
                    String receiverhostname = parts[2]; // Assuming client type is the third part after splitting by :
                    // Save mapping to file
                    saveMapping(receiveremail, receiverhostname);
                    currentReceiver = receiveremail;
                    System.out.println(currentReceiver);

                    continue;
                }
                if (message.startsWith("SYN")) {
                    System.out.println("3-way handshake initiated with client: ");
                    String synMessage = message;

                    // Extract client email from SYN message
                    String[] parts = synMessage.split(" ");
                    String clientEmail = parts[2]; // Assuming client email is the third part after splitting by space

                    // Get client address and port
                    InetAddress clientAddress = receivePacket.getAddress();
                    int clientPort = receivePacket.getPort();

                    // Save mapping to file
                    saveMapping(clientEmail, clientAddress.getHostName());

                    // Get sequence number from SYN packet
                    int seqNum = extractSeqNum(synMessage);
                    System.out.println("Received SYN from client: " + clientAddress.getHostName() + " with email: " + clientEmail);

                    // Save client information
                    clientList.add(new Client(clientAddress, clientPort, clientEmail));
                    seqNumMap.put(clientAddress, seqNum);
                    System.out.println("Received SYN from client: " + clientAddress.getHostName() + " with sequence number: " + seqNum);

                    seqNum++;
                    // Send SYN+ACK packet to client
                    sendSYNACKPacketToClient(serverSocket, clientAddress, clientPort, seqNum);
                    seqNumMap.put(clientAddress, seqNum);

                    // Print 3-way handshake
                    System.out.println("Sent SYN+ACK to client: " + clientAddress.getHostName() + " with sequence number: " + (seqNum ));

                    // Receive ACK packet from client
                    DatagramPacket ackPacket = new DatagramPacket(receiveData, receiveData.length);
                    serverSocket.receive(ackPacket);

                    // Extract message from received packet
                    String ackMessage = new String(ackPacket.getData(), 0, ackPacket.getLength());

                    // Process ACK packet
                    if (ackMessage.startsWith("ACK")) {
                        // Get sequence number from ACK packet
                        int ackSeqNum = extractSeqNum(ackMessage);

                        // Print 3-way handshake
                        System.out.println("Received ACK from client: " + clientAddress.getHostName() + " with sequence number: " + ackSeqNum);
                        seqNumMap.put(clientAddress, ackSeqNum);

                    }

                    continue;
                }


                // Extract client address and port
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();

                // After receiving email, check if the received message is a FIN packet
                if (message.startsWith("FIN")) {
                    // Get sequence number from FIN packet
                    int finSeqNum = extractSeqNum(message);
                    System.out.println("Received FIN from Client: " + clientAddress.getHostName() + " with sequence number: " + finSeqNum);

                    // Send FIN-ACK packet to client
                    sendFINACKPacketToClient(serverSocket, clientAddress, clientPort, finSeqNum + 1);
                    System.out.println("Sent FIN-Ack to Client: " + clientAddress.getHostName() + " with sequence number: " + (finSeqNum + 1));

                    // Wait for ACK from client
                    receiveData = new byte[1024];
                    DatagramPacket serverReceivePacket = new DatagramPacket(receiveData, receiveData.length);
                    serverSocket.receive(serverReceivePacket);
                    String AckMessage = new String(serverReceivePacket.getData(), 0, serverReceivePacket.getLength());

                    // Check if the ACK is received
                    if (AckMessage.startsWith("ACK")) {
                        // Get sequence number from ACK packet
                        int ackSeqNum = extractSeqNum(AckMessage);
                        System.out.println("Received ACK from Client: " + clientAddress.getHostName() + " with sequence number: " + ackSeqNum);

                        // Remove the client from the client list
                        for (Client client : clientList) {
                            if (client.getAddress().equals(clientAddress) && client.getPort() == clientPort) {
                                clientList.remove(client);
                                break;
                            }
                        }
                    }
                    continue;
                }

                // Check if the ACK is received
                if (message.startsWith("ACK")) {
                    // Get sequence number from ACK packet
                    int ackSeqNum = extractSeqNum(message);
                    System.out.println("Received ACK from Client: " + clientAddress.getHostName() + " with sequence number: " + ackSeqNum);
                    seqNumMap.put(clientAddress,ackSeqNum);
                    continue;
                }


                // After receiving email, check if the received message is an "update inbox" command
                if (message.startsWith("update inbox")) {
                    // Extract client hostname and receiver email
                    String[] parts = message.split(":");
                    String clientHostname = receivePacket.getAddress().getHostName();
                    System.out.println(clientHostname);

                    // Find folder named "hostname_receiveremail" and check the inbox folder
                    String email = "";
                    for (Client client : clientList) {
                        if ((client.getAddress().getHostName()).equals(clientHostname)) {

                            email = client.getEmail();
                            break;
                        }
                    }
                    String receiverDirectoryPath = EMAILS_DIRECTORY + File.separator + email;
                    File inboxDirectory = new File(receiverDirectoryPath + File.separator + "inbox");
                    System.out.println(inboxDirectory);
                    System.out.println(receiverDirectoryPath);

                    // Send all the emails in the inbox to the receiver
                    if (inboxDirectory.exists() && inboxDirectory.isDirectory()) {
                        File[] emailFiles = inboxDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt")); // Filter files by extension
                        for (File emailFile : emailFiles) {
                            String filename = emailFile.getName().replaceAll(".txt", "");
                            StringBuilder emailContent = new StringBuilder();
                            try (BufferedReader reader = new BufferedReader(new FileReader(emailFile))) {
                                String line;
                                // Inside your loop
                                while ((line = reader.readLine()) != null) {
                                    emailContent.append(line).append("\n"); // Append email content
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            // Extract header fields
                            String[] headerLines = emailContent.toString().split("\n");

                            String to = null;
                            String from = null;
                            String subject = null;
                            String body = "";
                            String attachmentFilename = null; // Initialize attachment filename

                            // Extract header fields and save them in different string variables
                            for (String line : headerLines) {
                                if (line.startsWith("TO:")) {
                                    to = line.substring(3).trim();
                                } else if (line.startsWith("FROM:")) {
                                    from = line.substring(5).trim();
                                } else if (line.startsWith("SUBJECT:")) {
                                    subject = line.substring(8).trim();
                                    // Extract timestamp from subject for attachment filename comparison

                                } else {
                                    body += line + "\n";
                                }
                            }
// Extract timestamp from subject for attachment filename comparison

                            String[] filenameparts = filename.split("_");

                            String timestamp = filenameparts[2];
                            attachmentFilename = "attachment_" + subject + "_" + timestamp;
                            attachmentFilename=attachmentFilename.replaceAll(" ","");
                            int seqNum = seqNumMap.get(clientAddress);
                            System.out.println(attachmentFilename);
                            // Construct email message
                            String forwardEmail = "filename: " + filename + "\nTo: " + to + "\nFrom: " + from + "\nSubject: " + subject + "\n\n" + body;

                            // Check if attachment file exists
                            File attachmentFile = new File(inboxDirectory + File.separator + attachmentFilename);
                            System.out.println("Attachment file path: " + attachmentFile.getAbsolutePath());
                            System.out.println("Does attachment file exist? " + attachmentFile.exists());
                            boolean exits = false;

                            File[] inboxContents = inboxDirectory.listFiles();

                            for (File file : inboxContents) {
                                if (file.getName().startsWith(attachmentFilename)) {
                                    attachmentFile=file;
                                    exits = true;
                                    break;
                                }
                            }

                            if (exits) {
                                exits = false;
                                // Read attachment content
                                try (BufferedReader attachmentReader = new BufferedReader(new FileReader(attachmentFile))) {
                                    byte[] attachmentData = Files.readAllBytes(Paths.get(attachmentFile.getAbsolutePath()));
                                    String attachmentEncoded = Base64.getEncoder().encodeToString(attachmentData);
                                    forwardEmail += "\n\nAttachment:\n" + attachmentEncoded;

                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                // Encode attachment data as Base64 string
                                // Append attachment content to email
                            }
                            if (forwardEmail.getBytes().length > BUFFER_SIZE) {
                                // Fragment the email message
                                List<String> emailFragments = fragmentEmail(forwardEmail, seqNumMap.get(clientAddress));
                                // Send each fragment to the server
                                for (String fragment : emailFragments) {
                                    boolean acked1 = false;

                                    while (!acked1) {
                                        
                                        sendFragment(serverSocket, fragment, clientAddress, clientPort);

                                        // Start a separate thread to receive acknowledgments
                                        AckReceiver ackReceiver = new AckReceiver(serverSocket);
                                        ackReceiver.start();
                                
                                        // Wait for acknowledgment or timeout
                                        try {
                                            ackReceiver.join(1000); // Wait for up to 5 seconds for acknowledgment
                                            acked1 = ackReceiver.isAcked();
                                        } catch (InterruptedException e) {
                                            // Handle interruption
                                        }
                                
                                        if (!acked1) {
                                            // Handle timeout or acknowledgment not received
                                            System.out.println("Timeout occurred. Resending fragment...");
                                        } else {
                                            // Acknowledgment received
                                            int forwardedSeqNum = ackReceiver.getSeqNum();
                                            // Save the sequence number and process other features as needed
                                            seqNumMap.put(clientAddress, forwardedSeqNum);
                                            System.out.println("Received ACK from receiver client: " + clientAddress.getHostName() + " with sequence number: " + forwardedSeqNum);
                                        }
                                    }
                                }
                                

}

                             else {
                                forwardEmail += "\nSeqNum: " + seqNumMap.get(clientAddress);

                                // Send the packet to the server
                                byte[] sendData = forwardEmail.getBytes();
                                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                                serverSocket.send(sendPacket);

                                System.out.println("Email forwarded to Receiver Client:");
                                System.out.println("Forwarded to: " + clientAddress.getHostName());

                                // Update sequence number for the receiver client
                                seqNumMap.put(clientAddress, (seqNum + forwardEmail.length()));
                            }
                            String endMessage = "EOT";
                            byte[] sendData = endMessage.getBytes();
                            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                            serverSocket.send(sendPacket);
                        }

                    } else {
                        System.out.println("Inbox directory not found for client: " + clientHostname);
                    }

                    System.out.println("email sent to :" + currentReceiver);

                    // Skip processing further in the loop
                    continue;
                }

//====================================================================================================================================================================================


                if (message.contains("FRAGMENTSeqNum:")) {
                    handleEmailFragment(serverSocket, receivePacket.getAddress(), receivePacket.getPort(), message, emailFragmentsMap,seqNumList, seqNumMap.get(receivePacket.getAddress()));
      
        
                    if (message.contains("FRAGMENTEND")) {
                        hasFragmentEnd = true;
                    }

                    if (!hasFragmentEnd) {
                        continue;
                    }

                } else {
                    System.out.println("Received complete email: " + message);

                // Count number of bytes in the message the client sent
                    int bytesCount = message.getBytes().length;
                    // Extract sequence number from received message
                    int seqNum = extractSeqNum(message);
                 //   System.out.println("\nA mail received, from : " + clientAddress.getHostName() + " seq = " + seqNum + "\n");
                    // Update sequence number for the client
                    seqNumMap.put(clientAddress, (seqNum + bytesCount));

                    sendACKPacketToClient(serverSocket, clientAddress, clientPort, seqNumMap.get(clientAddress));
                    System.out.println("Sent ACK to client: " + clientAddress.getHostName() + " with sequence number: " + seqNumMap.get(clientAddress));
                    hasFragmentEnd = true;
                    emailBuilder=message;

                }

                if (!hasFragmentEnd) {
                    continue;
                }

                completeEmail= emailBuilder;
                message=completeEmail;


                System.out.println("\n=================================================================================\n\n");

               // System.out.println("Bytes in the message: " + bytesCount);

                String[] parts0=message.split("Attachment:");

                //System.out.println(parts0[0]);

                // Extract email content and header fields and split them into parts for easier validation
                String[] parts = parts0[0].split("\n\n", 2); // Split email content from header
                String header = parts[0];
                //String body = parts[1].replaceAll("\nSeqNum: \\d+", ""); // Remove sequence number from the body
                String bodyx = parts[1];

                String[] headerLines = header.split("\n");

                String to = null;
                String from = null;
                String subject = null;
                String cc = null;

                // Extract header fields and save them in different string variables
                for (String line : headerLines) {
                    if (line.startsWith("To:")) {
                        to = line.substring(3).trim();
                    } else if (line.startsWith("From:")) {
                        from = line.substring(5).trim();
                    } else if (line.startsWith("Subject:")) {
                        subject = line.substring(8).trim();
                    }else if (line.startsWith("CC:")) {
                        cc = line.substring(3).trim();
                    }
                }

                // Validate header fields make sure nothing is null
                boolean isValidHeader = isValidEmailAddress(to) && isValidEmailAddress(from) && subject != null;

                // Check if both 'To' and 'From' email addresses are valid
                boolean isValidEmails = validEmails.contains(to) && validEmails.contains(from);

                // Include CC addresses in the list of recipients
                List<String> recipients = new ArrayList<>();
                recipients.add(to);
                if (cc != null && !cc.isEmpty()) {
                    String[] ccAddresses = cc.split(",");
                    for (String ccAddress : ccAddresses) {
                        if (isValidEmailAddress(ccAddress.trim())) {
                            recipients.add(ccAddress.trim());
                        }
                    }
                }

                // Send appropriate response based on header and email validation
                String response = ""; // Initialize with a default value

                if (isValidHeader && isValidEmails) {

                    // Process email forwarding for each recipient
                    for (String recipient : recipients) {

                    String senderDirectory = createSenderDirectory(from);
                    String sentDirectory = createSentDirectory(senderDirectory);
                    // Example filenames
                    String emailFilename = generateUniqueEmailFilename(sentDirectory, subject);
                    String attachmentFilename = generateUniqueAttachmentFilename(sentDirectory, subject);
                    // Get client hostname
                    String clientHostname = clientAddress.getHostName();

                    // Check if attachment exists
                    boolean hasAttachment = message.contains("Attachment:");

                    // If attachment exists, extract and save it
                    if (hasAttachment) {
                        // Split message to extract attachment
                        String[] messageParts = message.split("Attachment: ");
                        String attachmentEncoded = messageParts[1].trim();

                        // Decode base64 encoded attachment content
                        byte[] attachmentData = Base64.getDecoder().decode(attachmentEncoded);

                        String attachmentType = getAttachmentType(attachmentData);

                        // Save attachment to file with appropriate extension
                        saveAttachmentToFile(attachmentData, attachmentFilename, attachmentType);
                    }

                    try (PrintWriter writer = new PrintWriter(new FileWriter(emailFilename))) {
                        // Write email content to file
                        writer.println("FROM: " + from);
                        writer.println("TO: " + recipient);
                        writer.println("SUBJECT: " + subject);
                        writer.println("TIME: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                        writer.println("\n\n" + bodyx);

                    } catch (IOException e) {
                        e.printStackTrace();
                        response = "File Saving Error"; // Notify client about file saving error
                    }

                    // Show the email's details in the server terminal
                    System.out.println("\n---------------------------------------------------------------------------------\n");
                    System.out.println("Mail Received from " + clientHostname);
                    System.out.println("FROM: " + from);
                    System.out.println("TO: " + recipient);
                    System.out.println("SUBJECT: " + subject);
                    System.out.println("TIME: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                    System.out.println("\n\n" + bodyx);
                    System.out.println("\n---------------------------------------------------------------------------------");
                    System.out.println("\n");


                            // Create directories
                            String receiverDirectory = createReceiverDirectory(recipient);
                            String inboxDirectory = createInboxDirectory(receiverDirectory);
                            // Example filenames
                             emailFilename = generateUniqueEmailFilename(inboxDirectory, subject);
                             attachmentFilename = generateUniqueAttachmentFilename(inboxDirectory, subject);

                            System.out.println("Receiver Directory: " + receiverDirectory);
                            System.out.println("Inbox Directory: " + inboxDirectory);
                            System.out.println("Email Filename: " + emailFilename);
                            System.out.println("Attachment Filename: " + attachmentFilename);
                            // Check if attachment exists

                            // If attachment exists, extract and save it
                            if (hasAttachment) {
                                // Split message to extract attachment
                                String[] messageParts = message.split("Attachment:");
                                String attachmentEncoded = messageParts[1].trim();

                                // Decode base64 encoded attachment content
                                byte[] attachmentData = Base64.getDecoder().decode(attachmentEncoded);

                                String attachmentType = getAttachmentType(attachmentData);

                                // Save attachment to file with appropriate extension
                                saveAttachmentToFile(attachmentData, attachmentFilename, attachmentType);
                            }

                            try (PrintWriter writer = new PrintWriter(new FileWriter(emailFilename))) {
                                // Write email content to file
                                writer.println("FROM: " + from);
                                writer.println("TO: " + recipient);
                                writer.println("SUBJECT: " + subject);
                                writer.println("TIME: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                                writer.println("\n\n" + bodyx);

                            } catch (IOException e) {
                                e.printStackTrace();
                                response = "File Saving Error"; // Notify client about file saving error
                            }

                    // Check if the receiver client is mapped and forward the email

                    String receiverHostname = getEmailReceiverHostname(recipient);
                    if (receiverHostname != null) {

                        // Check if receiver is in the list of connected clients
                        boolean isReceiverConnected = false;
                        for (Client client : clientList) {
                            if (client.getEmail().equals(recipient) && client.getAddress().getHostName().equals(receiverHostname)) {
                                isReceiverConnected = true;
                                break;
                            }
                        }

                        if (isReceiverConnected) {
                            InetAddress receiverAddress = InetAddress.getByName(receiverHostname);

                            // Find the receiver client in the clientList
                            Client receiverClient = null;
                            for (Client client : clientList) {
                                if (client.getAddress().equals(receiverAddress) && client.getEmail().equals(recipient)) {
                                    receiverClient = client;
                                    break;
                                }
                            }

                            // Forward the email if receiver client is found
                            if (receiverClient != null) {

                                if (receiverClient.getEmail().equals(recipient)) {
                                    // Send email to receiver client
                                   // sendEmailToReceiver(recipient, from, subject, bodyx, receiverAddress, receiverPort, sequenceNumber);

                                    String email=completeEmail;

                                    // Store the email in 'request' String
                                    String request = email ;

                                    // Count number of bytes in the message with the headers title (ex:to,from...etc)
                                    int bytesCount = request.getBytes().length;
                                    System.out.println("\n\nBytes in the message: " + bytesCount);

                                    // Check if email size exceeds buffer size
                                    if (email.getBytes().length > BUFFER_SIZE) {
                                        // Fragment the email message
                                        List<String> emailFragments = fragmentEmail(email,seqNumMap.get(receiverClient.getAddress()));
                                           // Send each fragment to the server
                                for (String fragment : emailFragments) {
                                    boolean acked1 = false;

                                    while (!acked1) {
                                        
                                        sendFragment(serverSocket, fragment, receiverClient.getAddress(),receiverClient.getPort());

                                        // Start a separate thread to receive acknowledgments
                                        AckReceiver ackReceiver = new AckReceiver(serverSocket);
                                        ackReceiver.start();
                                
                                        // Wait for acknowledgment or timeout
                                        try {
                                            ackReceiver.join(1000); // Wait for up to 5 seconds for acknowledgment
                                            acked1 = ackReceiver.isAcked();
                                        } catch (InterruptedException e) {
                                            // Handle interruption
                                        }
                                
                                        if (!acked1) {
                                            // Handle timeout or acknowledgment not received
                                            System.out.println("Timeout occurred. Resending fragment...");
                                        } else {
                                            // Acknowledgment received
                                            int forwardedSeqNum = ackReceiver.getSeqNum();
                                            // Save the sequence number and process other features as needed
                                            seqNumMap.put(receiverClient.getAddress(), forwardedSeqNum);
                                            System.out.println("Received ACK from receiver client: " + receiverClient.getAddress().getHostName() + " with sequence number: " + forwardedSeqNum);
                                        }
                                    }
                                }
                                
                            
                                        
        
                                    

                                    }else {
                                        request += "\nSeqNum: " + seqNumMap.get(receiverClient.getAddress());

                                        // Send the packet to the server
                                        byte[] sendData = request.getBytes();
                                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, receiverClient.getAddress(), receiverClient.getPort());
                                        serverSocket.send(sendPacket);

                                        // Wait for acknowledgment from receiver client
                                        byte[] ackData = new byte[1024];
                                        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);
                                        serverSocket.receive(ackPacket);

                                        // Extract acknowledgment message from receiver client
                                        String ackMessage = new String(ackPacket.getData(), 0, ackPacket.getLength());

                                        // Extract sequence number and forwarded message size from acknowledgment
                                        int forwardedSeqNum = extractSeqNum(ackMessage);

                                        // Update sequence number for the receiver client
                                        seqNumMap.put(receiverAddress, forwardedSeqNum);

                                        // Display acknowledgment information
                                        System.out.println("Received ACK from receiver client: " + receiverAddress.getHostName() + " with sequence number: " + forwardedSeqNum);

                                    }



                                }


                            }
                        } 
                    }}

                    // Send confirmation response to client with timestamp
                    String timestamp = LocalDateTime.now().toString();
                    response = "250 OK_" + timestamp; // Use a separator to distinguish response code and timestamp
                    System.out.println("The Header fields and both 'To' and 'From' emails are valid.");
                    System.out.println("\nSending '250 OK'");

                } else if (isValidHeader && !isValidEmails) {

                    // Receiver hostname not found, handle this scenario accordingly
                    System.out.println("Receiver hostname not found for email to: " + to);
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm"));

                    response = "550 Error_" + timestamp; // Use a separator to distinguish response code and timestamp
                    System.out.println("The mapping does not exist.");
                    System.out.println("\nSending '550 ERROR'");

                } else {
                    // Send error response for invalid header or emails
                    response = "501 Error";
                    System.out.println("The Header fields are not valid.");
                    System.out.println("\nSending '501 Error'\n\n");
                }

                byte[] sendData = response.getBytes();

                // Calculate size of response message and display it on the terminal before sending it to the client
                int responseSize = response.getBytes().length;
                System.out.println("Bytes in the response message: " + responseSize);

                // Send response to client
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                serverSocket.send(sendPacket);

                // Receive ACK packet from client
                DatagramPacket ackPacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(ackPacket);
                InetAddress clientAddress1 = ackPacket.getAddress();
                // Extract message from received packet
                String ackMessage = new String(ackPacket.getData(), 0, ackPacket.getLength());

                // Process ACK packet
                if (ackMessage.startsWith("ACK")) {
                    // Get sequence number from ACK packet
                    int ackSeqNum = extractSeqNum(ackMessage);

                    System.out.println("Received ACK from client: " + clientAddress1.getHostName() + " with sequence number: " + ackSeqNum);
                    seqNumMap.put(clientAddress1, ackSeqNum);
                }

                // Return to waiting state and not terminate the server connection
                System.out.println("\n\nReturning to waiting state...\n");
                System.out.println("\n---------------------------------------------------------------------------------\n\n");

                hasFragmentEnd = false;
                emailBuilder="";
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
          
    
        }
    }
}
    
    
    static String emailBuilder = "";

    private static List<String> fragmentEmail(String email,int seqNum) {
        List<String> fragments = new ArrayList<>();
        int emailLength = email.getBytes().length;
        int digitsInSeqNum = String.valueOf(seqNum).length();
        int maxFragmentSize = BUFFER_SIZE - ("FRAGMENTSeqNum:10000000000000000" + seqNum + " ").getBytes().length - digitsInSeqNum;

        int startIndex = 0;
        int endIndex = Math.min(maxFragmentSize, emailLength);
        while (startIndex < emailLength) {
            String fragment = email.substring(startIndex, endIndex);
            fragment += "\nFRAGMENTSeqNum: " + seqNum;
            fragments.add(fragment);
            startIndex = endIndex;
            endIndex = Math.min(startIndex + maxFragmentSize, emailLength);
            seqNum += 1024; // Update sequence number for next fragment
        }
        // Add "FRAGMENTEND" marker to the last fragment
        if (!fragments.isEmpty()) {
            String lastFragment = fragments.get(fragments.size() - 1);
            lastFragment += "\nFRAGMENTEND";
            fragments.set(fragments.size() - 1, lastFragment);
            System.out.println(lastFragment);
        }
        return fragments;
    }

    private static void sendFragment(DatagramSocket socket, String fragment, InetAddress clientaddress,int port) throws IOException {
        byte[] sendData = fragment.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientaddress, port);
        socket.send(sendPacket);
    }
    // Simulate packet loss

    private static void handleEmailFragment(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort, String emailFragment, Map<Integer, StringBuilder> emailFragmentsMap, Map<InetAddress, List<Integer>> seqNumList, int ackNum) throws IOException {
       
        
        // Split the email fragment into parts
        String[] parts = emailFragment.split("\nFRAGMENTSeqNum: ");
        if (parts.length >= 2) {
            // Extract the sequence number
            String seqNumString = parts[1].trim().split("\\s+")[0]; // Extract the first part as the sequence number
            int seqNum = Integer.parseInt(seqNumString);
            
            // Get or create the sequence number list for the client
            List<Integer> seqList = seqNumList.computeIfAbsent(clientAddress, k -> new ArrayList<>());
            
            // Check if the sequence number is already present in the list
            if (seqList.contains(seqNum)) {
                // Skip the packet
                System.out.println("Duplicate or out-of-order packet received, skipping...");
                return;
            } else {
                // Add the sequence number to the list
                seqList.add(seqNum);

            }
    
            // Process the email fragment
            emailBuilder += parts[0].trim();
    
            // Print the received fragment and the ACK number sent
            System.out.println("Received Fragment with SeqNum " + seqNum + ", Sent ACK " + (seqNum + 1024));
      // Send ACK to the client for the received fragment
        sendACKPacketToClient(serverSocket, clientAddress, clientPort, (seqNumMap.get(clientAddress) + 1024));
        seqNumMap.put(clientAddress,(seqNumMap.get(clientAddress) + 1024));
        } else {
            System.out.println("Invalid email fragment: " + emailFragment);
        }
    }

    private static String createReceiverDirectory(String toemail) {
        String receiverDirectoryPath = EMAILS_DIRECTORY + File.separator+ toemail;
        File receiverDirectory = new File(receiverDirectoryPath);
        if (!receiverDirectory.exists()) {
            receiverDirectory.mkdirs();
        }
        return receiverDirectoryPath;
    }
    private static String createSenderDirectory(String fromemail) {
        String receiverDirectoryPath = EMAILS_DIRECTORY + File.separator + fromemail;
        File receiverDirectory = new File(receiverDirectoryPath);
        if (!receiverDirectory.exists()) {
            receiverDirectory.mkdirs();
        }
        return receiverDirectoryPath;
    }
    private static String createSentDirectory(String receiverDirectory) {
        String inboxDirectoryPath = receiverDirectory + File.separator + "sent";
        File inboxDirectory = new File(inboxDirectoryPath);
        if (!inboxDirectory.exists()) {
            inboxDirectory.mkdirs();
        }
        return inboxDirectoryPath;
    }
    private static String createInboxDirectory(String receiverDirectory) {
        String inboxDirectoryPath = receiverDirectory + File.separator + "inbox";
        File inboxDirectory = new File(inboxDirectoryPath);
        if (!inboxDirectory.exists()) {
            inboxDirectory.mkdirs();
        }
        return inboxDirectoryPath;
    }

    private static String generateUniqueEmailFilename(String inboxDirectory, String subject) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return inboxDirectory + File.separator + "email_" + sanitizeFilename(subject) + "_" + timestamp + ".txt";
    }

    private static String generateUniqueAttachmentFilename(String inboxDirectory, String subject) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return inboxDirectory + File.separator + "attachment_" + sanitizeFilename(subject) + "_" + timestamp;
    }

    private static String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9]", "_");
    }


    // Method to extract sequence number from the message
    private static int extractSeqNum(String message) {
        // Define a regular expression pattern to match the sequence number
        Pattern pattern = Pattern.compile("SeqNum: (\\d+)");
        Matcher matcher = pattern.matcher(message);

        // Check if the pattern is found in the message
        if (matcher.find()) {
            // Extract and return the sequence number
            return Integer.parseInt(matcher.group(1));
        } else {
            // If no match is found, return a default value or throw an exception
            throw new IllegalArgumentException("Sequence number not found in message: " + message);
        }
    }


    // Method to validate email address format
    private static boolean isValidEmailAddress(String email) {
        // Check if the email is not null
        return email != null;
    }

    // Method to load valid email addresses from file
    private static List<String> loadValidEmails() {
        List<String> validEmails = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(EMAIL_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                validEmails.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return validEmails;
    }

    // Method to load email-to-receiver mappings from file
    private static void loadEmailToReceiverMapping() {
        try (BufferedReader reader = new BufferedReader(new FileReader(MAPPING_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String receiverEmail = parts[0].trim();
                    String receiverHostname = parts[1].trim();
                    emailToReceiverMapping.put(receiverEmail, receiverHostname);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to load email-to-receiver mappings from file
    private static String getEmailReceiverHostname(String toEmail) {
        try (BufferedReader reader = new BufferedReader(new FileReader(MAPPING_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String receiverEmail = parts[0].trim();
                    String receiverHostname = parts[1].trim();
                    if (receiverEmail.equals(toEmail)) {
                        return receiverHostname;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null; // Return null if mapping not found
    }

    private static String getAttachmentType(byte[] attachmentData) {
        String attachmentType = "txt"; // Default to text file if type cannot be determined

        // Check if the data starts with PDF magic number
        if (startsWithMagicNumber(attachmentData, "%PDF-")) {
            attachmentType = "pdf";
        }
        // Check if the data starts with JPEG magic number
        else if (startsWithMagicNumber(attachmentData, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            attachmentType = "jpg";
        }
        // Check if the data starts with PNG magic number
        else if (startsWithMagicNumber(attachmentData, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            attachmentType = "png";
        }
        // Check if the data starts with GIF magic number
        else if (startsWithMagicNumber(attachmentData, "GIF89a") || startsWithMagicNumber(attachmentData, "GIF87a")) {
            attachmentType = "gif";
        }
        // Check if the data starts with ZIP magic number
        else if (startsWithMagicNumber(attachmentData, "PK\u0003\u0004")) {
            attachmentType = "zip";
        }
        // Check if the data starts with Microsoft Word magic number
        else if (startsWithMagicNumber(attachmentData, "\u00D0\u00CF\u0011\u00E0\u00A1\u00B1\u00E1")) {
            attachmentType = "doc";
        }
        // Check if the data starts with Microsoft Excel magic number
        else if (startsWithMagicNumber(attachmentData, "PK\u0003\u0004")) {
            attachmentType = "xls";
        }
        // Add more checks for other types if needed

        return attachmentType;
    }

    private static boolean startsWithMagicNumber(byte[] data, byte[] magicBytes) {
        if (magicBytes.length > data.length) {
            return false;
        }
        for (int i = 0; i < magicBytes.length; i++) {
            if (data[i] != magicBytes[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithMagicNumber(byte[] data, String magicNumber) {
        byte[] magicBytes = magicNumber.getBytes(StandardCharsets.US_ASCII);
        return startsWithMagicNumber(data, magicBytes);
    }

    private static void saveAttachmentToFile(byte[] attachmentData, String filename, String attachmentType) {
        try (FileOutputStream fos = new FileOutputStream(filename + "." + attachmentType)) {
            fos.write(attachmentData);
            //System.out.println("Attachment saved successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to send SYN+ACK packet to client
    private static void sendSYNACKPacketToClient(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort, int seqNum) {
        try {
            String synAckMessage = "SYN-ACK SeqNum: " + seqNum;
            byte[] sendData = synAckMessage.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
            serverSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to send ACK packet to client
    private static void sendACKPacketToClient(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort, int seqNum) {
        try {
            String ackMessage = "ACK SeqNum: " + seqNum;
            byte[] sendData = ackMessage.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
            serverSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to save email to receiver mapping to file
    private static void saveMapping(String email, String hostname) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(MAPPING_FILE, true))) {
            writer.write(email + ":" + hostname);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to send FIN-ACK packet to client
    private static void sendFINACKPacketToClient(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort, int seqNum) {
        try {
            String finAckMessage = "FIN-ACK SeqNum: " + seqNum;
            byte[] sendData = finAckMessage.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
            serverSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class Client {
        private InetAddress address;
        private int port;
        private String email;

        public Client(InetAddress address, int port, String email) {
            this.address = address;
            this.port = port;
            this.email = email;
        }

        public InetAddress getAddress() {
            return address;
        }

        public int getPort() {
            return port;
        }

        public String getEmail() {
            return email;
        }
    }

    public void start() throws IOException, InterruptedException {

    Thread ServerThread = new Thread(new ServerThread()); // Create a new sendThread
    ServerThread.start();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        SimpleUDPServer server = new SimpleUDPServer();
        server.start();
        // Register a shutdown hook1
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        // Empty the content of the mapping file
        try {
            FileWriter fileWriter = new FileWriter(MAPPING_FILE);
            fileWriter.write("");
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
            // Handle the exception as needed
        }
    }));}
    
}
