package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.base.{SharedTestProjectToken, SimpleTestCase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.experimental.categories.Category

@Category(Array(classOf[LanguageTests]))
abstract class SimpleScalaParserTestBase extends SimpleTestCase with ScalaParserTestOps {

  override def parseText(text: String): ScalaFile =
    parseText(text.withNormalizedSeparator, lang = language)

  protected def language: Language = ScalaLanguage.INSTANCE

  override protected def sharedProjectToken: SharedTestProjectToken =
    SharedTestProjectToken(language)
}
