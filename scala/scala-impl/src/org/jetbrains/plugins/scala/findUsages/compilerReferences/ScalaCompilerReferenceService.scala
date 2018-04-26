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
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.plugin.scala.compilerReferences.{BuildData, CompilerReferenceIndexBuilder}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType
import org.jetbrains.plugins.scala.project._
import org.jetbrains.sbt.project.module.SbtModuleType

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

  override def projectOpened(): Unit = if (CompilerReferenceService.isEnabled) {
    val messageBus = project.getMessageBus
    val connection = messageBus.connect(project)
    val publisher  = messageBus.syncPublisher(CompilerReferenceIndexingTopics.indexingStatus)

    connection.subscribe(CompilerReferenceIndexingTopics.indexingStatus, new CompilerReferenceIndexingStatusListener {
      override def beforeIndexingStarted(): Unit =
        closeReaderIfNeed(IndexCloseReason.COMPILATION_STARTED)

      override def onIndexingFinished(): Unit = openReaderIfNeed(IndexOpenReason.COMPILATION_FINISHED)
    })

    connection.subscribe(CustomBuilderMessageHandler.TOPIC, new CustomBuilderMessageHandler {
      import org.jetbrains.plugin.scala.compilerReferences.Codec._
      override def messageReceived(builderId: String, messageType: String, messageText: String): Unit =
        if (builderId == CompilerReferenceIndexBuilder.id) {
          val buildData = messageText.decode[BuildData]

          buildData.foreach { data =>
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
    CompilerReferenceServiceBase.executeOnBuildThread(markAsOutdated(false))

    executeOnPooledThread {
      val validIndexExists = upToDateCompilerIndexExists(project)
      val compilerManager  = CompilerManager.getInstance(project)

      if (validIndexExists) {
        project.modules.filter(SbtModuleType.unapply(_).isEmpty).foreach { m =>
          val scope = compilerManager.createModuleCompileScope(m, false)
          if (compilerManager.isUpToDate(scope)) publisher.modulesUpToDate(Set(m.getName))
        }
        myActiveBuilds += 1 // workaround for indexing not being a part of compilation for Scala
        publisher.onIndexingFinished()
      }
    }

    Disposer.register(project, () => closeReaderIfNeed(IndexCloseReason.PROJECT_CLOSED))
  }

  override def asCompilerElements(
    psiElement:                       PsiElement,
    buildHierarchyForLibraryElements: Boolean,
    checkNotDirty:                    Boolean
  ): CompilerReferenceServiceBase.CompilerElementInfo = {
    val referencingElement = referencingBytecodeElement(psiElement)
    super.asCompilerElements(referencingElement, buildHierarchyForLibraryElements, checkNotDirty)
  }

  /**
   * Returns usages only from up-to-date compiled scope.
   */
  def implicitUsages(target: PsiElement): Set[LinesWithUsagesInFile] = withLock(myReadDataLock) {
    val usages = Set.newBuilder[LinesWithUsagesInFile]

    for {
      elementInfo <- asCompilerElements(target, buildHierarchyForLibraryElements = false, checkNotDirty = false).toOption
      reader      <- myReader.toOption
      targets     = elementInfo.searchElements
    } yield targets.foreach(target => usages ++= reader.findImplicitReferences(target))

    val dirtyScope = dirtyScopeForDefinition(target)
    usages.result().filterNot(usage => dirtyScope.contains(usage.file))
  }

  private def dirtyScopeForDefinition(e: PsiElement): GlobalSearchScope = {
    import com.intellij.psi.search.GlobalSearchScope._

    val dirtyModules      = myDirtyScopeHolder.getAllDirtyModules
    val dirtyModulesScope = union(dirtyModules.asScala.map(_.getModuleScope)(collection.breakOut))
    val elemModule        = inReadAction(ModuleUtilCore.findModuleForPsiElement(e).toOption)

    val dependentsScope =
      elemModule.collect { case m if dirtyModules.contains(m) => m.getModuleTestsWithDependentsScope }
        .getOrElse(EMPTY_SCOPE)

    union(Array(dependentsScope, dirtyModulesScope))
  }
}

private[findUsages] object ScalaCompilerReferenceService {
  private val logger = Logger.getInstance(classOf[ScalaCompilerReferenceService])

  def getInstance(project: Project): ScalaCompilerReferenceService =
    project.getComponent(classOf[ScalaCompilerReferenceService])

  private def referencingBytecodeElement(element: PsiElement): PsiElement =
    inReadAction(element match {
      case hasSyntheticGetter(getter)      => getter
      case isAnyValExtensionMethod(method) => method
      case _                               => element
      // @TODO: desugar generator arrows to map/flatMap/withFilter
    })

  object hasSyntheticGetter {
    private[this] def syntheticGetterMethod(e: ScTypedDefinition): FakePsiMethod =
      new FakePsiMethod(
        e,
        e.name,
        Array.empty,
        e.`type`().getOrAny,
        Function.const(false)
      )

    def unapply(e: PsiElement): Option[FakePsiMethod] = e match {
      case c: ScClassParameter if !c.isPrivateThis => Option(syntheticGetterMethod(c))
      case (bp: ScBindingPattern) && ScalaPsiUtil.inNameContext(v: ScValueOrVariable)
          if !v.isLocal && !v.isPrivateThis =>
        Option(syntheticGetterMethod(bp))
      case _ => None
    }
  }

  object isAnyValExtensionMethod {
    private[this] def syntheticExtensionMethod(e: ScFunction): FakePsiMethod =
      new FakePsiMethod(
        e,
        e.name + "$extension",
        Array.empty,
        e.`type`().getOrAny,
        Function.const(false)
      ) {
        override def getContainingClass: PsiClass = ScalaPsiUtil.getCompanionModule(e.containingClass).orNull
      }

    def unapply(e: ScFunction): Option[FakePsiMethod] =
      e.containingClass.toOption.collect {
        case aClass if ValueClassType.isValueClass(aClass) => syntheticExtensionMethod(e)
      }
  }
}
