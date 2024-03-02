import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Client {
    public static void main(String[] args) {
        DatagramSocket clientSocket = null;
        Scanner scanner = new Scanner(System.in);

        try {
            // Create a DatagramSocket
            clientSocket = new DatagramSocket();

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

            while (true) {
                String to = getEmail(scanner, "To: ");
                while (!isValidEmailAddress(to)) {
                    System.out.println("Invalid email format for 'To' email! Please re-enter.");
                    to = getEmail(scanner, "To: ");
                }
                String from = getEmail(scanner, "From: ");
                while (!isValidEmailAddress(from)) {
                    System.out.println("Invalid email format for 'From' email! Please re-enter.");
                    from = getEmail(scanner, "From: ");
                }
                String subject = getSubject(scanner, "Subject: ");
                String body = getBody(scanner, "Body: ");

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

                // Construct email message in save it in 'email' String
                String email = "To: " + to + "\nFrom: " + from + "\nSubject: " + subject + "\n\n" + body;
                // Store the email in 'request' String
                String request = email;
                // Count number of bytes in the message with the headers title (ex:to,from...etc)
                int bytesCount = email.getBytes().length;
                System.out.println("\n\nBytes in the message: " + bytesCount);

                // Send the packet to the server
                byte[] sendData = request.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, 25);
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
                // Additional condition if none of the above is satisfied
                else {
                    // Handle unexpected response
                    System.out.println("Unexpected response from server: " + response);
                }

                // Ask the user if they want to send another email or quit by entering (yes/no)
                System.out.print("\n\nDo you want to send another email? (yes/no): ");
                String sendAnother = scanner.nextLine().trim().toLowerCase();
                if (!sendAnother.equals("yes")) {
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

    // Method to validate and get user input for subject
    private static String getSubject(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            } else {
                input = "(No Subject)";
                return input;
            }
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
}
