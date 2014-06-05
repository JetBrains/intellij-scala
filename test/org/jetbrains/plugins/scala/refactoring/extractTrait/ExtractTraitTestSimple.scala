package org.jetbrains.plugins.scala
package refactoring.extractTrait

/**
 * Nikolay.Tropin
 * 2014-06-02
 */
class ExtractTraitTestSimple extends ExtractTraitTestBase {

  def testDef() {
    val text =
      """
        |trait A {<caret>
        |  def a() = 1
        |}
        |""".stripMargin
    val result =
      """
        |trait A extends ExtractedTrait
        |
        |trait ExtractedTrait {
        |
        |  def a() = 1
        |}
      """.stripMargin
    val resultDecl =
      """
        |trait A extends ExtractedTrait {
        |  override def a() = 1
        |}
        |
        |trait ExtractedTrait {
        |
        |  def a()
        |}
      """.stripMargin

    checkResult(text, result, onlyDeclarations = false)
    checkResult(text, resultDecl, onlyDeclarations = true)
  }

  def testDef2() {
    val text =
      """
        |trait A {<caret>
        |  def a(i: Int): Int = 1
        |}
        |""".stripMargin
    val result =
      """
        |trait A extends ExtractedTrait
        |
        |trait ExtractedTrait {
        |
        |  def a(i: Int): Int = 1
        |}
      """.stripMargin
    val resultDecl =
      """
        |trait A extends ExtractedTrait {
        |  override def a(i: Int): Int = 1
        |}
        |
        |trait ExtractedTrait {
        |
        |  def a(i: Int): Int
        |}
      """.stripMargin

    checkResult(text, result, onlyDeclarations = false)
    checkResult(text, resultDecl, onlyDeclarations = true)
  }

  def testValAndVar() {
    val text =
      """
        |trait A {<caret>
        |  val a = 1
        |  var b = 2
        |}
        |""".stripMargin
    val result =
      """
        |trait A extends ExtractedTrait
        |
        |trait ExtractedTrait {
        |
        |  val a = 1
        |  var b = 2
        |}
      """.stripMargin
    val resultDecl =
      """
        |trait A extends ExtractedTrait {
        |  override val a = 1
        |  override var b = 2
        |}
        |
        |trait ExtractedTrait {
        |
        |  val a: Int
        |  var b: Int
        |}
      """.stripMargin

    checkResult(text, result, onlyDeclarations = false)
    checkResult(text, resultDecl, onlyDeclarations = true)
  }

  def testDeclarations() {
    val text =
      """
        |abstract class A {
        |  val a: Int
        |  protected def foo()
        |}
      """.stripMargin

    val result =
      """
        |abstract class A extends ExtractedTrait
        |
        |trait ExtractedTrait {
        |
        |  val a: Int
        |
        |  protected def foo()
        |}
      """.stripMargin
    checkResult(text, result, onlyDeclarations = false)
  }

  def testDontExtractConstructor() {
    val text =
      """
        |class A {<caret>
        |
        |  def this(i: Int) {
        |    b = i
        |  }
        |
        |  val a = 1
        |  var b = 2
        |}
        |""".stripMargin
    val result =
      """
        |class A extends ExtractedTrait {
        |
        |  def this(i: Int) {
        |    b = i
        |  }
        |
        |}
        |
        |trait ExtractedTrait {
        |
        |  val a = 1
        |  var b = 2
        |}
      """.stripMargin

    checkResult(text, result, onlyDeclarations = false)
  }

  def testDontExtractPrivateMethods() {
    val text =
      """
        |class A {<caret>
        |  private def foo() {}
        |
        |  val a = 1
        |  var b = 2
        |}
        |""".stripMargin
    val result =
      """
        |class A extends ExtractedTrait {
        |  private def foo() {}
        |
        |}
        |
        |trait ExtractedTrait {
        |
        |  val a = 1
        |  var b = 2
        |}
      """.stripMargin

    checkResult(text, result, onlyDeclarations = false)
  }

  def testDontExtractTypeDefs() {
    val text =
      """
        |class A {<caret>
        |  class Inner
        |
        |  val a = 1
        |  var b = 2
        |}
        |""".stripMargin
    val result =
      """
        |class A extends ExtractedTrait {
        |  class Inner
        |
        |}
        |
        |trait ExtractedTrait {
        |
        |  val a = 1
        |  var b = 2
        |}
      """.stripMargin

    checkResult(text, result, onlyDeclarations = false)
  }

  def testTypeAlias() {
    val text =
      """
        |trait A {<caret>
        |  type T = Int
        |  type S <: String
        |}
        |""".stripMargin
    val result =
      """
        |trait A extends ExtractedTrait
        |
        |trait ExtractedTrait {
        |
        |  type T = Int
        |  type S <: String
        |}
      """.stripMargin
    val resultDecl =
      """
        |trait A extends ExtractedTrait {
        |  override type T = Int
        |}
        |
        |trait ExtractedTrait {
        |
        |  type T
        |  type S <: String
        |}
      """.stripMargin

    checkResult(text, result, onlyDeclarations = false)
    checkResult(text, resultDecl, onlyDeclarations = true)
  }

  def testExtractFromAnonymous() {
    val text =
      """
        |class A {
        |  val a = new Any {
        |    def foo() {}<caret>
        |    val x = 1
        |  }
        |}
      """.stripMargin

    val result =
      """
        |class A {
        |  val a = new Any with ExtractedTrait
        |
        |  trait ExtractedTrait {
        |
        |    def foo() {}
        |
        |    val x = 1
        |
        |  }
        |
        |}
      """.stripMargin

    checkResult(text, result, onlyDeclarations = false)
  }
}
