package org.jetbrains.plugins.scala.structureView

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.icons.Icons._
import org.jetbrains.plugins.scala.structureView.ScalaStructureViewTestBase.Node

class Scala3StructureViewTest extends ScalaStructureViewCommonTests {

  override protected def scalaLanguage: Language = Scala3Language.INSTANCE

  override protected def check(@org.intellij.lang.annotations.Language("Scala 3") code: String, nodes: Node*): Unit =
    super.check(code, nodes: _*)

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
      |enum MyEnum:
      |  case X, Y, Z
      |
      |type MyTypeAlias[T] = (String, T)
      |
      |val myValue = 1
      |
      |val myVariable = 2
      |
      |def myFunction: String = ???
      |
      |extension (x: MyClass) def myExtension(y: String) = ???
      |""".stripMargin

  private val TopLevelDefinitionsNodes: Seq[Node] =
    Seq(
      Node(CLASS, "MyClass()"),
      Node(CASE_CLASS, "MyCaseClass()"),
      Node(ABSTRACT_CLASS, "MyAbstractClass()"),
      Node(TRAIT, "MyTrait"),
      Node(OBJECT, "MyObject"),
      Node(ENUM, "MyEnum"),
      Node(TYPE_ALIAS, "MyTypeAlias"),
      Node(VAL, "myValue"),
      Node(VAR, "myVariable"),
      Node(FUNCTION, "myFunction: String"),
      Node(EXTENSION, "extension (MyClass)", Seq(
        Node(FUNCTION, "myExtension(String)")
      ): _*),
    )

  def testTopLevelDefinitions_InRootPackage(): Unit = {
    check(TopLevelDefinitionsText, TopLevelDefinitionsNodes: _*)
  }

  def testTopLevelDefinitions_InPackage(): Unit = {
    check("package aaa.bbb.ccc\n" + TopLevelDefinitionsText, TopLevelDefinitionsNodes: _*)
  }
}
