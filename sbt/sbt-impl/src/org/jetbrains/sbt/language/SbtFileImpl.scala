package org.jetbrains.sbt
package language

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.{Module, ModuleManager, ModuleUtilCore}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi._
import com.intellij.psi.search.{GlobalSearchScope, searches}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.caches.{ModTracker, cached, cachedInUserData}
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl._
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaFeatures}
import org.jetbrains.sbt.project.SbtBuildModuleUriProvider
import org.jetbrains.sbt.project.module.SbtModule.{Build, Imports}

import scala.jdk.CollectionConverters._

final class SbtFileImpl private[language](provider: FileViewProvider)
  extends ScalaFileImpl(provider, SbtFileType)
    with SbtFile
    with ScDeclarationSequenceHolder {

  import SbtFileImpl._

  override def typeDefinitions: Seq[ScTypeDefinition] = Seq.empty

  override val allowsForwardReferences: Boolean = true

  override def processDeclarations(processor: scope.PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean =
    super[ScalaFileImpl].processDeclarations(processor, state, lastParent, place) &&
      super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place) &&
      syntheticFile().forall { file =>
        file.processDeclarations(processor, state, file.getLastChild, place)
      }

  private val syntheticFile = cached("syntheticFile", ModTracker.physicalPsiChange(getProject), () => {
    implicit val manager: ScalaPsiManager = ScalaPsiManager.instance(getProject)
    @NonNls val imports = importsFor(targetModule).map {
      // TODO this is a workaround, we need to find out why references stopped resolving via the chained imports
      case "Keys._" => "sbt.Keys._"
      case "Build._" => "sbt.Build._"
      // TODO: this is a workaround. `processDeclarations` does not resolve "Play.autoImport -> PlayImport"
      //    However, when object with implicit imports is located in the same file where plugin object resides
      //    everything is resolved, but PlayImport and Play are in different files.
      case "_root_.play.Play.autoImport._" => "_root_.play.PlayImport._"
      case importText => importText
    }

    if (imports.isEmpty) None
    else Some(ScalaPsiElementFactory.createScalaFileFromText(imports.mkString("import ", ", ", ";"), ScalaFeatures.default))
  })

  /**
   * @note dual logic is located in [[org.jetbrains.sbt.language.psi.search.SbtBuildModuleUseScopeEnlarger]]
   * @todo consider rewriting this using [[com.intellij.psi.ResolveScopeEnlarger]]
   */
  override def getFileResolveScope: GlobalSearchScope = {
    val target = targetModule
    target match {
      case SbtModuleWithScope(_, moduleWithDependenciesAndLibrariesScope) =>
        moduleWithDependenciesAndLibrariesScope
      case _ =>
        super.getFileResolveScope
    }
  }

  private def targetModule: TargetModule = cachedInUserData("targetModule", this, ProjectRootManager.getInstance(getProject)) {
    val moduleForFile = ModuleUtilCore.findModuleForPsiElement(this)
    moduleForFile match {
      case null =>
        ModuleLess
      case module =>
        val buildModule = findBuildModule(module)
        buildModule match {
          case Some(buildModule) =>
            val moduleWithDepsAndLibsScope = buildModule.getModuleWithDependenciesAndLibrariesScope(false)
            SbtModuleWithScope(buildModule, moduleWithDepsAndLibsScope)
          case None =>
            DefinitionModule(module)
        }
    }
  }

  override def findBuildModule(module: Module): Option[Module] =
    if (module.hasBuildModuleType)
      Some(module)
    else {
      val manager = ModuleManager.getInstance(getProject)
      val modules = manager.getModules
      val sbtBuildModuleUri = SbtBuildModuleUriProvider.getBuildModuleUri(module)
      val result = for {
        buildModuleUri <- sbtBuildModuleUri
        module <- modules.find(Build(_) == buildModuleUri)
      } yield module

      if (result.isEmpty && ApplicationManager.getApplication.isUnitTestMode) {
        //NOTE: right now this legacy way of determining build module is left for tests only
        //It simplifies setup logic for tests which work with sbt files.
        //In theory we could remove this extra branch, but we would need to improve setup for tests
        val buildModuleName = module.getName + Sbt.BuildModuleSuffix
        modules.find(_.getName == buildModuleName)
      }
      else result
    }
}

object SbtFileImpl {

  private sealed trait TargetModule {
    def module: Module
  }

  private case object ModuleLess extends TargetModule {
    override def module: Nothing = throw new NoSuchElementException("Module not found")
  }

  private case class DefinitionModule(override val module: Module) extends TargetModule

  private case class SbtModuleWithScope(override val module: Module,
                                        moduleWithDependenciesAndLibrariesScope: GlobalSearchScope) extends TargetModule

  private def importsFor(module: TargetModule)
                        (implicit manager: ScalaPsiManager): Seq[String] = module match {
    case ModuleLess => Seq.empty
    case DefinitionModule(module) => Imports(module)
    case SbtModuleWithScope(module, moduleWithDependenciesAndLibrariesScope) =>
      val moduleScope = module.getModuleScope
      val localObjectsWithDefinitions = for {
        fqn <- Sbt.DefinitionHolderClasses
        aClass <- manager.getCachedClasses(moduleWithDependenciesAndLibrariesScope, fqn)

        inheritor <- searches.ClassInheritorsSearch
          .search(aClass, moduleScope, true)
          .findAll
          .asScala
      } yield s"${inheritor.qualifiedName}._"

      Imports(module) ++ localObjectsWithDefinitions
  }
}
