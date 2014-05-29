package org.jetbrains.plugins.scala
package codeInsight.intention.format

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import format.{StringPart, StringFormatter, StringParser}
import extensions._
import org.jetbrains.plugins.scala.util.MultilineStringUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

/**
 * Pavel Fatin
 */

abstract class AbstractFormatConversionIntention(name: String,
                                                 parser: StringParser,
                                                 formatter: StringFormatter,
                                                 eager: Boolean = false) extends PsiElementBaseIntentionAction {
  setText(name)

  override def getFamilyName = name

  private def findTargetIn(element: PsiElement): Option[(PsiElement, Seq[StringPart])] = {
    val candidates = {
      val list = element :: element.parentsInFile.toList
      if (eager) list.reverse else list
    }
    val results = candidates.map(parser.parse)
    candidates.zip(results).collectFirst {
      case (candidate, Some(parts)) => (candidate, parts)
    }
  }

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    findTargetIn(element).isDefined
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val Some((target, parts)) = findTargetIn(element)

    val result = {
      val s = formatter.format(parts)
      ScalaPsiElementFactory.createExpressionFromText(s, element.getManager)
    }

    target.replace(result) match {
      case lit: ScLiteral if lit.isMultiLineString =>
        MultilineStringUtil.addMarginsAndFormatMLString(lit, editor.getDocument)
      case _ =>
    }
  }
}
