package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.{Lock, ReentrantLock}

import scala.collection.mutable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.jps.incremental.CompiledClass
import org.jetbrains.plugin.scala.compilerReferences.BuildData
import org.jetbrains.plugins.scala.extensions._

private class CompilerReferenceIndexer(project: Project) {
  import CompilerReferenceIndexer._

  private[this] val parsingJobs    = new AtomicInteger(0)
  private[this] val executionLock  = new ReentrantLock()
  private[this] val indexWriteLock = new ReentrantLock()
  private[this] val runningTasks   = mutable.ArrayBuffer.empty[Future[_]]

  private[this] val parserJobQueue = new ConcurrentLinkedQueue[CompiledClass]()
  private[this] val writerJobQueue = new LinkedBlockingDeque[WriterJob](1000)
  private[this] val nThreads       = Runtime.getRuntime.availableProcessors()

  private[this] val parsingExecutor      = Executors.newSingleThreadExecutor
  private[this] val indexWritingExecutor = Executors.newFixedThreadPool(nThreads)

  Disposer.register(project, () => {
    parsingExecutor.shutdownNow()
    indexWritingExecutor.shutdownNow()
    if (executionLock.isLocked) {
      withLock(indexWriteLock) {
        indexDir(project).foreach(CompilerReferenceIndex.removeIndexFiles)
      }
    }
  })

  private def onException(writer: ScalaCompilerReferenceWriter, e: Throwable): Unit = {
    runningTasks.foreach(_.cancel(true))
    parsingJobs.set(0)
    logger.error("An exception occured while trying to build compiler reference index.", e)
    parserJobQueue.clear()
    writerJobQueue.clear()
    runningTasks.clear()
    writer.close(true)
  }

  private def parseClassfiles(writer: ScalaCompilerReferenceWriter): Unit =
    while (!parserJobQueue.isEmpty && !Thread.currentThread().isInterrupted) {
      try {
        val target     = parserJobQueue.poll()
        val sourceFile = Option(ContainerUtil.getFirstItem(target.getSourceFiles))
        val parsed     = ClassfileParser.parse(target.getOutputFile)
        val data       = sourceFile.map(ClassfileData(parsed, _, writer))
        data.foreach(ProcessClassfile andThen writerJobQueue.put)
      } catch {
        case _: InterruptedException => Thread.currentThread().interrupt()
        case e: Throwable            => onException(writer, e)
      }
    }

  private def writeParsedClassfile(
    writer: ScalaCompilerReferenceWriter,
    indicator: ProgressIndicator,
    totalClassfiles: Int
  ): Unit =
    withLock(indexWriteLock) {
      var processed = 0
      indicator.setFraction(0d)

      while ((parsingJobs.get() != 0 || !writerJobQueue.isEmpty) && !Thread.currentThread().isInterrupted) {
        try {
          if (processed % 10 == 0) {
            ProgressManager.checkCanceled()
            indicator.setFraction(processed * 1d / totalClassfiles)
          }

          writer.getRebuildRequestCause.nullSafe.foreach(onException(writer, _))
          val job = writerJobQueue.poll(1, TimeUnit.SECONDS)
          job match {
            case ProcessClassfile(data)   => writer.registerClassfileData(data)
            case ProcessDeletedFile(file) => writer.processDeletedFile(file)
            case null                     =>
          }
          processed += 1
        } catch {
          case _: InterruptedException => Thread.currentThread().interrupt()
          case e: Throwable            => onException(writer, e)
        }
      }
    }

  def writeBuildData(buildData: BuildData, onSuccess: => Any): Unit =
    runWithProgressAsync(s"Building compiler indices...") { progressIndicator =>
      withLock(executionLock) {

        buildData.removedSources.iterator.map(ProcessDeletedFile).foreach(writerJobQueue.add)
        buildData.compiledClasses.foreach(parserJobQueue.add)

        val writer =
          indexDir(project).flatMap(ScalaCompilerReferenceWriter(_, buildData.affectedModules, buildData.isRebuild))

        writer.foreach { writer =>
          val tasks = (1 to nThreads).map(
            _ =>
              toCallable {
                parsingJobs.incrementAndGet()
                parseClassfiles(writer)
                parsingJobs.decrementAndGet()
            }
          )

          val parsingTasks = tasks.map(parsingExecutor.submit(_))

          val indexingTask = indexWritingExecutor.submit(
            toCallable(writeParsedClassfile(writer, progressIndicator, buildData.compiledClasses.size))
          )

          runningTasks ++= parsingTasks
          runningTasks += indexingTask

          try {
            indexingTask.get()
            writer.close(false)
            onSuccess
          } catch {
            case e: InterruptedException  => onException(writer, e)
            case _: CancellationException =>
          }
        }

        runningTasks.clear()
      }
    }

  private def withLock[T](lock: Lock)(body: => T): T = {
    lock.lock()
    val result = body
    lock.unlock()
    result
  }

  private def runWithProgressAsync[T](title: String)(body: ProgressIndicator => T): Unit =
    ProgressManager
      .getInstance()
      .run(new Task.Backgroundable(project, title) {
        override def run(indicator: ProgressIndicator): Unit = body(indicator)
      })
}

private object CompilerReferenceIndexer {
  private val logger = Logger.getInstance(classOf[CompilerReferenceIndexer])

  private sealed trait WriterJob
  private final case class ProcessDeletedFile(file: String)      extends WriterJob
  private final case class ProcessClassfile(data: ClassfileData) extends WriterJob
}
