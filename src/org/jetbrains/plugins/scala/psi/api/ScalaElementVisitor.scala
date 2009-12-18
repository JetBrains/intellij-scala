package org.jetbrains.plugins.scala.psi.api

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.{PsiFile, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaRecursiveElementVisitor extends ScalaElementVisitor {
  override def visitElement(element: ScalaPsiElement): Unit = {
    element.acceptChildren(this)
  }
}

class ScalaElementVisitor extends PsiElementVisitor {
  def visitReference(ref: ScReferenceElement) {
    visitElement(ref)
  }

  override def visitFile(file: PsiFile) = file match {
    case sf: ScalaFile => visitElement(sf)
    case _ => visitElement(file)
  }

  def visitElement(element: ScalaPsiElement) = super.visitElement(element)

  def visitPattern(pat: ScPattern) {}
}