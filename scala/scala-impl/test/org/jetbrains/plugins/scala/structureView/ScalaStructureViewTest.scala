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

  def testClass(): Unit = {
    check("""
          class C
          """,
      Node(CLASS, "C",
        Node(FUNCTION, "this")))
  }

  def testTrait(): Unit = {
    check("""
          trait T
          """,
      Node(TRAIT, "T"))
  }

  def testObject(): Unit = {
    check("""
          object O
          """,
      Node(OBJECT, "O"))
  }

  def testMethod(): Unit = {
    check("""
          def m: Int = 1
          """,
      Node(FUNCTION, "m: Int"))
  }

  def testParameterInMethod(): Unit = {
    check("""
          def m(p: Float): Int = 1
          """,
      Node(FUNCTION, "m(Float): Int"))
  }

  def testMultipleParametersInMethod(): Unit = {
    check("""
          def m(p1: Float, p2: Double): Int = 1
          """,
      Node(FUNCTION, "m(Float, Double): Int"))
  }

  def testMultipleArgumentListsInMethod(): Unit = {
    check("""
          def m(p1: Float)(p2: Double): Int = 1
          """,
      Node(FUNCTION, "m(Float)(Double): Int"))
  }

  def testVariable(): Unit = {
    check("""
          var v: Int = 1
          """,
      Node(VAR, "v"))
  }

  def testMultipleVariables(): Unit = {
    check("""
          var v1, v2: Int = 1
          """,
      Node(VAR, "v1"),
      Node(VAR, "v2"))
  }

  def testValue(): Unit = {
    check("""
          val v: Int = 1
          """,
      Node(VAL, "v"))
  }

  def testMultipleValues(): Unit = {
    check("""
          val v1, v2: Int = 1
          """,
      Node(VAL, "v1"),
      Node(VAL, "v2"))
  }

  def testTypeAlias(): Unit = {
    check("""
          type T = Int
          """,
      Node(TYPE_ALIAS, "T"))
  }

//  def testPrimaryConstructor(): Unit = {
//    check("""
//          class C()
//          """,
//      Node(CLASS, "C",
//        Node(FUNCTION, "this")))
//  }

  def testParameterInPrimaryConstructor(): Unit = {
    check("""
          class C(p: Int)
          """,
      Node(CLASS, "C",
        Node(FUNCTION, "this(Int)"))) // TODO
  }

  def testMultipleParametersInPrimaryConstructor(): Unit = {
    check("""
          class C(p1: Float, p2: Double)
          """,
      Node(CLASS, "C",
        Node(FUNCTION, "this(Float, Double)"))) // TODO
  }

  def testMultipleArgumentListsInPrimaryConstructor(): Unit = {
    check("""
          class C(p1: Float)(p2: Double)
          """,
      Node(CLASS, "C",
        Node(FUNCTION, "this(Float)(Double)"))) // TODO
  }

  //  def testVariableInPrimaryConstructor(): Unit = {
//    check("""
//          class C(var p: Int)
//          """,
//      Node(CLASS, "C",
//        Node(VAR, "p")))
//  }

//  def testValueInPrimaryConstructor(): Unit = {
//    check("""
//          class C(val p: Int)
//          """,
//      Node(CLASS, "C",
//        Node(VAL, "p",
//        Node(FUNCTION, "this"))))
//  }

  def testAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this() { this() }
          }
          """,
      Node(CLASS, "C",
        Node(FUNCTION, "this()",
        Node(FUNCTION, "this"))))
  }

  def testParameterInAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this(p: Int) { this() }
          }
          """,
      Node(CLASS, "C",
        Node(FUNCTION, "this(Int)",
        Node(FUNCTION, "this"))))
  }

  def testMultipleParametersInAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this(p1: Float, p2: Double) { this() }
          }
          """,
      Node(CLASS, "C",
        Node(FUNCTION, "this(Float, Double)",
        Node(FUNCTION, "this"))))
  }

  def testMultipleArgumentListsInAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this(p1: Float)(p2: Double) { this() }
          }
          """,
      Node(CLASS, "C",
        Node(FUNCTION, "this(Float)(Double)",
        Node(FUNCTION, "this"))))
  }

  def testInsideClass(): Unit = {
    check("""
          class C {
            class Inner
          }
          """,
      Node(CLASS, "C",
        Node(FUNCTION, "this",
        Node(CLASS, "Inner",
        Node(FUNCTION, "this")))))
  }

  def testInsideTrait(): Unit = {
    check("""
          trait T {
            class Inner
          }
          """,
      Node(TRAIT, "T",
        Node(CLASS, "Inner"),
        Node(FUNCTION, "this")))
  }

  def testInsideObject(): Unit = {
    check("""
          object O {
            class Inner
          }
          """,
      Node(OBJECT, "O",
        Node(CLASS, "Inner"),
        Node(FUNCTION, "this")))
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
