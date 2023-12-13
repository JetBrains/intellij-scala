package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumClassCase, ScEnumSingletonCase, ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScGiven, ScGivenDefinition, ScObject, ScTrait}

private object ScalaDocumentationUtils {

  // TODO: review usages, maybe proper way will be to use null / None?
  val EmptyDoc = ""

  def getKeywordAndTextAttributesKey(element: PsiElement): (String, TextAttributesKey) = element match {
    case _: ScGiven                                                  => ("given", DefaultHighlighter.GIVEN)
    case ScGivenDefinition.DesugaredTypeDefinition(_)                => ("given", DefaultHighlighter.GIVEN)
    case _: ScEnum                                                   => ("enum", DefaultHighlighter.ENUM)
    case _: ScEnumClassCase                                          => ("case", DefaultHighlighter.ENUM_CLASS_CASE)
    case _: ScEnumSingletonCase                                      => ("case", DefaultHighlighter.ENUM_SINGLETON_CASE)
    case c: ScClass if c.hasModifierPropertyScala("abstract")        => ("class", DefaultHighlighter.ABSTRACT_CLASS)
    case _: ScClass                                                  => ("class", DefaultHighlighter.CLASS)
    case _: ScObject                                                 => ("object", DefaultHighlighter.OBJECT)
    case _: ScTrait                                                  => ("trait", DefaultHighlighter.TRAIT)
    case _: ScTypeAlias                                              => ("type", DefaultHighlighter.TYPE_ALIAS)
    case _: ScFunction                                               => ("def", DefaultHighlighter.METHOD_DECLARATION)
    case c: ScClassParameter if c.isVal                              => ("val", DefaultHighlighter.VALUES)
    case c: ScClassParameter if c.isVar                              => ("var", DefaultHighlighter.VARIABLES)
    case _: ScValue                                                  => ("val", DefaultHighlighter.VALUES)
    case _: ScVariable                                               => ("var", DefaultHighlighter.VARIABLES)
    case _                                                           => ("", DefaultHighlighter.STRING)
  }
}
