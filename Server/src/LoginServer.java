import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LoginServer {
    private static final int PORT = 12346; // 服务器端口号

    public static void main(String[] args) {
        // 启动服务器线程
        new Thread(() -> startServer()).start();
    }

    // 启动服务器
    private static void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))) {
            while (true) {
                Socket socket = serverSocket.accept();
                String clientAddress = socket.getInetAddress().getHostAddress();

                new ClientHandler(socket, clientAddress).start(); // 启动客户端处理线程
            }
        } catch (IOException e) {
            System.err.println("服务器异常：" + e.getMessage());
        }
    }

    // 客户端处理线程
    private static class ClientHandler extends Thread {
        private Socket socket;
        private String clientAddress;
        private BufferedReader in;
        private PrintWriter out;

        public ClientHandler(Socket socket, String clientAddress) {
            this.socket = socket;
            this.clientAddress = clientAddress;
        }

        @Override
        public void run() {
            try {
                String nickname;
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                nickname = in.readLine().trim();
                String password = in.readLine().trim();
                String activity = in.readLine().trim();

                if (activity.equals("login")) {
                    try (Connection connection = DatabaseConnection.getConnection();
                         PreparedStatement statement = connection.prepareStatement("SELECT * FROM users WHERE nickname = ? AND password = ?")) {
                        statement.setString(1, nickname);
                        statement.setString(2, password);
                        ResultSet resultSet = statement.executeQuery();
                        out.println(resultSet.next());
                    } catch (SQLException e) {
                        e.printStackTrace();
                        out.println(false);
                    }finally {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else if (activity.equals("register")) {
                    try (Connection connection = DatabaseConnection.getConnection();
                         PreparedStatement statement = connection.prepareStatement("INSERT INTO users (nickname, password) VALUES (?, ?)")) {
                        statement.setString(1, nickname);
                        statement.setString(2, password);
                        statement.executeUpdate();
                        out.println(true);
                    } catch (SQLException e) {
                        e.printStackTrace();
                        out.println(false);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

// 数据库连接类
class DatabaseConnection {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/chat_app";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "040924wkz";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}