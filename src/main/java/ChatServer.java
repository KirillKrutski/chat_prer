import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int PORT = 12345;
    private static Connection connection;
    private static ConcurrentHashMap<String, PrintWriter> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Chat server started on port " + PORT);
            setupDatabase();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void setupDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/chat", "useradmin", "admin");
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (username VARCHAR(50) PRIMARY KEY, password VARCHAR(50))");
                stmt.execute("CREATE TABLE IF NOT EXISTS messages (id SERIAL PRIMARY KEY, username VARCHAR(50), message TEXT)");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                while (true) {
                    String line = in.readLine();
                    if (line == null) {
                        return;
                    }

                    String[] tokens = line.split(" ", 3);
                    if (tokens[0].equals("REGISTER")) {
                        registerUser(tokens[1], tokens[2]);
                    } else if (tokens[0].equals("LOGIN")) {
                        if (authenticateUser(tokens[1], tokens[2])) {
                            this.username = tokens[1];
                            clients.put(username, out);
                            out.println("SUCCESS");
                            break;
                        } else {
                            out.println("FAILED");
                        }
                    }
                }

                String message;
                while ((message = in.readLine()) != null) {
                    handleClientMessage(message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (username != null) {
                    clients.remove(username);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void registerUser(String username, String password) {
            try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO users (username, password) VALUES (?, ?)")) {
                stmt.setString(1, username);
                stmt.setString(2, password);
                stmt.executeUpdate();
                out.println("SUCCESS");
            } catch (SQLException e) {
                out.println("FAILED");
            }
        }

        private boolean authenticateUser(String username, String password) {
            try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM users WHERE username = ? AND password = ?")) {
                stmt.setString(1, username);
                stmt.setString(2, password);
                ResultSet rs = stmt.executeQuery();
                return rs.next();
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        private void handleClientMessage(String message) {
            String[] tokens = message.split(" ", 4);
            try {
                if (tokens[0].equals("MESSAGE")) {
                    saveMessage(tokens[1], tokens[2]);
                } else if (tokens[0].equals("DELETE")) {
                    updateMessage(tokens[1], Integer.parseInt(tokens[2]), "[deleted]");
                } else if (tokens[0].equals("EDIT")) {
                    if (tokens.length < 4) {
                        out.println("Error: Invalid command format. Usage: EDIT <username> <message_id> <new_message>");
                        return;
                    }
                    updateMessage(tokens[1], Integer.parseInt(tokens[2]), tokens[3]);
                }
            } catch (NumberFormatException e) {
                out.println("Error: Message ID must be a valid number.");
            }
        }


        private void broadcastMessage(String username, String message) {
            for (PrintWriter client : clients.values()) {
                client.println(username + ": " + message);
            }
        }

        private void saveMessage(String username, String message) {
            try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO messages (username, message) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, username);
                stmt.setString(2, message);
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    int messageId = rs.getInt(1);
                    broadcastMessage(username, "[ID: " + messageId + "] " + message);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void updateMessage(String username, int id, String newMessage) {
            try (PreparedStatement checkStmt = connection.prepareStatement("SELECT username FROM messages WHERE id = ?")) {
                checkStmt.setInt(1, id);
                ResultSet rs = checkStmt.executeQuery();

                if (!rs.next()) {
                    out.println("Error: Message ID not found. Please enter a valid ID.");
                    return;
                }

                String owner = rs.getString("username");
                if (!owner.equals(username)) {
                    out.println("Error: You can only edit your own messages.");
                    return;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return;
            }

            try (PreparedStatement stmt = connection.prepareStatement("UPDATE messages SET message = ? WHERE id = ?")) {
                stmt.setString(1, newMessage);
                stmt.setInt(2, id);
                stmt.executeUpdate();

                broadcastMessage(username, newMessage.equals("[deleted]")
                        ? "Message ID [" + id + "] has been deleted."
                        : "Message ID [" + id + "] has been updated to: " + newMessage);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

    }
}
