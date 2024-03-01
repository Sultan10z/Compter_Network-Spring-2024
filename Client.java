import java.io.*;// Input/output operations
import java.net.*;// Networking operations
import java.util.Scanner;// Scanner for input parsing
import java.util.regex.Pattern;// Regular expression pattern matching

// Date and time manipulation
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class Client {
    public static void main(String[] args) {
        DatagramSocket clientSocket = null;
        Scanner scanner = new Scanner(System.in);

        try {
            // Create a DatagramSocket
            clientSocket = new DatagramSocket();
            System.out.println("Client socket created");

            // Get email details from user
            System.out.println("Mail Client starting on host: " + InetAddress.getLocalHost().getHostName());

            String serverName;
            InetAddress serverAddress = null;

            // Validate server name input & see if it is connnected
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


            while (true) {
                // Get 'To' email and validate it's format and ask the client to re-enter the 'To' email if it is not in valid foramt
                String to = getEmail(scanner, "To: ");
                // Validate email addresses
                while (!isValidEmailAddress(to)) {
                    System.out.println("Invalid email format for 'To' email! Please re-enter.");
                    to = getEmail(scanner, "To: ");
                }
                // Get 'From' email and validate it's format and ask the client to re-enter the 'From' email if it is not in valid foramt
                String from = getEmail(scanner, "From: ");
                // Validate email addresses
                while (!isValidEmailAddress(from)) {
                    System.out.println("Invalid email format for 'From' email! Please re-enter.");
                    from = getEmail(scanner, "From: ");
                }

                // Get email details
                String subject = getInput(scanner, "Subject: ");
                String body = getInput(scanner, "Body: ");

                // Construct email message in save it in 'email' String
                String email = "To: " + to + "\nFrom: " + from + "\nSubject: " + subject + "\n\n" + body;
                // Store the email in 'request' String
                String request = email;
                // Count number of bytes in the message with the headers title (ex:to,from...etc)
                int bytesCount = email.getBytes().length;
                System.out.println("Bytes in the message: " + bytesCount);

                // Send the packet to the server
                byte[] sendData = request.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, 12345);
                clientSocket.send(sendPacket);

                // Display confirmation message/ waiting for response message
                System.out.println("\n\nMail Sent to Server, waiting...\n\n");



                // Receive response from the server
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                clientSocket.receive(receivePacket);

                // Extract response from the server and split it into parts
                String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                String[] responseParts = response.split("_"); // Separate response code and timestamp

                // Check if the message "250 OK" (successfully recieved)
                if (responseParts[0].equals("250 OK")) {
                    // Display confirmation message with timestamp
                    System.out.println("250 OK \nEmail received successfully at " + "     " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                }
                // Check if the message "501 Error" (Header is not validated and not successfully recieved)
                else if (responseParts[0].equals("501 Error")) {
                    // Display error message with timestamp
                    System.out.println("Error: " + responseParts[0] + "     " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE. MMM d, yyyy HH:mm")));
                }
                // Additional condition if none of the above is satisfied
                else {
                    // Handle unexpected response
                    System.out.println("Unexpected response from server: " + response);
                }

                // Ask the user if they want to send another email or quit by entering (yes/no)
                System.out.print("Do you want to send another email? (yes/no): ");
                String choice = scanner.nextLine().trim().toLowerCase();
                if (!choice.equals("yes")) {
                    break; // Exit the loop if the user chooses not to send another email and terminate the client connection
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close the scanner and client socket when terminating
            if (scanner != null) {
                scanner.close();
            }
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        }
    }

    // Method to validate and get user input (all the headers should not be empty)
    private static String getInput(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            } else {
                //display error message to the user
                System.out.println("Error: Input cannot be empty. Please try again.");
            }
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
}
