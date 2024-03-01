import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {
    private static final String EMAIL_FILE = "emails.txt";
    private static final String EMAILS_DIRECTORY = "emails";

    // Map to keep track of client directories
    private static Map<String, String> clientDirectories = new HashMap<>();

    public static void main(String[] args) {
        DatagramSocket serverSocket = null;
        int emailCount = 0;

        List<String> validEmails = loadValidEmails();

        try {
            serverSocket = new DatagramSocket(12345);
            byte[] receiveData = new byte[1024];

            System.out.println("Mail Server Starting at host: " + InetAddress.getLocalHost().getHostName());
            System.out.println("Waiting to be contacted for transferring Mail...\n\n");

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);

                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());

                int bytesCount = message.getBytes().length;
                System.out.println("Bytes in the message: " + bytesCount);

                String[] parts = message.split("\n\n", 2);
                String header = parts[0];
                String body = parts[1];
                String[] headerLines = header.split("\n");

                String to = null;
                String from = null;
                String subject = null;

                for (String line : headerLines) {
                    if (line.startsWith("To:")) {
                        to = line.substring(3).trim();
                    } else if (line.startsWith("From:")) {
                        from = line.substring(5).trim();
                    } else if (line.startsWith("Subject:")) {
                        subject = line.substring(8).trim();
                    }
                }

                boolean isValidHeader = isValidEmailAddress(to) && isValidEmailAddress(from) && subject != null;
                boolean isValidEmails = validEmails.contains(to) && validEmails.contains(from);

                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();
                String response;

                if (isValidHeader && isValidEmails) {
                    String clientHostname = clientAddress.getHostName();
                    String clientDirectory = getClientDirectory(clientHostname);
                    String filename = generateUniqueFilename(clientDirectory);
                    try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                        writer.println("FROM: " + from);
                        writer.println("TO: " + to);
                        writer.println("SUBJECT: " + subject);
                        writer.println("TIME: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                        writer.println("\n\n" + body);
                    } catch (IOException e) {
                        e.printStackTrace();
                        response = "File Saving Error";
                    }

                    System.out.println("\n---------------------------------------------------------------------------------\n");
                    System.out.println("Mail Received from " + clientHostname);
                    System.out.println("FROM: " + from);
                    System.out.println("TO: " + to);
                    System.out.println("SUBJECT: " + subject);
                    System.out.println("TIME: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                    System.out.println("\n\n" + body);
                    System.out.println("\n---------------------------------------------------------------------------------");
                    System.out.println("\n\n");

                    String timestamp = LocalDateTime.now().toString();
                    response = "250 OK_" + timestamp;

                    System.out.println("The Header fields and both 'To' and 'From' emails are valid.");
                    System.out.println("\nSending '250 OK'");
                } else {
                    System.out.println("\n---------------------------------------------------------------------------------\n");
                    System.out.println("Mail Received from " + clientAddress.getHostName());
                    System.out.println("FROM: " + from);
                    System.out.println("TO: " + to);
                    System.out.println("SUBJECT: " + subject);
                    System.out.println("TIME: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                    System.out.println("\n\n" + body);
                    System.out.println("\n---------------------------------------------------------------------------------");
                    System.out.println("\n\n");

                    response = "501 Error";
                    System.out.println("The Header fields are not valid.");
                    System.out.println("\nSending '501 Error'\n\n");
                }

                byte[] sendData = response.getBytes();
                int responseSize = response.getBytes().length;
                System.out.println("Bytes in the response message: " + responseSize);

                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                serverSocket.send(sendPacket);

                System.out.println("\n\nReturning to waiting state...\n");
                System.out.println("\n---------------------------------------------------------------------------------\n\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        }
    }

    private static boolean isValidEmailAddress(String email) {
        return email != null;
    }

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

    private static String getClientDirectory(String clientHostname) {
        // Check if client directory already exists, if not, create it
        if (!clientDirectories.containsKey(clientHostname)) {
            String clientDirectory = EMAILS_DIRECTORY + "/emails_" + clientHostname;
            new File(clientDirectory).mkdirs();
            clientDirectories.put(clientHostname, clientDirectory);
        }
        return clientDirectories.get(clientHostname);
    }

    private static String generateUniqueFilename(String clientDirectory) {
        return clientDirectory + "/email_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".txt";
    }
}
