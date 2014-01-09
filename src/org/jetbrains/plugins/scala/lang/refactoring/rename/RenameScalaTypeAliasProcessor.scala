package org.jetbrains.plugins.scala
package lang
package refactoring
package rename


import com.intellij.psi.PsiElement
import psi.api.statements.ScTypeAlias
import com.intellij.refactoring.rename.{RenameJavaMemberProcessor, RenameJavaMethodProcessor}
import com.intellij.psi.search.searches.ReferencesSearch

/**
 * User: Jason Zaugg
 */

class RenameScalaTypeAliasProcessor extends RenameJavaMemberProcessor {
  override def canProcessElement(element: PsiElement): Boolean = element.isInstanceOf[ScTypeAlias]

  override def findReferences(element: PsiElement) = ScalaRenameUtil.findReferences(element)
}

