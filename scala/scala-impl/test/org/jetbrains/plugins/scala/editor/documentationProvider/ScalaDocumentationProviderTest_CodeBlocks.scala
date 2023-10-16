package org.jetbrains.plugins.scala.editor.documentationProvider

import org.junit.Assert.{assertEquals, assertTrue}

class ScalaDocumentationProviderTest_CodeBlocks extends ScalaDocumentationProviderTestBase {

  private def extractCodeBlockSection(actualDoc: String, startSearchFromIndex: Int = 0): (String, Int) = {
    val codeStart = actualDoc.indexOf("<code>", startSearchFromIndex)
    assertTrue(s"Can't find code block in tag doc text: $actualDoc", codeStart >= 0)
    val codeEnd = actualDoc.indexOf("</code>", codeStart + 7)
    val codeBlockSection = actualDoc.substring(codeStart + 7, codeEnd).trim
    (codeBlockSection, codeEnd)
  }

  def testNoAdditionalIndent(): Unit = {
    val fileContent =
      s"""
         |/**
         | * {{{
         | * class Wrapper(val underlying: Int) extends AnyVal {
         | *   def foo: Wrapper = new Wrapper(underlying * 19)
         | * }
         | * }}}
         | **/
         |class ${|}Foo
         |""".stripMargin

    val actualDoc = generateDoc(fileContent)
    val (actualCode, _) = extractCodeBlockSection(actualDoc)

    assertEquals(
      "Code snippet section",
      """
        |class Wrapper(val underlying: Int) extends AnyVal {
        |  def foo: Wrapper = new Wrapper(underlying * 19)
        |}""".stripMargin.trim,
      actualCode,
    )
  }

  def testAdditionalIndentIsRemoved(): Unit = {
    val fileContent =
      s"""
         |/**
         | * {{{
         | *    class Wrapper(val underlying: Int) extends AnyVal {
         | *      def foo: Wrapper = new Wrapper(underlying * 19)
         | *    }
         | * }}}
         | **/
         |class ${|}Foo
         |""".stripMargin

    val actualDoc = generateDoc(fileContent)
    val (actualCode, _) = extractCodeBlockSection(actualDoc)

    assertEquals(
      "Code snippet section",
      """
        |class Wrapper(val underlying: Int) extends AnyVal {
        |  def foo: Wrapper = new Wrapper(underlying * 19)
        |}""".stripMargin.trim,
      actualCode,
    )
  }

  def testAdditionalIndentInManySnippetsIsRemoved(): Unit = {
    val fileContent =
      s"""
         |/**
         | * {{{
         | * class Wrapper(val underlying: Int) extends AnyVal {
         | *   def foo: Wrapper = new Wrapper(underlying * 19)
         | * }
         | * }}}
         | * {{{
         | *    class Wrapper(val underlying: Int) extends AnyVal {
         | *      def foo: Wrapper = new Wrapper(underlying * 19)
         | *    }
         | * }}}
         | * {{{
         | *       class Wrapper(val underlying: Int) extends AnyVal {
         | *         def foo: Wrapper = new Wrapper(underlying * 19)
         | *       }
         | * }}}
         | **/
         |class ${|}Foo
         |""".stripMargin

    val actualDoc = generateDoc(fileContent)
    val (actualCode1, index1) = extractCodeBlockSection(actualDoc)

    assertEquals(
      "Code snippet section 1",
      """
        |class Wrapper(val underlying: Int) extends AnyVal {
        |  def foo: Wrapper = new Wrapper(underlying * 19)
        |}""".stripMargin.trim,
      actualCode1,
    )

    val (actualCode2, index2) = extractCodeBlockSection(actualDoc.substring(index1))

    assertEquals(
      "Code snippet section 2",
      """
        |class Wrapper(val underlying: Int) extends AnyVal {
        |  def foo: Wrapper = new Wrapper(underlying * 19)
        |}""".stripMargin.trim,
      actualCode2,
    )

    val (actualCode3, _) = extractCodeBlockSection(actualDoc.substring(index2))

    assertEquals(
      "Code snippet section 3",
      """
        |class Wrapper(val underlying: Int) extends AnyVal {
        |  def foo: Wrapper = new Wrapper(underlying * 19)
        |}""".stripMargin.trim,
      actualCode3,
    )
  }
}
