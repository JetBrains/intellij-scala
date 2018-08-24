package org.jetbrains.plugins.scala
package annotator
package template

/**
 * @author Alefas
 * @since 17.10.12
 */
class NeedsToBeMixinTest extends AnnotatorTestBase(NeedsToBeMixin) {
  def testProblem() {
    val message = ScalaBundle.message("mixin.required", "Class", "C", "base", "T")

    assertMatches(messages(
      """
        |trait Base {
        |  def base: Int
        |}
        |trait T extends Base {
        |  abstract override def base: Int = 1
        |}
        |class C extends T
      """.stripMargin
    )) {
      case Error("C", `message`) :: Nil =>
    }
  }

  def testFine() {
    assertNothing(messages(
      """
        |trait Base {
        |  def base: Int
        |}
        |trait T extends Base {
        |  abstract override def base: Int = 1
        |}
        |trait Impl {
        |  def base: Int = 2
        |}
        |class C extends Impl with T
      """.stripMargin
    ))
  }
}
