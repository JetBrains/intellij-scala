package org.jetbrains.plugins.scala
package codeInsight
package daemon

import com.intellij.codeInsight.daemon.RainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTagValue

/**
 * Support for "Semantic Highlighting" feature of Intellij IDEA
 *
 * see also [[org.jetbrains.plugins.scala.highlighter.ScalaColorSchemeAnnotator]]
 */
final class ScalaRainbowVisitor extends RainbowVisitor {

  import ScalaRainbowVisitor._

  override def suitableForFile(file: PsiFile): Boolean = file match {
    case _: ScalaFile => true
    case _ => false
  }

  override def visit(element: PsiElement): Unit = {
    Some(element).collect {
      case tagValue: ScDocTagValue => (tagValue, tagValue)
      case named@NameContext(context) => (context, named.nameId)
      case reference@ScReferenceExpression(NameContext(context)) => (context, reference.nameId)
    }.collect {
      case (ColorKey(PsiContext(context), colorKey), rainbowElement) =>
        getInfo(context, rainbowElement, rainbowElement.getText, colorKey)
    }.foreach {
      addInfo
    }
  }

  override def getInfo(context: PsiElement,
                       rainbowElement: PsiElement,
                       name: String,
                       colorKey: TextAttributesKey): HighlightInfo = super.getInfo(
    context,
    rainbowElement,
    ScalaNamesUtil.isBacktickedName.unapply(name).getOrElse(name),
    colorKey
  )

  override def clone: ScalaRainbowVisitor = new ScalaRainbowVisitor
}

private object ScalaRainbowVisitor {

  object NameContext {

    def unapply(element: ScNamedElement): Option[PsiElement] = element match {
      case parameter: ScParameter => Some(parameter)
      case pattern: ScBindingPattern => Option(pattern.nameContext)
      case _ => None
    }
  }

  object ColorKey {

    import DefaultHighlighter._

    def unapply(element: PsiElement): Option[(PsiElement, TextAttributesKey)] = element match {
      case tagValue: ScDocTagValue => Some(tagValue, SCALA_DOC_TAG_PARAM_VALUE)
      case _: ScClassParameter => None
      case parameter: ScParameter => Some(parameter, if (parameter.isAnonymousParameter) ANONYMOUS_PARAMETER else PARAMETER)
      case value: ScValue if value.isLocal => Some(value, LOCAL_VALUES)
      case variable: ScVariable if variable.isLocal => Some(variable, LOCAL_VARIABLES)
      case clause: ScCaseClause => Some(clause, PATTERN)
      case patterned: ScPatterned => Some(patterned, GENERATOR)
      case _ => None
    }
  }

  object PsiContext {

    import PsiTreeUtil.getContextOfType

    def unapply(element: PsiElement): Option[ScalaPsiElement] = element match {
      case clause: ScCaseClause => Some(clause)
      case patterned: ScPatterned => Option(getContextOfType(patterned, classOf[ScFor]))
      case _ =>
        Option(getContextOfType(element, false, classOf[ScFunction], classOf[ScFunctionExpr])).filterNot {
          case function: ScFunction => function.isSynthetic
          case _ => false
        }
    }
  }

}