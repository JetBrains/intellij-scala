package org.jetbrains.sbt
package language

import com.intellij.psi.{PsiClass, PsiElement, ResolveState, FileViewProvider}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager, ScalaFileImpl}
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.openapi.module.{Module, ModuleManager, ModuleUtilCore}
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.extensions.toPsiClassExt
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.sbt.project.module.SbtModule
import collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class SbtFileImpl(provider: FileViewProvider) extends ScalaFileImpl(provider, SbtFileType) with ScDeclarationSequenceHolder{
  override def isScriptFile(withCashing: Boolean) = false

  override def immediateTypeDefinitions = Seq.empty

  override def packagings = Seq.empty

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = 
    super[ScalaFileImpl].processDeclarations(processor, state, lastParent, place) &&
    super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place) &&
    processImplicitImports(processor, state, lastParent,place)

  private def processImplicitImports(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    val expressions = implicitImportExpressions ++ localObjectsWithDefinitions.map(_.qualifiedName + "._")

    // TODO this is a workaround, we need to find out why references stopped resolving via the chained imports
    val expressions0 = expressions.map {
      case "Keys._" => "sbt.Keys._"
      case "Build._" => "sbt.Build._"
      case it => it
    }

    expressions0.isEmpty || {
      val code = s"import ${expressions0.mkString(", ")};"
      val file = ScalaPsiElementFactory.parseFile(code, getManager)
      file.processDeclarations(processor, state, file.lastChild.get, place)
    }
  }

  private def implicitImportExpressions = projectDefinitionModule.orElse(fileModule)
          .fold(Seq.empty[String])(SbtModule.getImportsFrom)

  private def localObjectsWithDefinitions: Seq[PsiClass] = {
    projectDefinitionModule.fold(Seq.empty[PsiClass]) { module =>
      val manager = ScalaPsiManager.instance(getProject)

      val moduleScope = module.getModuleScope
      val moduleWithLibrariesScope = module.getModuleWithLibrariesScope

      Sbt.DefinitionHolderClasses.flatMap(manager.getCachedClasses(moduleWithLibrariesScope, _))
              .flatMap(ClassInheritorsSearch.search(_, moduleScope, true).findAll.asScala)
    }
  }

  override def getFileResolveScope: GlobalSearchScope =
    projectDefinitionModule.fold(super.getFileResolveScope)(_.getModuleWithLibrariesScope)

  private def projectDefinitionModule: Option[Module] = fileModule.flatMap { module =>
    Option(ModuleManager.getInstance(getProject).findModuleByName(module.getName + Sbt.BuildModuleSuffix))
  }

  private def fileModule: Option[Module] = Option(ModuleUtilCore.findModuleForPsiElement(this))
}
