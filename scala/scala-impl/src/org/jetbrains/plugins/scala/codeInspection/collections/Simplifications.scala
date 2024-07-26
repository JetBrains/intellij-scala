package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, SmartPsiElementPointer}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr._

import scala.language.implicitConversions

case class Simplification(exprToReplace: SmartPsiElementPointer[ScExpression], replacementText: String, @Nls hint: String, rangeInParent: TextRange)

class SimplificationBuilder private[collections] (val exprToReplace: ScExpression) {
  private var rangeInParent: TextRange =
    rightRangeInParent(exprToReplace, exprToReplace)

  private var replacementText: String = ""
  @Nls
  private var hint: String = NlsString.force("")

  def highlightFrom(expr: ScExpression): SimplificationBuilder = {
    this.rangeInParent = rightRangeInParent(expr, exprToReplace)
    this
  }

  def highlightAll: SimplificationBuilder = highlightElem(exprToReplace)

  def highlightRef: SimplificationBuilder = highlightElem(refNameId(exprToReplace).getOrElse(exprToReplace))

  def highlightElem(elem: PsiElement): SimplificationBuilder = {
    this.rangeInParent = elem.getTextRange.shiftRight( - exprToReplace.getTextOffset)
    this
  }

  def highlightRange(start: Int, end: Int): SimplificationBuilder = {
    this.rangeInParent = new TextRange(start, end).shiftRight( - exprToReplace.getTextOffset)
    this
  }

  def withText(s: String): SimplificationBuilder = {
    this.replacementText = s
    this
  }

  def wrapInBlock(): SimplificationBuilder = {
    this.replacementText = "{" + this.replacementText + "}"
    this
  }


  def withHint(@Nls s: String): SimplificationBuilder = {
    this.hint = s
    this
  }
  def toSimplification: Simplification = {
    val smartPointer = exprToReplace.createSmartPointer
    Simplification(smartPointer, replacementText, hint, rangeInParent)
  }
}

object SimplificationBuilder {
  implicit def toSimplification(s: SimplificationBuilder): Simplification = s.toSimplification
}


abstract class SimplificationType {
  @Nls
  def hint: String
  @Nls
  def description: String = hint

  def getSimplification(expr: ScExpression): Option[Simplification] = None

  def getSimplifications(expr: ScExpression): Seq[Simplification] = Seq.empty

  def replace(expr: ScExpression): SimplificationBuilder = {
    new SimplificationBuilder(expr).withHint(hint)
  }
}