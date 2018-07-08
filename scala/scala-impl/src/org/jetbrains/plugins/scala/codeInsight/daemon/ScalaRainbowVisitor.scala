package org.jetbrains.plugins.scala
package codeInsight
package daemon

import com.intellij.codeInsight.daemon.RainbowVisitor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTagValue

final class ScalaRainbowVisitor extends RainbowVisitor {

  import ScalaRainbowVisitor._

  override def suitableForFile(file: PsiFile): Boolean = file match {
    case _: ScalaFile => true
    case _ => false
  }

  override def visit(element: PsiElement): Unit = element match {
    case named@ColoredNameContext(context, colorKey) =>
      addInfo(context, colorKey, named.nameId)
    case reference@ScReferenceExpression(ColoredNameContext(context, colorKey)) =>
      addInfo(context, colorKey, reference.nameId)
    case docTag: ScDocTagValue =>
      addInfo(docTag, DefaultHighlighter.SCALA_DOC_TAG_PARAM_VALUE, docTag)
    case _ =>
  }

  override def clone(): ScalaRainbowVisitor = new ScalaRainbowVisitor

  private def addInfo(element: PsiElement,
                      colorKey: TextAttributesKey,
                      rainbowElement: PsiElement): Unit = {
    import PsiTreeUtil.getContextOfType
    val context = element match {
      case clause: ScCaseClause => clause
      case patterned: ScPatterned => getContextOfType(patterned, classOf[ScForStatement])
      case _ => getContextOfType(element, false, classOf[ScFunction], classOf[ScFunctionExpr])
    }

    if (context != null) {
      val info = getInfo(context, rainbowElement, rainbowElement.getText, colorKey)
      addInfo(info)
    }
  }
}

private object ScalaRainbowVisitor {

  object ColoredNameContext {

    def unapply(element: ScNamedElement): Option[(ScalaPsiElement, TextAttributesKey)] = {
      val nameContext = element match {
        case parameter: ScParameter => parameter
        case pattern: ScBindingPattern => pattern.nameContext
        case _ => null
      }

      import DefaultHighlighter._
      nameContext match {
        case _: ScClassParameter => None
        case parameter: ScParameter if parameter.isAnonymousParameter => Some(parameter, ANONYMOUS_PARAMETER)
        case parameter: ScParameter => Some(parameter, PARAMETER)
        case value: ScValue if value.isLocal => Some(value, LOCAL_VALUES)
        case variable: ScVariable if variable.isLocal => Some(variable, LOCAL_VARIABLES)
        case clause: ScCaseClause => Some(clause, PATTERN)
        case patterned: ScPatterned => Some(patterned, GENERATOR)
        case _ => None
      }
    }
  }

}