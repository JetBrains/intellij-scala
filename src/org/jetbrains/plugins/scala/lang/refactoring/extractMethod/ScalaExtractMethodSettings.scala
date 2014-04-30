package org.jetbrains.plugins.scala.lang
package refactoring.extractMethod

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.psi.PsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.01.2010
 */

class ScalaExtractMethodSettings(
        val methodName: String,
        val parameters: Array[ExtractMethodParameter],
        val outputs: Array[ExtractMethodOutput],
        val visibility: String,
        val scope: PsiElement,
        val nextSibling: PsiElement,
        val elements: Array[PsiElement],
        val returnType: Option[ScType],
        val lastReturn: Boolean,
        val lastMeaningful: Option[ScType]) {
  lazy val (calcReturnTypeIsUnit, calcReturnTypeText) = ScalaExtractMethodUtils.calcReturnTypeExt(returnType, outputs, lastReturn, lastMeaningful)
}