package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

import scala.annotation.nowarn

abstract class FileDeclarationsContributor {
  def accept(holder: PsiElement): Boolean

  @nowarn("cat=deprecation")
  def processAdditionalDeclarations(processor: PsiScopeProcessor, holder: PsiElement, state: ResolveState, lastParent: PsiElement): Unit
}

object FileDeclarationsContributor
  extends ExtensionPointDeclaration[FileDeclarationsContributor](
    "org.intellij.scala.fileDeclarationsContributor"
  ) {
  
  def getAllFor(holder: PsiElement): Seq[FileDeclarationsContributor] =
    implementations.filter(_.accept(holder))
}
