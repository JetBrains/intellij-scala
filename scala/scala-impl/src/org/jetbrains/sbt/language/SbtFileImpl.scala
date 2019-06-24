package org.jetbrains.sbt
package language

import com.intellij.openapi.module.{Module, ModuleManager, ModuleUtilCore}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl._
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInUserData, ModCount}
import org.jetbrains.sbt.project.data.SbtModuleData
import org.jetbrains.sbt.project.module.SbtModule

import scala.collection.JavaConverters

/**
 * @author Pavel Fatin
 */
final class SbtFileImpl private[language](provider: FileViewProvider)
  extends ScalaFileImpl(provider, SbtFileType)
    with ScDeclarationSequenceHolder {

  override def isScriptFileImpl: Boolean = false

  override def typeDefinitions: Seq[ScTypeDefinition] = Seq.empty

  override val allowsForwardReferences: Boolean = true

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean =
    super[ScalaFileImpl].processDeclarations(processor, state, lastParent, place) &&
      super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place) &&
      (fileWithImplicitImports match {
        case null => true
        case file => file.processDeclarations(processor, state, file.getLastChild, place)
      })

  @Cached(ModCount.getModificationCount, this)
  private def fileWithImplicitImports: PsiFile = findModule match {
    case null => null
    case module =>
      import SbtModule.Imports

      val expressions = projectDefinitionModule(module)
        .fold(Imports(module)) { definitionModule =>
          Imports(definitionModule) ++ localObjectsWithDefinitions(definitionModule)
        }.map {
        // TODO this is a workaround, we need to find out why references stopped resolving via the chained imports
        case "Keys._" => "sbt.Keys._"
        case "Build._" => "sbt.Build._"
        // TODO: this is a workaround. `processDeclarations` does not resolve "Play.autoImport -> PlayImport"
        //    However, when object with implicit imports is located in the same file where plugin object resides
        //    everything is resolved, but PlayImport and Play are in different files.
        case "_root_.play.Play.autoImport._" => "_root_.play.PlayImport._"
        case importText => importText
      }

      if (expressions.isEmpty) null
      else ScalaPsiElementFactory.createScalaFileFromText(expressions.mkString("import ", ", ", ";"))
  }

  private def localObjectsWithDefinitions(module: Module): Seq[String] = {
    val manager = ScalaPsiManager.instance(getProject)

    val moduleScope = module.getModuleScope
    val moduleWithDependenciesAndLibrariesScope = module.getModuleWithDependenciesAndLibrariesScope(false)

    Sbt.DefinitionHolderClasses.flatMap {
      manager.getCachedClasses(moduleWithDependenciesAndLibrariesScope, _)
    }.flatMap {
      import JavaConverters._
      ClassInheritorsSearch.search(_, moduleScope, true).findAll.asScala
    }.map {
      _.qualifiedName + "._"
    }
  }

  @CachedInUserData(this, ProjectRootManager.getInstance(getProject))
  override def getFileResolveScope: GlobalSearchScope =
    Option(findModule)
      .flatMap(projectDefinitionModule)
      .fold(super.getFileResolveScope) {
        _.getModuleWithDependenciesAndLibrariesScope(false)
      }

  private def projectDefinitionModule(module: Module) = {
    val manager = ModuleManager.getInstance(getProject)

    for {
      SbtModuleData(_, buildURI) <- SbtUtil.getSbtModuleData(module)

      module <- manager.getModules.find { module =>
        SbtModule.Build(module) == buildURI
      }.orElse {
        // TODO remove in 2018.3+
        // this is the old way of finding a build module which breaks if the way the module name is assigned changes
        Option(manager.findModuleByName(module.getName + Sbt.BuildModuleSuffix))
      }
    } yield module
  }

  private def findModule = ModuleUtilCore.findModuleForPsiElement(this)
}
