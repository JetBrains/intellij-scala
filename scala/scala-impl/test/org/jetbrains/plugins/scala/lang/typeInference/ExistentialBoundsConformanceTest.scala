package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class ExistentialBoundsConformanceTest extends ScalaLightCodeInsightFixtureTestCase {
  def testSCL19100(): Unit = {
    myFixture.addClass(
      """
        |package foo;
        |
        |class Parent {}
        |class Child extends Parent {}
        |
        |class GenericClass<T extends Parent>{}
        |
        |class ConcreteClass extends GenericClass<Child> {
        |    public static GenericClass<?> getModuleType() {
        |        return null;
        |    }
        |}
        |""".stripMargin
    )

    checkTextHasNoErrors(
      """
        |package foo;
        |class ScalaClass {
        |  val x: GenericClass[_ <: Parent] = ConcreteClass.getModuleType
        |}
        |""".stripMargin
    )
  }

  def testSCL20558(): Unit = {
    myFixture.addClass(
      """
        |package foo;
        |public interface EditorCustomElementRenderer {
        |}
        |""".stripMargin
    )

    myFixture.addClass(
      """
        |package foo;
        |public class MyJavaInlay<T extends EditorCustomElementRenderer> {
        |}
        |""".stripMargin
    )

    checkTextHasNoErrors(
      """
        |package foo
        |
        |object Example1 {
        |  val value1: MyJavaInlay[_ <: EditorCustomElementRenderer] = ( null : MyJavaInlay[_])
        |}
        |""".stripMargin
    )

    checkHasErrorAroundCaret(
      s"""
         |package foo
         |object Example2 {
         |  trait MyInlay1[T <: EditorCustomElementRenderer]
         |
         |  val value2: MyInlay1[_ <: EditorCustomElementRenderer] = (null: MyIn${CARET}lay1[_])
         |}
         |""".stripMargin
    )
  }
}
