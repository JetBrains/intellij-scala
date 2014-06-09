package org.jetbrains.plugins.scala
package refactoring.extractTrait

/**
* Nikolay.Tropin
* 2014-06-02
*/
class ExtractTraitTestSelfType extends ExtractTraitTestBase {

  def testMethodFromClassItself() {
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
  }

  def testMembersFromAncestor() {
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
  }

  def testMembersFromTwoAncestors() {
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

  def testMembersFromTwoAncestors2() {
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

  def testMemberFromObject() {
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
