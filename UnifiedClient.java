import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;

public class UnifiedClient {
    private static DatagramSocket socket;
    private static InetAddress serverAddress;
    private int serverPort = 25; // Assuming SMTP-like communication on port 25
    private Scanner scanner = new Scanner(System.in);
    private static final String EMAILS_DIRECTORY = "emails"; // Base directory to save email files
    private final Map<Integer, String> emailDetailsMap = new HashMap<>();
    private int emailCount = 0;
    private static Map<String, String> clientDirectories = new HashMap<>();
    Thread receiveThread = new Thread(new ReceiveThread());
     static int seqNum=1;
     String myemail="";

    private static final int BUFFER_SIZE = 1024;
    private static final String FRAGMENT_DELIMITER = "<FRAGMENT>";
    // Define window size
    private static final int WINDOW_SIZE = 50;

    // Maintain two pointers for the window
    private int base = 0;

    private int nextSeqNum = 0;

    // Maintain a count of sent fragments within the window
    private int sentCount = 0;



    // List to keep track of acknowledgment status within the window
    private List<Boolean> ackStatus = new ArrayList<>();
    public boolean acked;
    public UnifiedClient(String serverName) throws IOException {
        System.out.println("Mail Client starting on host: " + InetAddress.getLocalHost().getHostName());

        // Validate server name input & see if it is connected
        while (true) {
            System.out.print("\n\nType name of Mail server: ");
            serverName = scanner.nextLine().trim();
            System.out.print("\n\nType your email address: ");
            myemail = scanner.nextLine().trim();
            this.socket = new DatagramSocket();
            //this.socket.setSoTimeout(15000); // Set a timeout for receive operations if needed
            try {
                // Print message indicating connection with server
                serverAddress = InetAddress.getByName(serverName);
                System.out.println("\n\nCONNECTED TO " + serverName + " MAIL SERVER!\n\n");

                // Perform three-way handshake with server
                performThreeWayHandshake(this.socket, serverAddress,myemail);

                break; // If no exception, server name is valid
            } catch (UnknownHostException e) {
                System.out.println("Error: Server name not found. Please enter a valid server name.");
            }
        }


    }

    public void start() throws IOException, InterruptedException {
        Scanner scan = new Scanner(System.in);
        receiveThread.start();
        boolean menuRequested = false;

        boolean running = true;
        while (running) {
            if (menuRequested) {
                showOptions();
                menuRequested = false; // Reset menu flag
            }

            String choice = scan.nextLine().trim();
            switch (choice) {
                case "menu":
                    menuRequested = true; // Set menu flag
                    break;
                case "1":
                    Thread sendThread = new Thread(new SendThread()); // Create a new sendThread
                    sendThread.start(); // Start the sendThread
                    sendThread.join(); // Wait for sendThread to finish
                    break;
                case "2":
                        updateInbox();
                    break;
                case "3":
                    listEmails();
                    break;
                case "4":
                    terminateConnection();
                    receiveThread.interrupt();
                    receiveThread.join();
                    if (!receiveThread.isAlive()) {
                        closeResources();
                    }
                    running = false;
                    break;
                case "yes":
                    attachmentopenchoice="yes";
                default:
                    System.out.println("Invalid option, please choose again.");
            }
        }
    }

    private void closeResources() {
        if (scanner != null) {
            scanner.close();
        }
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public void listEmails() throws IOException {
        emailDetailsMap.clear(); // Clear the email details map before listing emails
        emailCount = 0;
        String myEmail = myemail;
        String hostname = InetAddress.getLocalHost().getHostName();

        String myDirectory = EMAILS_DIRECTORY + File.separator+ myEmail;
        File inboxDirectory = new File(myDirectory + File.separator + "inbox");

        if (!inboxDirectory.exists() || !inboxDirectory.isDirectory()) {
            System.out.println("Emails directory or inbox directory not found.");
        }

        File[] emailFiles = inboxDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
        if (emailFiles == null || emailFiles.length == 0) {

            System.out.println("No email files found in the inbox directory.\n");
            
        }
        else{
            System.out.println("INBOX:\n=======================================================================\n");

        // Iterate through each email file
        for (File emailFile : emailFiles) {
            String [] parts = emailFile.getName().split("_");
            String time = parts[2].replaceAll(".txt","");

            StringBuilder emailContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(emailFile))) {
                String line;
                // Read the content of the email file
                while ((line = reader.readLine()) != null) {
                    emailContent.append(line).append("\n");
                }
            }

            // Extract header fields from email content
            String[] headerLines = emailContent.toString().split("\n");
            String to = null, from = null, subject = null, timestamp = null;

            // Extract header fields
            for (String line : headerLines) {
                if (line.startsWith("TO:")) {
                    to = line.substring(3).trim();
                } else if (line.startsWith("FROM:")) {
                    from = line.substring(5).trim();
                } else if (line.startsWith("SUBJECT:")) {
                    subject = line.substring(8).trim();
                } else if (line.startsWith("TIME:")) {
                    timestamp = line.substring(6).trim();
                }
            }

            // Print the email details in the required format
            emailCount++;
            System.out.println(emailCount + ". From: " + from + ", To: " + to + ", Subject: " + subject + ", Timestamp: " + timestamp+"\n");

            // Store email details in the map for later retrieval
            emailDetailsMap.put(emailCount, emailContent.toString());

            // Check for attachment with same subject and timestamp
            String attachmentFilename = "attachment_" + subject + "_" + time;
            File[] inboxContents = inboxDirectory.listFiles();
            File attachmentFile = new File(inboxDirectory, attachmentFilename);

            for (File file : inboxContents) {
                if (file.getName().startsWith(attachmentFilename)) {
                     attachmentFile = file;
                    break;
                }
            }
         //   System.out.println( attachmentFile) ;
           // System.out.println( attachmentFile.exists()) ;

            if (attachmentFile.exists()) {
                // Attachment found, store its details
                byte[] attachmentData = Files.readAllBytes(Paths.get(attachmentFile.getAbsolutePath()));

                String attachmentType = getAttachmentType(attachmentData);
                String attachmentFileName = attachmentFile.getName();
             //   System.out.println(attachmentFileName);

                emailDetailsMap.put(emailCount, emailContent.toString()+"\nAttachment included!\n\n\n\n\n"+attachmentFileName);
            }
        }
    }
        File sentDirectory = new File(myDirectory + File.separator + "sent");

        if (!sentDirectory.exists() || !sentDirectory.isDirectory()) {
            System.out.println("Emails directory or inbox directory not found.");
        }

        File[] emailFiles1 = sentDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
        if (emailFiles1 == null || emailFiles1.length == 0) {
            System.out.println("No email files found in the inbox directory.");
        }
else{
    System.out.println("\n\nSENT:\n=======================================================================\n");

        // Iterate through each email file
        for (File emailFile : emailFiles1) {
            String [] parts = emailFile.getName().split("_");
            String time = parts[2].replaceAll(".txt","");

            StringBuilder emailContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(emailFile))) {
                String line;
                // Read the content of the email file
                while ((line = reader.readLine()) != null) {
                    emailContent.append(line).append("\n");
                }
            }

            // Extract header fields from email content
            String[] headerLines = emailContent.toString().split("\n");
            String to = null, from = null, subject = null, timestamp = null;

            // Extract header fields
            for (String line : headerLines) {
                if (line.startsWith("TO:")) {
                    to = line.substring(3).trim();
                } else if (line.startsWith("FROM:")) {
                    from = line.substring(5).trim();
                } else if (line.startsWith("SUBJECT:")) {
                    subject = line.substring(8).trim();
                } else if (line.startsWith("TIME:")) {
                    timestamp = line.substring(6).trim();
                }
            }

            // Print the email details in the required format
            emailCount++;
            System.out.println(emailCount + ". From: " + from + ", To: " + to + ", Subject: " + subject + ", Timestamp: " + timestamp+"\n");

            // Store email details in the map for later retrieval
            emailDetailsMap.put(emailCount, emailContent.toString());

            // Check for attachment with same subject and timestamp
            String attachmentFilename = "attachment_" + subject + "_" + time;
            File[] sentContents = sentDirectory.listFiles();
            File attachmentFile = new File(sentDirectory, attachmentFilename);

            for (File file : sentContents) {
                if (file.getName().startsWith(attachmentFilename)) {
                     attachmentFile = file;
                    break;
                }
            }
            //System.out.println( attachmentFile) ;

            if (attachmentFile.exists()) {
                // Attachment found, store its details
                byte[] attachmentData = Files.readAllBytes(Paths.get(attachmentFile.getAbsolutePath()));

                String attachmentType = getAttachmentType(attachmentData);
                String attachmentFileName = attachmentFile.getName();
               // System.out.println(attachmentFileName);

                emailDetailsMap.put(emailCount, emailContent.toString()+"\nAttachment included!\n\n\n\n\n"+attachmentFileName);
            }
        }
    }
boolean choicessflag=false;
        // If there are emails listed, ask the user if they want to view a specific email
        if (emailCount > 0) {
            while(true){
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter the number of the email you want to view details for (or enter 0 to exit): ");
            int selectedEmailIndex = scanner.nextInt();

            if (selectedEmailIndex > 0 && selectedEmailIndex <= emailCount) {
                // Retrieve and print the full details of the selected email
                String selectedEmailDetails = emailDetailsMap.get(selectedEmailIndex);
                if (selectedEmailDetails != null) {
                    System.out.println("\nSelected Email Details:\n" + selectedEmailDetails);

                    // Check if the selected email contains an attachment
                    String attachmentDetails = emailDetailsMap.get(selectedEmailIndex);
                    while(attachmentDetails.contains("Attachment included!")) {
                        String[] parts = attachmentDetails.split("\n\n\n\n\n");
                        String attachmentFileNameWithType = parts[1];

                        // Extract attachment filename and type
                        String attachmentFileName = parts[0];
                        String attachmentType = parts[1];
                        // Prompt the user to open attachment in browser
                        System.out.println("Attachment received. Open it? (yes/no)");
                        String openInBrowser = scanner.next().trim().toLowerCase();
                        if (openInBrowser.equals("yes")) {

                            File attachmentFile = new File(inboxDirectory, attachmentFileNameWithType);

                            // You can implement your logic to open the attachment here
                            System.out.println("Opening attachment...");
                            if((attachmentFile).exists()){
                            openAttachmentInBrowser(inboxDirectory+File.separator+attachmentFileNameWithType);}else{
                            openAttachmentInBrowser(sentDirectory+File.separator+attachmentFileNameWithType);

                            }
                            break;
                        }
                        else if(openInBrowser.equals("no")) {
                            break;
                        }
                        else{
                            continue;}
                    }


                    while (true) {
                        System.out.print("\nDo you want to continue viewing emails? (yes/no): ");
                        String choice = scanner.nextLine().toLowerCase();
            
                        if (choice.equals("yes")) {
                            // Continue with whatever you want to do when the choice is "yes"
                            System.out.println("Continuing to view emails...");
                            choicessflag=true;
                            break;
                        } else if (choice.equals("no")) {
                            // Break the loop if the choice is "no"
                            System.out.println("Exiting email viewer...");
                            choicessflag=false;

                            break;
                        } else {
                            // If the choice is neither "yes" nor "no", ask again
                            System.out.println("Invalid choice. Please enter 'yes' or 'no'.");
                        }
                    }

                    if (choicessflag){
                        continue;
                    }else{break;}

                }else {
                    System.out.println("No details found for the selected email.");
                }
            } else {
                System.out.println("Invalid input or email number does not exist.");
            }
        }} else {
            System.out.println("No emails found.");
        }
    }
    private class SendThread implements Runnable {

        @Override
        public void run() {
            try{

                seqNum++;

                while (true) {
                    String to = getEmail(scanner, "To: ");
                    if(to.equals("exit")){
                        break;
                    }
                    while (!isValidEmailAddress(to)) {
                        System.out.println("Invalid email format for 'To' email! Please re-enter.");
                        to = getEmail(scanner, "To: ");
                    }
                    String from = getEmail(scanner, "From: ");
                    if(from.equals("exit")){
                        break;
                    }
                    while (!isValidEmailAddress(from)) {
                        System.out.println("Invalid email format for 'From' email! Please re-enter.");
                        from = getEmail(scanner, "From: ");
                    }

                    String cc = getCC(scanner, "CC: ");
                    if(cc.equals("exit")){
                        break;
                    }

                    String subject = getSubject(scanner, "Subject: ");
                    if(subject.equals("exit")){
                        break;
                    }
                    String body = getBody(scanner, "Body: ");
                    if(body.equals("exit")){
                        break;
                    }
                    boolean editInfo = true;
                    while (editInfo) {
                        // Ask the user if they want to edit the entered information
                        System.out.print("\n\nDo you want to edit the entered information? (yes/no): ");
                        String choice = scanner.nextLine().trim().toLowerCase();
                        if (choice.equals("yes")) {
                            System.out.println("Select which detail to edit:");
                            System.out.println("1. To");
                            System.out.println("2. From");
                            System.out.println("3. Subject");
                            System.out.println("4. Body");
                            System.out.print("Enter your choice (1-4): ");
                            int detailChoice = Integer.parseInt(scanner.nextLine().trim());

                            switch (detailChoice) {
                                case 1:
                                    to = getEmail(scanner, "To: ");
                                    break;
                                case 2:
                                    from = getEmail(scanner, "From: ");
                                    break;
                                case 3:
                                    subject = getSubject(scanner, "Subject: ");
                                    break;
                                case 4:
                                    body = getBody(scanner, "Body: ");
                                    break;
                                default:
                                    System.out.println("Invalid choice. No changes made.");
                                    break;
                            }
                        } else if (choice.equals("no")) {
                            editInfo = false;
                        } else {
                            System.out.println("Invalid choice. Please enter 'yes' or 'no'.");
                        }
                    }

                    // Ask user if they want to attach a file
                    // Ask user if they want to attach a file
                    System.out.print("Do you want to attach a file? (yes/no): ");
                    String attachChoice = scanner.nextLine().trim().toLowerCase();
                    String attachmentPath = null;
                    if (attachChoice.equals("yes")) {
                        // Use default file explorer to select file
                        Desktop desktop = Desktop.getDesktop();
                        FileDialog fileDialog = new FileDialog((Frame)null, "Select File to Attach");
                        fileDialog.setVisible(true);
                        String selectedFile = fileDialog.getFile();
                        if (selectedFile != null) {
                            attachmentPath = fileDialog.getDirectory() + selectedFile;
                        } else {
                            // User canceled file selection
                            System.out.println("File selection canceled.");
                            continue; // Restart the loop to ask for attachment again
                        }
                    }






                    // Construct email message with attachment
                    String email = "To: " + to + "\nFrom: " + from +"\nCC: "+ cc +"\nSubject: " + subject + "\n\n" + body;

                    if (attachmentPath != null) {
                        try {
                            // Read attachment file as bytes
                            byte[] attachmentData = Files.readAllBytes(Paths.get(attachmentPath));
                            // Encode attachment data as Base64 string
                            String attachmentEncoded = Base64.getEncoder().encodeToString(attachmentData);
                            // Append attachment to email with a delimiter
                            email += "\nAttachment: " + attachmentEncoded ; // Append attachment and sequence number
                        } catch (IOException e) {
                            e.printStackTrace();
                            continue; // Restart the loop to ask for attachment again
                        }
                    } else {
                        // Append only sequence number if no attachment
                    }
                    String clientDirectory = createSentDirectory(myemail);

                    String filename = generateUniqueEmailFilename(clientDirectory, subject);
                    String filename2 = generateUniqueAttachmentFilename(clientDirectory, subject);
                    // Check if attachment exists
                    boolean hasAttachment = email.contains("Attachment:");

                    // If attachment exists, extract and save it
                    if (hasAttachment) {
                        // Split message to extract attachment
                        String[] messageParts = email.split("Attachment:");
                        String emailContent = messageParts[0];
                        String attachmentEncoded = messageParts[1].trim();

                        // Decode base64 encoded attachment content
                        byte[] attachmentData = Base64.getDecoder().decode(attachmentEncoded);

                        String attachmentType = getAttachmentType(attachmentData);

                        // Save attachment to file with appropriate extension
                        saveAttachmentToFile(attachmentData, filename2, attachmentType);
                    }

                    try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                        // Write email content to file
                        writer.println("FROM: " + from);
                        writer.println("TO: " + to);
                        writer.println("SUBJECT: " + subject);
                        writer.println("TIME: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                        writer.println("\n\n" + body);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // Store the email in 'request' String
                    String request = email ;

                    // Count number of bytes in the message with the headers title (ex:to,from...etc)
                    int bytesCount = request.getBytes().length;
                    System.out.println("\n\nBytes in the message: " + bytesCount);

                    // Check if email size exceeds buffer size
if (email.getBytes().length > BUFFER_SIZE) {
    acked = false;

    // Fragment the email message
    List<String> emailFragments = fragmentEmail(email);

    // Send each fragment to the server
    for (String fragment : emailFragments) {
        // Send the fragment
        sendFragment(socket, fragment, serverAddress);
        Thread.sleep(2);

        // Wait for acknowledgment of the fragment
        long startTime = System.currentTimeMillis();
        long timeout = 1000; // Timeout duration in milliseconds (adjust as needed)
        while (!acked) {
            // Check if acknowledgment received or timeout reached
            if (System.currentTimeMillis() - startTime >= timeout) {
                System.out.println("Acknowledgment not received for fragment, skipping next fragment.");
                sendFragment(socket, fragment, serverAddress);

               break; // Skip sending the next fragment
            }
            Thread.sleep(2); // Adjust sleep duration as needed
        }
        Thread.sleep(2); // Adjust sleep duration as needed

        // Reset acked flag for the next fragment
        acked = false;
    }

    

} else {
    request += "\nSeqNum: " + seqNum;

    // Send the packet to the server
    byte[] sendData = request.getBytes();
    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, 25);
    socket.send(sendPacket);
}

// Display confirmation message/ waiting for response message
System.out.println("\n\nMail Sent to Server, waiting...\n\n");
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
    }
    // Method to wait for ACK from the server
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
    private static List<String> fragmentEmail(String email) {
        List<String> fragments = new ArrayList<>();
        int emailLength = email.getBytes().length;
        int digitsInSeqNum = String.valueOf(seqNum).length();
        int maxFragmentSize = BUFFER_SIZE - ("FRAGMENTSeqNum:10000000000" + seqNum + " ").getBytes().length - digitsInSeqNum;

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

    private static void sendFragment(DatagramSocket clientSocket, String fragment, InetAddress serverAddress) throws IOException {
        byte[] sendData = fragment.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, 25);
        clientSocket.send(sendPacket);
    }
    static Map<Integer, StringBuilder> emailFragmentsMap = new HashMap<>();
    Map<InetAddress, List<Integer>> seqNumList=new HashMap<>();

    private static String emailBuilder="";
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
    
        } else {
            System.out.println("Invalid email fragment: " + emailFragment);
        }
    }
    /*private static void handleEmailFragment(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort, String emailFragment, Map<Integer, StringBuilder> emailFragmentsMap, int ackNum) throws IOException {
        // Split the email fragment into parts
        String[] parts = emailFragment.split("\nFRAGMENTSeqNum: ");
        if (parts.length >= 2) {
            // Extract the sequence number
            String seqNumString = parts[1].trim().split("\\s+")[0]; // Extract the first part as the sequence number
            int seqNum = Integer.parseInt(seqNumString);

            emailBuilder+=parts[0].trim();


            // Print the received fragment and the ACK number sent
            System.out.println("Received Fragment with SeqNum " + seqNum + ", Sent ACK " + (seqNum + 1024));
            seqNum=seqNum+1024;

        } else {
            System.out.println("Invalid email fragment: " + emailFragment);
        }
    }*/
    private static boolean hasFragmentEnd=false;

    private class ReceiveThread implements Runnable {
        @Override
        public void run() {

                while (!Thread.interrupted()) {
                    try{  // Listen for emails from the server continuously
                        // Get client hostname
                        String clientHostname = InetAddress.getLocalHost().getHostName();
                        // Listen for forwarded messages
                        byte[] receiveData = new byte[1024];
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        socket.receive(receivePacket);
                        String email = new String(receivePacket.getData(), 0, receivePacket.getLength());

                      if(email.startsWith("EOT")){
                          hasFragmentEnd = false;

                          emailBuilder="";

                          continue;
                      }



                      if(email.startsWith("FIN-ACK")){
                            // Check if the FIN-ACK is received
                                // Get sequence number from FIN ACK packet
                                int finAckSeqNum = extractSeqNum(email);
                                System.out.println("Received FIN-ACK from Server: " + receivePacket.getAddress().getHostName() + " with sequence number: " + finAckSeqNum);
                                finAckSeqNum++;
                                sendACKPacketToServer(socket, serverAddress, finAckSeqNum);
                                System.out.println("Sent ACK with seq : " + finAckSeqNum);
                                continue;
                        }
                        if (email.startsWith("ACK")) {
                            // Get sequence number from ACK packet
                            seqNum = extractSeqNum(email);

                            System.out.println("Received ACK from Server: " + receivePacket.getAddress().getHostName() + " with sequence number: " + seqNum);
                            acked=true;

                            continue;
                        }


                        if(email.contains("250 OK")||email.contains("501 Error")||email.contains("550 Error")){

                            // Extract response from the server and split it into parts
                            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                            String[] responseParts = response.split("_"); // Separate response code and timestamp

                            // Check if the message "250 OK" (successfully received)
                            if (responseParts[0].equals("250 OK")) {
                                // Display confirmation message with timestamp
                                System.out.println("250 OK \nEmail received successfully at " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                            }
                            // Check if the message "501 Error" (Header is not validated and not successfully received)
                            else if (responseParts[0].equals("501 Error")) {
                                // Display error message with timestamp
                                System.out.println("Error: " + responseParts[0] + "     " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                            }
                            else if (responseParts[0].equals("550 Error")) {
                                // Display error message with timestamp
                                System.out.println("Error: " + responseParts[0] + "     " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                            }
                            // Additional condition if none of the above is satisfied
                            else {
                                // Handle unexpected response
                                System.out.println("Unexpected response from server: " + response);
                            }

                            seqNum++;

                            sendACKPacketToServer(socket, serverAddress, seqNum);
                            System.out.println("Sent Ack with seq : " + seqNum);
                            continue;
                        }

                        if (email.contains("FRAGMENTSeqNum:")) {
                            handleEmailFragment(socket, receivePacket.getAddress(), receivePacket.getPort(),  email, emailFragmentsMap, seqNumList, seqNum) ;

                            if (email.contains("FRAGMENTEND")) {
                                hasFragmentEnd = true;
                            }

                            sendACKPacketToServer(socket, serverAddress, seqNum+1024);
seqNum+=1024;
                  } else {
                        System.out.println("Received complete email: " + email);

                        // Count number of bytes in the message the client sent
                        int bytesCount = email.getBytes().length;
                        // Extract sequence number from received message
                        int seqNuma = extractSeqNum(email);
                        //   System.out.println("\nA mail received, from : " + clientAddress.getHostName() + " seq = " + seqNum + "\n");
                        // Update sequence number for the client
                            seqNum+=seqNuma;

                            sendACKPacketToServer(socket, serverAddress, seqNum+1024);
                        System.out.println("Sent ACK to client: " + serverAddress.getHostName() + " with sequence number: " + seqNum);
                        hasFragmentEnd = true;
                        emailBuilder=email;

                    }

                    if (!hasFragmentEnd) {
                        continue;
                    }

                        email= emailBuilder;

                        if (email.contains("filename")) {
                            email =email.replaceAll("\nSeqNum: \\d+", ""); // Remove sequence number from the body

                            // Extract sequence number from the received email
                            String[] parts0=email.split("Attachment:");

                            System.out.println(parts0[0]);

                            // Extract email content and header fields and split them into parts for easier validation
                            String[] parts = parts0[0].split("\n\n", 2); // Split email content from header
                            String header = parts[0];
                            String body = parts[1]; // Remove sequence number from the body
                            String[] headerLines = header.split("\n");


                            int bytesCount = email.getBytes().length;

                            int AckSeqNum = seqNum + bytesCount;

                            //sendACKPacketToServer(socket, serverAddress, AckSeqNum);

                            String to = null;
                            String from = null;
                            String subject = null;
                            String filename = null;
                            String time = null;

                            // Extract header fields and save them in different string variables
                            for (String line : headerLines) {
                                if (line.startsWith("To:")) {
                                    to = line.substring(3).trim();
                                } else if (line.startsWith("From:")) {
                                    from = line.substring(5).trim();
                                } else if (line.startsWith("Subject:")) {
                                    subject = line.substring(8).trim();
                                } else if (line.startsWith("filename:")) {
                                    filename = line.substring(10).trim();
                                }
                            }

                            // Get client directory or create new if it doesn't exist
                            String clientDirectory = createInboxDirectory(myemail);

                            System.out.println("Filename: " + filename); // Add this line for debugging
                            String filename4 = generateUniqueFilename4(clientDirectory, filename);
                            String filename3 = generateUniqueFilename3(clientDirectory, filename);

                            try (PrintWriter writer = new PrintWriter(new FileWriter(filename3))) {
                                // Write email content to file
                                writer.println("FROM: " + from);
                                writer.println("TO: " + to);
                                writer.println("SUBJECT: " + subject);
                                writer.println(body);

                            } catch (IOException e) {
                                e.printStackTrace();
                                String response = "File Saving Error"; // Notify client about file saving error
                            }

                            // Check if attachment exists
                            boolean hasAttachment = email.contains("Attachment:");

                            // If attachment exists, extract and save it
                            if (hasAttachment) {
                                // Split message to extract attachment
                                String[] messageParts = email.split("Attachment:");
                                String emailContent = messageParts[0];
                                String attachmentEncoded = messageParts[1].trim();
                                //System.out.println(attachmentEncoded);
                                // Decode base64 encoded attachment content
                                byte[] attachmentData = Base64.getDecoder().decode(attachmentEncoded);
                                if(attachmentEncoded.contains("FRAGMENT")){
                                System.out.println("meow");
                                }

                                String attachmentType = getAttachmentType(attachmentData);
                                System.out.println(attachmentType);

                                // Save attachment to file with appropriate extension
                                saveAttachmentToFile(attachmentData, filename4, attachmentType);
                            }

                            continue;

                        }

                        // Extract sequence number from the received email
                        String[] parts0=email.split("Attachment:");

                        System.out.println(parts0[0]);

                        // Extract email content and header fields and split them into parts for easier validation
                        String[] parts = parts0[0].split("\n\n", 2); // Split email content from header
                        String header = parts[0];
                        String body = parts[1]; // Remove sequence number from the body
                        String[] headerLines = header.split("\n");

                        String to = null;
                        String from = null;
                        String subject = null;

                        // Extract header fields and save them in different string variables
                        for (String line : headerLines) {
                            if (line.startsWith("To:")) {
                                to = line.substring(3).trim();
                            } else if (line.startsWith("From:")) {
                                from = line.substring(5).trim();
                            } else if (line.startsWith("Subject:")) {
                                subject = line.substring(8).trim();
                            }
                        }


                        // Get client directory or create new if it doesn't exist
                        String clientDirectory = createInboxDirectory(myemail);
                        // Generate unique filename for email
                        String filename = generateUniqueEmailFilename(clientDirectory, subject);
                        String filename2 = generateUniqueAttachmentFilename(clientDirectory, subject);
                        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                            // Write email content to file
                            writer.println("FROM: " + from);
                            writer.println("TO: " + to);
                            writer.println("SUBJECT: " + subject);
                            writer.println("TIME: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                            writer.println("\n\n" + body);

                        } catch (IOException e) {
                            e.printStackTrace();
                            String response = "File Saving Error"; // Notify client about file saving error
                        }

                        // Check if attachment exists
                        boolean hasAttachment = email.contains("Attachment:");
                        System.out.println(hasAttachment);
                        // If attachment exists, extract and save it
                        if (hasAttachment) {
                            // Split message to extract attachment
                            String[] messageParts = email.split("Attachment:");
                            String emailContent = messageParts[0];
                            String attachmentEncoded = messageParts[1].trim();

                            // Decode base64 encoded attachment content
                            byte[] attachmentData = Base64.getDecoder().decode(attachmentEncoded);

                            String attachmentType = getAttachmentType(attachmentData);

                            // Save attachment to file with appropriate extension
                            saveAttachmentToFile(attachmentData, filename2, attachmentType);

                            // Prompt the user to open attachment in browser
                            System.out.println("Attachment received. Open it in browser? (yes/no)");
                            String openInBrowser = scanner.nextLine().trim().toLowerCase();
                            if (openInBrowser.equals("yes")||attachmentopenchoice.equals("yes")) {
                                // Open attachment in browser
                                openAttachmentInBrowser(filename2 + "." + attachmentType);
                                attachmentopenchoice="";

                            }
                        }


                    } catch (IOException e) {
                e.printStackTrace();
            }
        }
                }

        }

private static String attachmentopenchoice="";
    public static void main(String[] args) {
        try {
            // Example usage with "localhost" as the server address
            UnifiedClient client = new UnifiedClient("localhost");
            client.start();
        } catch (IOException e) {
            System.err.println("Could not start client: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // Method to validate and get user input for subject
    private static String getSubject(Scanner scanner, String prompt) {

        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        if (!input.isEmpty()) {
            return input;
        } else {
            input="";
            return input;

            //input = "(No Subject)";
            // return input;
        }


    }

    private static String getCC(Scanner scanner, String prompt) {

        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        if (!input.isEmpty()) {
            return input;
        } else {
            input="";
            return input;

            //input = "(No Subject)";
            // return input;
        }


    }

    private static String getBody(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            return input;
        }
    }

    // Method to validate email address format
    private static boolean isValidEmailAddress(String email) {
        // we'll just check if the string follows typical email format
        String emailRegex = "^[a-zA-Z0-9_+&-]+(?:\\.[a-zA-Z0-9_+&-]+)*@" +
                "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

        Pattern pattern = Pattern.compile(emailRegex);
        if (email == null) return false;
        return pattern.matcher(email).matches();
    }

    // Method to get email from user
    private static String getEmail(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String email = scanner.nextLine().trim();
            if (!email.isEmpty()) {
                return email;
            } else {
                //display error message to the user
                System.out.println("Error: Email cannot be empty. Please try again.");
            }
        }
    }

    // Method to perform the three-way handshake with the server
    private static void performThreeWayHandshake(DatagramSocket clientSocket, InetAddress serverAddress,String myemail) {
        try {
            // Send SYN packet to server
            sendSYNPacketToServer( clientSocket,  serverAddress,25,myemail, seqNum);

            // Receive SYN-ACK packet from server
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);
            String synAckMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
            System.out.println("Sent SYN to Server: " + serverAddress.getHostName() + " with sequence number: " + seqNum);

            seqNum=extractSeqNum(synAckMessage);

            // Process SYN-ACK packet
            if (synAckMessage.startsWith("SYN-ACK")) {
                // Extract sequence number from SYN-ACK message
                int synAckSeqNum = Integer.parseInt(synAckMessage.split(": ")[1]);
                System.out.println("\nReceived SYN-ACK from Server: " + serverAddress.getHostName() + " with sequence number: " + synAckSeqNum);
                seqNum++;

                // Send ACK packet to server with incremented sequence number
                sendACKPacketToServer(clientSocket, serverAddress, seqNum);
                System.out.println("\nSent ACK to Server: " + serverAddress.getHostName() + " with sequence number: " + seqNum);

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Modify the sendSYNPacketToServer method in the client code to include the client email
    private static void sendSYNPacketToServer(DatagramSocket clientSocket, InetAddress serverAddress, int serverPort, String clientemail, int seqNum) {
        try {
            String synMessage = "SYN clientemail: " + clientemail + " SeqNum: " + seqNum;
            byte[] sendData = synMessage.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
            clientSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Method to send ACK packet to server
    private static void sendACKPacketToServer(DatagramSocket clientSocket, InetAddress serverAddress, int seqNum) {
        try {
            String ackMessage = "ACK SeqNum: " + seqNum;
            byte[] sendData = ackMessage.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, 25);
            clientSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

    // Method to send FIN packet to server
    private static void sendFINPacketToServer(DatagramSocket clientSocket, InetAddress serverAddress, int seqNum) {
        try {
            String finMessage = "FIN SeqNum: " + seqNum;
            byte[] sendData = finMessage.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, 25);
            clientSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to generate unique filename based on current timestamp and email subject
    private static String generateUniqueFilename(String clientDirectory, String subject) {
        // Format the subject to remove any special characters that are not allowed in filenames
        String formattedSubject = subject.replaceAll("[^a-zA-Z0-9]", "_");
        // Get the current timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // Construct the filename with the specified format
        return clientDirectory + "/email_" + formattedSubject + "__" + timestamp+ ".txt";
    }

    private static String generateUniqueFilename2(String clientDirectory, String subject) {
        // Format the subject to remove any special characters that are not allowed in filenames
        String formattedSubject = subject.replaceAll("[^a-zA-Z0-9]", "_");
        // Get the current timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // Construct the filename with the specified format
        return clientDirectory + "/email_" + formattedSubject + "_" + timestamp;
    }
    private static String generateUniqueFilename4(String clientDirectory, String filename) {
        filename=filename.replaceAll("email_","");
        return clientDirectory + "/attachment_" +filename;
    }

    private static String getClientDirectory(String clientHostname) {
        // Check if client directory already exists, if not, create it
        if (!clientDirectories.containsKey(clientHostname)) {
            String clientDirectory = EMAILS_DIRECTORY + "/emails_" + clientHostname;
            new File(clientDirectory).mkdirs();
            clientDirectories.put(clientHostname, clientDirectory);
        }
        return clientDirectories.get(clientHostname);
    }
    private static String createSentDirectory(String receiverDirectory) {
        String inboxDirectoryPath = receiverDirectory + File.separator + "sent";
        File inboxDirectory = new File(inboxDirectoryPath);
        if (!inboxDirectory.exists()) {
            inboxDirectory.mkdirs();
        }
        return inboxDirectoryPath;
    }
    private static String getAttachmentType(byte[] attachmentData) {
        String attachmentType = "txt"; // Default to text file if type cannot be determined

        // Check if the data starts with PDF magic number
        if (startsWithMagicNumber(attachmentData, "%PDF-")) {
            attachmentType = "pdf";
        }
        // Check if the data starts with JPEG magic number
        else if (startsWithMagicNumber(attachmentData, new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF})) {
            attachmentType = "jpg";
        }
        // Check if the data starts with PNG magic number
        else if (startsWithMagicNumber(attachmentData, new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
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








    private static String generateUniqueFilename3(String clientDirectory, String filename) {
        return clientDirectory + "/" + filename+".txt";
    }
    // Method to open attachment in browser
    private static void openAttachmentInBrowser(String filePath) {
        try {
            File file = new File(filePath);
            Desktop desktop = Desktop.getDesktop();
            desktop.open(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Method to send request to update inbox
    private static void sendUpdateInboxRequest(DatagramSocket clientSocket, InetAddress serverAddress, String clientHostname) {
        try {
            // Construct the "update inbox" command with client hostname and receiver email
            String command = "update inbox:" + clientHostname;
            byte[] sendData = command.getBytes();

            // Send the command to the server
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, 25);
            clientSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String createReceiverDirectory(String receiverHostname,String toemail) {
        String receiverDirectoryPath = EMAILS_DIRECTORY + File.separator + receiverHostname+"_"+toemail;
        File receiverDirectory = new File(receiverDirectoryPath);
        if (!receiverDirectory.exists()) {
            receiverDirectory.mkdirs();
        }
        return receiverDirectoryPath;
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


    private void showOptions() {
        System.out.println("\nPlease choose an option:");
        System.out.println("1. Send an email");
        System.out.println("2. Update inbox");
        System.out.println("3. List emails");
        System.out.println("4. Terminate");
        System.out.print("Enter your choice: ");
    }

    private void sendEmail() {
        // Logic to send an email
    }

    private void updateInbox() throws UnknownHostException, InterruptedException {


// Get client hostname
        String clientHostname = InetAddress.getLocalHost().getHostName();
        // Check if the user wants to update the inbox
        System.out.print("Enter 'update inbox' to receive pending emails (or type 'exit' to quit): ");
        String command = scanner.nextLine().trim();

        if (command.equalsIgnoreCase("update inbox")) {
            // Send request to update inbox
            sendUpdateInboxRequest(socket, serverAddress, clientHostname);
        }
             }





    private void terminateConnection() throws IOException {
        // Logic to terminate the connection
        System.out.println("Terminating connection...");
// After sending email, check if the user wants to terminate the connection
        System.out.print("\n\nDo you want to terminate the connection? (yes/no): ");
        String terminateChoice = scanner.nextLine().trim().toLowerCase();
        if (terminateChoice.equals("yes")) {
            // Send FIN packet to server
           // seqNum=seqNum+1;
            sendFINPacketToServer(socket, serverAddress, seqNum);
            System.out.println("Sent FIN with seq : " + seqNum);






        }
    }

}
