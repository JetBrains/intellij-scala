package org.jetbrains.plugins.scala
package refactoring.extractTrait

class ExtractTraitTestSelfType extends ExtractTraitTestBase {

  def testMethodFromClassItself(): Unit = {
    val text =
      """
        |class A {<caret>
        |
        |  def foo() = bar()
        |
        |  def bar() = 1
        |}
      """.stripMargin

    val result =
      """
        |class A extends ExtractedTrait {
        |
        |  def bar() = 1
        |}
        |
        |trait ExtractedTrait {
        |  this: A =>
        |
        |  def foo() = bar()
        |}
      """.stripMargin

    checkResult(text, result, onlyDeclarations = false, onlyFirstMember = true)

    val result2 =
      """
        |class A extends ExtractedTrait {
        |
        |  override def foo() = bar()
        |
        |  def bar() = 1
        |}
        |
        |trait ExtractedTrait {
        |
        |  def foo()
        |}
      """.stripMargin

    checkResult(text, result2, onlyDeclarations = true, onlyFirstMember = true)
  }

  def testMembersFromAncestor(): Unit = {
    val text =
      """
        |class A extends AA {<caret>
        |  def foo() = bar()
        |}
        |
        |trait AA {
        |  def bar() = 1
        |}
      """.stripMargin

    val result =
      """
        |class A extends AA with ExtractedTrait
        |
        |trait ExtractedTrait {
        |  this: AA =>
        |
        |  def foo() = bar()
        |}
        |
        |trait AA {
        |  def bar() = 1
        |}
      """.stripMargin

    checkResult(text, result, onlyDeclarations = false)

    val result2 =
      """
        |class A extends AA with ExtractedTrait {
        |  override def foo() = bar()
        |}
        |
        |trait ExtractedTrait {
        |
        |  def foo()
        |}
        |
        |trait AA {
        |  def bar() = 1
        |}
      """.stripMargin

    checkResult(text, result2, onlyDeclarations = true)
  }

  def testMembersFromTwoAncestors(): Unit = {
    val text =
      """
        |class A extends AA with BB {<caret>
        |  val foo = bar() + x
        |}
        |
        |trait AA {
        |  def bar() = 1
        |}
        |
        |trait BB {
        |  val x = 2
        |}
      """.stripMargin

    val result =
      """
        |class A extends AA with BB with ExtractedTrait
        |
        |trait ExtractedTrait {
        |  this: BB with AA =>
        |
        |  val foo = bar() + x
        |}
        |
        |trait AA {
        |  def bar() = 1
        |}
        |
        |trait BB {
        |  val x = 2
        |}
      """.stripMargin

    checkResult(text, result, onlyDeclarations = false)
  }

  def testMembersFromTwoAncestors2(): Unit = {
    val text =
      """
        |class A extends AA {<caret>
        |  val foo = bar() + x
        |}
        |
        |trait AA extends BB {
        |  def bar() = 1
        |}
        |
        |trait BB {
        |  val x = 2
        |}
      """.stripMargin

    val result =
      """
        |class A extends AA with ExtractedTrait
        |
        |trait ExtractedTrait {
        |  this: AA =>
        |
        |  val foo = bar() + x
        |}
        |
        |trait AA extends BB {
        |  def bar() = 1
        |}
        |
        |trait BB {
        |  val x = 2
        |}
      """.stripMargin

    checkResult(text, result, onlyDeclarations = false)
  }

  def testMemberFromObject(): Unit = {
    val text =
      """
        |object A {
        |
        |  def foo() = bar()
        |
        |  def bar() = 1
        |}
      """.stripMargin

    val result =
      """
        |object A extends ExtractedTrait {
        |
        |  def bar() = 1
        |}
        |
        |trait ExtractedTrait {
        |  this: A.type =>
        |
        |  def foo() = bar()
        |}
      """.stripMargin
    checkResult(text, result, onlyDeclarations = false, onlyFirstMember = true)
  }
}
