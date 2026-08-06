package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression}

final class FakePsiCodeBlock(body: ScExpression) extends LightElement(body.getManager, body.getLanguage) with PsiCodeBlock {
  override def shouldChangeModificationCount(place: PsiElement): Boolean = false

  override def getRBrace: PsiJavaToken = null

  override def getLBrace: PsiJavaToken = null

  override def getLastBodyElement: PsiElement = null

  override def getFirstBodyElement: PsiElement = null

  override def getStatements: Array[PsiStatement] = body match {
    case x: ScBlockExpr => x.statements.map(new FakePsiStatement(_)).toArray
    case _ => Array(new FakePsiStatement(body))
  }

  override def getTextRange: TextRange = body.getTextRange

  override def getTextOffset: Int = body.getTextOffset

  override def getStartOffsetInParent: Int = body.startOffsetInParent

  override def getContainingFile: PsiFile = body.getContainingFile

  override def toString = s"FakePsiCodeBlock(${body.toString}})"
}

final class FakePsiStatement(elem: PsiElement) extends LightElement(elem.getManager, elem.getLanguage) with PsiStatement {
  override def getTextRange: TextRange = elem.getTextRange

  override def getTextOffset: Int = elem.getTextOffset

  override def getContainingFile: PsiFile = elem.getContainingFile

  override def toString = s"FakePsiStatement(${elem.toString})"
}