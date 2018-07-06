package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util
import java.util.function.BiConsumer

import com.intellij.compiler.CompilerReferenceService
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase.{IndexCloseReason, IndexOpenReason}
import com.intellij.compiler.server.CustomBuilderMessageHandler
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiDocumentManager, PsiElement}
import com.intellij.util.messages.{MessageBus, MessageBusConnection}
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.plugin.scala.compilerReferences.{ChunkBuildData, ScalaCompilerReferenceIndexBuilder => Builder}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.IndexerJob.{CloseWriter, OpenWriter, ProcessChunkData}
import org.jetbrains.plugins.scala.project._

import scala.collection.JavaConverters._
import scala.collection.mutable

private[findUsages] class ScalaCompilerReferenceService(
  project:             Project,
  fileDocumentManager: FileDocumentManager,
  psiDocumentManager:  PsiDocumentManager
) extends {
  private[this] val callback: BiConsumer[MessageBusConnection, util.Set[String]] = (connection, affectedModules) =>
    connection.subscribe(
      CompilerReferenceIndexingTopics.indexingStatus,
      new CompilerReferenceIndexingStatusListener {
        override def modulesUpToDate(affectedModuleNames: Iterable[String]): Unit =
          affectedModules.addAll(affectedModuleNames.toSet.asJava)
      }
  )
} with CompilerReferenceServiceBase[ScalaCompilerReferenceReader](
  project,
  fileDocumentManager,
  psiDocumentManager,
  ScalaCompilerReferenceReaderFactory,
  callback
) { self =>
  import ScalaCompilerReferenceService._

  private[this] val indexerScheduler =
    new CompilerReferenceIndexerScheduler(project, myReaderFactory.expectedIndexVersion())

  // guarded by myOpenCloseLock
  private[this] val failedToParse = mutable.HashSet.empty[String]

  @volatile private[this] var isUpToDateChecked = false

  private[this] def handleBuilderMessage(messageType: String, messageText: String, bus: MessageBus): Unit = {
    import org.jetbrains.plugin.scala.compilerReferences.Codec._

    val publisher = bus.syncPublisher(CompilerReferenceIndexingTopics.indexingStatus)
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
        publisher.beforeIndexingStarted()
        val isCleanBuild = messageText.decode[Boolean]
        isCleanBuild.foreach(isClean => indexerScheduler.schedule(OpenWriter(isClean, () => ())))
      case Builder.compilationFinishedType =>
        indexerScheduler.schedule(CloseWriter(publisher.onIndexingFinished))
    }
  }

  private[this] def onIndexCorruption(): Unit = {
    logger.error(
      s"Fatal indexing failure, failed to parse following " +
        s"class files ${failedToParse.mkString("[\n", "  \n", "]")}. " +
        s"Compiler index will be invalidated."
    )
    failedToParse.clear()
    removeIndexFiles(project)
  }

  private[this] def openReaderOrInvalidateIndex(
    failure: Option[IndexerParsingFailure]
  ): Unit = withLock(myOpenCloseLock) {
    failure.foreach { case IndexerParsingFailure(classes) => failedToParse ++= classes.map(_.getPath) }

    if (myActiveBuilds == 1 && failedToParse.nonEmpty) onIndexCorruption()
    openReaderIfNeed(IndexOpenReason.COMPILATION_FINISHED)
  }

  override def projectOpened(): Unit = if (CompilerReferenceService.isEnabled) {
    val messageBus = project.getMessageBus
    val connection = messageBus.connect(project)

    connection.subscribe(
      CompilerReferenceIndexingTopics.indexingStatus,
      new CompilerReferenceIndexingStatusListener {
        override def beforeIndexingStarted(): Unit =
          closeReaderIfNeed(IndexCloseReason.COMPILATION_STARTED)
        override def onIndexingFinished(failure: Option[IndexerParsingFailure]): Unit =
          openReaderOrInvalidateIndex(failure)
      }
    )

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
      val compilerManager  = CompilerManager.getInstance(project)

      if (validIndexExists) {
        val publisher = messageBus.syncPublisher(CompilerReferenceIndexingTopics.indexingStatus)

        project.sourceModules.foreach { m =>
          val scope = compilerManager.createModuleCompileScope(m, false)
          if (compilerManager.isUpToDate(scope)) publisher.modulesUpToDate(Set(m.getName))
        }

        myActiveBuilds += 1
        publisher.onIndexingFinished(None)
      }
      isUpToDateChecked = true
    }

    Disposer.register(project, () => closeReaderIfNeed(IndexCloseReason.PROJECT_CLOSED))
  }

  /** Should only be called under [[myReadDataLock]] */
  private[this] def withReader(
    target: PsiElement,
    filterScope: Boolean = false
  )(
    builder: ScalaCompilerReferenceReader => CompilerRef => Set[UsagesInFile]
  ): Set[UsagesInFile] = {
    val usages = Set.newBuilder[UsagesInFile]

    for {
      info   <- asCompilerElements(target, false, false).toOption
      reader <- myReader.toOption
      targets = info.searchElements
    } yield targets.foreach(usages ++= builder(reader)(_))

    if (filterScope) {
      val dirtyScope = dirtyScopeForDefinition(target)
      usages.result().filterNot(usage => dirtyScope.contains(usage.file))
    } else usages.result()
  }

  def SAMInheritorsOf(aClass: PsiClass): Set[UsagesInFile] = withLock(myReadDataLock)(
    withReader(aClass)(_.SAMInheritorsOf)
  )

  /**
   * Returns usages only from up-to-date compiled scope.
   */
  def usagesOf(target: PsiElement): Set[UsagesInFile] = withLock(myReadDataLock) {
    val actualTarget = ScalaCompilerRefAdapter.bytecodeElement(target)
    withReader(actualTarget)(_.usagesOf)
  }

  def isCompilerIndexReady: Boolean = isUpToDateChecked

  def isInProgress: Boolean = myActiveBuilds != 0 ||
    CompilerManager.getInstance(project).isCompilationActive

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
