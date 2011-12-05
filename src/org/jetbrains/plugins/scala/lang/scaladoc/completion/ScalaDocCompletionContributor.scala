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
import psi.api.{ScDocResolvableCodeReference, ScDocReferenceElement}
import lang.psi.api.base.ScStableCodeReferenceElement


/**
 * User: Dmitry Naydanov
 * Date: 11/26/11
 */

class ScalaDocCompletionContributor extends CompletionContributor {
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaDocTokenType.DOC_TAG_NAME), new CompletionProvider[CompletionParameters]() {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      for (tag <- MyScaladocParsing.allTags) {
        result.addElement(new LookupElement {
          def getLookupString: String = tag.substring(1)
        })
      }
    }
  })
}