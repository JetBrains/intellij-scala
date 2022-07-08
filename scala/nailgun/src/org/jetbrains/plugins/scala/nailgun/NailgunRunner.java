package org.jetbrains.plugins.scala.nailgun;

import com.facebook.nailgun.Alias;
import com.facebook.nailgun.NGConstants;
import com.facebook.nailgun.NGServer;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

/**
 * used from {@link org.jetbrains.plugins.scala.compiler.CompileServerLauncher}
 */
@SuppressWarnings("JavadocReference")
public class NailgunRunner {

  /** NOTE: set of commands should be equal to the commands from {@link org.jetbrains.jps.incremental.scala.remote.CommandIds} */
  private static final String[] COMMANDS = {
          "compile",
          "compile-jps",
          "get-metrics",
          "start-metering",
          "end-metering"
  };
  private static final String SERVER_DESCRIPTION = "Scala compile server";

  private static final String STOP_ALIAS_START = "stop_";
  private static final String STOP_CLASS_NAME = "com.facebook.nailgun.builtins.NGStop";

  /**
   * An alternative to default nailgun main {@link com.facebook.nailgun.NGServer#main(java.lang.String[])}
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 4)
      throw new IllegalArgumentException("Usage: NailgunRunner [port] [id] [classpath] [system-dir-path]");

    int port = Integer.parseInt(args[0]);
    String id = args[1];
    String classpath = args[2];
    Path scalaCompileServerSystemDir = Paths.get(args[3]);

    URLClassLoader classLoader = constructClassLoader(classpath);

    TokensGenerator.generateAndWriteTokenFor(scalaCompileServerSystemDir, port);

    InetAddress address = InetAddress.getByName(null);
    NGServer server = createServer(address, port, id, scalaCompileServerSystemDir, classLoader);

    Thread thread = new Thread(server);
    thread.setName("NGServer(" + address.toString() + ", " + port + "," + id + ")");
    thread.setContextClassLoader(classLoader);
    thread.start();

    Runtime.getRuntime().addShutdownHook(new ShutdownHook(server, scalaCompileServerSystemDir));
  }

  /**
   * Extra class loader is required due to several reasons:
   * <p>
   * 1. Dotty compiler interfaces (used inside Main during compilation)
   * casts classloader to a URLClassloader, and in JRE 11 AppClassLoader is not an instance of URLClassloader.<br>
   * <p>
   * 2. In order to run REPL instances (aka iLoopWrapper) (for Worksheet in REPL mode) in compiler server process.
   * REPL instances can use arbitrary scala versions in runtime. However, Compile Server uses fixed scala version.
   * In order to interact with REPL in compile server `repl-interface` is used. It's written in java to avoid any
   * scala binary incompatibility errors at runtime.
   * <p>
   * Final classloader hierarchy looks like this
   * <pre>
   *         [PlatformClassLoader]
   *                   |
   *            [AppClassLoader] (pure java)
   *                   |
   *            [repl-interface] (pure java)
   *            /      |       \
   *           /       |    [JPS & Compiler Server jars] (scala version A)
   *          /        |
   *         /  [REPL instance 1] (scala version B)
   *        /
   *   [REPL instance 2] (scala version C)
   * </pre>
   * Where:<br>
   * [PlatformClassLoader] - contains all jvm classes<br>
   * [AppClassLoader] - contains jars required to run Nailgun itself (scala-nailgun-runner, nailgun.jar)<br>
   * [repl-interface] - contains jars through which repl instances interact with main CompileServer<br>
   * [JPS & Compiler Server jars] - contains main compiler server jars with fixed scala version (2.13.2 at this moment)<br>
   * [REPL instance N] - classloaders created for each REPL instance with arbitrary scala version
   *
   * @see org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapperFactoryHandler#createClassLoader(org.jetbrains.plugins.scala.compiler.data.CompilerJars)
   */
  public static URLClassLoader constructClassLoader(String classpath) {
    Function<String, URL> pathToUrl = path -> {
      try {
        return new File(path).toURI().toURL();
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    };

    URL[] urls = Stream.of(classpath.split(File.pathSeparator))
            .map(pathToUrl)
            .toArray(URL[]::new);

    //noinspection Convert2MethodRef
    URL[] replInterfaceUrls = Arrays.stream(urls).filter(it -> isReplInterfaceJar(it)).toArray(URL[]::new);
    if (replInterfaceUrls.length == 0) {
      throw new IllegalStateException("repl interface jar not found");
    }
    URL[] otherUrls = Arrays.stream(urls).filter(it -> !isReplInterfaceJar(it)).toArray(URL[]::new);

    URLClassLoader replLoader = new URLClassLoader(replInterfaceUrls, NailgunRunner.class.getClassLoader());
    return new URLClassLoader(otherUrls, replLoader);
  }

  private static boolean isReplInterfaceJar(URL url) {
    String urlString = url.toString();
    return urlString.contains("repl-interface.jar");
  }

  private static NGServer createServer(InetAddress address, int port, String id, Path scalaCompileServerSystemDir, URLClassLoader classLoader)
          throws Exception {

    NGServer server = new NGServer(
            address,
            port,
            // explicitly pass default argument values to remind their existence
            NGServer.DEFAULT_SESSIONPOOLSIZE,
            NGConstants.HEARTBEAT_TIMEOUT_MILLIS
    );

    server.setAllowNailsByClassName(false);

    Class<?> mainNailClass = Utils.loadAndSetupServerMainNailClass(classLoader, scalaCompileServerSystemDir);
    Utils.setupServerShutdownTimer(mainNailClass, server);
    for (String command : COMMANDS) {
      server.getAliasManager().addAlias(new Alias(command, SERVER_DESCRIPTION, mainNailClass));
    }

    // TODO: token should be checked
    Class<?> stopClass = classLoader.loadClass(STOP_CLASS_NAME);
    String stopAlias = STOP_ALIAS_START + id;
    server.getAliasManager().addAlias(new Alias(stopAlias, "", stopClass));

    return server;
  }

  private static class ShutdownHook extends Thread {
    private static final int WAIT_FOR_SERVER_TERMINATION_TIMEOUT_MS = 3000;

    private final NGServer myServer;
    private final Path scalaCompileServerSystemDir;

    ShutdownHook(NGServer server, Path scalaCompileServerSystemDir) {
      myServer = server;
      this.scalaCompileServerSystemDir = scalaCompileServerSystemDir;
    }

    @Override
    public void run() {
      TokensGenerator.deleteTokenFor(scalaCompileServerSystemDir, myServer.getPort());

      myServer.shutdown();

      long waitStart = System.currentTimeMillis();
      while (System.currentTimeMillis() - waitStart < WAIT_FOR_SERVER_TERMINATION_TIMEOUT_MS) {
        if (!myServer.isRunning())
          break;

        try {
          //noinspection BusyWait
          Thread.sleep(100);
        } catch (InterruptedException e) {
          // do nothing
        }
      }

      // copied from com.facebook.nailgun.NGServer.NGServerShutdowner
      if (myServer.isRunning()) {
        System.err.println("Unable to cleanly shutdown server.  Exiting JVM Anyway.");
      } else {
        System.out.println("NGServer shut down.");
      }
      System.err.flush();
      System.out.flush();
    }
  }

  private static class TokensGenerator {

    static void generateAndWriteTokenFor(Path scalaCompileServerSystemDir, int port) throws IOException {
      Path path = tokenPathFor(scalaCompileServerSystemDir, port);
      writeTokenTo(path, UUID.randomUUID());
    }

    /** duplicated in {@link org.jetbrains.plugins.scala.server.CompileServerToken} */
    static Path tokenPathFor(Path scalaCompileServerSystemDir, int port) {
      return scalaCompileServerSystemDir.resolve("tokens").resolve(Integer.toString(port));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void writeTokenTo(Path path, UUID uuid) throws IOException {
      File directory = path.getParent().toFile();

      if (!directory.exists()) {
        if (!directory.mkdirs()) {
          throw new IOException("Cannot create directory: " + directory);
        }
      }

      final boolean isPosix = path.getFileSystem().supportedFileAttributeViews().contains("posix");

      if (isPosix) {
        Files.createFile(path, PosixFilePermissions.asFileAttribute(
                new HashSet<>(asList(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        ));
      } else {
        // Windows
        final File file = path.toFile();
        file.createNewFile();
        file.setExecutable(false);
        file.setReadable(/* readable */ true, /* ownerOnly */ true);
        file.setWritable(/* writable */ true, /* ownerOnly */ true);
      }

      Files.write(path, uuid.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static void deleteTokenFor(Path scalaCompileServerSystemDir, int port) {
      File tokenFile = tokenPathFor(scalaCompileServerSystemDir, port).toFile();
      if (!tokenFile.delete()) {
        tokenFile.deleteOnExit();
      }
    }
  }
}
