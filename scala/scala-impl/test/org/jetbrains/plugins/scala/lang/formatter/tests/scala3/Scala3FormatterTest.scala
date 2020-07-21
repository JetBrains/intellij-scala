package org.jetbrains.plugins.scala
package lang
package formatter
package tests
package scala3

import com.intellij.lang.Language

class Scala3FormatterTest extends AbstractScalaFormatterTestBase {
  override protected def language: Language = Scala3Language.INSTANCE

  def testClassColon(): Unit = doTextTest(
    """
      |class Test :
      |  def test = ()
      |""".stripMargin,
    """
      |class Test:
      |  def test = ()
      |""".stripMargin
  )

  def testClassEnd(): Unit = doTextTest(
    """
      |class Test:
      |  def test = ()
      |end Test
      |""".stripMargin
  )

}
