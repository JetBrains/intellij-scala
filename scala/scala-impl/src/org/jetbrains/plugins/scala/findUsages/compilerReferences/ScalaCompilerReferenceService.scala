package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util.UUID

import com.intellij.compiler.CompilerReferenceService
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase.{IndexCloseReason, IndexOpenReason}
import com.intellij.compiler.server.{BuildManagerListener, CustomBuilderMessageHandler}
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
import com.intellij.util.messages.MessageBus
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.plugin.scala.compilerReferences.{ChunkBuildData, ScalaCompilerReferenceIndexBuilder => Builder}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.IndexerFailure.{FailedToParse, FatalFailure}
import org.jetbrains.plugins.scala.findUsages.compilerReferences.IndexerJob.{CloseWriter, OpenWriter, ProcessChunkData}
import org.jetbrains.plugins.scala.findUsages.compilerReferences.SbtCompilationListener.ProjectIdentifier
import org.jetbrains.plugins.scala.findUsages.compilerReferences.SbtCompilationListener.ProjectIdentifier._
import org.jetbrains.plugins.scala.project._

import scala.collection.JavaConverters._
import scala.collection.mutable

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

  // guarded by myOpenCloseLock
  private[this] val failedToParse = mutable.HashSet.empty[String]

  @volatile private[this] var isUpToDateChecked              = false
  @volatile private[this] var lastCompilationTimestamp: Long = -1

  private[this] def handleBuilderMessage(messageType: String, messageText: String, bus: MessageBus): Unit = {
    import org.jetbrains.plugin.scala.compilerReferences.Codec._

    val publisher = bus.syncPublisher(CompilerReferenceServiceStatusListener.topic)
    logger.debug(s"Received compiler index builder message: $messageType: $messageText.")

    messageType match {
      case Builder.chunkBuildDataType =>
        val buildData = messageText.decode[ChunkBuildData]

        // @TODO: what if we actually get malformed data
        buildData.foreach { data =>
          val modules = data.affectedModules
          logger.debug(s"Scheduled building index for modules ${modules.mkString("[", ", ", "]")}")

          indexerScheduler.schedule(ProcessChunkData(data, () => {
            publisher.modulesUpToDate(modules)

            if (failedToParse.nonEmpty) {
              // if we failed to parse some classes during previous indexing
              // phase running concurrently with this one, check if current phase handled
              // these files and therefore restored index consitency
              failedToParse --= data.removedSources
              failedToParse --= data.compiledClasses.map(_.getOutputFile.getPath)
            }
          }))
        }
      case Builder.compilationStartedType =>
        val isCleanBuild = messageText.decode[Boolean]
        isCleanBuild.foreach(isClean => indexerScheduler.schedule(OpenWriter(isClean, () => ())))
      case Builder.compilationFinishedType =>
        val timestamp = messageText.decode[Long]
        timestamp.foreach(lastCompilationTimestamp = _)
        indexerScheduler.schedule(CloseWriter { maybeFailure =>
          openReaderOrInvalidateIndex(maybeFailure)
          publisher.onIndexingFinished(maybeFailure)
        })
    }
  }

  private[this] def onIndexCorruption(): Unit = {
    failedToParse.clear()
    removeIndexFiles(project)
  }

  private[this] def openReaderOrInvalidateIndex(
    failure: Option[IndexerFailure]
  ): Unit = withLock(myOpenCloseLock) {
    failure.foreach {
      case FailedToParse(classes) => failedToParse ++= classes.map(_.getPath)
      case FatalFailure(causes) =>
        logger.error("Fatal failure occured while trying to build compiler indices.")
        causes.foreach(logger.error)
        onIndexCorruption()
    }

    if (myActiveBuilds == 1 && failedToParse.nonEmpty) {
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

    ProjectManager.getInstance().addProjectManagerListener(project => {
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

    connection.subscribe(BuildManagerListener.TOPIC, new BuildManagerListener {
      override def buildStarted(
        project:    Project,
        sessionId:  UUID,
        isAutomake: Boolean
      ): Unit = if (project == self.project) {
        messageBus.syncPublisher(CompilerReferenceServiceStatusListener.topic).beforeIndexingStarted()
        closeReaderIfNeed(IndexCloseReason.COMPILATION_STARTED)
      }
    })

    connection.subscribe(
      CustomBuilderMessageHandler.TOPIC,
      new CustomBuilderMessageHandler {
        override def messageReceived(
          builderId:   String,
          messageType: String,
          messageText: String
        ): Unit = if (builderId == Builder.id) handleBuilderMessage(messageType, messageText, messageBus)
      }
    )

    myDirtyScopeHolder.installVFSListener()
    markAsOutdated(false)

    executeOnPooledThread {
      val validIndexExists = upToDateCompilerIndexExists(project, myReaderFactory.expectedIndexVersion())
      val compilerManager = CompilerManager.getInstance(project)

      if (validIndexExists) {
        val publisher = messageBus.syncPublisher(CompilerReferenceServiceStatusListener.topic)

        closeReaderIfNeed(IndexCloseReason.COMPILATION_STARTED)

        project.sourceModules.foreach { m =>
          val scope = compilerManager.createModuleCompileScope(m, false)
          if (compilerManager.isUpToDate(scope)) publisher.modulesUpToDate(Set(m.getName))
        }

        openReaderIfNeed(IndexOpenReason.COMPILATION_FINISHED)

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
      info   <- asCompilerElements(target, false, false).toOption
      reader <- myReader.toOption
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
