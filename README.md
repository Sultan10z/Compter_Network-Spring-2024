# Project: Mail Transfer Protocol (SMTP) over UDP

This repository contains the implementation of project for the Computer Networks course (Spring 2024) focusing on Simple Mail Transfer Protocol (SMTP) over UDP.

---

### Table of Contents
1. [Project Description](#project-description)
2. [Features](#features)
3. [Requirements](#requirements)
4. [Installation and Setup](#installation-and-setup)
5. [Usage](#usage)
6. [File Descriptions](#file-descriptions)
7. [Protocol Details](#protocol-details)
8. [Testing](#testing)
9. [Troubleshooting](#troubleshooting)

---

### Project Description
This project implements a simplified version of the Mail Transfer Protocol using UDP. It includes a server (`SimpleUDPServer.java`) and a client (`UnifiedClient.java`) to facilitate the transfer of messages over a network. The implementation is designed to demonstrate fundamental concepts of computer networks, including socket programming, message formatting, and protocol design.

### Features
- **SimpleUDPServer**: Handles incoming messages, processes them, and sends appropriate responses.
- **UnifiedClient**: Sends messages to the server and processes the responses.
- **Message Logging**: Records all interactions between the client and server for debugging and verification purposes.
- **Email Validation**: Valid emails are available in `emails.txt` and should be in the same path as the server file.

### Requirements
- Java Development Kit (JDK) 8 or higher
- An Integrated Development Environment (IDE) such as Eclipse or IntelliJ IDEA

### Installation and Setup
1. **Clone the Repository**:
    ```sh
    git clone <repository-url>
    ```
2. **Navigate to the Project Directory**:
    ```sh
    cd <project-directory>
    ```
3. **Open the Project in Your IDE**:
    - Import the project as a Java project.
4. **Build the Project**:
    - Compile `SimpleUDPServer.java` and `UnifiedClient.java`.
5. **Ensure `emails.txt` is Present**:
    - Place the `emails.txt` file, containing valid email addresses, in the same directory as `SimpleUDPServer.java`.

### Usage
1. **Run the Server**:
    - In your IDE, run `SimpleUDPServer.java`. The server will start and listen on the specified port.
2. **Run the Client**:
    - In your IDE, run `UnifiedClient.java`. Follow the prompts to send messages to the server.

### File Descriptions
- **SimpleUDPServer.java**: Implements the server-side logic for handling incoming UDP messages, processing them, and sending responses. The server also validates emails based on `emails.txt`.
- **UnifiedClient.java**: Implements the client-side logic for sending messages to the server and processing the responses.
- **emails.txt**: A text file containing a list of valid email addresses. Each email address should be on a separate line.

### Protocol Details
The protocol is designed to handle basic message transfer using the User Datagram Protocol (UDP). Each message follows a predefined format to ensure proper parsing and handling by both the client and server. Key aspects of the protocol include:
- **Message Format**: Each message is composed of a header and a body, separated by a delimiter.
- **Headers**: Include metadata such as message type and length.
- **Body**: Contains the actual message content.
- **Email Validation**: The server checks if the email address is present in `emails.txt` before processing the message.

### Testing
To ensure the correct functionality of the protocol, perform the following tests:
1. **Basic Connectivity Test**: Verify that the client can establish a connection with the server.
2. **Message Transfer Test**: Send a message from the client to the server and check for the correct response.
3. **Email Validation Test**: Send a message with a valid and invalid email address to verify the validation logic.
4. **Edge Cases**: Test with various message lengths and formats to ensure robust handling.

### Troubleshooting
- **Common Issues**:
    - **Server Not Responding**: Ensure the server is running and listening on the correct port.
    - **Message Format Errors**: Verify that the messages conform to the expected format.
    - **Invalid Email Errors**: Ensure the email address is listed in `emails.txt`.
- **Debugging Tips**:
    - Use logging statements to track the flow of messages and identify any discrepancies.
    - Check for exceptions and errors in the console output.
