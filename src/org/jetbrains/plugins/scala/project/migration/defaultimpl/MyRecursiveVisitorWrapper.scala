package org.jetbrains.plugins.scala.project.migration.defaultimpl

import com.intellij.psi.{PsiElement, PsiElementVisitor}

/**
  * User: Dmitry.Naydanov
  * Date: 23.09.16.
  */
class MyRecursiveVisitorWrapper(actions: Seq[PartialFunction[PsiElement, Any]]) extends PsiElementVisitor {
  override def visitElement(element: PsiElement): Unit = {
    actions.find(_.isDefinedAt(element)).map(_(element))
    
    element.acceptChildren(this)
  }
}
