package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

trait SelfPsiCreator {
  def createElement(node: ASTNode): PsiElement
}