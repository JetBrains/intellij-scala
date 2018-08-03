package org.jetbrains.plugins.scala.worksheet

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.{PsiElement, ResolveState}
import com.intellij.psi.scope.PsiScopeProcessor

/**
  * User: Dmitry.Naydanov
  * Date: 02.08.18.
  */
abstract class FileDeclarationsContributor {
  def accept(holder: PsiElement): Boolean
  def processAdditionalDeclarations(processor: PsiScopeProcessor, holder: PsiElement, state: ResolveState): Unit
}

object FileDeclarationsContributor {
  val EP_NAME: ExtensionPointName[FileDeclarationsContributor] = 
    ExtensionPointName.create[FileDeclarationsContributor]("org.intellij.scala.fileDeclarationsContributor")
  
  def getAllFor(holder: PsiElement): Array[FileDeclarationsContributor] = 
    EP_NAME.getExtensions.filter(_.accept(holder))
}
