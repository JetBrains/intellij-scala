package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

object ScalaPsiCreator extends PsiCreator {

  def createElement(node: ASTNode): PsiElement = node.getElementType match {
    case creator: SelfPsiCreator => creator.createElement(node)
    case _: scaladoc.lexer.ScalaDocElementType => scaladoc.psi.ScalaDocPsiCreator.createElement(node)
    case _ => new ASTWrapperPsiElement(node)
  }

  trait SelfPsiCreator extends PsiCreator
}

trait PsiCreator {
  def createElement(node: ASTNode): PsiElement
}