package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.File

import com.intellij.compiler.CompilerReferenceService
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase.{IndexCloseReason, IndexOpenReason}
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiDocumentManager, PsiElement}
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.IndexerFailure.{FailedToParse, FatalFailure}
import org.jetbrains.plugins.scala.findUsages.compilerReferences.IndexerJob.{CloseWriter, InvalidateIndex, OpenWriter, ProcessChunkData}
import org.jetbrains.plugins.scala.indices.protocol.CompilationInfo
import org.jetbrains.plugins.scala.project._
import org.jetbrains.sbt.project.settings.SbtShellSettingsListener

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

  // to be accessed only from job callbacks
  private[this] val failedToParse = mutable.HashSet.empty[File]

  @volatile private[this] var isUpToDateChecked = false
  //@TODO: separate timestamps for each module
  @volatile private[this] var lastCompilationTimestamp: Long = -1

  private[this] var currentCompilerMode: CompilerMode = CompilerMode.forProject(project)

  private[this] def isJPSMode: Boolean = currentCompilerMode == CompilerMode.JPS

  private[this] def onIndexCorruption(): Unit = {
    failedToParse.clear()
    indexerScheduler.schedule(InvalidateIndex)
  }

  private[this] def onCompilationStart(): Unit = {
    try project.getMessageBus.syncPublisher(CompilerReferenceServiceStatusListener.topic).beforeIndexingStarted()
    catch { case NonFatal(e) => logger.error(e) }

    closeReaderIfNeed(IndexCloseReason.COMPILATION_STARTED)
  }

  // to be called only from job callbacks
  private[this] def openReader(
    failure: Option[IndexerFailure]
  ): Unit = withLock(myOpenCloseLock) {
    failure.foreach {
      case FailedToParse(classes) => failedToParse ++= classes
      case FatalFailure(causes) =>
        logger.error("Fatal failure occured while trying to build compiler indices.")
        causes.foreach(logger.error)
        onIndexCorruption()
    }

    if (myActiveBuilds == 1 /*&& failedToParse.nonEmpty*/) { // FIXME
      logger.error(
        s"Fatal indexing failure, failed to parse the following " +
          s"class files ${failedToParse.mkString("[\n", "  \n", "]")}. " +
          s"Compiler index will be invalidated."
      )
      onIndexCorruption()
    }
    openReaderIfNeed(IndexOpenReason.COMPILATION_FINISHED)
  }

  override def projectOpened(): Unit = if (CompilerReferenceService.isEnabled) {
    val messageBus = project.getMessageBus
    val connection = messageBus.connect(project)

    ProjectManager
      .getInstance()
      .addProjectManagerListener(project => {
        if (project != self.project) true
        else if (isIndexingInProgress) {
          val message =
            """
              |Background bytecode indexing is active. Are you sure you want to stop it and exit?
              |Exiting right now will lead to index invalidation.
          """.stripMargin

          val title    = ApplicationBundle.message("exit.confirm.title")
          val exitCode = Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon)
          exitCode == Messages.YES
        } else true
      })

    connection.subscribe(SbtShellSettingsListener.topic, new SbtShellSettingsListener {
      override def buildWithSbtShellSettingChanged(useSbtShell: Boolean): Unit = {
        // IDEA JPS compiler <-> sbt shell change happened
        // current index must be invalidated
        onIndexCorruption()

        currentCompilerMode =
          if (useSbtShell) CompilerMode.SBT
          else             CompilerMode.JPS
      }
    })

    connection.subscribe(CompilerIndicesCompilationWatcher.topic, new CompilerIndicesCompilationWatcher {
      override def onCompilationStart(): Unit = self.onCompilationStart()
      override def onError(): Unit            = onIndexCorruption()

      override def startIndexing(isCleanBuild: Boolean): Unit = indexerScheduler.schedule(OpenWriter(isCleanBuild))

      override def processCompilationInfo(info: CompilationInfo, isOffline: Boolean = false): Unit = {
        val modules = info.affectedModules
        logger.debug(s"Scheduled building index for modules ${modules.mkString("[", ", ", "]")}")

        indexerScheduler.schedule(ProcessChunkData(info, () => {
          if (!isOffline) { // do not mark modules as up-to-date when indexing 'offline' sbt compilations
            val publisher = messageBus.syncPublisher(CompilerReferenceServiceStatusListener.topic)
            try publisher.modulesUpToDate(modules)
            catch { case NonFatal(e) => logger.error(e) }
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

      override def finishIndexing(timestamp: Long): Unit = {
        lastCompilationTimestamp = timestamp

        indexerScheduler.schedule(CloseWriter { maybeFailure =>
          openReader(maybeFailure)
          val publisher = messageBus.syncPublisher(CompilerReferenceServiceStatusListener.topic)

          try publisher.onIndexingFinished(maybeFailure)
          catch { case NonFatal(e) => logger.error(e) }
        })
      }
    })

    new JpsCompilationWatcher(project, () => currentCompilerMode).start()
    new SbtCompilationWatcher(project, () => currentCompilerMode).start()

    myDirtyScopeHolder.installVFSListener()
    markAsOutdated(false)

    executeOnPooledThread {
      if (isJPSMode) { // up to date checks are not supported for sbt-compiled projects
        val validIndexExists = upToDateCompilerIndexExists(project, myReaderFactory.expectedIndexVersion())
        val compilerManager  = CompilerManager.getInstance(project)

        if (validIndexExists) {
          val publisher = messageBus.syncPublisher(CompilerReferenceServiceStatusListener.topic)

          closeReaderIfNeed(IndexCloseReason.COMPILATION_STARTED)

          project.sourceModules.foreach { m =>
            val scope = compilerManager.createModuleCompileScope(m, false)

            if (compilerManager.isUpToDate(scope))
              try publisher.modulesUpToDate(Set(m.getName))
              catch { case NonFatal(e) => logger.error(e) }
          }

          openReaderIfNeed(IndexOpenReason.COMPILATION_FINISHED)
        }
      }
      isUpToDateChecked = true
    }

    Disposer.register(project, () => closeReaderIfNeed(IndexCloseReason.PROJECT_CLOSED))
  }

  /** Should only be called under [[myReadDataLock]] */
  private[this] def withReader(
    target:      PsiElement,
    filterScope: Boolean = false
  )(
    builder: ScalaCompilerReferenceReader => CompilerRef => Set[UsagesInFile]
  ): Timestamped[Set[UsagesInFile]] = {
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

    Timestamped(lastCompilationTimestamp, result)
  }

  def SAMInheritorsOf(aClass: PsiClass): Timestamped[Set[UsagesInFile]] =
    withLock(myReadDataLock)(withReader(aClass)(_.SAMInheritorsOf))

  /**
   * Returns usages only from up-to-date compiled scope.
   */
  def usagesOf(target: PsiElement): Timestamped[Set[UsagesInFile]] = withLock(myReadDataLock) {
    val actualTarget = ScalaCompilerRefAdapter.bytecodeElement(target)
    withReader(actualTarget)(_.usagesOf)
  }

  def isCompilerIndexReady: Boolean = isUpToDateChecked

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
}

private[findUsages] object ScalaCompilerReferenceService {
  private val logger = Logger.getInstance(classOf[ScalaCompilerReferenceService])

  def getInstance(project: Project): ScalaCompilerReferenceService =
    project.getComponent(classOf[ScalaCompilerReferenceService])
}
