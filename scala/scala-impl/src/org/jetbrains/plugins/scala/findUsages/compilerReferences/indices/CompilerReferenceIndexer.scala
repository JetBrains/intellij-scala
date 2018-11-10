package org.jetbrains.plugins.scala.findUsages.compilerReferences
package indices

import java.io.File
import java.util.concurrent._
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.bytecode.{ClassfileParser, CompiledScalaFile}
import org.jetbrains.plugins.scala.findUsages.compilerReferences.indices.IndexerFailure._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.indices.IndexerJob._
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

  private[this] val failedToParse = ContainerUtil.newConcurrentSet[ClassParsingFailure]()
  private[this] val fatalFailure  = new AtomicReference[Option[Throwable]](Option.empty)

  Disposer.register(project, () => shutdown())

  private[this] def shutdown(): Unit = if (!isShutdown) {
    parsingExecutor.shutdownNow()
    indexWritingExecutor.shutdownNow()
  }

  private[this] def onException(e: Throwable, shouldClearIndex: Boolean): Unit = {
    fatalFailure.updateAndGet(_.orElse(Option(e)))
    if (shouldClearIndex) indexWriter.foreach(_.close(shouldClearIndex = true))
    shutdown()
  }

  private[this] def isShutdown: Boolean =
    (parsingExecutor == null || parsingExecutor.isShutdown) &&
      (indexWritingExecutor == null || indexWritingExecutor.isShutdown)

  private[this] def checkInterruptStatus(): Unit =
    if (Thread.interrupted()) throw new InterruptedException

  private def parseClassfiles(writer: ScalaCompilerReferenceWriter): Unit =
    try {
      while (!parserJobQueue.isEmpty) {
        checkInterruptStatus()

        for {
          compiledClasses <- parserJobQueue.poll().toOption
          sourceFile      <- compiledClasses.headOption.map(_.source)
          classfiles      = compiledClasses.map(_.output)
        } try {
          val parsed = ClassfileParser.parse(classfiles)
          val data   = CompiledScalaFile(sourceFile, parsed, writer)
          writerJobQueue.put(ProcessCompiledFile(data))
        } catch { case NonFatal(e) => failedToParse.add(ClassParsingFailure(classfiles, e)) }
      }
    } catch { case e: Throwable => onException(e, shouldClearIndex = false) }

  private def writeParsedClassfile(
    writer:          ScalaCompilerReferenceWriter,
    indicator:       ProgressIndicator,
    totalClassfiles: Int
  ): Unit =
    try {
      var processed = 0
      indicator.setFraction(0d)

      while (parsingJobs.get() != 0 || !writerJobQueue.isEmpty) {
        checkInterruptStatus()

        if (processed % 10 == 0) {
          ProgressManager.checkCanceled()
          indicator.setFraction(processed * 1d / totalClassfiles)
        }

        writer.getRebuildRequestCause.nullSafe.foreach(onException(_, shouldClearIndex = true))
        val job = writerJobQueue.poll(1, TimeUnit.SECONDS)

        job match {
          case ProcessCompiledFile(data) => processed += 1; writer.registerClassfileData(data)
          case ProcessDeletedFile(file)  => processed += 1; writer.processDeletedFile(file.getPath)
          case null                      => ()
        }
      }
    } catch { case e: Throwable => onException(e, shouldClearIndex = true) }

  private[this] def initialiseExecutorsIfNeeded(): Unit = if (isShutdown) {
    parsingExecutor      = Executors.newFixedThreadPool(nThreads)
    indexWritingExecutor = Executors.newSingleThreadExecutor()
  }

  def toTask(job: IndexerJob): Task.Backgroundable =
    job match {
      case OpenWriter(isCleanBuild) => task(project, "Initializing compiler indices writer") { _ =>
        initialiseExecutorsIfNeeded()
        indexWriter = indexDir(project).flatMap(ScalaCompilerReferenceWriter(_, expectedIndexVersion, isCleanBuild))
      }
      case CloseWriter(onFinish) => task(project, "Closing compiler indices writer") { _ =>
        val maybeFatalFailure = fatalFailure.get().map(FatalFailure)

        val maybeFailure = maybeFatalFailure.orElse {
          if (!failedToParse.isEmpty) FailedToParse(failedToParse.asScala).toOption
          else                        None
        }

        cleanUp(maybeFatalFailure.isDefined)
        onFinish(maybeFailure)
      }
      case ProcessCompilationInfo(info, onFinish) => new IndexCompilationInfoTask(info, onFinish)
      case InvalidateIndex(index) =>
        task(project, "Invalidating compiler indices") { _ =>
          index.foreach(_.close())
          cleanUp(shouldClearIndex = true)
        }
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
      fatalFailure.set(Option.empty)
    }

  private final class IndexCompilationInfoTask(info: CompilationInfo, callback: () => Unit)
      extends Task.Backgroundable(project, "Indexing classfiles ...", true) {

    override def run(progressIndicator: ProgressIndicator): Unit =
      if (!isShutdown) {
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
              if (fatalFailure.get().isEmpty) callback()
            } catch {
              case e: Throwable => onException(e, shouldClearIndex = true)
            }
        }

        parsingJobs.set(0)
        parserJobQueue.clear()
        writerJobQueue.clear()
      } else log.error("Unable to start indexing, since executors are shutdown.")
  }
}

private[compilerReferences] object CompilerReferenceIndexer {
  private val log = Logger.getInstance(classOf[CompilerReferenceIndexer])

  private[compilerReferences] final case class ClassParsingFailure(classfiles: Set[File], cause: Throwable) {
    def errorMessage: String = s"Failed to parse ${classfiles.mkString("[\n", "\t\n", "]")}"
  }

  private sealed trait WriterJob
  private final case class ProcessDeletedFile(file:  File)              extends WriterJob
  private final case class ProcessCompiledFile(data: CompiledScalaFile) extends WriterJob
}
