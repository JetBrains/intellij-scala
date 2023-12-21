package org.jetbrains.plugins.scala.structureView

import com.intellij.lang.Language
import com.intellij.testFramework.{PlatformTestUtil, UsefulTestCase}
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

  def testAnonymousClasses_InsideValAndVarBody(): Unit = {
    val code =
      """object MyClass {
        |  //`val`, fields
        |  val value1: Runnable = new Runnable() { override def run(): Unit = () }
        |  val value2: Runnable = { new Runnable() { override def run(): Unit = () } }
        |  val value3: Runnable = { { new Runnable() { override def run(): Unit = () } } }
        |  val (value4: Runnable) = { new Runnable() { override def run(): Unit = () } }
        |  val (value5, value6) = (
        |    new Runnable() { override def run(): Unit = () },
        |    { new Runnable() { override def run(): Unit = () } },
        |  )
        |
        |  //`var`, local members
        |  def main(args: Array[String]): Unit = {
        |    var value1: Runnable = new Runnable() { override def run(): Unit = () }
        |    var value2: Runnable = { new Runnable() { override def run(): Unit = () } }
        |    var value3: Runnable = { { new Runnable() { override def run(): Unit = () } } }
        |    var (value4: Runnable) = { new Runnable() { override def run(): Unit = () } }
        |    var (value5, value6) = (
        |      new Runnable() { override def run(): Unit = () },
        |      { new Runnable() { override def run(): Unit = () } },
        |    )
        |  }
        |}
        |""".stripMargin

    val expectedStructureWithAnonymousEnabled =
      s"""-AnonymousClasses_InsideValAndVarBody.scala
         | -MyClass
         |  value1: Runnable
         |  -$$1
         |   run(): Unit
         |  -value2: Runnable
         |   -$$2
         |    run(): Unit
         |  -value3: Runnable
         |   -$EmptyBlockNodeText
         |    -$$3
         |     run(): Unit
         |  value4
         |  value5
         |  value6
         |  -$$5
         |   run(): Unit
         |  -$$6
         |   run(): Unit
         |  -main(Array[String]): Unit
         |   -$$7
         |    run(): Unit
         |   -$$8
         |    run(): Unit
         |   -$$9
         |    run(): Unit
         |   -$$10
         |    run(): Unit
         |   -$$11
         |    run(): Unit
         |   -$$12
         |    run(): Unit
         |""".stripMargin.trim

    myFixture.configureByText(s"${getTestName(false)}.scala", code)

    //NOTE: our common test code from `ScalaStructureViewTestBase` can't test
    // nodes coming from com.intellij.ide.util.FileStructureNodeProvider
    //In IntelliJ tests they test it using this fixture method
    myFixture.testStructureView { svc =>
      val tree = svc.getTree

      svc.setActionActive(ScalaAnonymousClassesNodeProvider.ID, true)

      PlatformTestUtil.expandAll(tree)
      PlatformTestUtil.assertTreeEqual(tree, expectedStructureWithAnonymousEnabled)
    }
  }

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
