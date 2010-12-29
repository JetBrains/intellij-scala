package org.jetbrains.plugins.scala.lang.psi

import api.base.ScLiteral
import impl.ScalaPsiElementFactory
import java.lang.String
import com.intellij.psi.{PsiLiteral, PsiElement, PsiAnnotationSupport}
import com.intellij.openapi.util.text.StringUtil

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaAnnotationSupport extends PsiAnnotationSupport {
  def createLiteralValue(value: String, context: PsiElement): PsiLiteral = {
    return ScalaPsiElementFactory.createExpressionFromText("\"" + StringUtil.escapeStringCharacters(value) + "\"",
      context.getManager).asInstanceOf[ScLiteral]
  }
}