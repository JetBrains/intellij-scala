package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.kRETURN
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.api.Nothing
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScReturnImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScReturn {

  override def keyword: PsiElement = findChildByType(kRETURN)

  protected override def innerType: TypeResult = Right(Nothing)

  override def toString: String = "ReturnStatement"
}