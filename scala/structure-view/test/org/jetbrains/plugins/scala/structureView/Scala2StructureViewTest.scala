package org.jetbrains.plugins.scala.structureView

import com.intellij.lang.Language
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.icons.Icons.*
import org.jetbrains.plugins.scala.structureView.ScalaStructureViewTestBase.Node
import org.jetbrains.plugins.scala.{ScalaLanguage, ScalaVersion}

class Scala2StructureViewTest extends ScalaStructureViewCommonTests {

  override protected def scalaLanguage: Language = ScalaLanguage.INSTANCE

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  // NOTE: in Scala 2 top level functions/value/etc... are not supported
  // but it still will not hurt to show them in the structure view even in a non-compilable code
  private val TopLevelDefinitionsText =
    """class MyClass()
      |
      |case class MyCaseClass()
      |
      |abstract class MyAbstractClass()
      |
      |trait MyTrait
      |
      |object MyObject
      |
      |type MyTypeAlias[T] = (String, T)
      |
      |val myValue = 1
      |
      |var myVariable = 2
      |
      |def myFunction: String = ???
      |
      |""".stripMargin

  private lazy val TopLevelDefinitionsNodes: Seq[Node] =
    Seq(
      Node(CLASS, "MyClass()"),
      Node(CASE_CLASS, "MyCaseClass()"),
      Node(ABSTRACT_CLASS, "MyAbstractClass()"),
      Node(TRAIT, "MyTrait"),
      Node(OBJECT, "MyObject"),
      Node(TYPE_ALIAS, "MyTypeAlias"),
      Node(VAL, "myValue"),
      Node(VAR, "myVariable"),
      Node(FUNCTION, "myFunction: String"),
    )

  def testTopLevelDefinitions_InRootPackage(): Unit = {
    check(TopLevelDefinitionsText, TopLevelDefinitionsNodes: _*)
  }

  def testThatCheckMethodCorrectlyFailsOnWrongIcons(): Unit =
    assertThrows(
      classOf[org.junit.ComparisonFailure],
      """class A""",
      Node(OBJECT, "A")
    )

  def testThatCheckMethodCorrectlyFailsOnWrongNames(): Unit =
    assertThrows(
      classOf[org.junit.ComparisonFailure],
      """class A""",
      Node(CLASS, "B")
    )

  // TODO:
  //  We could parse top level definitions in Scala, even though it's not compilable (we already do so in root package, for "script" files)
  //  We could show the error later in annotator.
//  def testTopLevelDefinitions_InPackage(): Unit = {
//    check("package aaa.bbb.ccc\n" + TopLevelDefinitionsText, TopLevelDefinitionsNodes: _*)
//  }

  /**
   * NOTE: kind of a hack for Scala 3.<br>
   * `UsefulTestCase.assertThrows` cannot be called directly from the test methods because in Scala 3 lambdas are
   * private in the bytecode and have names starting with the methods they are defined in.<br>
   *
   * E.g.: `def testFoo(): Unit = UsefulTestCase.assertThrows(classOf[org.junit.ComparisonFailure], () => check(code, nodes: _*))`
   * will produce `private static final void testFoo$$anonFun$1();` which will cause JUnit's failed assertion
   * "Test method isn't public"
   */
  private def assertThrows(exceptionClass: Class[_ <: Throwable],
                           @org.intellij.lang.annotations.Language("Scala") code: String,
                           nodes: Node*): Unit =
    UsefulTestCase.assertThrows(exceptionClass, () => check(code, nodes: _*))
}
