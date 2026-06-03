package org.jetbrains.jps.incremental.scala.local.worksheet.repl_interface;

import java.io.Flushable;

// TODO: rename to something more abstract, ReplInstanceWrapper?
//  ILoop was in Scala 2, in Scala 3 it is ReplDriver
public interface ILoopWrapper {
    void init();

    void shutdown();

    void reset();

    boolean processChunk(String input);

    /**
     * @return either PrintWriter (Scala 2) or PrintStream (Scala 3)
     * do not use Either, use only java primitives
     */
    Flushable getOutput();
}
