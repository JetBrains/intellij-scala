package org.jetbrains.plugins.scala.lang
package parser
package scala3

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.Scala3Language

trait SimpleScala3ParserTestBase extends SimpleScalaParserTestBase {
  override protected def language: Language = Scala3Language.INSTANCE
}
