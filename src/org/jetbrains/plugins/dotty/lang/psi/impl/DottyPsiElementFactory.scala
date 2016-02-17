package org.jetbrains.plugins.dotty.lang.psi.impl

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.dotty.lang.parser.parsing.types.Type.parse
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createElementWithContext

/**
  * @author adkozlov
  */
object DottyPsiElementFactory {

  def createTypeElementFromText(text: String, context: PsiElement, child: PsiElement): ScTypeElement = {
    createElementWithContext(text, context, child, parse(_)) match {
      case te: ScTypeElement => te
      case _ => null
    }
  }
}
