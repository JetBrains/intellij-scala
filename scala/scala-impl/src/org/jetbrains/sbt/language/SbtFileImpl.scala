package org.jetbrains.sbt
package language

import com.intellij.openapi.module.{Module, ModuleManager, ModuleUtilCore}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.{FileViewProvider, PsiClass, PsiElement, ResolveState}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaFileFromText
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInUserData, ModCount}
import org.jetbrains.sbt.project.module.SbtModule

import scala.collection.JavaConverters._

/**
  * @author Pavel Fatin
  */
class SbtFileImpl(provider: FileViewProvider) extends ScalaFileImpl(provider, SbtFileType) with ScDeclarationSequenceHolder {
  override def isScriptFileImpl: Boolean = false

  override def immediateTypeDefinitions: Seq[Nothing] = Seq.empty

  override def packagings: Seq[Nothing] = Seq.empty

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean =
    super[ScalaFileImpl].processDeclarations(processor, state, lastParent, place) &&
      super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place) &&
      processImplicitImports(processor, state, lastParent, place)

  override val allowsForwardReferences: Boolean = true

  private def processImplicitImports(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    fileWithImplicitImports.forall { file =>
      file.processDeclarations(processor, state, file.getLastChild, place)
    }
  }

  @Cached(ModCount.getModificationCount, this)
  private def fileWithImplicitImports: Option[ScalaFile] = {
    val expressions = implicitImportExpressions ++ localObjectsWithDefinitions.map(_.qualifiedName + "._")

    // TODO this is a workaround, we need to find out why references stopped resolving via the chained imports
    val expressions0 = expressions.map {
      case "Keys._" => "sbt.Keys._"
      case "Build._" => "sbt.Build._"
      // TODO: this is a workaround. `processDeclarations` does not resolve "Play.autoImport -> PlayImport"
      //    However, when object with implicit imports is located in the same file where plugin object resides
      //    everything is resolved, but PlayImport and Play are in different files.
      case "_root_.play.Play.autoImport._" => "_root_.play.PlayImport._"
      case it => it
    }

    if (expressions0.isEmpty) None
    else {
      val code = s"import ${expressions0.mkString(", ")};"
      createScalaFileFromText(code)(getManager).toOption
    }
  }

  private def implicitImportExpressions = projectDefinitionModule.orElse(fileModule)
    .fold(Seq.empty[String])(SbtModule.getImportsFrom)

  private def localObjectsWithDefinitions: Seq[PsiClass] = {
    projectDefinitionModule.fold(Seq.empty[PsiClass]) { module =>
      val manager = ScalaPsiManager.instance(getProject)

      val moduleScope = module.getModuleScope
      val moduleWithDependenciesAndLibrariesScope = module.getModuleWithDependenciesAndLibrariesScope(false)

      Sbt.DefinitionHolderClasses.flatMap(manager.getCachedClasses(moduleWithDependenciesAndLibrariesScope, _))
        .flatMap(ClassInheritorsSearch.search(_, moduleScope, true).findAll.asScala)
    }
  }

  @CachedInUserData(this, ProjectRootManager.getInstance(getProject))
  override def getFileResolveScope: GlobalSearchScope =
    projectDefinitionModule.fold(super.getFileResolveScope)(_.getModuleWithDependenciesAndLibrariesScope(false))

  private def projectDefinitionModule: Option[Module] = {
    val result = for {
      module <- fileModule
      data <- SbtUtil.getSbtModuleData(module)
      buildModule <- SbtModule.findBuildModule(getProject, data.id, data.buildURI)
    } yield {
      buildModule
    }

    // TODO remove in 2018.3+
    // this is the old way of finding a build module which breaks if the way the module name is assigned changes
    lazy val moduleManager = ModuleManager.getInstance(getProject)
    lazy val legacy = fileModule.flatMap { module =>
      Option(moduleManager.findModuleByName(module.getName + Sbt.BuildModuleSuffix))
    }

    result.orElse(legacy)
  }

  private def fileModule: Option[Module] = Option(ModuleUtilCore.findModuleForPsiElement(this))
}
