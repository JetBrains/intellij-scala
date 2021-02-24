package org.jetbrains.plugins.scala.lang.formatter.tests.scala3

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

abstract class Scala3FormatterBaseTest extends AbstractScalaFormatterTestBase {
  override protected def language: Language = Scala3Language.INSTANCE
}
