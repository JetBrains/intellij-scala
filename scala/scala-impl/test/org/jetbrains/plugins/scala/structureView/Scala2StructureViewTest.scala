package org.jetbrains.plugins.scala.structureView

import com.intellij.lang.Language
import com.intellij.testFramework.UsefulTestCase.assertThrows
import org.jetbrains.plugins.scala.{ScalaLanguage, ScalaVersion}
import org.jetbrains.plugins.scala.icons.Icons._
import org.jetbrains.plugins.scala.structureView.ScalaStructureViewTestBase.Node

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

  def testThatCheckMethodCorrectlyFailsOnWrongIcons(): Unit = {
    assertThrows(
      classOf[org.junit.ComparisonFailure],
      null,
      () => {
        check(
          """class A""",
          Node(OBJECT, "A")
        )
      }
    )
  }

  def testThatCheckMethodCorrectlyFailsOnWrongNames(): Unit = {
    assertThrows(
      classOf[org.junit.ComparisonFailure],
      null,
      () => {
        check(
          """class A""",
          Node(CLASS, "B")
        )
      }
    )
  }

  // TODO:
  //  We could parse top level definitions in Scala, even though it's not compilable (we already do so in root package, for "script" files)
  //  We could show the error later in annotator.
//  def testTopLevelDefinitions_InPackage(): Unit = {
//    check("package aaa.bbb.ccc\n" + TopLevelDefinitionsText, TopLevelDefinitionsNodes: _*)
//  }
}
