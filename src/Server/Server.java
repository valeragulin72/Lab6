package Server;

import Client.Interpretator;
import Client.Client;
import Xml.Xml;
import Commands.*;
import Movie.*;
import Interaction.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Objects;
import java.util.logging.Logger;

public class Server {

    private static final UserInteraction interaction = new ConsoleInteraction();
    private static Hashtable<String, Movie> collection = new Hashtable<>();
    public static File file;
    private static DatagramChannel serverSocket;
    public static final int port = 7182;
    private static LocalDateTime creationDate;
    private static LocalDateTime initDate;
    private static final Logger log = Logger.getLogger(Client.class.getName());
    private static SocketAddress remoteAddr;
    private static byte[] buffBytes;

    public static void main(String[] args) throws Exception {
        if (Arrays.stream(args).count() != 0) {
            String fileName = args[0];
            if (fileName != null) {
                file = new File(fileName);
                log.info("File found!\n\nLaunch preparation.");
                if (!prepare()) {
                    log.info("Launch stop.");
                    return;
                }
            } else {
                interaction.print(true, "File not found or incorrect input.");
            }
        } else {
            interaction.print(true, "File not found or incorrect input.");
        }

        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    FileWriter fileWriter = new FileWriter(file);
                    fileWriter.write(Xml.toXml(new HashtableInfo(collection, creationDate)));
                    fileWriter.flush();
                } catch (Exception e) {
                    log.info(e.getMessage());
                }
                log.info("Server stop.");
            }));
        } catch (Exception e) {
            log.info("Failed to set exit condition.");
            return;
        }


        buffBytes = new byte[2000000];
        SocketAddress addr;

        try {
            addr = new InetSocketAddress("localhost", port);
            serverSocket = DatagramChannel.open();
            serverSocket.configureBlocking(false);
            serverSocket.bind(addr);
        } catch (IOException e) {
            log.info(String.format("Unable to start server (%s)%n", e.getMessage()));
            return;
        }

        log.info("Server launched at: " + addr);
        while (true) {
            ByteBuffer buff = ByteBuffer.wrap(buffBytes);
            try {
                do {
                    remoteAddr = serverSocket.receive(buff);
                } while (remoteAddr == null);

                log.info(String.format("Client %s:%s connected!",remoteAddr,serverSocket.getLocalAddress()));

                Command command = (Command) Interpretator.receiver(buffBytes);;
                log.info("Entered command " + command);
                Message message;
                if (command instanceof Date) {
                    message = ((Date) command).execute(collection, initDate);
                } else {
                    message = command.execute(collection);
                }
                buff.clear();
                byte[] answer = Interpretator.sender(message);
                buff = ByteBuffer.wrap(answer);
                serverSocket.send(buff, remoteAddr);
                buff.clear();

            } catch (IOException | ClassNotFoundException e) {
                log.info("Connection lost! ");
            }
        }
    }

    private static void uploadInformation() throws FileNotFoundException, IllegalAccessException, NoSuchFieldException {
        log.info("Uploading file " + file);
        HashtableInfo hashtableInfo = Xml.fromXml(file);
        collection = Objects.requireNonNull(hashtableInfo).getCollection();
        creationDate = hashtableInfo.getCreationDate();
        log.info("Collection upload successfully!\n");
    }

    private static boolean prepare() {
        try {
            uploadInformation();
        } catch (FileNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            if (e instanceof NoSuchFieldException || e instanceof IllegalAccessException || e instanceof NullPointerException) {
                log.info("Problems processing the file. Data not read. We create a new file.");
            }
            initDate = LocalDateTime.now();
            FileWriter fileWriter;
            try {
                fileWriter = new FileWriter(file);
                fileWriter.close();
            } catch (IOException ex) {
                log.info("The file could not be created, there are insufficient permissions, or the format of the file name is incorrect.");
                log.info("Error message: " + ex.getMessage());
                return false;
            }
        }
        return true;
    }
}
