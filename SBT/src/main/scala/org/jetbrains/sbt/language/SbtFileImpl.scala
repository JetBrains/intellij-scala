package org.jetbrains.sbt
package language

import com.intellij.psi.{PsiElement, ResolveState, FileViewProvider}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.openapi.module.{ModuleManager, ModuleUtilCore}
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder

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

  override def implicitlyImportedObjects = super.implicitlyImportedObjects ++ Seq("sbt", "sbt.Process", "sbt.Keys")

  override def getFileResolveScope = {
    val manager = ModuleManager.getInstance(getProject)

    Option(ModuleUtilCore.findModuleForPsiElement(this))
      .flatMap(module => Option(manager.findModuleByName(module.getName + Sbt.BuildModuleSuffix)))
      .map(_.getModuleWithLibrariesScope)
      .getOrElse(super.getFileResolveScope)
  }
}
