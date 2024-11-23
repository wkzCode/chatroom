import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.sql.*;

public class ChatClient {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginRegisterWindow());
    }
}

// 登录/注册窗口类
class LoginRegisterWindow extends JFrame {
    private JTextField nicknameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerButton;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public LoginRegisterWindow() {
        try {
            socket = new Socket("192.168.251.154", 12346);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        setTitle("用户登录/注册");
        setSize(300, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(3, 2, 10, 10));

        // 界面组件
        panel.add(new JLabel("昵称:"));
        nicknameField = new JTextField();
        panel.add(nicknameField);

        panel.add(new JLabel("密码:"));
        passwordField = new JPasswordField();
        panel.add(passwordField);

        loginButton = new JButton("登录");
        registerButton = new JButton("注册");

        panel.add(loginButton);
        panel.add(registerButton);

        add(panel);
        setVisible(true);

        // 按钮事件监听
        registerButton.addActionListener(e -> handleRegister());
        loginButton.addActionListener(e -> handleLogin());
    }

    // 处理登录
    private void handleLogin() {
        String nickname = nicknameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        if (nickname.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "昵称或密码不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 验证登录信息
        if (authenticateUser(socket, nickname, password)) {
            JOptionPane.showMessageDialog(this, "登录成功！");
            dispose();
            try {
                socket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            new ChatWindow(nickname);
        } else {
            JOptionPane.showMessageDialog(this, "登录失败：昵称或密码错误！", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    // 处理注册
    private void handleRegister() {
        String nickname = nicknameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        if (nickname.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "昵称或密码不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 插入新用户信息
        if (registerUser(socket, nickname, password)) {
            JOptionPane.showMessageDialog(this, "注册成功，请登录！");
            try {
                Thread.currentThread().sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, "注册失败", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    // 用户登录
    private boolean authenticateUser(Socket socket, String nickname, String password) {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        out.println(nickname);
        out.println(password);
        out.println("login");
        boolean b;
        try {
            b = Boolean.parseBoolean(in.readLine().trim());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return b;
    }

    // 用户注册
    private boolean registerUser(Socket socket, String nickname, String password) {
        boolean b;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        out.println(nickname);
        out.println(password);
        out.println("register");
        try {
            b = Boolean.parseBoolean(in.readLine().trim());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return b;
    }
}

// 聊天窗口类
class ChatWindow extends JFrame {
    private JFrame frame;
    private JTextField nicknameField;
    private JButton connectButton;
    private JButton disconnectButton;
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JList<String> onlineList;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ChatWindow(String nickname) {
        setupUI(nickname);
    }

    private void setupUI(String nickname) {
        frame = new JFrame("Chat Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        frame.setLayout(new BorderLayout());

        // 顶部区域：昵称输入与连接按钮
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("昵称：" + nickname));
        connectButton = new JButton("连接");
        disconnectButton = new JButton("断开连接");
        disconnectButton.setEnabled(false);
        topPanel.add(connectButton);
        topPanel.add(disconnectButton);

        frame.add(topPanel, BorderLayout.NORTH);

        // 中部区域：聊天显示和在线人员列表
        JPanel centerPanel = new JPanel(new GridLayout(1, 2));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        centerPanel.add(chatScrollPane);

        onlineList = new JList<>();
        JScrollPane onlineScrollPane = new JScrollPane(onlineList);
        centerPanel.add(onlineScrollPane);

        frame.add(centerPanel, BorderLayout.CENTER);

        // 底部区域：输入框与发送按钮
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JLabel("聊天信息："), BorderLayout.WEST);
        messageField = new JTextField();
        bottomPanel.add(messageField, BorderLayout.CENTER);
        sendButton = new JButton("发送");
        sendButton.setEnabled(false);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        frame.add(bottomPanel, BorderLayout.SOUTH);

        // 按钮功能绑定
        connectButton.addActionListener(e -> connectToServer(nickname));//
        disconnectButton.addActionListener(e -> disconnectFromServer());
        sendButton.addActionListener(e -> sendMessage());

        frame.setVisible(true);
    }

    private void connectToServer(String nickname) {
        try {
            socket = new Socket("192.168.251.154", 12345);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println(nickname); // 发送昵称到服务器

            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
            sendButton.setEnabled(true);

            chatArea.append("连接到服务器成功！\n");

            // 开启新线程读取服务器消息
            new Thread(this::readMessages).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "无法连接到服务器！");
        }
    }

    private void disconnectFromServer() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            connectButton.setEnabled(true);
            disconnectButton.setEnabled(false);
            sendButton.setEnabled(false);
            chatArea.append("已断开连接。\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && out != null) {
            out.println(message); // 发送消息到服务器
            messageField.setText("");
        }
    }

    private void readMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("在线用户：")) {
                    // 更新在线列表
                    String[] users = message.substring(5).split(", ");
                    onlineList.setListData(users);
                } else {
                    chatArea.append(message + "\n");
                }
            }
        } catch (IOException e) {
            chatArea.append("与服务器的连接已断开。\n");
        }
    }
}
