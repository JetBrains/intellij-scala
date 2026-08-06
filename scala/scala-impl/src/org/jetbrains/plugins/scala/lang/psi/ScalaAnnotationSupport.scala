package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiAnnotationSupport, PsiElement, PsiLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.project.ScalaFeaturesProvider

class ScalaAnnotationSupport extends PsiAnnotationSupport {
  override def createLiteralValue(value: String, context: PsiElement): PsiLiteral =
    createExpressionFromText("\"" + StringUtil.escapeStringCharacters(value) + "\"", ScalaFeaturesProvider.forPsiOrDefault(context))(using context.getManager)
      .asInstanceOf[ScLiteral]
}
