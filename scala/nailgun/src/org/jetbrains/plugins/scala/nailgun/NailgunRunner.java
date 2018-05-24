package org.jetbrains.plugins.scala.nailgun;

import com.martiansoftware.nailgun.Alias;
import com.martiansoftware.nailgun.NGServer;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.UUID;

import static java.util.Arrays.asList;

/**
 * @author Pavel Fatin
 */
public class NailgunRunner {
  private static final String SERVER_ALIAS = "compile-server";
  private static final String SERVER_DESCRIPTION = "Scala compile server";
  private static final String SERVER_CLASS_NAME = "org.jetbrains.jps.incremental.scala.remote.Main";

  private static final String STOP_ALIAS_START = "stop_";
  private static final String STOP_CLASS_NAME = "com.martiansoftware.nailgun.builtins.NGStop";

  public static void main(String[] args) throws IOException, ClassNotFoundException {
    if (args.length != 2) throw new IllegalArgumentException("Usage: NailgunRunner [port] [id]");

    InetAddress address = InetAddress.getByName(null);
    int port = Integer.parseInt(args[0]);
    String id = args[1];

    writeTokenTo(tokenPathFor(port), UUID.randomUUID());

    NGServer server = createServer(address, port, id);

    Thread thread = new Thread(server);
    thread.setName("NGServer(" + address.toString() + ", " + port + "," + id + ")");
    thread.start();

    Runtime.getRuntime().addShutdownHook(new ShutdownHook(server));
  }

  private static Path tokenPathFor(int port) {
    return Paths.get(System.getProperty("user.home"), ".idea-build", "tokens", Integer.toString(port));
  }

  private static void writeTokenTo(Path path, UUID uuid) throws IOException {
    File directory = path.getParent().toFile();

    if (!directory.exists()) {
      if (!directory.mkdirs()) {
        throw new IOException("Cannot create directory: " + directory);
      }
    }

    Files.write(path, uuid.toString().getBytes());

    PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
    if (view != null) {
      try {
        view.setPermissions(new HashSet<>(asList(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
      } catch (IOException e) {
        System.err.println("Cannot set permissions: " + path);
      }
    }
  }

  private static NGServer createServer(InetAddress address, int port, String id)
      throws ClassNotFoundException {
    NGServer server = new NGServer(address, port);

    server.setAllowNailsByClassName(false);

    Class serverClass = Class.forName(SERVER_CLASS_NAME);
    server.getAliasManager().addAlias(new Alias(SERVER_ALIAS, SERVER_DESCRIPTION, serverClass));

    Class stopClass = Class.forName(STOP_CLASS_NAME);
    String stopAlias = STOP_ALIAS_START + id;
    server.getAliasManager().addAlias(new Alias(stopAlias, "", stopClass));

    return server;
  }

  private static class ShutdownHook extends Thread {
    static final int TIMEOUT = 30;

    private final NGServer myServer;

    ShutdownHook(NGServer server) {
      myServer = server;
    }

    public void run() {
      File tokenFile = tokenPathFor(myServer.getPort()).toFile();
      if (!tokenFile.delete()) {
        tokenFile.deleteOnExit();
      }

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
