package org.jetbrains.plugins.scala.lang.scaladoc.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions.{ObjectExt, OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.completion.{ScalaCompletionContributor, positionFromParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing.TagNames
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

final class ScalaDocCompletionContributor extends ScalaCompletionContributor with DumbAware {
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaDocTokenType.DOC_TAG_NAME), new CompletionProvider[CompletionParameters] {
    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
      val position = positionFromParameters(parameters)
      val posParent = position.contexts
        .dropWhile(!_.is[ScDocComment])
        .nextOption()
        .filterByType[ScDocComment]

      posParent.foreach { parent =>
        val allowedTagNames = parent.getOwner match {
          case _ : ScFunction =>
            TagNames.AllTagNames
          case _ : ScClass =>
            TagNames.AllTagNames - TagNames.Return
          case _ : ScTypeAlias | _: ScTrait =>
            TagNames.AllTagNames -- Set(TagNames.Return, TagNames.Throws, TagNames.Param)
          case _ =>
            TagNames.AllTagNames -- TagNames.TagNamesWithParameters - TagNames.Return
        }

        allowedTagNames.foreach { tagName =>
          result.addElement(new LookupElement {
            override def getLookupString: String = tagName
          })
        }
      }

      result.stopHere()
    }
  })
}
