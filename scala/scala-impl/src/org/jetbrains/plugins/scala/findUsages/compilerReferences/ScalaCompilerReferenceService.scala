package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock

import com.intellij.compiler.backwardRefs.LanguageCompilerRefAdapter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.{PsiClass, PsiDocumentManager, PsiElement}
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.CompilationWatcher.CompilerIndicesState
import org.jetbrains.plugins.scala.findUsages.compilerReferences.IndexerFailure.{FailedToParse, FatalFailure}
import org.jetbrains.plugins.scala.findUsages.compilerReferences.IndexerJob._
import org.jetbrains.plugins.scala.indices.protocol.CompilationInfo

import scala.collection.JavaConverters._

private[findUsages] class ScalaCompilerReferenceService(
  project:        Project,
  fileDocManager: FileDocumentManager,
  psiDocManager:  PsiDocumentManager
) extends ProjectComponent {
  import ScalaCompilerReferenceService._

  private[this] val projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex
  private[this] val readerFactory    = ScalaCompilerReferenceReaderFactory
  private[this] val lock             = new ReentrantReadWriteLock()
  private[this] val openCloseLock    = lock.writeLock()
  private[this] val readDataLock     = lock.readLock()

  private[this] val dirtyScopeHolder = new ScalaDirtyScopeHolder(
    project,
    LanguageCompilerRefAdapter.INSTANCES.flatMap(_.getFileTypes.asScala),
    projectFileIndex,
    fileDocManager,
    psiDocManager
  )

  private[this] val activeIndexingPhases = new AtomicInteger()
  private[this] var reader               = Option.empty[ScalaCompilerReferenceReader] /** access only in [[transactionManager.inTransaction()]] */

  private[this] val indexerScheduler =
    new CompilerReferenceIndexerScheduler(project, readerFactory.expectedIndexVersion())

  private[this] val failedToParse         = ContainerUtil.newConcurrentSet[File]()
  private[this] val compilationTimestamps = ContainerUtil.newConcurrentMap[String, Long]()
  private[this] var currentCompilerMode   = CompilerMode.forProject(project)
  private[this] val messageBus            = project.getMessageBus

  private[this] val publisher = new CompilerIndicesEventPublisher {
    override def compilerModeChanged(mode: CompilerMode): Unit = {
      // IDEA JPS compiler <-> sbt shell change happened
      // current index must be invalidated
      onIndexCorruption()
      currentCompilerMode = mode
    }

    override def onCompilationStart(): Unit                  = closeReader(incrementBuildCount = true)
    override def onError(): Unit                             = onIndexCorruption()
    override def onCompilationFinish(): Unit                 = indexerScheduler.schedule("Open compiler index reader", () => openReader())
    override def startIndexing(isCleanBuild: Boolean): Unit  = indexerScheduler.schedule(OpenWriter(isCleanBuild))

    override def processCompilationInfo(info: CompilationInfo, isOffline: Boolean): Unit = {
      val modules = info.affectedModules(project).map(_.getName)
      logger.debug(s"Scheduled building index for modules ${modules.mkString("[", ", ", "]")}")

      indexerScheduler.schedule(ProcessCompilationInfo(info, () => {
        if (!isOffline) { // do not mark modules as up-to-date when indexing 'offline' sbt compilations
          modules.foreach(compilationTimestamps.put(_, info.startTimestamp))
          logger.debug(s"Compiler indices for modules ${modules.mkString(", ")} are updated.")
          dirtyScopeHolder.compilationInfoIndexed(info)
        }

        if (!failedToParse.isEmpty) {
          // if we failed to parse some classes during previous indexing
          // phase running concurrently with this one, check if current phase handled
          // these files and therefore restored index consitency
          failedToParse.removeAll(info.removedSources.asJava)
          failedToParse.removeAll(info.generatedClasses.map(_.output).asJava)
        }
      }))
    }

    override def finishIndexing(): Unit = indexerScheduler.schedule(CloseWriter(_.foreach(processIndexingFailure)))
  }

  private[this] val transactionManager: TransactionManager[CompilerIndicesState] =
    new TransactionManager[CompilerIndicesState] {
      override def inTransaction[T](body: CompilerIndicesState => T): T =
        openCloseLock.locked(body((currentCompilerMode, publisher)))
    }

  private[this] def onIndexCorruption(): Unit = transactionManager.inTransaction { _ =>
    val index = reader.map(_.getIndex())
    dirtyScopeHolder.reset()
    indexerScheduler.schedule(InvalidateIndex(index))
    indexerScheduler.schedule("Index invalidation callback", () => {
      logger.warn(s"Compiler indices were corrupted and invalidated.")
      activeIndexingPhases.set(0)
      failedToParse.clear()
    })
  }

  private[this] def closeReader(incrementBuildCount: Boolean): Unit = transactionManager.inTransaction { _ =>
    if (incrementBuildCount) {
      activeIndexingPhases.incrementAndGet()
      dirtyScopeHolder.indexingStarted()
    }

    reader.foreach(_.close(false))
    reader = None
  }

  private[this] def openReader(): Unit = transactionManager.inTransaction { _ =>
    if (activeIndexingPhases.get() != 0) {
      dirtyScopeHolder.indexingFinished()
      activeIndexingPhases.decrementAndGet()

      if (activeIndexingPhases.get() == 0 && project.isOpen) {
        if (false/*&& failedToParse.nonEmpty*/) { // FIXME
          logger.error(
            s"Fatal indexing failure, failed to parse the following " +
              s"class files ${failedToParse.asScala.mkString("[\n", "  \n", "]")}. " +
              s"Compiler index will be invalidated."
          )
          onIndexCorruption()
        } else {
          reader = Option(readerFactory.create(project))
          messageBus.syncPublisher(CompilerReferenceServiceStatusListener.topic).onIndexingFinished()
        }
      }
    }
  }

  private[this] def processIndexingFailure(failure: IndexerFailure): Unit =
    transactionManager.inTransaction { _ =>
      failure match {
        case FailedToParse(failures) =>
          failures.foreach(f => logger.info(f.errorMessage, f.cause))
          failedToParse.addAll(failures.flatMap(_.classfiles).asJavaCollection)
        case FatalFailure(cause) =>
          logger.error(s"Fatal failure occured while trying to build compiler indices", cause)
          onIndexCorruption()
      }
    }

  override def projectOpened(): Unit =
    if (CompilerIndicesSettings(project).indexingEnabled || ApplicationManager.getApplication.isUnitTestMode) {
      new JpsCompilationWatcher(project, transactionManager).start()
      new SbtCompilationWatcher(project, transactionManager, readerFactory.expectedIndexVersion()).start()

      dirtyScopeHolder.markProjectAsOutdated()
      dirtyScopeHolder.installVFSListener()

      Disposer.register(project, () => {
        openCloseLock.locked {
          if (isIndexingInProgress) {
            // if the project is force-closed while indexing is in progress - invalidate index
            indexDir(project).foreach(CompilerReferenceIndex.removeIndexFiles)
          }
          closeReader(incrementBuildCount = false)
        }
      })
    }

  private[this] def toCompilerRef(e: PsiElement): Option[CompilerRef] = readDataLock.locked {
    for {
      r       <- reader
      file    <- PsiUtilCore.getVirtualFile(e).toOption
      adapter <- LanguageCompilerRefAdapter.findAdapter(file).toOption
    } yield adapter.asCompilerRef(e, r.getNameEnumerator)
  }

  private[this] def withReader(
    target:      PsiElement,
    filterScope: Boolean
  )(
    builder: ScalaCompilerReferenceReader => CompilerRef => Set[UsagesInFile]
  ): Set[Timestamped[UsagesInFile]] = {
    val usages = Set.newBuilder[UsagesInFile]

    for {
      ref <- toCompilerRef(target)
      r   <- reader
    } usages ++= builder(r)(ref)

    val result =
      if (filterScope) usages.result().filterNot(usage => dirtyScopeHolder.contains(usage.file))
      else             usages.result()

    result.map { usage =>
      val module = projectFileIndex.getModuleForFile(usage.file)
      val ts     = compilationTimestamps.getOrDefault(module.getName, -1)
      Timestamped(ts, usage)
    }
  }

  def SAMInheritorsOf(aClass: PsiClass, filterScope: Boolean = false): Set[Timestamped[UsagesInFile]] =
    readDataLock.locked(withReader(aClass, filterScope)(_.SAMInheritorsOf))

  /**
   * Returns usages only from up-to-date compiled scope.
   */
  def usagesOf(target: PsiElement, filterScope: Boolean = false): Set[Timestamped[UsagesInFile]] =
    readDataLock.locked {
      val actualTarget = ScalaCompilerRefAdapter.bytecodeElement(target)
      withReader(actualTarget, filterScope)(_.usagesOf)
    }

  def isIndexingInProgress: Boolean = activeIndexingPhases.get() != 0

  // transactions MUST BE SHORT (they are used in UI thread in SbtProjectSettingsControl)
  def inTransaction[T](body: CompilerIndicesState => T): T = transactionManager.inTransaction(body)

  def invalidateIndex(): Unit                    = onIndexCorruption()
  def getDirtyScopeHolder: ScalaDirtyScopeHolder = dirtyScopeHolder

  def scopeWithoutReferences(target: PsiElement): GlobalSearchScope = {
    //FIXME
    GlobalSearchScope.EMPTY_SCOPE
  }
}

object ScalaCompilerReferenceService {
  private val logger = Logger.getInstance(classOf[ScalaCompilerReferenceService])

  def apply(project: Project): ScalaCompilerReferenceService =
    project.getComponent(classOf[ScalaCompilerReferenceService])
}
