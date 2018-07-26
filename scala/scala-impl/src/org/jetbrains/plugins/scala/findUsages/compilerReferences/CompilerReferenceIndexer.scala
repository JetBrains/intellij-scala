package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.File
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.jps.incremental.CompiledClass
import org.jetbrains.plugin.scala.compilerReferences.ChunkBuildData
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.IndexerFailure._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.IndexerJob.{CloseWriter, OpenWriter, ProcessChunkData}

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

private class CompilerReferenceIndexer(project: Project, expectedIndexVersion: Int) {
  import CompilerReferenceIndexer._

  private[this] val parsingJobs    = new AtomicInteger(0)
  private[this] val indexWriteLock = new ReentrantLock()
  private[this] val executionLock  = new ReentrantLock()

  private[this] val parserJobQueue = new ConcurrentLinkedQueue[Set[CompiledClass]]()
  private[this] val writerJobQueue = new LinkedBlockingDeque[WriterJob](1000)
  private[this] val nThreads       = Runtime.getRuntime.availableProcessors()

  private[this] var parsingExecutor: ExecutorService             = _
  private[this] var indexWritingExecutor: ExecutorService        = _
  private[this] var writer: Option[ScalaCompilerReferenceWriter] = None

  private[this] val failedToParse = ContainerUtil.newConcurrentSet[File]()
  private[this] val fatalFailures = ContainerUtil.newConcurrentSet[Throwable]()

  private[this] def shutdownIfNeeded(executor: ExecutorService): Unit =
    if (executor != null && !executor.isShutdown) executor.shutdownNow()

  Disposer.register(project, () => {
    shutdownIfNeeded(parsingExecutor)
    shutdownIfNeeded(indexWritingExecutor)
    if (executionLock.isLocked) {
      withLock(indexWriteLock) {
        indexDir(project).foreach(CompilerReferenceIndex.removeIndexFiles)
      }
    }
  })

  private def onException(e: Throwable): Unit = {
    shutdownIfNeeded(parsingExecutor)
    shutdownIfNeeded(indexWritingExecutor)
    fatalFailures.add(e)
  }

  private def parseClassfiles(writer: ScalaCompilerReferenceWriter): Unit = {
    while (!parserJobQueue.isEmpty && !Thread.interrupted()) {
      try {
        val compiledClasses = parserJobQueue.poll()
        val sourceFile      = compiledClasses.headOption.map(_.getSourceFile)
        val classfiles      = compiledClasses.map(_.getOutputFile)

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
  ): Unit =
    withLock(indexWriteLock) {
      var processed = 0
      indicator.setFraction(0d)

      while ((parsingJobs.get() != 0 || !writerJobQueue.isEmpty) && !Thread.interrupted()) {
        try {
          if (processed % 10 == 0) {
            ProgressManager.checkCanceled()
            indicator.setFraction(processed * 1d / totalClassfiles)
          }

          writer.getRebuildRequestCause.nullSafe.foreach(onException)
          val job = writerJobQueue.poll(1, TimeUnit.SECONDS)
          job match {
            case ProcessCompiledFile(data) => processed += 1; writer.registerClassfileData(data)
            case ProcessDeletedFile(file)  => processed += 1; writer.processDeletedFile(file)
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

  def process(job: IndexerJob): Unit = withLock(executionLock) {
    job match {
      case OpenWriter(isCleanBuild, onFinish) =>
        initialiseExecutorsIfNeeded()
        writer = indexDir(project).flatMap(ScalaCompilerReferenceWriter(_, expectedIndexVersion, isCleanBuild))
        onFinish()
      case CloseWriter(onFinish) =>
        val maybeFailure =
          if (!fatalFailures.isEmpty) FatalFailure(fatalFailures.asScala).toOption
          else if (!failedToParse.isEmpty) FailedToParse(failedToParse.asScala).toOption
          else None

        writer.foreach(_.close(!fatalFailures.isEmpty))
        writer = None
        fatalFailures.clear()
        failedToParse.clear()
        onFinish(maybeFailure)
      case ProcessChunkData(data, onFinish) =>
        indexBuildData(data, onFinish)
    }
  }

  def indexBuildData(buildData: ChunkBuildData, onSuccess: () => Unit): Unit =
  //@FIXME: progress not showing
    runWithProgress("Building compiler indices...") { () =>
      if (!parsingExecutor.isShutdown && !indexWritingExecutor.isShutdown) {
        writer.foreach { writer =>

          buildData.removedSources.iterator.map(ProcessDeletedFile).foreach(writerJobQueue.add)
          buildData.compiledClasses.groupBy(_.getSourceFile).foreach {
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

          val progressIndicator = ProgressManager.getInstance().getProgressIndicator

          val indexingTask = indexWritingExecutor.submit(
            toCallable(
              writeParsedClassfile(
                writer,
                progressIndicator,
                buildData.compiledClasses.size + buildData.removedSources.size
              )
            )
          )

          try {
            indexingTask.get()
            if (failedToParse.isEmpty) onSuccess()
          } catch {
            case e: Throwable => onException(e)
          }
        }

        parsingJobs.set(0)
        parserJobQueue.clear()
        writerJobQueue.clear()
      }
    }

  private def runWithProgress[T](title: String)(body: Runnable): Unit =
    ProgressManager
      .getInstance()
      .runProcess(body, new ProgressIndicatorBase() { setText(title) })
}

private object CompilerReferenceIndexer {
  private sealed trait WriterJob
  private final case class ProcessDeletedFile(file:  String)            extends WriterJob
  private final case class ProcessCompiledFile(data: CompiledScalaFile) extends WriterJob
}
