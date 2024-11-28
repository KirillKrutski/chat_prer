import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.sql.*;

public class ChatClient {
    private JFrame loginFrame;
    private JFrame chatFrame;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton deleteButton;
    private JButton editButton;
    private String username;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ChatClient() {
        setupLoginUI();
    }

    private void setupLoginUI() {
        loginFrame = new JFrame("Login");
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setSize(400, 200);
        loginFrame.setLayout(new GridLayout(3, 2));

        JLabel usernameLabel = new JLabel("Username:");
        usernameField = new JTextField();
        JLabel passwordLabel = new JLabel("Password:");
        passwordField = new JPasswordField();
        JButton loginButton = new JButton("Login");
        JButton registerButton = new JButton("Register");

        loginButton.addActionListener(e -> authenticateUser(false));
        registerButton.addActionListener(e -> authenticateUser(true));

        loginFrame.add(usernameLabel);
        loginFrame.add(usernameField);
        loginFrame.add(passwordLabel);
        loginFrame.add(passwordField);
        loginFrame.add(loginButton);
        loginFrame.add(registerButton);

        loginFrame.setVisible(true);
    }

    private void authenticateUser(boolean isRegistering) {
        try {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(loginFrame, "Please fill in all fields.");
                return;
            }

            socket = new Socket("localhost", 12345);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            if (isRegistering) {
                out.println("REGISTER " + username + " " + password);
            } else {
                out.println("LOGIN " + username + " " + password);
            }

            String response = in.readLine();
            if ("SUCCESS".equals(response)) {
                this.username = username;
                loginFrame.dispose();
                setupChatUI();
            } else {
                JOptionPane.showMessageDialog(loginFrame, "Authentication failed: " + response);
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(loginFrame, "Error connecting to server.");
        }
    }

    private void setupChatUI() {
        chatFrame = new JFrame("Chat - " + username);
        chatFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        chatFrame.setSize(600, 400);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BorderLayout());

        messageField = new JTextField();
        sendButton = new JButton("Send");
        deleteButton = new JButton("Delete");
        editButton = new JButton("Edit");

        sendButton.addActionListener(e -> sendMessage());
        deleteButton.addActionListener(e -> deleteMessage());
        editButton.addActionListener(e -> editMessage());

        inputPanel.add(messageField, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(sendButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(editButton);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        chatFrame.add(scrollPane, BorderLayout.CENTER);
        chatFrame.add(inputPanel, BorderLayout.SOUTH);

        chatFrame.setVisible(true);

        new Thread(this::receiveMessages).start();
    }

    private void sendMessage() {
        String message = messageField.getText();
        if (!message.isEmpty()) {
            out.println("MESSAGE " + username + " " + message);
            messageField.setText("");
        }
    }

    private void deleteMessage() {
        String messageId = JOptionPane.showInputDialog(chatFrame, "Enter message ID to delete:");
        if (messageId != null && !messageId.isEmpty()) {
            out.println("DELETE " + username + " " + messageId);
        }
    }

    private void editMessage() {
        String messageId = JOptionPane.showInputDialog(chatFrame, "Enter message ID to edit:");
        if (messageId != null && !messageId.isEmpty()) {
            String newMessage = JOptionPane.showInputDialog(chatFrame, "Enter new message:");
            if (newMessage != null && !newMessage.isEmpty()) {
                out.println("EDIT " + username + " " + messageId + " " + newMessage);
            }
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                chatArea.append(message + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClient::new);
    }
}
