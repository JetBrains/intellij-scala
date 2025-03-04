package org.jetbrains.plugins.scala.markdown

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.Language
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider
import org.jetbrains.plugins.scala.Scala3Language

import java.util.Collections.emptyList
import java.util.List as JList

final class ScalaCodeFenceLanguageProvider extends CodeFenceLanguageProvider:
  override def getCompletionVariantsForInfoString(parameters: CompletionParameters): JList[LookupElement] = emptyList()

  override def getLanguageByInfoString(infoString: String): Language =
    if "scala".equalsIgnoreCase(infoString) then Scala3Language.INSTANCE
    else null
