import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ReceiverClient {
    private static final String MAPPING_FILE = "mapping.txt"; // File containing mappings from email addresses to receiver client hostnames

    public static void main(String[] args) {
        DatagramSocket clientSocket = null;
        Scanner scanner = new Scanner(System.in);

        try {
            // Create a DatagramSocket
            clientSocket = new DatagramSocket(12345);

            // Get email details from user
            System.out.println("Mail Client starting on host: " + InetAddress.getLocalHost().getHostName());

            String serverName;
            InetAddress serverAddress = null;

            // Validate server name input & see if it is connected
            while (true) {
                System.out.print("\n\nType name of Mail server: ");
                serverName = scanner.nextLine().trim();
                try {
                    // Print message indicating connection with server
                    serverAddress = InetAddress.getByName(serverName);
                    System.out.println("\n\nCONNECTED TO " + serverName + " MAIL SERVER!\n\n");
                    break; // If no exception, server name is valid
                } catch (UnknownHostException e) {
                    System.out.println("Error: Server name not found. Please enter a valid server name.");
                }
            }

            // Get email address for the receiver client
            System.out.print("Enter your email address: ");
            String receiverEmail = scanner.nextLine().trim();

            // Get hostname for the receiver client
            System.out.print("Enter your hostname: ");
            String receiverHostname = scanner.nextLine().trim();

            // Save mapping to file
            saveMapping(receiverEmail, receiverHostname);

            // Listen for emails from the server continuously
            while (true) {
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                clientSocket.receive(receivePacket);
                String email = new String(receivePacket.getData(), 0, receivePacket.getLength());
                System.out.println("\n\nReceived Email:");
                System.out.println(email);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close resources
            if (clientSocket != null) {
                clientSocket.close();
            }
            if (scanner != null) {
                scanner.close();
            }
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
}