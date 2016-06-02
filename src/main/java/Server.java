import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

import org.apache.commons.cli.*;

public class Server {

    private static final int SELECT_TIMEOUT = 1000;

    private static Selector selector;
    private static ByteBuffer buf;
    private static boolean isRunning = true;

    private static int nReceived = 0;
    private static int printHeaderCounter = 0;
    private static long lastStats = System.currentTimeMillis() - 1000;
    private static int serverPort = Constants.SERVER_PORT;
    private static int datagramSize;
    private static byte[] datagramWithZeroes;

    private static void setup() throws java.io.IOException {

        datagramWithZeroes = new byte[datagramSize];

        selector = Selector.open();
        buf = ByteBuffer.allocate(Constants.BUFFER_SIZE);

        DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
        channel.socket().bind(new InetSocketAddress(serverPort));

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                isRunning = false;
            }
        });
    }

    private static void stats() {

        if (--printHeaderCounter <= 0) {
            printHeaderCounter = Constants.HEADER_PERIOD_LINES;

            System.out.printf("+------------------+\n");
            System.out.printf("| %1$16s |\n", "Packets received");
            System.out.printf("+------------------+\n");
        }

        System.out.printf("| %1$16d |\n", nReceived);

        nReceived = 0;
    }

    private static void finalStats() {
        System.out.println();
        System.out.println("Datagram size: " + datagramSize);
    }

    private static void processDatagram(DatagramChannel channel) throws java.io.IOException {

        buf.clear();
        SocketAddress client = channel.receive(buf);

        buf.clear();
        buf.put(datagramWithZeroes);
        buf.flip();
        channel.send(buf, client);

        nReceived++;
    }

    private static void receive() throws java.io.IOException {

        if (selector.select(SELECT_TIMEOUT) == 0) return;

        Iterator<SelectionKey> keyIter = selector.selectedKeys().iterator();
        while (keyIter.hasNext()) {

            SelectionKey key = keyIter.next();

            if (key.isReadable()) {
                processDatagram((DatagramChannel) key.channel());
            } else {
                System.err.println("Unexpected operation");
            }

            keyIter.remove();
        }
    }

    private static void loop() throws java.io.IOException {
        long now;

        while (isRunning) {

                receive();

                now = System.currentTimeMillis();
                if (now > lastStats + 1000) {
                    lastStats = now;
                    stats();
                }
        }

        finalStats();
    }

    private static void parseCommandLine(String args[]) {
        Options options = new Options();
        options.addOption("s", true, "response datagram size, in bytes (default: 8)");
        options.addOption("p", true, "server port (default: 31337)");

        try {
            CommandLine cmd = (new DefaultParser()).parse(options, args);

            datagramSize = cmd.hasOption("s") ? Integer.parseInt(cmd.getOptionValue("s")) : Constants.DATAGRAM_SIZE;
            serverPort = cmd.hasOption("p") ? Integer.parseInt(cmd.getOptionValue("p")) : Constants.SERVER_PORT;

        } catch (ParseException e) {
            System.err.println(e.getMessage());

            (new HelpFormatter()).printHelp("Client", options);

            System.exit(0);
        }
    }

    public static void main(String[] args) {

        parseCommandLine(args);

        try {

            setup();
            System.out.println("Server is listening on port " + Constants.SERVER_PORT + ". Entering the main loop...");
            loop();

        } catch (java.io.IOException error) {
            System.err.println(error.toString());
        }
    }
}
