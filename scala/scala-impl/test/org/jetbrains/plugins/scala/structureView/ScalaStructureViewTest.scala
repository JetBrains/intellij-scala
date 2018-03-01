package org.jetbrains.plugins.scala.structureView

import javax.swing.Icon

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.icons.Icons._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.structureView.ScalaStructureViewModel
import org.jetbrains.plugins.scala.structureView.ScalaStructureViewTest._
import org.junit.Assert

import scala.util.matching.Regex

/**
  * @author Pavel Fatin
  */
class ScalaStructureViewTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testEmptyFile(): Unit = {
    check("")
  }

  def testVariable(): Unit = {
    check("""
          var v: Int = 1
          """,
      Node(FIELD_VAR, "v"))
  }

  def testMultipleVariables(): Unit = {
    check("""
          var v1, v2: Int = 1
          """,
      Node(FIELD_VAR, "v1"),
      Node(FIELD_VAR, "v2"))
  }

  def testValue(): Unit = {
    check("""
          val v: Int = 1
          """,
      Node(FIELD_VAL, "v"))
  }

  def testMultipleValues(): Unit = {
    check("""
          val v1, v2: Int = 1
          """,
      Node(FIELD_VAL, "v1"),
      Node(FIELD_VAL, "v2"))
  }

  def testTypeAlias(): Unit = {
    check("""
          type T = Int
          """,
      Node(TYPE_ALIAS, "T"))
  }

  def testMethod(): Unit = {
    check("""
          def m: Int = 1
          """,
      Node(FUNCTION, "m: Int"))
  }

  def testTypeParametersInMethod(): Unit = {
    check("""
          def m[A, B]: Int = 1
          """,
      Node(FUNCTION, "m[A, B]: Int"))
  }

//  def testMethodTypeParameterPresentation(): Unit = {
//    check("""
//          def m[T <: Any]: Int = 1
//          """,
//      Node(FUNCTION, "m[T]: Int"))
//  }

  def testParameterListInMethod(): Unit = {
    check("""
          def m(): Int = 1
          """,
      Node(FUNCTION, "m(): Int"))
  }

  def testMultipleParametersInMethod(): Unit = {
    check("""
          def m(p1: Float, p2: Double): Int = 1
          """,
      Node(FUNCTION, "m(Float, Double): Int"))
  }

  def testMultipleParameterListsInMethod(): Unit = {
    check("""
          def m(p1: Float)(p2: Double): Int = 1
          """,
      Node(FUNCTION, "m(Float)(Double): Int"))
  }

  def testObject(): Unit = {
    check("""
          object O
          """,
      Node(OBJECT, "O"))
  }

  def testClass(): Unit = {
    check("""
          class C
          """,
      Node(CLASS, "C"))
  }

  def testClassTypeParameters(): Unit = {
    check("""
          class C[A, B]
          """,
      Node(CLASS, "C[A, B]"))
  }

  def testTrait(): Unit = {
    check("""
          trait T
          """,
      Node(TRAIT, "T"))
  }

  def testTraitTypeParameters(): Unit = {
    check("""
          trait T[A, B]
          """,
      Node(TRAIT, "T[A, B]"))
  }

  def testTypeDefinitionTypeParameterPresentation(): Unit = {
    check("""
          class C[T <: Any]
          """,
      Node(CLASS, "C[T]"))
  }

  def testParameterListInPrimaryConstructor(): Unit = {
    check("""
          class C()
          """,
      Node(CLASS, "C()"))
  }

  def testMultipleParametersInPrimaryConstructor(): Unit = {
    check("""
          class C(p1: Float, p2: Double)
          """,
      Node(CLASS, "C(Float, Double)"))
  }

  def testMultipleParameterListsInPrimaryConstructor(): Unit = {
    check("""
          class C(p1: Float)(p2: Double)
          """,
      Node(CLASS, "C(Float)(Double)"))
  }

//  def testVariablesInPrimaryConstructor(): Unit = {
//    check("""
//          class C(var p1: Float, p2: Double)
//          """,
//      Node(CLASS, "C(Float, Double)",
//        Node(FIELD_VAR, "p1"),
//        Node(FIELD_VAR, "p2")))
//  }

//  def testValuesInPrimaryConstructor(): Unit = {
//    check("""
//          class C(val p1: Floar, val p2: Double)
//          """,
//      Node(CLASS, "C(Float, Double)",
//        Node(FIELD_FIELD_VAL, "p1",
//        Node(FIELD_FIELD_VAL, "p2")))
//  }

//  def testMultipleParameterListsWithMembersInPrimaryConstructor(): Unit = {
//    check("""
//          class C(val p1: Float)(val p2: Double)
//          """,
//      Node(CLASS, "C(Float)(Double)",
//        Node(FIELD_FIELD_VAL, "p1",
//        Node(FIELD_FIELD_VAL, "p2"))))
//  }

  def testAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this() { this() }
          }
          """,
      Node(CLASS, "C",
        Node(FUNCTION, "this()")))
  }

  def testParameterInAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this(p: Int) { this() }
          }
          """,
      Node(CLASS, "C",
        Node(FUNCTION, "this(Int)")))
  }

  def testMultipleParametersInAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this(p1: Float, p2: Double) { this() }
          }
          """,
      Node(CLASS, "C",
        Node(FUNCTION, "this(Float, Double)")))
  }

  def testMultipleParameterListsInAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this(p1: Float)(p2: Double) { this() }
          }
          """,
      Node(CLASS, "C",
        Node(FUNCTION, "this(Float)(Double)")))
  }

  def testInsideClass(): Unit = {
    check("""
          class C {
            class Inner
          }
          """,
      Node(CLASS, "C",
        Node(CLASS, "Inner")))
  }

  def testInsideTrait(): Unit = {
    check("""
          trait T {
            class Inner
          }
          """,
      Node(TRAIT, "T",
        Node(CLASS, "Inner")))
  }

  def testInsideObject(): Unit = {
    check("""
          object O {
            class Inner
          }
          """,
      Node(OBJECT, "O",
        Node(CLASS, "Inner")))
  }

//  def testInsideMethod(): Unit = {
//    check("""
//          def m = {
//            class Inner
//          }
//          """,
//      Node(FUNCTION, "m",
//        Node(CLASS, "Inner")))
//  }


//  def testMultipleClasses(): Unit = {
//    check("""
//          class A
//          class B
//          """,
//      Node(CLASS, "A",
//        Node(FUNCTION, "this")),
//      Node(CLASS, "B",
//        Node(FUNCTION, "this")))
//  }
//
//  def testClassAndObject(): Unit = {
//    check("""
//          class C
//          object C
//          """,
//      Node(CLASS, "C",
//        Node(FUNCTION, "this")),
//      Node(OBJECT, "C"))
//  }

  private def check(@Language("Scala") code: String, nodes: Node*): Unit = {
    val actualNode = {
      val file = psiFileOf(code)(getProject)
      val model = new ScalaStructureViewModel(file)
      Node(model.getRoot)
    }

    val expectedNode = Node(FILE, "foo.scala", nodes: _*)

    Assert.assertEquals(expectedNode.toString, actualNode.toString)
  }
}

private object ScalaStructureViewTest {
  private final val IconFileName = new Regex("(?<=/)[^/]+(?=\\.png)")

  case class Node(icon: Icon, name: String, children: Node*) {
    override def toString: String =
      IconFileName.findFirstIn(icon.toString).mkString("[", "", "] ") + name + "\n" +
        children.map(node => "  " + node.toString).mkString
  }

  object Node {
    def apply(element: StructureViewTreeElement): Node = {
      val presentation = element.getPresentation

      Node(presentation.getIcon(true), presentation.getPresentableText,
        element.getChildren.map { case element: StructureViewTreeElement => Node(element) }: _*)
    }
  }

  def psiFileOf(@Language("Scala") s: String)(project: Project): ScalaFile = {
    PsiFileFactory.getInstance(project)
      .createFileFromText("foo.scala", ScalaFileType.INSTANCE, s)
      .asInstanceOf[ScalaFile]
  }
}
