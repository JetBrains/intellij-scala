package org.jetbrains.plugins.scala.lang.actions.editor

class DotOnNewLineTypingTest extends EditorTypeActionTestBase {

  private val | = CARET
  private val Tab = "\t"

  override protected def typedChar: Char = '.'

  private def indentOptions = getCommonSettings.getIndentOptions

  // Original "before" contain spaces even if "use indents" option is enabled.
  // It's a valid state of editor, if user manually typed those spaces.
  // In case when indents are used we run tests second time as if tabs are used in "before" value
  override protected def doTest(before: String, after: String, fileName: String): Unit = {
    super.doTest(before, after, fileName)
    if (indentOptions.USE_TAB_CHARACTER) {
      val spacesToReplace = " " * indentOptions.TAB_SIZE
      val beforeWithIndents = before.replace(spacesToReplace, "\t")
      val afterWithIndents = after.replace(spacesToReplace, "\t")
      super.doTest(beforeWithIndents, afterWithIndents, fileName)
    }
  }

  def testIndent(): Unit =
    doTest(
      s"""value
         |${|}""".stripMargin,
      s"""value
         |  .${|}""".stripMargin
    )

  def testIndent_NestedCode(): Unit =
    doTest(
      s"""{
         |  value
         |  ${|}
         |}""".stripMargin,
      s"""{
         |  value
         |    .${|}
         |}""".stripMargin
    )

  def testIndent_NestedCode_1(): Unit =
    doTest(
      s"""def foo = {
         |  def bar = {
         |    value
         |    ${|}
         |  }
         |}""".stripMargin,
      s"""def foo = {
         |  def bar = {
         |    value
         |      .${|}
         |  }
         |}""".stripMargin,
    )

  def testIndent_NonDefaultSettings(): Unit = {
    indentOptions.CONTINUATION_INDENT_SIZE = 4
    indentOptions.INDENT_SIZE = indentOptions.CONTINUATION_INDENT_SIZE
    doTest(
      s"""value
         |${|}""".stripMargin,
      s"""value
         |    .${|}""".stripMargin
    )
  }

  def testIndent_NonDefaultSettings_NestedCode(): Unit = {
    indentOptions.CONTINUATION_INDENT_SIZE = 4
    indentOptions.INDENT_SIZE = indentOptions.CONTINUATION_INDENT_SIZE
    doTest(
      s"""{
         |    value
         |    ${|}
         |}""".stripMargin,
      s"""{
         |    value
         |        .${|}
         |}""".stripMargin
    )
  }

  def testIndent_NonDefaultSettings_NestedCode_1(): Unit = {
    indentOptions.CONTINUATION_INDENT_SIZE = 4
    indentOptions.INDENT_SIZE = indentOptions.CONTINUATION_INDENT_SIZE
    doTest(
      s"""def foo = {
         |    def bar = {
         |        value
         |        ${|}
         |    }
         |}""".stripMargin,
      s"""def foo = {
         |    def bar = {
         |        value
         |            .${|}
         |    }
         |}""".stripMargin,
    )
  }

  def testIndent_NonDefaultSettings_UseTabs(): Unit = {
    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.TAB_SIZE = 4
    indentOptions.CONTINUATION_INDENT_SIZE = 4
    indentOptions.INDENT_SIZE = indentOptions.CONTINUATION_INDENT_SIZE
    doTest(
      s"""value
         |${|}""".stripMargin,
      s"""value
         |$Tab.${|}""".stripMargin
    )
  }

  def testIndent_NonDefaultSettings_NestedCode_UseTabs(): Unit = {
    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.TAB_SIZE = 4
    indentOptions.CONTINUATION_INDENT_SIZE = 4
    indentOptions.INDENT_SIZE = indentOptions.CONTINUATION_INDENT_SIZE
    doTest(
      s"""{
         |    value
         |    ${|}
         |}""".stripMargin,
      s"""{
         |    value
         |$Tab$Tab.${|}
         |}""".stripMargin
    )
  }

  def testIndent_NonDefaultSettings_NestedCode_UseTabs_1(): Unit = {
    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.TAB_SIZE = 4
    indentOptions.CONTINUATION_INDENT_SIZE = 4
    indentOptions.INDENT_SIZE = indentOptions.CONTINUATION_INDENT_SIZE
    doTest(
      s"""def foo = {
         |    def bar = {
         |        value
         |        ${|}
         |    }
         |}""".stripMargin,
      s"""def foo = {
         |    def bar = {
         |        value
         |$Tab$Tab$Tab.${|}
         |    }
         |}""".stripMargin,
    )
  }

  def testIndent_NonDefaultSettings_NestedCode_UseTabs_TypingInNonIntendedEmptyLine(): Unit = {
    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.TAB_SIZE = 4
    indentOptions.CONTINUATION_INDENT_SIZE = 4
    indentOptions.INDENT_SIZE = indentOptions.CONTINUATION_INDENT_SIZE
    doTest(
      s"""{
         |    value
         |${|}
         |}""".stripMargin,
      s"""{
         |    value
         |$Tab$Tab.${|}
         |}""".stripMargin
    )
  }


  def testIndent_NonDefaultSettings_NestedCode_UseTabs_TypingInNonIntendedEmptyLine_1(): Unit = {
    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.TAB_SIZE = 4
    indentOptions.CONTINUATION_INDENT_SIZE = 4
    indentOptions.INDENT_SIZE = indentOptions.CONTINUATION_INDENT_SIZE
    doTest(
      s"""def foo = {
         |    def bar = {
         |        value
         |${|}
         |    }
         |}""".stripMargin,
      s"""def foo = {
         |    def bar = {
         |        value
         |$Tab$Tab$Tab.${|}
         |    }
         |}""".stripMargin,
    )
  }

  def testIndent_NonDefaultSettings_UseTabs_SmallerThanIndentSize(): Unit = {
    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.TAB_SIZE = 4
    indentOptions.CONTINUATION_INDENT_SIZE = 6
    indentOptions.INDENT_SIZE = indentOptions.CONTINUATION_INDENT_SIZE
    doTest(
      s"""value
         |${|}""".stripMargin,
      s"""value
         |$Tab  .${|}""".stripMargin
    )
  }

  def testIndent_NonDefaultSettings_NestedCode_UseTabs_SmallerThanIndentSize(): Unit = {
    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.TAB_SIZE = 4
    indentOptions.CONTINUATION_INDENT_SIZE = 6
    indentOptions.INDENT_SIZE = indentOptions.CONTINUATION_INDENT_SIZE
    doTest(
      s"""{
         |      value
         |      ${|}
         |}""".stripMargin,
      s"""{
         |      value
         |$Tab$Tab$Tab.${|}
         |}""".stripMargin
    )
  }

  def testIndent_NonDefaultSettings_NestedCode_UseTabs_SmallerThanIndentSize_1(): Unit = {
    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.TAB_SIZE = 4
    indentOptions.CONTINUATION_INDENT_SIZE = 6
    indentOptions.INDENT_SIZE = indentOptions.CONTINUATION_INDENT_SIZE
    doTest(
      s"""def foo = {
         |      def bar = {
         |            value
         |            ${|}
         |      }
         |}""".stripMargin,
      s"""def foo = {
         |      def bar = {
         |            value
         |$Tab$Tab$Tab$Tab  .${|}
         |      }
         |}""".stripMargin,
    )
  }

  def testIndent_NonDefaultSettings_UseTabs_SmallerThanIndentSize_DifferentContinuationIndent(): Unit = {
    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.TAB_SIZE = 4
    indentOptions.CONTINUATION_INDENT_SIZE = 6
    indentOptions.INDENT_SIZE = 4
    doTest(
      s"""value
         |${|}""".stripMargin,
      s"""value
         |$Tab  .${|}""".stripMargin
    )
  }

  def testIndent_NonDefaultSettings_NestedCode_UseTabs_SmallerThanIndentSize_DifferentContinuationIndent(): Unit = {
    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.TAB_SIZE = 4
    indentOptions.CONTINUATION_INDENT_SIZE = 6
    indentOptions.INDENT_SIZE = 4
    doTest(
      s"""{
         |    value
         |    ${|}
         |}""".stripMargin,
      s"""{
         |    value
         |$Tab$Tab  .${|}
         |}""".stripMargin
    )
  }

  def testIndent_NonDefaultSettings_NestedCode_UseTabs_SmallerThanIndentSize_DifferentContinuationIndent_1(): Unit = {
    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.TAB_SIZE = 4
    indentOptions.CONTINUATION_INDENT_SIZE = 6
    indentOptions.INDENT_SIZE = 4
    doTest(
      s"""def foo = {
         |    def bar = {
         |        value
         |        ${|}
         |    }
         |}""".stripMargin,
      s"""def foo = {
         |    def bar = {
         |        value
         |$Tab$Tab$Tab  .${|}
         |    }
         |}""".stripMargin,
    )
  }
}
