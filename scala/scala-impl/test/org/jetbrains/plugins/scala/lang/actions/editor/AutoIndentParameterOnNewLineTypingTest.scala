package org.jetbrains.plugins.scala.lang.actions.editor

class AutoIndentParameterOnNewLineTypingTest extends EditorTypeActionTestBase {

  override protected def typedChar: Char = 'b'

  def testAlignParameter(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = true
    doTest(
      s"""class Example(a: Int,
         |${|})""".stripMargin,
      s"""class Example(a: Int,
         |              b${|})""".stripMargin
    )
  }

  def testAlignParameter_Nested(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = true
    doTest(
      s"""{
         |  class Example(a: Int,
         |  ${|})
         |}""".stripMargin,
      s"""{
         |  class Example(a: Int,
         |                b${|})
         |}""".stripMargin
    )
  }

  def testIndentParameter(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = false
    doTest(
      s"""class Example(a: Int,
         |${|})""".stripMargin,
      s"""class Example(a: Int,
         |  b${|})""".stripMargin
    )
  }

  def testIndentParameter_Nested(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = false
    doTest(
      s"""{
         |  class Example(a: Int,
         |  ${|})
         |}""".stripMargin,
      s"""{
         |  class Example(a: Int,
         |    b${|})
         |}""".stripMargin
    )
  }

  def testAlignArgument(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doTest(
      s"""Example(a,
         |${|})""".stripMargin,
      s"""Example(a,
         |        b${|})""".stripMargin
    )
  }

  def testAlignArgument_Nested(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doTest(
      s"""{
         |  Example(a,
         |${|})
         |}""".stripMargin,
      s"""{
         |  Example(a,
         |          b${|})
         |}""".stripMargin
    )
  }


  def testIndentArgument(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false
    doTest(
      s"""Example(a,
         |${|})""".stripMargin,
      s"""Example(a,
         |  b${|})""".stripMargin
    )
  }

  def testIndentArgument_Nested(): Unit = {
    getCommonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false
    doTest(
      s"""{
         |  Example(a,
         |  ${|})
         |}""".stripMargin,
      s"""{
         |  Example(a,
         |    b${|})
         |}""".stripMargin
    )
  }
}
