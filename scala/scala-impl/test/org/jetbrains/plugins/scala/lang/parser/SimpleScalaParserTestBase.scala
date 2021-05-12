package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.Language
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.DebugUtil.psiToString
import org.jetbrains.plugins.scala.base.{SharedTestProjectToken, SimpleTestCase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.applicability.ApplicabilityTestBase
import org.junit.Assert.assertEquals

abstract class SimpleScalaParserTestBase extends SimpleTestCase with ScalaParserTestOps {

  override def parseText(text: String): ScalaFile =
    parseText(text.withNormalizedSeparator, lang = language)

  protected def language: Language = ScalaLanguage.INSTANCE

  override protected def sharedProjectToken: SharedTestProjectToken =
    SharedTestProjectToken(language)
}
