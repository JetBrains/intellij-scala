package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.File
import java.util.concurrent.ConcurrentHashMap

import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase.{IndexCloseReason, IndexOpenReason}
import com.intellij.compiler.backwardRefs.{CompilerReferenceServiceBase, DirtyScopeHolder}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiDocumentManager, PsiElement}
import com.intellij.util.messages.MessageBus
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.CompilationWatcher.CompilerIndicesState
import org.jetbrains.plugins.scala.findUsages.compilerReferences.IndexerFailure.{FailedToParse, FatalFailure}
import org.jetbrains.plugins.scala.findUsages.compilerReferences.IndexerJob._
import org.jetbrains.plugins.scala.indices.protocol.CompilationInfo

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.control.NonFatal

/**
 *  NOTICE: all index manipulations must be done either via subclassing [[IndexerJob]] and adding custom processing logic
 *          or in callbacks of existing jobs.
 */
private[findUsages] class ScalaCompilerReferenceService(
  project:             Project,
  fileDocumentManager: FileDocumentManager,
  psiDocumentManager:  PsiDocumentManager
) extends CompilerReferenceServiceBase[ScalaCompilerReferenceReader](
      project,
      fileDocumentManager,
      psiDocumentManager,
      ScalaCompilerReferenceReaderFactory,
      (connection, affectedModules) =>
        connection.subscribe(
          CompilerReferenceServiceStatusListener.topic,
          new CompilerReferenceServiceStatusListener {
            override def modulesUpToDate(affectedModuleNames: Iterable[String]): Unit =
              affectedModules.addAll(affectedModuleNames.toSet.asJava)
          }
      )
    ) { self =>
  import ScalaCompilerReferenceService._

  private[this] val indexerScheduler =
    new CompilerReferenceIndexerScheduler(project, myReaderFactory.expectedIndexVersion())

  private[this] val failedToParse: mutable.Set[File]                       = mutable.HashSet.empty[File]
  private[this] val compilationTimestamps: ConcurrentHashMap[String, Long] = new ConcurrentHashMap[String, Long]
  private[this] var currentCompilerMode: CompilerMode                      = CompilerMode.forProject(project)
  private[this] val messageBus: MessageBus                                 = project.getMessageBus

  private[this] val publisher = new CompilerIndicesEventPublisher {
    override def compilerModeChanged(mode: CompilerMode): Unit = {
      // IDEA JPS compiler <-> sbt shell change happened
      // current index must be invalidated
      onIndexCorruption()
      currentCompilerMode = mode
    }

    override def onCompilationStart(): Unit                  = closeReader()
    override def onError(): Unit                             = onIndexCorruption()
    override def onCompilationFinish(): Unit                 = indexerScheduler.schedule(() => openReader())
    override def startIndexing(isCleanBuild: Boolean): Unit  = indexerScheduler.schedule(OpenWriter(isCleanBuild))

    override def processCompilationInfo(info: CompilationInfo, isOffline: Boolean): Unit = {
      val modules = info.affectedModules(project).map(_.getName)
      logger.debug(s"Scheduled building index for modules ${modules.mkString("[", ", ", "]")}")

      indexerScheduler.schedule(ProcessCompilationInfo(info, () => {
        if (!isOffline) { // do not mark modules as up-to-date when indexing 'offline' sbt compilations
          modules.foreach(compilationTimestamps.put(_, info.startTimestamp))
          logger.debug(s"Compiler indices for modules ${modules.mkString(", ")} are updated.")
          val publisher = messageBus.syncPublisher(CompilerReferenceServiceStatusListener.topic)
          try publisher.modulesUpToDate(modules)
          catch { case NonFatal(e) => logger.error("Error in CompilerReferenceServiveStatus listener event.", e) }
        }

        if (failedToParse.nonEmpty) {
          // if we failed to parse some classes during previous indexing
          // phase running concurrently with this one, check if current phase handled
          // these files and therefore restored index consitency
          failedToParse --= info.removedSources
          failedToParse --= info.generatedClasses.map(_.output)
        }
      }))
    }

    override def finishIndexing(): Unit = indexerScheduler.schedule(CloseWriter(_.foreach(processIndexingFailure)))
  }

  private[this] val transactionManager: TransactionManager[CompilerIndicesState] =
    new TransactionManager[CompilerIndicesState] {
      override def inTransaction[T](body: CompilerIndicesState => T): T =
        myOpenCloseLock.locked(body((currentCompilerMode, publisher)))
    }

  private[this] def onIndexCorruption(): Unit = transactionManager.inTransaction { _ =>
    indexerScheduler.schedule(InvalidateIndex)
    indexerScheduler.schedule(() => {
      logger.warn(s"Compiler indices were corrupted and invalidated.")
      myActiveBuilds = 1
      failedToParse.clear()
      myDirtyScopeHolder.upToDateChecked(false)
      openReader()
    })
  }

  /**
   * Both [[closeReader]] and [[openReader]] methods serve to satisfy DirtyScopeHolder
   * constraints (i.e. we treat any number of concurrent indexing phases as single
   * super-phase which starts with first and ends with last one).
   * //FIXME: rewrite DirtyScopeHolder to support our case.
   */
  private[this] def closeReader(): Unit = transactionManager.inTransaction { _ =>
    if (myActiveBuilds == 0) closeReaderIfNeed(IndexCloseReason.COMPILATION_STARTED)
    else                     myActiveBuilds += 1
  }

  private[this] def openReader(): Unit = transactionManager.inTransaction { _ =>
    if (myActiveBuilds == 1) {
      if (false/*&& failedToParse.nonEmpty*/) { // FIXME
        logger.error(
          s"Fatal indexing failure, failed to parse the following " +
            s"class files ${failedToParse.mkString("[\n", "  \n", "]")}. " +
            s"Compiler index will be invalidated."
        )
        onIndexCorruption()
      } else {
        openReaderIfNeed(IndexOpenReason.COMPILATION_FINISHED)
        messageBus.syncPublisher(CompilerReferenceServiceStatusListener.topic).onIndexingFinished()
      }
    } else if (myActiveBuilds > 0) myActiveBuilds -= 1
  }

  private[this] def processIndexingFailure(failure: IndexerFailure): Unit =
    transactionManager.inTransaction { _ =>
      failure match {
        case FailedToParse(classes) => failedToParse ++= classes
        case FatalFailure(causes) =>
          logger.error("Fatal failure occured while trying to build compiler indices.")
          causes.foreach(logger.error("Indexing failure", _))
          onIndexCorruption()
      }
    }

  override def projectOpened(): Unit = if (CompilerIndicesSettings(project).getClassfileIndexingEnabled) {
    new JpsCompilationWatcher(project, transactionManager).start()
    new SbtCompilationWatcher(project, transactionManager, myReaderFactory.expectedIndexVersion()).start()

    myDirtyScopeHolder.installVFSListener()
    markAsOutdated(false)

    Disposer.register(project, () => {
      myOpenCloseLock.locked {
        if (isIndexingInProgress) {
          // if the project is force-closed while indexing is in progress - invalidate index
          indexDir(project).foreach(CompilerReferenceIndex.removeIndexFiles)
        }
        closeReaderIfNeed(IndexCloseReason.PROJECT_CLOSED)
      }
    })
  }

  /** Should only be called under [[myReadDataLock]] */
  private[this] def withReader(
    target:      PsiElement,
    filterScope: Boolean
  )(
    builder: ScalaCompilerReferenceReader => CompilerRef => Set[UsagesInFile]
  ): Set[Timestamped[UsagesInFile]] = {
    val usages = Set.newBuilder[UsagesInFile]

    for {
      info    <- asCompilerElements(target, false, false).toOption
      reader  <- myReader.toOption
      targets = info.searchElements
    } yield targets.foreach(usages ++= builder(reader)(_))

    val result = if (filterScope) {
      val dirtyScope = dirtyScopeForDefinition(target)
      usages.result().filterNot(usage => dirtyScope.contains(usage.file))
    } else usages.result()

    result.map { usage =>
      val module = myProjectFileIndex.getModuleForFile(usage.file)
      val ts     = compilationTimestamps.getOrDefault(module.getName, -1)
      Timestamped(ts, usage)
    }
  }

  def SAMInheritorsOf(aClass: PsiClass, filterScope: Boolean = false): Set[Timestamped[UsagesInFile]] =
    myReadDataLock.locked(withReader(aClass, filterScope)(_.SAMInheritorsOf))

  /**
   * Returns usages only from up-to-date compiled scope.
   */
  def usagesOf(target: PsiElement, filterScope: Boolean = false): Set[Timestamped[UsagesInFile]] =
    myReadDataLock.locked {
      val actualTarget = ScalaCompilerRefAdapter.bytecodeElement(target)
      withReader(actualTarget, filterScope)(_.usagesOf)
    }

  def isIndexingInProgress: Boolean = myActiveBuilds != 0

  def dirtyScopeForDefinition(e: PsiElement): GlobalSearchScope = {
    import com.intellij.psi.search.GlobalSearchScope._

    val dirtyModules = myDirtyScopeHolder.getAllDirtyModules

    val dirtyModulesScopes: Array[GlobalSearchScope] =
      dirtyModules.asScala.map(_.getModuleScope)(collection.breakOut)

    val elemModule = inReadAction(ModuleUtilCore.findModuleForPsiElement(e).toOption)

    val dependentsScope =
      elemModule.collect { case m if dirtyModules.contains(m) => m.getModuleTestsWithDependentsScope }
        .getOrElse(EMPTY_SCOPE)

    union(dirtyModulesScopes :+ dependentsScope)
  }

  // transactions MUST BE SHORT (they are used in UI thread in SbtProjectSettingsControl)
  def inTransaction[T](body: CompilerIndicesState => T): T = transactionManager.inTransaction(body)

  def invalidateIndex(): Unit = onIndexCorruption()

  def dirtyScopeHolder: DirtyScopeHolder = myDirtyScopeHolder
}

object ScalaCompilerReferenceService {
  private val logger = Logger.getInstance(classOf[ScalaCompilerReferenceService])

  def apply(project: Project): ScalaCompilerReferenceService =
    project.getComponent(classOf[ScalaCompilerReferenceService])
}
