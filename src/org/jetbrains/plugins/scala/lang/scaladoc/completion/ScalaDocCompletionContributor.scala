package org.jetbrains.plugins.scala
package lang
package scaladoc
package completion

import com.intellij.patterns.PlatformPatterns
import lexer.ScalaDocTokenType
import com.intellij.util.ProcessingContext
import com.intellij.codeInsight.completion._
import parser.parsing.MyScaladocParsing
import java.lang.String
import com.intellij.codeInsight.lookup.LookupElement
import lang.psi.api.base.ScStableCodeReferenceElement
import psi.api.{ScDocComment, ScDocResolvableCodeReference, ScDocReferenceElement}
import lang.psi.api.statements.{ScTypeAlias, ScFunction}
import lang.psi.api.toplevel.typedef.{ScTrait, ScClass}


/**
 * User: Dmitry Naydanov
 * Date: 11/26/11
 */

class ScalaDocCompletionContributor extends CompletionContributor {
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaDocTokenType.DOC_TAG_NAME), new CompletionProvider[CompletionParameters]() {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      var posParent = parameters.getPosition.getParent
      while (posParent != null && !posParent.isInstanceOf[ScDocComment]) {
        posParent = posParent.getParent
      }
      
      if (posParent != null) {
        val allowedTags = posParent.asInstanceOf[ScDocComment].getNextSiblingNotWhitespace match {
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
    }
  })
}