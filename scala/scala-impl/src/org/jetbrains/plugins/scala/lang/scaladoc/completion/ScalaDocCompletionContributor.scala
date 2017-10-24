package org.jetbrains.plugins.scala
package lang
package scaladoc
package completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionContributor
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment


/**
 * User: Dmitry Naydanov
 * Date: 11/26/11
 */

class ScalaDocCompletionContributor extends ScalaCompletionContributor {
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaDocTokenType.DOC_TAG_NAME), new CompletionProvider[CompletionParameters]() {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      var posParent = positionFromParameters(parameters).getContext
      while (posParent != null && !posParent.isInstanceOf[ScDocComment]) {
        posParent = posParent.getContext
      }
      
      if (posParent != null) {
        val allowedTags = posParent.asInstanceOf[ScDocComment].getOwner match {
          case _ : ScFunction => MyScaladocParsing.allTags
          case _ : ScClass => MyScaladocParsing.allTags - MyScaladocParsing.RETURN_TAG
          case _ : ScTypeAlias | _: ScTrait => MyScaladocParsing.allTags --
                  Set(MyScaladocParsing.RETURN_TAG, MyScaladocParsing.THROWS_TAG, MyScaladocParsing.PARAM_TAG)
          case _ => MyScaladocParsing.allTags -- MyScaladocParsing.tagsWithParameters - MyScaladocParsing.RETURN_TAG
        }

        for (tag <- allowedTags) {
          result.addElement(new LookupElement {
            def getLookupString: String = tag.substring(1)
          })
        }
      }
      result.stopHere()
    }
  })
}