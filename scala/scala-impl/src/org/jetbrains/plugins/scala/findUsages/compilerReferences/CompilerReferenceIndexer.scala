package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.File
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.IndexerFailure._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.IndexerJob._
import org.jetbrains.plugins.scala.indices.protocol.{CompilationInfo, CompiledClass}

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

private class CompilerReferenceIndexer(project: Project, expectedIndexVersion: Int) {
  import CompilerReferenceIndexer._

  private[this] val parsingJobs    = new AtomicInteger(0)
  private[this] val parserJobQueue = new ConcurrentLinkedQueue[Set[CompiledClass]]()
  private[this] val writerJobQueue = new LinkedBlockingDeque[WriterJob](1000)
  private[this] val nThreads       = Runtime.getRuntime.availableProcessors()

  private[this] var parsingExecutor: ExecutorService                  = _
  private[this] var indexWritingExecutor: ExecutorService             = _
  private[this] var indexWriter: Option[ScalaCompilerReferenceWriter] = None

  private[this] val failedToParse = ContainerUtil.newConcurrentSet[File]()
  private[this] val fatalFailures = ContainerUtil.newConcurrentSet[Throwable]()

  private[this] def shutdownIfNeeded(executor: ExecutorService): Unit =
    if (executor != null && !executor.isShutdown) executor.shutdownNow()

  Disposer.register(project, () => shutdown())

  private def shutdown(): Unit = {
    shutdownIfNeeded(parsingExecutor)
    shutdownIfNeeded(indexWritingExecutor)
  }

  private def onException(e: Throwable): Unit = {
    shutdown()
    fatalFailures.add(e)
  }

  private def parseClassfiles(writer: ScalaCompilerReferenceWriter): Unit = {
    while (!parserJobQueue.isEmpty && !Thread.currentThread().isInterrupted) {
      try {
        val compiledClasses = parserJobQueue.poll()
        val sourceFile      = compiledClasses.headOption.map(_.source)
        val classfiles      = compiledClasses.map(_.output)

        try {
          val parsed = ClassfileParser.parse(classfiles)
          val data   = sourceFile.map(CompiledScalaFile(_, parsed, writer))
          data.foreach(ProcessCompiledFile andThen writerJobQueue.put)
        } catch { case NonFatal(_) => failedToParse.addAll(classfiles.asJava) }
      } catch {
        case _: InterruptedException => Thread.currentThread().interrupt()
        case e: Throwable            => onException(e)
      }
    }

    if (Thread.interrupted()) onException(new InterruptedException)
  }

  private def writeParsedClassfile(
    writer:          ScalaCompilerReferenceWriter,
    indicator:       ProgressIndicator,
    totalClassfiles: Int
  ): Unit = {
    var processed = 0
    indicator.setFraction(0d)

    while ((parsingJobs.get() != 0 || !writerJobQueue.isEmpty) && !Thread.currentThread().isInterrupted) {
      try {
        if (processed % 10 == 0) {
          ProgressManager.checkCanceled()
          indicator.setFraction(processed * 1d / totalClassfiles)
        }

        writer.getRebuildRequestCause.nullSafe.foreach(onException)
        val job = writerJobQueue.poll(1, TimeUnit.SECONDS)
        job match {
          case ProcessCompiledFile(data) => processed += 1; writer.registerClassfileData(data)
          case ProcessDeletedFile(file)  => processed += 1; writer.processDeletedFile(file.getPath)
          case null                      => ()
        }
      } catch {
        case _: InterruptedException => Thread.currentThread().interrupt()
        case e: Throwable            => onException(e)
      }
    }

    if (Thread.interrupted()) onException(new InterruptedException)
  }

  private[this] def initialiseExecutorsIfNeeded(): Unit = {
    if (parsingExecutor == null || parsingExecutor.isShutdown)
      parsingExecutor = Executors.newFixedThreadPool(nThreads)

    if (indexWritingExecutor == null || indexWritingExecutor.isShutdown)
      indexWritingExecutor = Executors.newSingleThreadExecutor()
  }

  def toTask(job: IndexerJob): Task.Backgroundable =
    job match {
      case OpenWriter(isCleanBuild) => task(project) { _ =>
        initialiseExecutorsIfNeeded()
        indexWriter = indexDir(project).flatMap(ScalaCompilerReferenceWriter(_, expectedIndexVersion, isCleanBuild))
      }
      case CloseWriter(onFinish) => task(project) { _ =>
        val maybeFailure =
          if (!fatalFailures.isEmpty)      FatalFailure(fatalFailures.asScala).toOption
          else if (!failedToParse.isEmpty) FailedToParse(failedToParse.asScala).toOption
          else                             None

        cleanUp(!fatalFailures.isEmpty)
        onFinish(maybeFailure)
      }
      case ProcessCompilationInfo(info, onFinish) => new IndexCompilationInfoTask(info, onFinish)
      case InvalidateIndex =>
        task(project, "Invalidating compiler indices")(
          _ => cleanUp(shouldClearIndex = true)
        )
    }

  private[this] def cleanUp(shouldClearIndex: Boolean): Unit =
    try indexWriter match {
      case Some(writer)             => writer.close(shouldClearIndex)
      case None if shouldClearIndex => removeIndexFiles(project)
      case _                        => ()
    } finally {
      indexWriter = None
      parsingJobs.set(0)
      failedToParse.clear()
      fatalFailures.clear()
    }

  private final class IndexCompilationInfoTask(info: CompilationInfo, callback: () => Unit)
      extends Task.Backgroundable(project, "Indexing classfiles ...", true) {

    override def run(progressIndicator: ProgressIndicator): Unit =
      if (!parsingExecutor.isShutdown && !indexWritingExecutor.isShutdown) {
        indexWriter match {
          case None => log.warn("Failed to index compilation info due to index writer being disposed.")
          case Some(writer) =>
            info.removedSources.iterator.map(ProcessDeletedFile).foreach(writerJobQueue.add)
            info.generatedClasses.groupBy(_.source).foreach {
              case (_, classes) => parserJobQueue.add(classes)
            }

            val parsingTasks = (1 to nThreads).map(
              _ =>
                toCallable {
                  parseClassfiles(writer)
                  parsingJobs.decrementAndGet()
              }
            )

            parsingTasks.flatMap { task =>
              parsingJobs.incrementAndGet()
              try Option(parsingExecutor.submit(task))
              catch {
                case NonFatal(_) =>
                  parsingJobs.decrementAndGet()
                  None
              }
            }

            val indexingTask = indexWritingExecutor.submit(
              toCallable(
                writeParsedClassfile(
                  writer,
                  progressIndicator,
                  info.generatedClasses.size + info.removedSources.size
                )
              )
            )

            try {
              indexingTask.get()
              if (failedToParse.isEmpty) callback()
            } catch {
              case e: Throwable => onException(e)
            }
        }

        parsingJobs.set(0)
        parserJobQueue.clear()
        writerJobQueue.clear()
      } else log.warn("Unable to start indexing, since executors are shutdown.")
  }
}

private object CompilerReferenceIndexer {
  private val log = Logger.getInstance(classOf[CompilerReferenceIndexer])

  private sealed trait WriterJob
  private final case class ProcessDeletedFile(file:  File)              extends WriterJob
  private final case class ProcessCompiledFile(data: CompiledScalaFile) extends WriterJob

  def async[F[_], A](k: (Either[Throwable, A] => Unit) => Unit): F[A] = ???
}
