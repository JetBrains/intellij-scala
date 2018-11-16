package org.jetbrains.jps.incremental.scala.local.worksheet.compatibility;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapper;
import org.jetbrains.jps.incremental.scala.local.worksheet.WorksheetServer;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * User: Dmitry.Naydanov
 * Date: 2018-10-30.
 */
public class JavaILoopWrapperFactory {
  private static final String REPL_START = "$$worksheet$$repl$$start$$";
  private static final String REPL_CHUNK_END = "$$worksheet$$repl$$chunk$$end$$";
  private static final String REPL_LAST_CHUNK_PROCESSED = "$$worksheet$$repl$$last$$chunk$$processed$$";
  
  private static final String FALLBACK_CLASSNAME = "ILoopWrapperImpl";

  //maximum count of repl sessions handled at any time 
  private final static int REPL_SESSION_LIMIT = 5;
  private final static Map<String, Consumer<ILoopWrapper>> commands = 
      Collections.singletonMap(":reset", ILoopWrapper::reset);
  
  private final MySimpleCache cache = new MySimpleCache(REPL_SESSION_LIMIT);
  
  
  public void loadReplWrapperAndRun(
      List<String> worksheetArgsString, String nameForSt, 
      File library, File compiler, List<File> extra, List<File> classpath,
      OutputStream outStream, File iLoopFile, Comparable<String> clientProvider
  ) {
    WorksheetArgsJava argsJava = 
        WorksheetArgsJava.constructArgsFrom(worksheetArgsString, nameForSt, library, compiler, extra, classpath);
    
    Consumer<String> onProgress = clientProvider == null ? msg -> {} : clientProvider::compareTo;

    loadReplWrapperAndRun(argsJava, outStream, iLoopFile, onProgress);
  }
  
  private void loadReplWrapperAndRun(WorksheetArgsJava worksheetArgs, OutputStream outStream, 
                                     File iLoopFile, Consumer<String> onProgress) {
    ReplArgsJava replArgs = worksheetArgs.getReplArgs();
    if (replArgs == null) return;

    onProgress.accept("Retrieving REPL instance...");
   
    ILoopWrapper inst = cache.getOrCreate(
        replArgs.getSessionId(),
        () -> createILoopWrapper(worksheetArgs, iLoopFile, new WorksheetServer.MyUpdatePrintWriter(outStream)),
        ILoopWrapper::shutdown
    );
    if (inst == null) return;
    
    PrintWriter out = inst.getOutputWriter();
    if (out instanceof WorksheetServer.MyUpdatePrintWriter) ((WorksheetServer.MyUpdatePrintWriter) out).updateOut(outStream);

    onProgress.accept("Worksheet execution started");
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
      
      boolean shouldContinue = statement.trim().length() == 0 || inst.processChunk(statement);
      onProgress.accept("Executing worksheet...");
      printService(out, REPL_CHUNK_END);
      if (!shouldContinue) return;
    }

    printService(out, REPL_LAST_CHUNK_PROCESSED);
  }
  
  private void printService(PrintWriter out, String txt) {
    out.println();
    out.println(txt);
    out.flush();
  }
  
  private ILoopWrapper createILoopWrapper(WorksheetArgsJava worksheetArgs, File iLoopFile, PrintWriter out) {
    URLClassLoader loader;
    Class<?> clazz;
    
    try {
      loader = new URLClassLoader(new URL[]{iLoopFile.toURI().toURL()}, getClass().getClassLoader());
      
      int idxDot = iLoopFile.getName().lastIndexOf('.');
      int idxDash = iLoopFile.getName().lastIndexOf('-');
      String name = idxDot == -1 || idxDash == -1 || idxDash >= idxDot ? 
          FALLBACK_CLASSNAME : iLoopFile.getName().substring(idxDash + 1, idxDot);
      
      clazz = loader.loadClass("org.jetbrains.jps.incremental.scala.local.worksheet." + name);
    } catch (MalformedURLException | ClassNotFoundException ignored) {
      return null;
    }

    List<File> classpath = new ArrayList<>(Arrays.asList(worksheetArgs.getCompLibrary(), worksheetArgs.getCompiler()));
    classpath.addAll(worksheetArgs.getCompExtra());
    classpath.addAll(worksheetArgs.getOutputDirs());
    classpath.addAll(worksheetArgs.getClasspathURLs().stream().map(url -> {
      try {
        return url.toURI();
      } catch (URISyntaxException e) {
        return null;
      }
    }).filter(Objects::nonNull).map(File::new).collect(Collectors.toList()));
    
    List<String> stringCp = classpath.stream().filter(File::exists).map(File::getAbsolutePath).collect(Collectors.toList());

    try {
      ILoopWrapper inst = (ILoopWrapper) clazz.getConstructor(PrintWriter.class, List.class).newInstance(out, stringCp);
      inst.init();
      return inst;
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      return null;
    }
  }
  
  
  private static class MySimpleCache {
    private final int limit;
    private final ReplSessionComparator comparator;
    private final PriorityQueue<ReplSession> sessionsQueue;
    
    MySimpleCache(int limit) {
      this.limit = limit;
      this.comparator = new ReplSessionComparator();
      this.sessionsQueue = new PriorityQueue<>(limit);
    }
    
    ILoopWrapper getOrCreate(String id, Supplier<ILoopWrapper> onCreation, Consumer<ILoopWrapper> onDiscard) {
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
    
    private ReplSession findById(String id) {
      if (id == null) return null;
      for (ReplSession s : sessionsQueue) {
        if (s != null && id.equals(s.id)) return s;
      }
      
      return null;
    }
    
    private class ReplSessionComparator implements Comparator<ReplSession> {
      private final HashMap<String, Integer> storage = new HashMap<>();

      private void inc(String id) {
        storage.compute(id, (k, v) -> (v == null) ? null : v + 1);
      }

      private void dec(String id) {
        storage.compute(id, (k, v) -> (v == null) ? null : v - 1);
      }

      private void put(String id) {
        storage.put(id, 10);
      }

      private void remove(String id) {
        storage.remove(id);
      }

      @Override
      public int compare(ReplSession x, ReplSession y) {
        if (x == null) {
          return y == null ? 0 : 1;
        }

        if (y == null) return -1;

        if (!storage.containsKey(x.id)) return 1;
        if (!storage.containsKey(y.id)) return -1;

        return (int) storage.get(y.id) - storage.get(x.id);
      }

      @Override
      public boolean equals(Object obj) {
        return this == obj;
      }
    }

    private class ReplSession implements Comparable<ReplSession> {
      final String id;
      final ILoopWrapper wrapper;

      private ReplSession(String id, ILoopWrapper wrapper) {
        this.id = id;
        this.wrapper = wrapper;
      }

      @Override
      public int compareTo(@NotNull ReplSession o) {
        return MySimpleCache.this.comparator.compare(this, o);
      }
    }    
    
  }
  

}
