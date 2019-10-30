package org.jetbrains.jps.incremental.scala.local.worksheet.compatibility;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapper;
import org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapperReporter;
import org.jetbrains.jps.incremental.scala.local.worksheet.PrintWriterReporter;
import org.jetbrains.jps.incremental.scala.local.worksheet.WorksheetServer;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

//TODO: looks like we do not actually need this java wrapper any more, due to we use an isolated classLoader to instantiate ILoop
// remove it after plating around with worksheet after SCL-15948, SCL-15949 refactorings
/**
 * User: Dmitry.Naydanov
 * Date: 2018-10-30
 */
public class JavaILoopWrapperFactory {

  private static final String REPL_START                   = "$$worksheet$$repl$$start$$";
  private static final String REPL_CHUNK_START             = "$$worksheet$$repl$$chunk$$start$$";
  private static final String REPL_CHUNK_END               = "$$worksheet$$repl$$chunk$$end$$";
  private static final String REPL_CHUNK_COMPILATION_ERROR = "$$worksheet$$repl$$chunk$$compilation$$error$$";
  private static final String REPL_LAST_CHUNK_PROCESSED    = "$$worksheet$$repl$$last$$chunk$$processed$$";

  private static final String FALLBACK_CLASSNAME = "ILoopWrapperImpl";

  //maximum count of repl sessions handled at any time 
  private final static int REPL_SESSION_LIMIT = 5;
  private final static Map<String, Consumer<ILoopWrapper>> commands =
      Collections.singletonMap(":reset", ILoopWrapper::reset);

  private final MySimpleCache cache = new MySimpleCache(REPL_SESSION_LIMIT);

  //used in ILoopWrapperFactoryHandler
  public void loadReplWrapperAndRun(
      final List<String> worksheetArgs,
      final String nameForSt,
      final File library,
      final File compiler,
      final List<File> extra,
      final List<File> classpath,
      final OutputStream outStream,
      final File iLoopFile,
      final JavaClientProvider clientProvider,
      final ClassLoader classLoader
  ) {
    final WorksheetArgsJava argsJava = WorksheetArgsJava.constructArgsFrom(worksheetArgs, nameForSt, library, compiler, extra, classpath);
    final JavaClientProvider clientProviderNotNull = clientProvider != null ? clientProvider : JavaClientProvider.NO_OP_PROVIDER;
    loadReplWrapperAndRun(argsJava, outStream, iLoopFile, clientProviderNotNull, classLoader);
  }

  private void loadReplWrapperAndRun(final WorksheetArgsJava worksheetArgs,
                                     final OutputStream outStream,
                                     final File iLoopFile,
                                     @NotNull final JavaClientProvider clientProvider,
                                     final ClassLoader classLoader) {
    ReplArgsJava replArgs = worksheetArgs.getReplArgs();
    if (replArgs == null) return;

    clientProvider.onProgress("Retrieving REPL instance...");

    ILoopWrapper inst = cache.getOrCreate(
        replArgs.getSessionId(),
        () -> {
          PrintWriter printWriter = new WorksheetServer.MyUpdatePrintWriter(outStream);
          ILoopWrapperReporter reporter = new PrintWriterReporter(printWriter);
          return createILoopWrapper(worksheetArgs, iLoopFile, printWriter, reporter, classLoader);
        },
        ILoopWrapper::shutdown
    );
    if (inst == null) return;

    PrintWriter out = inst.getOutputWriter();
    if (out instanceof WorksheetServer.MyUpdatePrintWriter) {
      ((WorksheetServer.MyUpdatePrintWriter) out).updateOut(outStream);
    }

    clientProvider.onProgress("Worksheet execution started");
    printService(out, REPL_START);
    out.flush();

    String code = new String(Base64.getDecoder().decode(replArgs.getCodeChunk()), StandardCharsets.UTF_8);
    String[] statements = code.split(Pattern.quote("\n$\n$\n"));

    for (String statement : statements) {
      if (statement.startsWith(":")) {
        Consumer<ILoopWrapper> action = commands.get(statement);
        if (action != null) {
          action.accept(inst);
          continue;
        }
      }

      printService(out, REPL_CHUNK_START);
      boolean shouldContinue = statement.trim().length() == 0 || inst.processChunk(statement);
      clientProvider.onProgress("Executing worksheet..."); // TODO: add fraction
      if (shouldContinue) {
        printService(out, REPL_CHUNK_END);
      } else {
        printService(out, REPL_CHUNK_COMPILATION_ERROR);
        return;
      }
    }

    printService(out, REPL_LAST_CHUNK_PROCESSED);
  }

  private void printService(final PrintWriter out, final String txt) {
    out.println();
    out.println(txt);
    out.flush();
  }

  private ILoopWrapper createILoopWrapper(final WorksheetArgsJava worksheetArgs,
                                          final File iLoopFile,
                                          final PrintWriter out,
                                          final ILoopWrapperReporter reporter,
                                          final ClassLoader classLoader) {
    final URLClassLoader loader;
    final Class<?> clazz;

    try {
      final URL iLoopWrapperJar = iLoopFile.toURI().toURL();
      loader = new URLClassLoader(new URL[]{iLoopWrapperJar}, classLoader);

      int idxDot = iLoopFile.getName().lastIndexOf('.');
      int idxDash = iLoopFile.getName().lastIndexOf('-');
      String className = idxDot == -1 || idxDash == -1 || idxDash >= idxDot
              ? FALLBACK_CLASSNAME
              : iLoopFile.getName().substring(idxDash + 1, idxDot);

      clazz = loader.loadClass("org.jetbrains.jps.incremental.scala.local.worksheet." + className);
    } catch (MalformedURLException | ClassNotFoundException ignored) {
      return null;
    }

    List<File> classpath = new ArrayList<>();
    classpath.add(worksheetArgs.getCompLibrary());
    classpath.add(worksheetArgs.getCompiler());
    classpath.addAll(worksheetArgs.getCompExtra());
    classpath.addAll(worksheetArgs.getOutputDirs());
    classpath.addAll(worksheetArgs.getClasspathURLs().stream().map(toURI()).filter(Objects::nonNull).map(File::new).collect(Collectors.toList()));

    List<String> stringCp = classpath.stream()
            .filter(File::exists)
            .map(File::getAbsolutePath)
            .distinct()
            .sorted()
            .collect(Collectors.toList());

    try {
      Constructor<?> constructor = clazz.getConstructor(PrintWriter.class, ILoopWrapperReporter.class, List.class);
      ILoopWrapper inst = (ILoopWrapper) constructor.newInstance(out, reporter, stringCp);
      inst.init();
      return inst;
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      return null;
    }
  }

  @NotNull
  private Function<URL, URI> toURI() {
    return url -> {
      try {
        return url.toURI();
      } catch (URISyntaxException e) {
        return null;
      }
    };
  }

  private static class MySimpleCache {
    private final int limit;
    private final ReplSessionComparator comparator;
    private final PriorityQueue<ReplSession> sessionsQueue;

    MySimpleCache(final int limit) {
      this.limit = limit;
      this.comparator = new ReplSessionComparator();
      this.sessionsQueue = new PriorityQueue<>(limit);
    }

    ILoopWrapper getOrCreate(final String id,
                             final Supplier<ILoopWrapper> onCreation,
                             final Consumer<ILoopWrapper> onDiscard) {
      ReplSession existing = findById(id);

      if (existing != null) {
        comparator.inc(id);
        return existing.wrapper;
      }

      if (sessionsQueue.size() >= limit) {
        ReplSession anOldSession = sessionsQueue.poll();

        if (anOldSession != null) {
          onDiscard.accept(anOldSession.wrapper);
          comparator.remove(anOldSession.id);
        }
      }

      ReplSession newSession = new ReplSession(id, onCreation.get());

      comparator.put(id);
      sessionsQueue.offer(newSession);

      return newSession.wrapper;
    }

    private ReplSession findById(final String id) {
      if (id == null) return null;
      for (ReplSession s : sessionsQueue) {
        if (s != null && id.equals(s.id)) return s;
      }

      return null;
    }

    private class ReplSessionComparator implements Comparator<ReplSession> {
      private final HashMap<String, Integer> storage = new HashMap<>();

      private void inc(final String id) {
        storage.compute(id, (k, v) -> (v == null) ? null : v + 1);
      }

      private void dec(final String id) {
        storage.compute(id, (k, v) -> (v == null) ? null : v - 1);
      }

      private void put(final String id) {
        storage.put(id, 10);
      }

      private void remove(final String id) {
        storage.remove(id);
      }

      @Override
      public int compare(final ReplSession x, final ReplSession y) {
        if (x == null) {
          return y == null ? 0 : 1;
        }

        if (y == null) return -1;

        if (!storage.containsKey(x.id)) return 1;
        if (!storage.containsKey(y.id)) return -1;

        return (int) storage.get(y.id) - storage.get(x.id);
      }

      @Override
      public boolean equals(final Object obj) {
        return this == obj;
      }
    }

    private class ReplSession implements Comparable<ReplSession> {
      final String id;
      final ILoopWrapper wrapper;

      private ReplSession(final String id, final ILoopWrapper wrapper) {
        this.id = id;
        this.wrapper = wrapper;
      }

      @Override
      public int compareTo(@NotNull final ReplSession o) {
        return MySimpleCache.this.comparator.compare(this, o);
      }
    }
  }
}
