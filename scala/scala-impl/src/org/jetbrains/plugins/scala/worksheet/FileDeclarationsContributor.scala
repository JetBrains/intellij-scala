package org.jetbrains.plugins.scala.worksheet

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

abstract class FileDeclarationsContributor {
  def accept(holder: PsiElement): Boolean
  def processAdditionalDeclarations(processor: PsiScopeProcessor, holder: PsiElement, state: ResolveState): Unit
}

object FileDeclarationsContributor
  extends ExtensionPointDeclaration[FileDeclarationsContributor](
    "org.intellij.scala.fileDeclarationsContributor"
  ) {
  
  def getAllFor(holder: PsiElement): collection.Seq[FileDeclarationsContributor] =
    implementations.filter(_.accept(holder))
}
