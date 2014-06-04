package org.jetbrains.plugins.scala
package refactoring.extractTrait

/**
 * Nikolay.Tropin
 * 2014-06-04
 */
class ExtractTraitTestConflicts extends ExtractTraitTestBase {
  def testPrivateMember() {
    val text =
      """
        |class A {<caret>
        |
        |  def foo() = bar()
        |
        |  private def bar() = 1
        |}
      """.stripMargin

    val message = ScalaBundle.message("private.member.cannot.be.used.in.extracted.member", "bar", "foo(): Int")
    checkException(text, message, onlyDeclarations = false, onlyFirstMember = true)
  }

  def testFromAnonymousClass() {
    val text =
      """
        |object A {
        |  val x = new Runnable {<caret>
        |    def run() = bar()
        |
        |    def bar() {}
        |  }
        |}
      """.stripMargin

    val message = ScalaBundle.message("member.of.anonymous.class.cannot.be.used.in.extracted.member", "bar", "run(): Unit")
    checkException(text, message, onlyDeclarations = false, onlyFirstMember = true)

    val result =
      """
        |object A {
        |  val x = new Runnable with ExtractedTrait
        |
        |  trait ExtractedTrait {
        |
        |    def run() = bar()
        |
        |    def bar() {}
        |
        |  }
        |
        |}
      """.stripMargin
    checkResult(text, result, onlyDeclarations = false, onlyFirstMember = false)
  }

  def testSuperReference() {
    val text =
      """
        |class A extends AA {<caret>
        |  def foo() = super.bar()
        |}
        |
        |class AA {
        |  def bar() {}
        |}
      """.stripMargin
    val message = ScalaBundle.message("super.reference.used.in.extracted.member", "foo(): Unit")
    checkException(text, message, onlyDeclarations = false, onlyFirstMember = true)
  }

  def testClassTypeParams() {
    val text =
      """
        |class A extends AA[Int] {<caret>
        |  def foo() = bar()
        |}
        |
        |class AA[T] {
        |  def bar() {}
        |}
      """.stripMargin
    val message = ScalaBundle.message("type.parameters.for.self.type.not.supported", "AA")
    checkException(text, message, onlyDeclarations = false, onlyFirstMember = true)
  }

}
