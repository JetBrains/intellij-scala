package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiAnnotationSupport, PsiElement, PsiLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

class ScalaAnnotationSupport extends PsiAnnotationSupport {
  override def createLiteralValue(value: String, context: PsiElement): PsiLiteral =
    createExpressionFromText("\"" + StringUtil.escapeStringCharacters(value) + "\"")(context.getManager)
      .asInstanceOf[ScLiteral]
}