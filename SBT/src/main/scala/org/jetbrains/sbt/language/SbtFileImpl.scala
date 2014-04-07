package org.jetbrains.sbt
package language

import com.intellij.psi.{PsiClass, PsiElement, ResolveState, FileViewProvider}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiManager, ScalaFileImpl}
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.openapi.module.{Module, ModuleManager, ModuleUtilCore}
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.extensions.toPsiClassExt
import com.intellij.psi.search.GlobalSearchScope
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
    super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place)

  override def implicitlyImportedPackages = super.implicitlyImportedPackages :+ "sbt"

  override def implicitlyImportedObjects = super.implicitlyImportedObjects ++ Seq("sbt", "sbt.Process", "sbt.Keys") ++
          localObjectsWithDefinitions.map(_.qualifiedName)

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

  private def projectDefinitionModule: Option[Module] = {
    Option(ModuleUtilCore.findModuleForPsiElement(this)).flatMap { module =>
      Option(ModuleManager.getInstance(getProject).findModuleByName(module.getName + Sbt.BuildModuleSuffix))
    }
  }
}
