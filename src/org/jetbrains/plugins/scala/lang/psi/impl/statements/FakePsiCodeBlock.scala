package org.jetbrains.plugins.scala
package lang.psi.impl.statements

import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression}

final class FakePsiCodeBlock(body: ScExpression) extends LightElement(body.getManager, body.getLanguage) with PsiCodeBlock {
  def shouldChangeModificationCount(place: PsiElement): Boolean = false

  def getRBrace: PsiJavaToken = null

  def getLBrace: PsiJavaToken = null

  def getLastBodyElement: PsiElement = null

  def getFirstBodyElement: PsiElement = null

  def getStatements: Array[PsiStatement] = body match {
    case x: ScBlockExpr => x.statements.map(new FakePsiStatement(_)).toArray
    case _ => Array(new FakePsiStatement(body))
  }

  override def getTextRange: TextRange = body.getTextRange

  override def getTextOffset: Int = body.getTextOffset

  override def getContainingFile = body.getContainingFile

  override def toString = s"FakePsiCodeBlock(${body.toString}})"
}

final class FakePsiStatement(elem: PsiElement) extends LightElement(elem.getManager, elem.getLanguage) with PsiStatement {
  override def getTextRange: TextRange = elem.getTextRange

  override def getTextOffset: Int = elem.getTextOffset

  override def getContainingFile = elem.getContainingFile

  override def toString = s"FakePsiStatement(${elem.toString})"
}