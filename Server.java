import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {
    private static final String EMAIL_FILE = "emails.txt"; // File containing valid email addresses
    private static final String EMAILS_DIRECTORY = "emails"; // Base directory to save email files
    private static final String MAPPING_FILE = "mapping.txt"; // File containing mappings from email addresses to receiver client hostnames

    // Map to keep track of client directories
    private static Map<String, String> clientDirectories = new HashMap<>();
    private static Map<String, String> emailToReceiverMapping = new HashMap<>();

    public static void main(String[] args) {
        DatagramSocket serverSocket = null;
        int emailCount = 0; // Counter to create unique filenames to save the valid emails in the server directory

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

            // Listen for incoming requests indefinitely
            while (true) {
                // Receive request packet
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);

                // Extract message from received packet
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());

                // Count number of bytes in the message the client sent
                int bytesCount = message.getBytes().length;
                System.out.println("\n=================================================================================\n\n");

                System.out.println("Bytes in the message: " + bytesCount);

                // Extract email content and header fields and split them into parts for easier validation
                String[] parts = message.split("\n\n", 2); // Split email content from header
                String header = parts[0];
                String body = parts[1];
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

                // Validate header fields make sure nothing is null
                boolean isValidHeader = isValidEmailAddress(to) && isValidEmailAddress(from) && subject != null;

                // Check if both 'To' and 'From' email addresses are valid
                boolean isValidEmails = validEmails.contains(to) && validEmails.contains(from);

                // Send appropriate response based on header and email validation
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();
                String response;

                if (isValidHeader && isValidEmails) {
                    // Get client hostname
                    String clientHostname = clientAddress.getHostName();
                    // Get client directory or create new if it doesn't exist
                    String clientDirectory = getClientDirectory(clientHostname);
                    // Generate unique filename for email
                    String filename = generateUniqueFilename(clientDirectory);

                    try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                        // Write email content to file
                        writer.println("FROM: " + from);
                        writer.println("TO: " + to);
                        writer.println("SUBJECT: " + subject);
                        writer.println("TIME: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                        writer.println("\n\n" + body);


                    } catch (IOException e) {
                        e.printStackTrace();
                        response = "File Saving Error"; // Notify client about file saving error
                    }

                    // Show the email's details in the server terminal
                    System.out.println("\n---------------------------------------------------------------------------------\n");
                    System.out.println("Mail Received from " + clientHostname);
                    System.out.println("FROM: " + from);
                    System.out.println("TO: " + to);
                    System.out.println("SUBJECT: " + subject);
                    System.out.println("TIME: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                    System.out.println("\n\n" + body);
                    System.out.println("\n---------------------------------------------------------------------------------");
                    System.out.println("\n");

                    // Send confirmation response to client with timestamp
                    String timestamp = LocalDateTime.now().toString();
                    response = "250 OK_" + timestamp; // Use a separator to distinguish response code and timestamp

                    System.out.println("The Header fields and both 'To' and 'From' emails are valid.");
                    System.out.println("\nSending '250 OK'");

                    // Check if the receiver client is mapped and forward the email
                    if (emailToReceiverMapping.containsKey(to)) {
                        String receiverHostname = emailToReceiverMapping.get(to);
                        InetAddress receiverAddress = InetAddress.getByName(receiverHostname);
                        int receiverPort = 12345; // Assuming port 12345 for receiver clients

                        // Send email to receiver client
                        sendEmailToReceiver(filename, to, from, subject, body, receiverAddress, receiverPort);
                    }
                } else {
                    // Show the email's details in the server terminal
                    System.out.println("\n---------------------------------------------------------------------------------\n");
                    System.out.println("Mail Received from " + clientAddress.getHostName());
                    System.out.println("FROM: " + from);
                    System.out.println("TO: " + to);
                    System.out.println("SUBJECT: " + subject);
                    System.out.println("TIME: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                    System.out.println("\n\n" + body);
                    System.out.println("\n---------------------------------------------------------------------------------");
                    System.out.println("\n");

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

                // Return to waiting state and not terminate the server connection
                System.out.println("\n\nReturning to waiting state...\n");
                System.out.println("\n---------------------------------------------------------------------------------\n\n");

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close the server socket when terminating
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
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

    // Method to get client directory or create new if it doesn't exist
    private static String getClientDirectory(String clientHostname) {
        // Check if client directory already exists, if not, create it
        if (!clientDirectories.containsKey(clientHostname)) {
            String clientDirectory = EMAILS_DIRECTORY + "/emails_" + clientHostname;
            new File(clientDirectory).mkdirs();
            clientDirectories.put(clientHostname, clientDirectory);
        }
        return clientDirectories.get(clientHostname);
    }

    // Method to generate unique filename based on current timestamp
    private static String generateUniqueFilename(String clientDirectory) {
        return clientDirectory + "/email_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".txt";
    }

    // Method to send email to receiver client
    private static void sendEmailToReceiver(String filename, String to, String from, String subject, String body, InetAddress receiverAddress, int receiverPort) {
        try (DatagramSocket socket = new DatagramSocket()) {
            // Construct email message
            String email = "To: " + to + "\nFrom: " + from + "\nSubject: " + subject + "\n\n" + body;
            byte[] sendData = email.getBytes();

            // Send email to receiver client
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, receiverAddress, receiverPort);
            socket.send(sendPacket);

            System.out.println("Email forwarded to Receiver Client:");
            System.out.println("Forwarded to: " + receiverAddress.getHostAddress());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
