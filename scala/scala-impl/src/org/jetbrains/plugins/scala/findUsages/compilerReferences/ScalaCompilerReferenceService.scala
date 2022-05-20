package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.compiler.backwardRefs.LanguageCompilerRefAdapter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.{Project, ProjectManagerListener}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.search.{ScopeOptimizer, SearchScope}
import com.intellij.psi.{PsiClass, PsiDocumentManager, PsiElement}
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.compilation._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.indices.IndexerFailure._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.indices.IndexingStage._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.indices._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.settings.CompilerIndicesSettings
import org.jetbrains.plugins.scala.indices.protocol.CompilationInfo
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.project.ProjectExt

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicInteger, LongAdder}
import java.util.concurrent.locks.ReentrantReadWriteLock
import scala.jdk.CollectionConverters._

final private[findUsages] class ScalaCompilerReferenceService(project: Project) extends ModificationTracker {
  import ScalaCompilerReferenceService._

  private[this] val compilationCount = new LongAdder
  private[this] val projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex
  private[this] val lock             = new ReentrantReadWriteLock()
  private[this] val openCloseLock    = lock.writeLock()
  private[this] val readDataLock     = lock.readLock()

  private[this] val dirtyScopeHolder = new ScalaDirtyScopeHolder(
    project,
    LanguageCompilerRefAdapter.EP_NAME.getExtensions.flatMap(_.getFileTypes.asScala),
    projectFileIndex,
    FileDocumentManager.getInstance(),
    PsiDocumentManager.getInstance(project),
    this
  )

  private[this] val activeIndexingPhases = new AtomicInteger()

  /** access only in [[transactionManager.inTransaction()]] */
  private[this] var reader = Option.empty[ScalaCompilerReferenceReader]

  private[this] val indexerScheduler =
    new CompilerReferenceIndexerScheduler(project, ScalaCompilerReferenceReaderFactory.expectedIndexVersion)

  private[this] val failedToParse                       = ContainerUtil.newConcurrentSet[File]()
  private[this] val compilationTimestamps               = new ConcurrentHashMap[String, Long]()
  private[this] val messageBus                          = project.getMessageBus
  private[this] var currentCompilerMode: CompilerMode   = CompilerMode.JPS

  private[this] val publisher = new CompilerIndicesEventPublisher {
    override def onCompilerModeChange(mode: CompilerMode): Unit = {
      // IDEA JPS compiler <-> sbt shell change happened
      // current index must be invalidated
      onIndexCorruption()
      currentCompilerMode = mode
      logCompilerIndicesEvent(s"onCompilerModeChange. new mode: $mode")
    }

    override def onCompilationStart(): Unit = {
      closeReader(incrementBuildCount = true)
      logCompilerIndicesEvent(s"onCompilationStart. active indexing phases: ${activeIndexingPhases.get()}")
    }

    override def startIndexing(isCleanBuild: Boolean): Unit = {
      indexerScheduler.schedule(OpenWriter(isCleanBuild))
      logCompilerIndicesEvent(s"startIndexing. clean build: $isCleanBuild")
    }

    override def onError(message: String, cause: Option[Throwable]): Unit = {
      logger.error(message, cause.orNull)
      onIndexCorruption()
    }

    override def onCompilationFinish(success: Boolean): Unit = {
      indexerScheduler.schedule(ScalaBundle.message("open.compiler.index.reader"), () => {
        openReader(success)
        logCompilerIndicesEvent(
          s"onCompilationFinish. success: $success, active indexing phases: ${activeIndexingPhases.get()}"
        )
      })
    }

    override def processCompilationInfo(info: CompilationInfo, isOffline: Boolean): Unit = {
      val modules = info.affectedModules(project).map(_.getName)
      logger.debug(s"[compiler indices] processCompilationInfo. offline: $isOffline")

      indexerScheduler.schedule(ProcessCompilationInfo(info, () => {
        if (!isOffline) { // do not mark modules as up-to-date when indexing 'offline' sbt compilations
          modules.foreach(compilationTimestamps.put(_, info.startTimestamp))
          logger.debug(s"[compiler indices] Reindexed ${info.generatedClasses.size} classfiles.")
          dirtyScopeHolder.compilationInfoIndexed(info)
          messageBus.syncPublisher(CompilerReferenceServiceStatusListener.topic).onCompilationInfoIndexed(modules)
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

    override def finishIndexing(): Unit = {
      indexerScheduler.schedule(CloseWriter(_.foreach(processIndexingFailure)))
      logCompilerIndicesEvent("finishIndexing.")
    }
  }

  private[this] val transactionManager: TransactionGuard[CompilerIndicesState] =
    new TransactionGuard[CompilerIndicesState] {
      override def inTransaction[T](body: CompilerIndicesState => T): T =
        openCloseLock.locked(body((currentCompilerMode, publisher)))
    }

  private[this] def onIndexCorruption(): Unit = transactionManager.inTransaction { _ =>
    val index = reader.map(_.getIndex())
    dirtyScopeHolder.reset()
    messageBus.syncPublisher(CompilerReferenceServiceStatusListener.topic).onIndexingPhaseFinished(success = false)
    indexerScheduler.schedule(InvalidateIndex(index))
    indexerScheduler.schedule(ScalaBundle.message("index.invalidation.callback"), () => {
      logger.warn(s"Compiler indices were corrupted and invalidated.")
      activeIndexingPhases.set(0)
      failedToParse.clear()
    })
  }

  private[this] def closeReader(incrementBuildCount: Boolean): Unit = transactionManager.inTransaction { _ =>
    if (incrementBuildCount) {
      if (activeIndexingPhases.getAndIncrement() == 0) {
        messageBus.syncPublisher(CompilerReferenceServiceStatusListener.topic).onIndexingPhaseStarted()
      }
      dirtyScopeHolder.indexingStarted()
    }

    reader.foreach(_.close(false))
    reader = None
  }

  private[this] def openReader(indexingSuccessful: Boolean): Unit = transactionManager.inTransaction { _ =>
    if (activeIndexingPhases.get() > 0) {
      compilationCount.increment()
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
          reader = ScalaCompilerReferenceReaderFactory(project)
          messageBus.syncPublisher(CompilerReferenceServiceStatusListener.topic).onIndexingPhaseFinished(indexingSuccessful)
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

  override def getModificationCount: Long = compilationCount.longValue()

  def initializeReferenceService(): Unit =
    if (CompilerIndicesSettings(project).isBytecodeIndexingActive || ApplicationManager.getApplication.isUnitTestMode) {
      initializedReferenceService
    }

  private lazy val initializedReferenceService: Unit = {
    inTransaction { _ =>
      currentCompilerMode = CompilerMode.forProject(project)

      logger.info(
        s"Initialized ScalaCompilerReferenceService in ${project.getName}, " +
          s"current compiler mode = $currentCompilerMode"
      )
    }

    new JpsCompilationWatcher(project, transactionManager).start()
    new SbtCompilationWatcher(project, transactionManager, ScalaCompilerReferenceReaderFactory.expectedIndexVersion).start()

    dirtyScopeHolder.markProjectAsOutdated()
    dirtyScopeHolder.installVFSListener()

    invokeOnDispose(project.unloadAwareDisposable) {
      openCloseLock.locked {
        if (isIndexingInProgress) {
          // if the project is force-closed while indexing is in progress - invalidate index
          indexDir(project).foreach(CompilerReferenceIndex.removeIndexFiles)
        }
        closeReader(incrementBuildCount = false)
      }
    }
  }

  private[this] def toCompilerRef(e: PsiElement): Option[CompilerRef] = readDataLock.locked {
    for {
      r       <- reader
      file    <- ScalaPsiUtil.fileContext(e).toOption
      adapter <- LanguageCompilerRefAdapter.findAdapter(file).toOption
      ref     <- adapter.asCompilerRef(e, r.getNameEnumerator).toOption
    } yield ref
  }

  private[this] def withReader(target: PsiElement)(
    builder: ScalaCompilerReferenceReader => CompilerRef => Set[UsagesInFile]
  ): Set[Timestamped[UsagesInFile]] = {
    val usages = Set.newBuilder[UsagesInFile]

    for {
      ref <- toCompilerRef(target)
      r   <- reader
    } usages ++= builder(r)(ref)

    usages.result().map { usage =>
      val module = projectFileIndex.getModuleForFile(usage.file)
      val ts     = compilationTimestamps.getOrDefault(module.getName, -1)
      Timestamped(ts, usage)
    }
  }

  def SAMImplementationsOf(aClass: PsiClass, checkDeep: Boolean): Set[Timestamped[UsagesInFile]] =
    readDataLock.locked(withReader(aClass)(_.anonymousSAMImplementations))

  /**
   * Returns usages only from up-to-date compiled scope.
   */
  def usagesOf(target: PsiElement): Set[Timestamped[UsagesInFile]] =
    readDataLock.locked(withReader(target)(_.usagesOf))

  def isIndexingInProgress: Boolean = activeIndexingPhases.get() != 0

  // transactions MUST BE SHORT (they are used in UI thread in SbtProjectSettingsControl)
  def inTransaction[T](body: CompilerIndicesState => T): T = transactionManager.inTransaction(body)

  def invalidateIndex(): Unit                    = onIndexCorruption()
  def getDirtyScopeHolder: ScalaDirtyScopeHolder = dirtyScopeHolder

  initializeReferenceService()
}

object ScalaCompilerReferenceService {
  private val logger = Logger.getInstance(classOf[ScalaCompilerReferenceService])

  private def logCompilerIndicesEvent(message: String): Unit = logger.info(s"[compiler indices] $message")

  private[compilerReferences] type CompilerIndicesState = (CompilerMode, CompilerIndicesEventPublisher)

  def apply(project: Project): ScalaCompilerReferenceService =
    project.getService(classOf[ScalaCompilerReferenceService])

  class Startup extends ProjectManagerListener with StartupActivity {
    // ensure service is initialized with project
    override def runActivity(project: Project): Unit =
      ScalaCompilerReferenceService(project)
  }
}

class ScalaCompilerReferenceScopeOptimizer extends ScopeOptimizer {
  override def getRestrictedUseScope(element: PsiElement): SearchScope = {
    //FIXME
    null
  }
}
