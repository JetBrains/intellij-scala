package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io._
import java.net._
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.regex.Pattern
import java.{util => ju}

import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapperFactory._
import org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapperFactoryHandler.{ReplContext, ReplWrapperCompiled}
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgsRepl

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import org.jetbrains.plugins.scala.compiler.data.worksheet.ReplMessages

class ILoopWrapperFactory {

  //maximum count of repl sessions handled at any time
  private val ReplSessionLimit = 5
  private val cache = new ILoopWrapperFactory.MySimpleCache(ReplSessionLimit)

  private val commands: Map[String, ILoopWrapper => Unit] = Map((":reset", _.reset()))

  import ReplMessages._

  def clearCaches(): Unit = cache.clear()

  def loadReplWrapperAndRun(
    args: WorksheetArgsRepl,
    replContext: ReplContext,
    outStream: PrintStream,
    replWrapper: ReplWrapperCompiled,
    client: Client,
    classLoader: ClassLoader
  ): Unit = {
    client.progress("Retrieving REPL instance...")
    val inst = cache.getOrCreate(args.sessionId, () => {
      createILoopWrapper(args, replContext, replWrapper, outStream, classLoader, client) match {
        case Right(inst) =>
          inst.init()
          inst
        case Left(error)  =>
          client.trace(error)
          null
      }
    }, _.shutdown())
    if (inst == null) return

    val out = inst.getOutput
    out match {
      case stream: MyUpdatePrintStream => stream.updateOut(outStream)
      case writer: MyUpdatePrintWriter => writer.updateOut(outStream)
      case _                           =>
    }
    client.progress("Worksheet execution started")
    printService(out, ReplStart)
    try out.flush()
    catch {
      case e: IOException =>
        e.printStackTrace()
    }
    val code = new String(Base64.getDecoder.decode(args.codeChunk), StandardCharsets.UTF_8)
    val statements = code.split(Pattern.quote(ReplDelimiter))
    for (statement <- statements) {
      val commandAction = if (statement.startsWith(":")) commands.get(statement) else None
      commandAction match {
        case Some(action) =>
          action.apply(inst)
        case _        =>
          printService(out, ReplChunkStart)
          val shouldContinue = statement.trim.length == 0 || inst.processChunk(statement)
          client.progress("Executing worksheet...") // TODO: add fraction
          if (shouldContinue) {
            printService(out, ReplChunkEnd)
          } else {
            printService(out, ReplChunkCompilationError)
            return
          }
      }
    }
    printService(out, ReplLastChunkProcessed)
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
    args: WorksheetArgsRepl,
    replContext: ReplContext,
    replWrapper: ReplWrapperCompiled,
    out: PrintStream,
    classLoader: ClassLoader,
    client: Client
  ): Either[ILoopCreationException, ILoopWrapper] = {
    val ReplWrapperCompiled(replFile, replClassName, version) = replWrapper
    val loader = try {
      val iLoopWrapperJar = replFile.toURI.toURL
      new URLClassLoader(Array[URL](iLoopWrapperJar), classLoader)
    } catch {
      case ex: MalformedURLException =>
        return Left(ILoopCreationException(ex))
    }

    val clazz = try {
      loader.loadClass(s"org.jetbrains.jps.incremental.scala.local.worksheet.$replClassName")
    } catch {
      case ex: ClassNotFoundException =>
        return Left(ILoopCreationException(ex))
    }

    val replClasspath = Seq(
      replContext.compilerJars.allJars,
      args.outputDirs,
      replContext.classpath
    ).flatten
    val classpathStrings = replClasspath.filter(_.exists()).map(_.getAbsolutePath).distinct.sorted.asJava
    val scalaOptions = replContext.scalacOptions.asJava

    try {
      val inst = if (!version.isScala3) {
        val printWriter = new MyUpdatePrintWriter(out)
        val reporter    = new PrintWriterReporter(printWriter, client)
        val constructor = clazz.getConstructor(classOf[PrintWriter], classOf[ILoopWrapperReporter], classOf[ju.List[_]], classOf[ju.List[_]])
        constructor.newInstance(printWriter, reporter, classpathStrings, scalaOptions).asInstanceOf[ILoopWrapper]
      } else {
        val printStream = new MyUpdatePrintStream(out)
        val reporter    = new DebugLoggingReporter(client)
        val constructor = clazz.getConstructor(classOf[PrintStream], classOf[ILoopWrapperReporter], classOf[ju.List[_]], classOf[ju.List[_]])
        constructor.newInstance(printStream, reporter, classpathStrings, scalaOptions).asInstanceOf[ILoopWrapper]
      }
      Right(inst)
    } catch {
      case ex@(_: ReflectiveOperationException | _: IllegalArgumentException) =>
        Left(ILoopWrapperFactory.ILoopCreationException(ex))
    }
  }
}

private object ILoopWrapperFactory {

  private case class ILoopCreationException(cause: Throwable) extends Exception(cause)

  private class MySimpleCache(val limit: Int) {
    private val comparator    = new ReplSessionComparator
    private val sessionsQueue = new ju.PriorityQueue[ReplSession](limit)

    def clear(): Unit = {
      sessionsQueue.asScala.foreach(_.wrapper.shutdown())
      sessionsQueue.clear()
    }

    def getOrCreate(
      sessionId: String,
      onCreation: () => ILoopWrapper,
      onDiscard: ILoopWrapper => Unit
    ): ILoopWrapper = {
      findById(sessionId) match {
        case Some(existing) =>
          comparator.inc(sessionId)
          return existing.wrapper
        case _ =>
      }
      if (sessionsQueue.size >= limit) {
        val oldSession = sessionsQueue.poll
        if (oldSession != null) {
          onDiscard.apply(oldSession.wrapper)
          comparator.remove(oldSession.id)
        }
      }
      val newSession = new ReplSession(sessionId, onCreation())
      comparator.put(sessionId)
      sessionsQueue.offer(newSession)
      newSession.wrapper
    }

    private def findById(id: String): Option[ReplSession] =
      sessionsQueue.asScala.find(session => session != null && session.id == id)

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
