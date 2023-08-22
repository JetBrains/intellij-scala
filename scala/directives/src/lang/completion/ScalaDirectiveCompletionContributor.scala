//package org.jetbrains.plugins.scalaDirective.lang.completion
//
//import com.intellij.codeInsight.completion.{CompletionInitializationContext, CompletionParameters, CompletionProvider, CompletionResultSet, CompletionType}
//import com.intellij.patterns.PlatformPatterns.psiComment
//import com.intellij.patterns.StandardPatterns
//import com.intellij.util.ProcessingContext
//import org.jetbrains.plugins.scala.lang.completion.{ScalaCompletionContributor, positionFromParameters}
//import org.jetbrains.plugins.scalaDirective.lang.completion.lookups.ScalaDirectiveLookupItem
//
//final class ScalaDirectiveCompletionContributor extends ScalaCompletionContributor {
//  private val pattern = psiComment.withText(StandardPatterns.string.startsWith(DirectivePrefix))
//
//  extend(CompletionType.BASIC, pattern, new CompletionProvider[CompletionParameters] {
//    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
//      val place = positionFromParameters(parameters)
//      val trimmedText = place.getText
//        .stripPrefix(DirectivePrefix)
//        .replace(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED, "")
//        .trim
//
//      if (UsingDirective.startsWith(trimmedText)) {
//        result.addElement(ScalaDirectiveLookupItem(UsingDirective))
//      }
//    }
//  })
//}
