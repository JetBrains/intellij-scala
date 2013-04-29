package org.jetbrains.sbt
package language

import com.intellij.psi.{PsiElement, ResolveState, FileViewProvider}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import com.intellij.psi.scope.PsiScopeProcessor

/**
 * @author Pavel Fatin
 */
class SbtFileImpl(provider: FileViewProvider) extends ScalaFileImpl(provider, SbtFileType) {
  override def isScriptFile(withCashing: Boolean) = false

  override def immediateTypeDefinitions = Seq.empty

  override def packagings = Seq.empty

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    super.processDeclarations(processor, state, lastParent, place)
  }

  override def implicitlyImportedPackages = super.implicitlyImportedPackages :+ "sbt"

  override def implicitlyImportedObjects = super.implicitlyImportedObjects ++ Seq("sbt.Process", "sbt.Keys")
}
