package org.jetbrains.plugins.scala.editor.documentationProvider

class ScalaDocumentationProviderDocCodeTest extends ScalaDocumentationProviderTestBase {

  private def extractDocCode(actualDoc: String): (List[String], Int) = {
    val codeStart = actualDoc.indexOf("<code>")
    if (codeStart >= 0) {
      val codeEnd = actualDoc.indexOf("</code>", codeStart + 6)
      val lines = actualDoc
        .substring(codeStart + 6, codeEnd)
        .split("\n")
        .map(_.stripTrailing)
        .dropWhile(_.isEmpty)
        .reverse
        .dropWhile(_.isEmpty)
        .reverse
        .toList
      (lines, codeEnd + 7)
    } else (Nil, 0)
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
    val (actualCode, _) = extractDocCode(actualDoc)

    assert(actualCode.size == 3)
    assert(actualCode(0) == "class Wrapper(val underlying: Int) extends AnyVal {")
    assert(actualCode(1) == "  def foo: Wrapper = new Wrapper(underlying * 19)")
    assert(actualCode(2) == "}")
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
    val (actualCode, _) = extractDocCode(actualDoc)

    assert(actualCode.size == 3)
    assert(actualCode(0) == "class Wrapper(val underlying: Int) extends AnyVal {")
    assert(actualCode(1) == "  def foo: Wrapper = new Wrapper(underlying * 19)")
    assert(actualCode(2) == "}")
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
    val (actualCode1, index1) = extractDocCode(actualDoc)
    val (actualCode2, index2) = extractDocCode(actualDoc.substring(index1))
    val (actualCode3, _) = extractDocCode(actualDoc.substring(index2))

    assert(actualCode1.size == 3)
    assert(actualCode1(0) == "class Wrapper(val underlying: Int) extends AnyVal {")
    assert(actualCode1(1) == "  def foo: Wrapper = new Wrapper(underlying * 19)")
    assert(actualCode1(2) == "}")
    assert(actualCode1 == actualCode2)
    assert(actualCode1 == actualCode3)
  }
}
