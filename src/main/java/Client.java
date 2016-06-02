import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.commons.cli.*;

public class Client {

    private static final long DATAGRAMS_PER_SECOND = 200000;
    private static final long NANOSECONDS_PER_DATAGRAM = (long) (1000000000 / (double)DATAGRAMS_PER_SECOND);
    private static final boolean SHOULD_SLEEP = false;

    private static Selector selector;
    private static ByteBuffer buf;
    private static boolean isRunning = true;
    private static String serverAddress;
    private static int serverPort = Constants.SERVER_PORT;
    private static SocketAddress serverSocket;
    private static DatagramChannel channel;
    private static int printHeaderCounter = 0;
    private static long lastStats = System.currentTimeMillis() - 1000;
    private static int nSent = 0, nReceived = 0, nSendErrors = 0;
    private static int nTotalSent = 0, nTotalReceived = 0;
    private static long testStart, totalTestDurationMs = 0;
    private static int datagramSize;
    private static byte[] datagramWithZeroes;

    private static void setup() throws java.io.IOException {

        datagramWithZeroes = new byte[datagramSize];

        buf = ByteBuffer.allocate(Constants.BUFFER_SIZE);
        buf.clear();
        Arrays.fill(datagramWithZeroes, (byte) 0);

        selector = Selector.open();

        channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);

        serverSocket = new InetSocketAddress(serverAddress, serverPort);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                isRunning = false;
            }
        });

        testStart = System.currentTimeMillis();
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (java.lang.InterruptedException error) {
            System.err.println(error.toString());
        }
    }

    private static void sleepNano(long ns) {
        long start = System.nanoTime();
        while (start + ns >= System.nanoTime());
    }

    private static void finalStats() {
        System.out.println();
        totalTestDurationMs = System.currentTimeMillis() - testStart;
        System.out.println("Datagram size: " + datagramSize);
        System.out.printf("Total run time: %1$.1f seconds\n", totalTestDurationMs / 1000d);
        System.out.printf("Total datagrams sent: %1$d (avg. %2$dk per second)\n", nTotalSent, nTotalSent / totalTestDurationMs);
        System.out.printf("Total datagrams recv: %1$d (avg. %2$dk per second)\n", nTotalReceived, nTotalReceived / totalTestDurationMs);
        if ((nTotalSent - nTotalReceived) >= 1000) {
            System.out.printf("Total lost datagrams: %1$dk (%2$.2f%%)\n", (nTotalSent - nTotalReceived) / 1000, 100 * (nTotalSent - nTotalReceived) / (double) nTotalSent);
        } else {
            System.out.printf("Total lost datagrams: %1$d (%2$.2f%%)\n", nTotalSent - nTotalReceived, 100 * (nTotalSent - nTotalReceived) / (double) nTotalSent);
        }
    }

    private static void stats() {

        if (--printHeaderCounter <= 0) {
            printHeaderCounter = Constants.HEADER_PERIOD_LINES;

            System.out.printf("+------------------+------------------+------------------+------------------+------------------+------------------+\n");
            System.out.printf("| %1$16s | %2$16s | %3$16s | %4$16s | %5$16s | %6$16s |\n", "sent / s", "received / s", "us / sent", "balance", "% lost", "errors");
            System.out.printf("+------------------+------------------+------------------+------------------+------------------+------------------+\n");
        }

        System.out.printf("| %1$15dk | %2$15dk | %3$16.2f | %4$16d | %5$16.2f | %6$16d |\n",
                nSent / 1000,
                nReceived / 1000,
                1000000 / (double)nSent,
                nReceived - nSent,
                100.0 * (1.0 - nReceived / (float)nSent),
                nSendErrors
        );

        nSent = nReceived = nSendErrors = 0;
    }

    private static void processDatagram(DatagramChannel channel) throws java.io.IOException {
        buf.clear();
        channel.receive(buf);

        nReceived++;
        nTotalReceived++;
    }

    private static void receive() throws java.io.IOException {
        if (selector.selectNow() > 0) {

            Iterator<SelectionKey> keyIter = selector.selectedKeys().iterator();
            while (keyIter.hasNext()) {

                SelectionKey key = keyIter.next();

                if (key.isReadable()) {
                    processDatagram((DatagramChannel) key.channel());
                } else {
                    System.err.println("Unexpected operation");
                }

                keyIter.remove();
                break;
            }
        }
    }

    private static void send() throws java.io.IOException {
        int bytesSent;

        buf.clear();
        buf.put(datagramWithZeroes);
        buf.flip();
        bytesSent = channel.send(buf, serverSocket);

        if (bytesSent != datagramSize) {
            nSendErrors++;
        } else {
            nSent++;
            nTotalSent++;
        }
    }

    private static void parseCommandLine(String args[]) {
        Options options = new Options();
        options.addOption("t", true, "duration of test, in seconds (default: infinity)");
        options.addOption("s", true, "datagram size, in bytes (default: 8)");
        options.addOption("p", true, "server port (default: 31337)");
        options.addOption("a", true, "server address (default: localhost)");

        try {
            CommandLine cmd = (new DefaultParser()).parse(options, args);

            totalTestDurationMs = 1000 * Integer.parseInt(cmd.getOptionValue("t", "0"));
            datagramSize = cmd.hasOption("s") ? Integer.parseInt(cmd.getOptionValue("s")) : Constants.DATAGRAM_SIZE;
            serverAddress = cmd.getOptionValue("a", Constants.SERVER_ADDRESS);
            serverPort = cmd.hasOption("p") ? Integer.parseInt(cmd.getOptionValue("p")) : Constants.SERVER_PORT;

        } catch (ParseException e) {
            System.err.println(e.getMessage());

            (new HelpFormatter()).printHelp("Client", options);

            System.exit(0);
        }
    }

    public static void main(String args[]) {
        long now, curNano, prevNano;

        parseCommandLine(args);

        if (SHOULD_SLEEP) {
            prevNano = curNano = System.nanoTime();
        }

        try {
            setup();

            while (isRunning) {

                send();
                receive();

                now = System.currentTimeMillis();
                if (now > lastStats + Constants.STATS_PERIOD_MS) {
                    lastStats = now;
                    stats();
                }
                if (totalTestDurationMs != 0 && (now - testStart) > totalTestDurationMs) {
                    isRunning = false;
                }

                if (SHOULD_SLEEP) {
                    sleepNano(NANOSECONDS_PER_DATAGRAM - (curNano - prevNano));
                    prevNano = curNano;
                    curNano = System.nanoTime();
                }
            }

            finalStats();

        } catch (java.io.IOException error) {
            System.err.println(error.toString());
        }
    }
}
