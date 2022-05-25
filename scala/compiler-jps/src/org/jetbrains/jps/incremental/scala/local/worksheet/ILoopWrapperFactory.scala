package org.jetbrains.jps.incremental.scala.local.worksheet

import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapperFactory.{ILoopCreationException, MyUpdatePrintStream, MyUpdatePrintWriter}
import org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapperFactoryHandler.{ReplContext, ReplWrapperCompiled}
import org.jetbrains.jps.incremental.scala.local.worksheet.repl_interface.{ILoopWrapper, ILoopWrapperReporter, NoopReporter, PrintWriterReporter}
import org.jetbrains.plugins.scala.compiler.data.worksheet.{ReplMessages, WorksheetArgs}

import java.io._
import java.net._
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.regex.Pattern
import java.{util => ju}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

class ILoopWrapperFactory {

  //maximum count of repl sessions handled at any time
  private val ReplSessionLimit = 5
  private val cache = new ILoopWrapperFactory.MySimpleCache(ReplSessionLimit)

  private val commands: Map[String, ILoopWrapper => Unit] = Map((":reset", _.reset()))

  import ReplMessages._

  def clearCaches(): Unit = cache.clear()

  def clearSession(sessionId: String): Unit = cache.clear(sessionId)

  def loadReplWrapperAndRun(
    args: WorksheetArgs.RunRepl,
    replContext: ReplContext,
    outStream: PrintStream,
    replWrapper: ReplWrapperCompiled,
    client: Client,
    classLoader: ClassLoader
  ): Unit = {
    client.progress("Retrieving REPL instance...")
    val instOpt = cache.getOrCreate(args.sessionId, () => {
      client.progress("Creating REPL instance...")
      createILoopWrapper(args, replContext, replWrapper, outStream, classLoader) match {
        case Right(inst) =>
          inst.init()
          Some(inst)
        case Left(error)  =>
          client.trace(error)
          None
      }
    }, _.shutdown())
    val inst = instOpt.getOrElse(return)

    val out = inst.getOutput
    out match {
      case stream: MyUpdatePrintStream => stream.updateOut(outStream)
      case writer: MyUpdatePrintWriter => writer.updateOut(outStream)
      case _                           =>
    }
    client.progress("Worksheet execution started", Some(0))
    printService(out, ReplStart)

    try out.flush()
    catch {
      case e: IOException =>
        e.printStackTrace()
    }

    val code = new String(Base64.getDecoder.decode(args.codeChunk), StandardCharsets.UTF_8)
    // note: do not remove String generic parameter, it will fail in JVM 11
    val statements = if (code.isEmpty) Array.empty[String] else code.split(Pattern.quote(ReplDelimiter))
    for  { (statement, idx) <- statements.zipWithIndex if statement.trim.nonEmpty } {
      val commandAction = if (statement.startsWith(":")) commands.get(statement) else None
      commandAction match {
        case Some(action) =>
          action.apply(inst)
        case _        =>
          printService(out, ReplChunkStart)

          val progress = (idx + 1f) / statements.size
          client.progress("Executing worksheet...", Some(progress))

          val noErrors = try inst.processChunk(statement) catch {
            case NonFatal(ex) =>
              printStackTrace(ex, out)
              false
          }
          val shouldContinue = noErrors || args.continueOnChunkError
          if (shouldContinue) {
            printService(out, ReplChunkEnd)
          } else {
            printService(out, ReplChunkCompilationError)
            return
          }
      }
    }

    client.progress("Worksheet execution finished", Some(1))
    printService(out, ReplEnd)
  }

  private def printStackTrace(ex: Throwable, output: Flushable): Unit =
    output match {
      case stream: MyUpdatePrintStream => ex.printStackTrace(stream)
      case writer: MyUpdatePrintWriter => ex.printStackTrace(writer)
      case _                           =>
    }

  private def printService(out: Flushable, txt: String): Unit =
    out match {
      case stream: MyUpdatePrintStream => printService(stream, txt)
      case writer: MyUpdatePrintWriter => printService(writer, txt)
      case _                           =>
    }

  private def printService(out: PrintStream, txt: String): Unit = {
    out.println()
    out.println(txt)
    out.flush()
  }

  private def printService(out: PrintWriter, txt: String): Unit = {
    out.println()
    out.println(txt)
    out.flush()
  }

  private def createILoopWrapper(
    args: WorksheetArgs.RunRepl,
    replContext: ReplContext,
    replWrapper: ReplWrapperCompiled,
    out: PrintStream,
    classLoader: ClassLoader
  ): Either[ILoopCreationException, ILoopWrapper] = {
    val ReplWrapperCompiled(replFile, replClassName, version) = replWrapper
    val loader = try {
      val iLoopWrapperJar = replFile.toURI.toURL
      new URLClassLoader(Array[URL](iLoopWrapperJar), classLoader)
    } catch {
      case ex: MalformedURLException =>
        return Left(new ILoopCreationException(ex))
    }

    val clazz = try {
      // assuming that implementation classes will have same package as interface
      val basePackage = classOf[ILoopWrapper].getPackage.getName
      loader.loadClass(s"$basePackage.$replClassName")
    } catch {
      case ex: ClassNotFoundException =>
        return Left(new ILoopCreationException(s"Can't load ILoopWrapper $replWrapper (file exists: ${replFile.exists})", ex))
    }

    val replClasspathChunks = Seq(
      replContext.compilerJars.allJars,
      args.outputDirs,
      replContext.classpath
    )
    val replClasspath = replClasspathChunks.flatten
    val classpathStrings = replClasspath.filter(_.exists).map(_.getAbsolutePath).distinct.sorted.asJava
    val scalaOptions = replContext.scalacOptions.asJava

    try {
      val inst = if (!version.isScala3) {
        val printWriter = new MyUpdatePrintWriter(out)
        val reporter = new PrintWriterReporter(printWriter)
        val constructor = clazz.getConstructor(classOf[PrintWriter], classOf[ILoopWrapperReporter], classOf[ju.List[_]], classOf[ju.List[_]])
        constructor.newInstance(printWriter, reporter, classpathStrings, scalaOptions).asInstanceOf[ILoopWrapper]
      } else {
        val printStream = new MyUpdatePrintStream(out)
        val reporter = new NoopReporter
        val constructor = clazz.getConstructor(classOf[PrintStream], classOf[ILoopWrapperReporter], classOf[ju.List[_]], classOf[ju.List[_]])
        constructor.newInstance(printStream, reporter, classpathStrings, scalaOptions).asInstanceOf[ILoopWrapper]
      }
      Right(inst)
    } catch {
      case ex@(_: ReflectiveOperationException | _: IllegalArgumentException) =>
        val exception = new ILoopCreationException(ex)
        exception.setStackTrace(ex.getStackTrace)
        Left(exception)
      case wtf: Throwable =>
        throw wtf
    }
  }
}

private object ILoopWrapperFactory {

  private class ILoopCreationException(message: String, cause: Throwable) extends Exception(message, cause) {
    def this(cause: Throwable) = this(cause.toString, cause)
  }

  private class MySimpleCache(val limit: Int) {

    private val comparator    = new ReplSessionComparator
    private val sessionsQueue = new ju.PriorityQueue[ReplSession](limit)

    private def findById(id: String): Option[ReplSession] =
      sessionsQueue.asScala.find(session => session != null && session.id == id)

    def clear(sessionId: String): Unit = {
      val session = findById(sessionId)
      session.foreach { sess =>
        sess.wrapper.shutdown()
        sessionsQueue.remove(sess)
      }
    }

    def clear(): Unit = {
      sessionsQueue.asScala.foreach(_.wrapper.shutdown())
      sessionsQueue.clear()
    }

    def getOrCreate(
      sessionId: String,
      createWrapper: () => Option[ILoopWrapper],
      onDiscard: ILoopWrapper => Unit
    ): Option[ILoopWrapper] = {
      findById(sessionId) match {
        case Some(existing) =>
          comparator.inc(sessionId)
          return Some(existing.wrapper)
        case _ =>
      }
      if (sessionsQueue.size >= limit) {
        val oldSession = sessionsQueue.poll
        if (oldSession != null) {
          onDiscard.apply(oldSession.wrapper)
          comparator.remove(oldSession.id)
        }
      }

      val wrapper = createWrapper()
      wrapper.foreach { w =>
        val newSession = new ReplSession(sessionId, w)
        comparator.put(sessionId)
        sessionsQueue.offer(newSession)
      }
      wrapper
    }

    private class ReplSessionComparator extends ju.Comparator[ReplSession] {
      private val storage = new ju.HashMap[String, Integer]

      def inc(id: String): Unit = storage.compute(id, (_, v) => if (v == null) null else v + 1)
      //def dec(id: String): Unit = storage.compute(id, (_: String, v: Integer) => if (v == null) null else v - 1)
      def put(id: String): Unit = storage.put(id, 10)
      def remove(id: String): Unit = storage.remove(id)

      override def compare(x: ReplSession, y: ReplSession): Int =
        (x, y) match {
          case (null, null)                   => 0
          case (null, _)                      => 1
          case (_, null)                      => -1
          case _ if storage.containsKey(x.id) => 1
          case _ if storage.containsKey(y.id) => -1
          case _                              => storage.get(y.id).asInstanceOf[Int] - storage.get(x.id)
        }
    }

    private class ReplSession(val id: String, val wrapper: ILoopWrapper) extends Comparable[ReplSession] {
      override def compareTo(o: ReplSession): Int =
        MySimpleCache.this.comparator.compare(this, o)
    }
  }

  // buffering is already done in MyEncodingOutputStream
  private class MyUpdatePrintStream(stream: OutputStream) extends PrintStream(stream) {
    private var curHash = stream.hashCode

    def updateOut(stream: OutputStream): Unit = {
      if (stream.hashCode != curHash) {
        out = stream
        curHash = stream.hashCode()
      }
    }
  }

  private class MyUpdatePrintWriter(stream: OutputStream) extends PrintWriter(stream) {
    private var curHash = stream.hashCode()

    def updateOut(stream: OutputStream): Unit = {
      if (stream.hashCode() != curHash) {
        out = new BufferedWriter(new OutputStreamWriter(stream))
        curHash = stream.hashCode()
      }
    }
  }
}
