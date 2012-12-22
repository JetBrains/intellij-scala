package org.jetbrains.plugins.scala.nailgun;

import com.martiansoftware.nailgun.Alias;
import com.martiansoftware.nailgun.NGServer;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author Pavel Fatin
 */
public class NailgunRunner {
  private static final String SERVER_ALIAS = "compile-server";
  private static final String SERVER_DESCRIPTION = "Scala compile server";
  private static final String SERVER_CLASS_NAME = "org.jetbrains.jps.incremental.scala.remote.Main";

  public static void main(String[] args) throws UnknownHostException, ClassNotFoundException {
    if (args.length != 1) throw new IllegalArgumentException("Usage: NailgunRunner [port]");

    InetAddress address = InetAddress.getByName(null);
    int port = Integer.parseInt(args[0]);

    NGServer server = createServer(address, port);

    Thread thread = new Thread(server);
    thread.setName("NGServer(" + address.toString() + ", " + port + ")");
    thread.start();

    Runtime.getRuntime().addShutdownHook(new ShutdownHook(server));
  }

  private static NGServer createServer(InetAddress address, int port) throws ClassNotFoundException {
    NGServer server = new NGServer(address, port);

    server.setAllowNailsByClassName(false);

    Class serverClass = Class.forName(SERVER_CLASS_NAME);
    server.getAliasManager().addAlias(new Alias(SERVER_ALIAS, SERVER_DESCRIPTION, serverClass));

    return server;
  }

  private static class ShutdownHook extends Thread {
    public static final int TIMEOUT = 30;

    private NGServer myServer = null;

    ShutdownHook(NGServer server) {
      myServer = server;
    }

    public void run() {
      myServer.shutdown(false);

      for (int i = 0; i < TIMEOUT; i++) {
        if (!myServer.isRunning()) break;

        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          // do nothing
        }
      }
    }
  }
}
