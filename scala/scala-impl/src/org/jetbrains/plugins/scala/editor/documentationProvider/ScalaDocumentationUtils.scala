package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScGiven, ScObject, ScTrait}

private object ScalaDocumentationUtils {

  // TODO: review usages, maybe proper way will be to use null / None?
  val EmptyDoc = ""

  def getKeywordAndTextAttributesKey(element: PsiElement): (String, TextAttributesKey) = element match {
    case c: ScClass if c.hasModifierPropertyScala("abstract") => ("class", DefaultHighlighter.ABSTRACT_CLASS)
    case _: ScClass                                                  => ("class", DefaultHighlighter.CLASS)
    case _: ScObject                                                 => ("object", DefaultHighlighter.OBJECT)
    case _: ScTrait                                                  => ("trait", DefaultHighlighter.TRAIT)
    case _: ScEnum                                                   => ("enum", DefaultHighlighter.ENUM)
    case _: ScEnumCase                                               => ("case", DefaultHighlighter.ENUM_CLASS_CASE)
    case _: ScTypeAlias                                              => ("type", DefaultHighlighter.TYPE_ALIAS)
    case _: ScGiven                                                  => ("given", DefaultHighlighter.KEYWORD)
    case _: ScFunction                                               => ("def", DefaultHighlighter.METHOD_DECLARATION)
    case c: ScClassParameter if c.isVal                              => ("val", DefaultHighlighter.VALUES)
    case c: ScClassParameter if c.isVar                              => ("var", DefaultHighlighter.VARIABLES)
    case _: ScValue                                                  => ("val", DefaultHighlighter.VALUES)
    case _: ScVariable                                               => ("var", DefaultHighlighter.VARIABLES)
    case _                                                           => ("", DefaultHighlighter.STRING)
  }
}
