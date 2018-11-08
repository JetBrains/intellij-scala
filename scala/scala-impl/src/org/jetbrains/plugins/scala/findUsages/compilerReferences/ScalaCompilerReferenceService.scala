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
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.plugin.scala.compilerReferences.{BuildData, ScalaCompilerReferenceIndexBuilder}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project._

import scala.collection.JavaConverters._

private[findUsages] class ScalaCompilerReferenceService(
  project:             Project,
  fileDocumentManager: FileDocumentManager,
  psiDocumentManager:  PsiDocumentManager
) extends {
  private[this] val callback: BiConsumer[MessageBusConnection, util.Set[String]] = (connection, affectedModules) =>
    connection.subscribe(CompilerReferenceIndexingTopics.indexingStatus, new CompilerReferenceIndexingStatusListener {
      override def modulesUpToDate(affectedModuleNames: Iterable[String]): Unit =
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

  @volatile
  private[this] var isUpToDateChecked = false

  override def projectOpened(): Unit = if (CompilerReferenceService.isEnabled) {
    val messageBus = project.getMessageBus
    val connection = messageBus.connect(project)

    connection.subscribe(CompilerReferenceIndexingTopics.indexingStatus, new CompilerReferenceIndexingStatusListener {
      override def beforeIndexingStarted(): Unit =
        closeReaderIfNeed(IndexCloseReason.COMPILATION_STARTED)

      override def onIndexingFinished(): Unit = openReaderIfNeed(IndexOpenReason.COMPILATION_FINISHED)
    })

    connection.subscribe(CustomBuilderMessageHandler.TOPIC, new CustomBuilderMessageHandler {
      import org.jetbrains.plugin.scala.compilerReferences.Codec._
      override def messageReceived(builderId: String, messageType: String, messageText: String): Unit =
        if (builderId == ScalaCompilerReferenceIndexBuilder.id) {
          val buildData = messageText.decode[BuildData]

          buildData.foreach {
            data =>
              val publisher = messageBus.syncPublisher(CompilerReferenceIndexingTopics.indexingStatus)
              publisher.beforeIndexingStarted()
              val modules = data.affectedModules
              logger.debug(s"Building index for modules ${modules.mkString("[", ", ", "]")}")

              indexer.writeBuildData(data, onSuccess = {
                publisher.modulesUpToDate(modules)
                logger.debug(s"Finished building indices for modules ${modules.mkString("[", ", ", "]")}")
                publisher.onIndexingFinished()
              })
          }
        }
    })

    myDirtyScopeHolder.installVFSListener()
    markAsOutdated(false)

    executeOnPooledThread {
      val validIndexExists = upToDateCompilerIndexExists(project)
      val compilerManager  = CompilerManager.getInstance(project)

      if (validIndexExists) {
        val publisher = messageBus.syncPublisher(CompilerReferenceIndexingTopics.indexingStatus)

        project.sourceModules.foreach { m =>
          val scope = compilerManager.createModuleCompileScope(m, false)
          if (compilerManager.isUpToDate(scope)) publisher.modulesUpToDate(Set(m.getName))
        }

        myActiveBuilds += 1
        publisher.onIndexingFinished()
      }
      isUpToDateChecked = true
    }

    Disposer.register(project, () => closeReaderIfNeed(IndexCloseReason.PROJECT_CLOSED))
  }

  /**
   * Returns usages only from up-to-date compiled scope.
   */
  def usagesOf(target: PsiElement): Set[LinesWithUsagesInFile] = withLock(myReadDataLock) {
    val usages       = Set.newBuilder[LinesWithUsagesInFile]
    val actualTarget = ScalaCompilerRefAdapter.bytecodeElement(target)

    for {
      elementInfo <- asCompilerElements(actualTarget, false, false).toOption
      reader      <- myReader.toOption
      targets     = elementInfo.searchElements
    } yield targets.foreach(target => usages ++= reader.usagesOf(target))

    val dirtyScope = dirtyScopeForDefinition(target)
    usages.result().filterNot(usage => dirtyScope.contains(usage.file))
  }

  def isCompilerIndexReady: Boolean = isUpToDateChecked

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
