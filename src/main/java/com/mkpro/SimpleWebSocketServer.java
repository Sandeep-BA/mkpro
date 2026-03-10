package com.mkpro;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleWebSocketServer extends Thread {
    private final int port;
    private final Set<Socket> clients = Collections.synchronizedSet(new HashSet<>());
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    public SimpleWebSocketServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("WebSocket Server started on port " + port);
            while (running) {
                Socket client = serverSocket.accept();
                new Thread(() -> handleClient(client)).start();
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("WebSocket Server Error: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket client) {
        try {
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            Scanner s = new Scanner(in, "UTF-8");
            
            String data = s.useDelimiter("\\r\\n\\r\\n").next();
            
            if (data.contains("GET") && data.contains("Sec-WebSocket-Key")) {
                java.util.regex.Matcher match = java.util.regex.Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
                if (match.find()) {
                    String key = match.group(1).trim();
                    String response = "HTTP/1.1 101 Switching Protocols\r\n"
                            + "Connection: Upgrade\r\n"
                            + "Upgrade: websocket\r\n"
                            + "Sec-WebSocket-Accept: "
                            + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")))
                            + "\r\n\r\n";
                    out.write(response.getBytes("UTF-8"));
                    out.flush();
                    
                    clients.add(client);
                    
                    // Keep connection open
                    int b;
                    while ((b = in.read()) != -1) {
                        // Ignore input for now
                    }
                }
            }
        } catch (Exception e) {
            // Expected disconnect
        } finally {
            clients.remove(client);
            try { client.close(); } catch (IOException e) {}
        }
    }

    public void broadcast(String message) {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        int len = payload.length;
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        
        frame.write(0x81); // Text frame
        
        if (len <= 125) {
            frame.write(len);
        } else if (len <= 65535) {
            frame.write(126);
            frame.write(len >> 8);
            frame.write(len & 0xFF);
        } else {
            frame.write(127);
            frame.write(0); frame.write(0); frame.write(0); frame.write(0);
            frame.write(len >> 24);
            frame.write((len >> 16) & 0xFF);
            frame.write((len >> 8) & 0xFF);
            frame.write(len & 0xFF);
        }
        
        try {
            frame.write(payload);
            byte[] packet = frame.toByteArray();
            
            synchronized (clients) {
                Iterator<Socket> it = clients.iterator();
                while (it.hasNext()) {
                    Socket client = it.next();
                    try {
                        OutputStream out = client.getOutputStream();
                        out.write(packet);
                        out.flush();
                    } catch (IOException e) {
                        it.remove();
                        try { client.close(); } catch (Exception ex) {}
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopServer() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) {}
    }
}
