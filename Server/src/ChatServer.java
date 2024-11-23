import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;

public class ChatServer {
    private static final int PORT = 12345; // 服务器端口号
    private static Map<String, PrintWriter> userWriters = new ConcurrentHashMap<>(); // 线程安全的用户列表
    private static DefaultListModel<String> clientListModel = new DefaultListModel<>(); // 在线用户的列表模型
    private static JLabel onlineCountLabel; // 显示在线人数的标签

    public static void main(String[] args) {
        // 初始化服务器界面
        JFrame frame = new JFrame("服务器端");
        frame.setSize(400, 300);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 创建主面板
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // 创建显示在线用户的列表
        JList<String> clientList = new JList<>(clientListModel);
        JScrollPane scrollPane = new JScrollPane(clientList);
        panel.add(scrollPane, BorderLayout.CENTER);

        // 创建显示在线人数的标签
        onlineCountLabel = new JLabel("目前有 0 个客户端在线");
        onlineCountLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(onlineCountLabel, BorderLayout.SOUTH);

        frame.add(panel);
        frame.setVisible(true);

        // 启动服务器线程
        new Thread(() -> startServer()).start();
    }

    // 启动服务器
    private static void startServer() {
        System.out.println("服务器已启动，等待客户端连接...");
        try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))) {
            while (true) {
                Socket socket = serverSocket.accept();
                String clientAddress = socket.getInetAddress().getHostAddress();
                System.out.println("新客户端连接：" + clientAddress);

                // 在 GUI 中添加客户端 IP
                SwingUtilities.invokeLater(() -> {
                    clientListModel.addElement(clientAddress);
                    updateOnlineCount();
                });

                new ClientHandler(socket, clientAddress).start(); // 启动客户端处理线程
            }
        } catch (IOException e) {
            System.err.println("服务器异常：" + e.getMessage());
        }
    }

    // 更新在线人数显示
    private static void updateOnlineCount() {
        onlineCountLabel.setText("目前有 " + clientListModel.size() + " 个客户端在线");
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
                // 检查用户名是否重复
                synchronized (userWriters) {
                    if (nickname == null || userWriters.containsKey(nickname)) {
                        out.println("用户名无效或已被占用！");
                        socket.close();
                        return;
                    }
                    userWriters.put(nickname, out);
                }
                try {
                    // 广播用户加入消息
                    broadcast(nickname + " (" + clientAddress + ") 加入了聊天室！");
                    updateOnlineUsers();
                    System.out.println("当前在线用户数：" + userWriters.size());

                    // 监听并处理客户端消息
                    String message;
                    while ((message = in.readLine()) != null) {
                        broadcast(nickname + ": " + message);
                    }
                } catch (IOException e) {
                    System.out.println("客户端 " + clientAddress + " 连接中断。");
                } finally {
                    // 客户端断开连接
                    synchronized (userWriters) {
                        userWriters.values().remove(out); // 移除该客户端的输出流
                    }
                    SwingUtilities.invokeLater(() -> {
                        clientListModel.removeElement(clientAddress);
                        updateOnlineCount();
                    });
                    broadcast(clientAddress + " 已退出聊天室！");
                    updateOnlineUsers();
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }finally {
                        // 客户端断开连接
                        synchronized (userWriters) {
                            userWriters.values().remove(out); // 移除该客户端的输出流
                        }
                        SwingUtilities.invokeLater(() -> {
                            clientListModel.removeElement(clientAddress);
                            updateOnlineCount();
                        });
                        broadcast(clientAddress + " 已退出聊天室！");
                        updateOnlineUsers();
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // 广播消息给所有在线用户
        private void broadcast(String message) {
            synchronized (userWriters) {
                for (PrintWriter writer : userWriters.values()) {
                    writer.println(message);
                }
            }
        }

        // 更新在线用户列表
        private void updateOnlineUsers() {
            String userList = "在线用户：" + String.join(", ", userWriters.keySet());
            broadcast(userList); // 广播在线用户列表给所有客户端
        }
    }
}