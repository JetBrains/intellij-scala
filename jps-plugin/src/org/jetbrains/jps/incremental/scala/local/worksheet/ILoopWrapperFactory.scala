package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io.{File, OutputStream, PrintWriter}
import java.net.URLClassLoader
import java.util
import java.util.regex.Pattern
import java.util.{Base64, Comparator, PriorityQueue}

import org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapperFactory._
import org.jetbrains.jps.incremental.scala.local.worksheet.WorksheetServer.MyUpdatePrintWriter
import org.jetbrains.jps.incremental.scala.local.worksheet.compatibility.WorksheetArgsJava

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  * User: Dmitry.Naydanov
  * Date: 27.01.17.
  */
class ILoopWrapperFactory {
  private val cache = new MySimpleCache(REPL_SESSION_LIMIT)

  /**
    * We need it for ClassLoading magic. 
    * This is entry point for the factory. Invoked via reflection in ILoopWrapperFactoryHandler
    * Hack with client as comparable is f ugly but works 
    */
  def loadReplWrapperAndRun(worksheetArgsString: util.List[String], nameForSt: String,
                            library: File, compiler: File, extra: util.List[File], classpath: util.List[File],
                            outStream: OutputStream, iLoopFile: File, clientProvider: Comparable[String]) {
    val argsJava = WorksheetArgsJava.constructArgsFrom(worksheetArgsString, nameForSt, library, compiler, extra, classpath)
    
    val onProgress: String => Unit = if (clientProvider == null) (_: String) => () else (msg: String) => clientProvider.compareTo(msg) 
    
    loadReplWrapperAndRun(argsJava, outStream, iLoopFile, onProgress)   
  }

  /**
    * Main runner method.  
    *
    * @param worksheetArgs worksheet specific extracted args; contains sessionId to be associated with wrapper. 
    *                      In current impl it is path to .sc file (for "real" files) or !SCRATCH!\filename for light worksheets 
    * @param outStream     OutputStream that will be used for printing the result of input code execution
    * @param onProgress    Client method for displaying progress (if we must compile wrapper class)
    * @return REPL wrapper for worksheet, cached or newly created
    */
  private def loadReplWrapperAndRun(worksheetArgs: WorksheetArgsJava, outStream: OutputStream, iLoopFile: File, onProgress: String => Unit) {
    val replArgs = worksheetArgs.getReplArgs
    if (replArgs == null) return
    
    onProgress("Retrieving REPL instance...")
    
    val inst = cache.getOrCreate(
      replArgs.getSessionId, 
      () => createILoopWrapper(worksheetArgs, iLoopFile, new MyUpdatePrintWriter(outStream)),
      (wrapper: ILoopWrapper) => wrapper.shutdown()
    )
    
    val out = inst.getOutputWriter match {
      case up: MyUpdatePrintWriter =>
        up updateOut outStream
        up
      case other => other
    }
    
    def printService(txt: String) {
      out.println()
      out println txt
      out.flush()
    }
    
    onProgress("Worksheet execution started")
    printService(REPL_START)
    out.flush()

    val code = new String(Base64.getDecoder.decode(replArgs.getCodeChunk), "UTF-8")
    val statements = code split Pattern.quote("\n$\n$\n")

//    val stmtCount = statements.length
//    var count = 1.0f
//    def stmtProcessed() {
//      client.foreach(_.progress("Executing worksheet...", Some(count / stmtCount)))
//      count += 1
//    }
    
    var j = 0 // Compatibility 2.11 <-> 2.12 . We can't use ArrayOps and case classes with >1 args, so no foreach and no for with until
    
    while (j < statements.length) {
      statements.apply(j) match {
        case CommandExtractor(action) =>
          action(inst)
        case codeChunk =>
          val shouldContinue = codeChunk.trim.length == 0 || inst.processChunk(codeChunk)

          onProgress("Executing worksheet...")
          printService(REPL_CHUNK_END)
          if (!shouldContinue) return
      } 
      
      j += 1
    }

    printService(REPL_LAST_CHUNK_PROCESSED)
  }

  private def createILoopWrapper(worksheetArgs: WorksheetArgsJava, iLoopFile: File, out: PrintWriter): ILoopWrapper = {
    val loader = new URLClassLoader(Array(iLoopFile.toURI.toURL), getClass.getClassLoader)
    val clazz = loader.loadClass("org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapperImpl")

    val cp = Seq(worksheetArgs.getCompLibrary, worksheetArgs.getCompiler) ++ worksheetArgs.getCompExtra.asScala ++ 
      worksheetArgs.getOutputDirs.asScala ++ worksheetArgs.getClasspathURLs.asScala.map(u => new File(u.toURI)).filter(_.exists())

    val inst = clazz.getConstructor(classOf[PrintWriter], classOf[java.util.List[String]]).newInstance(out, cp.map(_.getAbsolutePath).asJava).asInstanceOf[ILoopWrapper]
    
    inst.init()
    inst
  }
}

object ILoopWrapperFactory {
  private val REPL_START = "$$worksheet$$repl$$start$$"
  private val REPL_CHUNK_END = "$$worksheet$$repl$$chunk$$end$$"
  private val REPL_LAST_CHUNK_PROCESSED = "$$worksheet$$repl$$last$$chunk$$processed$$"

  //maximum count of repl sessions handled at any time 
  val REPL_SESSION_LIMIT = 5

  private class MySimpleCache(private val limit: Int) {
    private val comparator = new ReplSessionComparator
    private val sessionsQueue = new PriorityQueue[ReplSession](limit)

    def getOrCreate(id: String, onCreation: () => ILoopWrapper, onDiscard: ILoopWrapper => Unit): ILoopWrapper = {
      val existingSession = findById(id)

      existingSession.foreach {
        session =>
          comparator.inc(id)
          return session.wrapper
      }

      if (sessionsQueue.size >= limit) {
        val anOldSession = sessionsQueue.poll()

        onDiscard(anOldSession.wrapper)
        comparator.remove(anOldSession.id)
      }

      val newSession = new ReplSession(id, onCreation())

      comparator.put(id)
      sessionsQueue.offer(newSession)

      newSession.wrapper
    }

    private def findById(id: String): Option[ReplSession] = {
      val i = sessionsQueue.iterator()
      
      while (i.hasNext) {
        val s = i.next()
        
        if (s != null && s.id == id) return Option(s)
      }
      
      None
    }

    private class ReplSessionComparator extends Comparator[ReplSession] {
      //session id -> count
      private val storage = mutable.HashMap[String, Int]()

      def inc(id: String) {
        storage.remove(id).map(_ + 1).map(v => storage.put(id, v))
      }

      def dec(id: String) {
        storage.remove(id).map(_ - 1).map(v => storage.put(id, v))
      }

      def put(id: String) {
        storage.put(id, 10)
      }

      def remove(id: String) {
        storage.remove(id)
      }

      override def compare(x: ReplSession, y: ReplSession): Int = {
        if (x == null) {
          if (y == null) 0 else 1
        } else if (y == null) -1 else {
          val vx = storage.getOrElse(x.id, return 1)
          val vy = storage.getOrElse(y.id, return -1)

          vy - vx
        }
        
      }
    }
    
    private class ReplSession(val id: String, val wrapper: ILoopWrapper) extends Comparable[ReplSession] {
      override def compareTo(o: ReplSession): Int = comparator.compare(this, o)
    }
  }
  
  case class Command(action: ILoopWrapper => Unit)

  object CommandExtractor {
    private val commands: Map[String, ILoopWrapper => Unit] =
      Map(":reset" -> { (wrapper: ILoopWrapper) => wrapper.reset() })

    def unapply(arg: String): Option[ILoopWrapper => Unit] = commands.get(arg)
  }

}
