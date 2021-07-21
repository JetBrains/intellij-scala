package org.jetbrains.plugins.scala.structureView

import com.intellij.lang.Language
import com.intellij.util.PlatformIcons
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.icons.Icons._
import org.jetbrains.plugins.scala.structureView.ScalaStructureViewTestBase.Node

class Scala3StructureViewTest extends ScalaStructureViewCommonTests {

  import com.intellij.util.{PlatformIcons => PI}

  override protected def scalaLanguage: Language = Scala3Language.INSTANCE

  override protected def check(@org.intellij.lang.annotations.Language("Scala 3") code: String, nodes: Node*): Unit =
    super.check(code, nodes: _*)

  private val EnumCaseIcon = CLASS

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
      |  case X
      |
      |type MyTypeAlias[T] = (String, T)
      |
      |val myValue = 1
      |
      |var myVariable = 2
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
      Node(ENUM, "MyEnum", Node(EnumCaseIcon, "X")),
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

  def testEnum_Simple(): Unit = {
    check(
      """enum MyEnum:
        |    case A, B
        |    case C
        |""".stripMargin,
      Node(
        ENUM, "MyEnum",
        Node(EnumCaseIcon, "A"),
        Node(EnumCaseIcon, "B"),
        Node(EnumCaseIcon, "C"),
      )
    )
  }

  def testEnum_WithTypeParameters(): Unit = {
    check(
      """enum ListEnum[+A]:
        |    case Cons(h: A, t: ListEnum[A])
        |    case Empty
        |""".stripMargin,
      Node(
        ENUM, "ListEnum[A]",
        Node(EnumCaseIcon, "Cons(A, ListEnum[A])"),
        Node(EnumCaseIcon, "Empty")
      )
    )
  }

  def testEnum_WithMembersAndParameters(): Unit = {
    check(
      """enum Planet(mass: Double, radius: Double):
        |  private final val G = 6.67300E-11
        |  def surfaceGravity = G * mass / (radius * radius)
        |  def surfaceWeight(otherMass: Double) =  otherMass * surfaceGravity
        |
        |  case Mercury extends Planet(3.303e+23, 2.4397e6)
        |  case Venus   extends Planet(4.869e+24, 6.0518e6)
        |  case Earth   extends Planet(5.976e+24, 6.37814e6)
        |  case Mars    extends Planet(6.421e+23, 3.3972e6)
        |  case Jupiter extends Planet(1.9e+27,   7.1492e7)
        |  case Saturn  extends Planet(5.688e+26, 6.0268e7)
        |  case Uranus  extends Planet(8.686e+25, 2.5559e7)
        |  case Neptune extends Planet(1.024e+26, 2.4746e7)
        |end Planet
        |""".stripMargin,
      Node(
        ENUM, "Planet(Double, Double)",
        Node(layered(FIELD_VAL, FinalMark), PlatformIcons.PRIVATE_ICON, "G"),
        Node(PI.METHOD_ICON, "surfaceGravity"),
        Node(PI.METHOD_ICON, "surfaceWeight(Double)"),
        Node(EnumCaseIcon, "Mercury"),
        Node(EnumCaseIcon, "Venus"),
        Node(EnumCaseIcon, "Earth"),
        Node(EnumCaseIcon, "Mars"),
        Node(EnumCaseIcon, "Jupiter"),
        Node(EnumCaseIcon, "Saturn"),
        Node(EnumCaseIcon, "Uranus"),
        Node(EnumCaseIcon, "Neptune"),
      )
    )
  }
}
