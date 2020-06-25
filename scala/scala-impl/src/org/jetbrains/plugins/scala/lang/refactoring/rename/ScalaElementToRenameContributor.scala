package org.jetbrains.plugins.scala.lang.refactoring.rename

import java.util

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

@ApiStatus.Internal
abstract class ScalaElementToRenameContributor {
  def addElements(original: PsiElement, newName: String, allRenames: util.Map[PsiElement, String]): Unit
}

object ScalaElementToRenameContributor
  extends ExtensionPointDeclaration[ScalaElementToRenameContributor](
    "org.intellij.scala.scalaElementToRenameContributor"
  ) {

  def addAllElements(original: PsiElement, newName: String, allRenames: util.Map[PsiElement, String]): Unit =
    implementations.foreach(_.addElements(original, newName, allRenames))
}