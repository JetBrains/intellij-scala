package org.jetbrains.plugins.scala.lang
package refactoring.extractMethod

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import scala.collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.01.2010
 */

class ScalaExtractMethodSettings(
        val methodName: String,
        val parameters: Array[ExtractMethodParameter],
        val outputs: Array[ExtractMethodOutput],
        val visibility: String,
        val nextSibling: PsiElement,
        val elements: Array[PsiElement],
        val returnType: Option[ScType],
        val lastReturn: Boolean,
        val lastMeaningful: Option[ScType],
        val innerClassSettings: InnerClassSettings) {

  lazy val (calcReturnTypeIsUnit, calcReturnTypeText) = ScalaExtractMethodUtils.calcReturnTypeExt(this)

  val typeParameters: Seq[ScTypeParam] = {
    val tp: ArrayBuffer[ScTypeParam] = new ArrayBuffer
    var elem: PsiElement = elements.apply(0)
    val nextRange = nextSibling.getTextRange
    while (elem != null && !(elem.getTextRange.contains(nextRange) &&
            !elem.getTextRange.equalsToRange(nextRange.getStartOffset, nextRange.getEndOffset))) {
      elem match {
        case tpo: ScTypeParametersOwner => tp ++= tpo.typeParameters
        case _ =>
      }
      elem = elem.getParent
    }
    tp.reverse
  }
}