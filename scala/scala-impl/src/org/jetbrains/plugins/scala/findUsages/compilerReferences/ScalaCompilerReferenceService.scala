package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util
import java.util.UUID
import java.util.function.BiConsumer

import scala.collection.JavaConverters._
import com.intellij.compiler.CompilerReferenceService
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase.{IndexCloseReason, IndexOpenReason}
import com.intellij.compiler.server.{BuildManagerListener, CustomBuilderMessageHandler}
import com.intellij.openapi.compiler.{CompilationStatusListener, CompileContext, CompilerTopics}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.plugin.scala.compilerReferences.{BuildData, CompilerReferenceIndexBuilder}

private[findUsages] class ScalaCompilerReferenceService(
  project: Project,
  fileDocumentManager: FileDocumentManager,
  psiDocumentManager: PsiDocumentManager
) extends {
  private[this] val callback: BiConsumer[MessageBusConnection, util.Set[String]] = (connection, affectedModules) =>
    connection.subscribe(CompilerReferenceIndexingTopics.indexingStatus, new CompilerReferenceIndexingStatusListener {
      override def onIndexingFinished(affectedModuleNames: Iterable[String]): Unit =
        affectedModules.addAll(affectedModuleNames.toSet.asJava)
    })
} with CompilerReferenceServiceBase[ScalaCompilerReferenceReader](
  project,
  fileDocumentManager,
  psiDocumentManager,
  ScalaCompilerReferenceReaderFactory,
  callback
) { self =>
  import ScalaCompilerReferenceService._

  private[this] val indexer = new CompilerReferenceIndexer(project)

  override def projectOpened(): Unit = if (CompilerReferenceService.isEnabled) {
    val messageBus = project.getMessageBus
    val connection = messageBus.connect(project)
    val publisher = messageBus.syncPublisher(CompilerReferenceIndexingTopics.indexingStatus)

    connection.subscribe(BuildManagerListener.TOPIC, new BuildManagerListener {
      override def buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean): Unit =
        if (project == self.project) closeReaderIfNeed(IndexCloseReason.COMPILATION_STARTED)
    })

    connection.subscribe(CompilerReferenceIndexingTopics.indexingStatus, new CompilerReferenceIndexingStatusListener {
      override def onIndexingFinished(affectedModuleNames: Iterable[String]): Unit =
        openReaderIfNeed(IndexOpenReason.COMPILATION_FINISHED)
    })

    connection.subscribe(CompilerTopics.COMPILATION_STATUS, new CompilationStatusListener {
      override def compilationFinished(
        aborted: Boolean,
        errors: Int,
        warnings: Int,
        compileContext: CompileContext
      ): Unit =
        if (aborted) openReaderIfNeed(IndexOpenReason.COMPILATION_FINISHED)
    })

    connection.subscribe(CustomBuilderMessageHandler.TOPIC, new CustomBuilderMessageHandler {
      import org.jetbrains.plugin.scala.compilerReferences.Codec._
      override def messageReceived(builderId: String, messageType: String, messageText: String): Unit =
        if (builderId == CompilerReferenceIndexBuilder.id) {
          val buildData = messageText.decode[BuildData]

          buildData.foreach { data =>
            val modules = data.affectedModules
            logger.debug(s"Building index for modules ${modules.mkString("[", ", ", "]")}")

            indexer.writeBuildData(data, onSuccess = {
              publisher.onIndexingFinished(modules)
              logger.debug(s"Finished building indices for modules ${modules.mkString("[", ", ", "]")}")
            })
          }
        }
    })

    myDirtyScopeHolder.installVFSListener()
    CompilerReferenceServiceBase.executeOnBuildThread(() => markAsOutdated(false))
    Disposer.register(project, () => closeReaderIfNeed(IndexCloseReason.PROJECT_CLOSED))
  }

  override def asCompilerElements(
    psiElement: PsiElement,
    buildHierarchyForLibraryElements: Boolean,
    checkNotDirty: Boolean
  ): CompilerReferenceServiceBase.CompilerElementInfo = {
    // @TODO: handle Scala-specific stuff (e.g. synthetic getter/setters, objects etc.)
    super.asCompilerElements(psiElement, buildHierarchyForLibraryElements, checkNotDirty)
  }
}

private[findUsages] object ScalaCompilerReferenceService {
  private val logger = Logger.getInstance(classOf[ScalaCompilerReferenceService])

  def getInstance(project: Project): ScalaCompilerReferenceService =
    project.getComponent(classOf[ScalaCompilerReferenceService])
}
