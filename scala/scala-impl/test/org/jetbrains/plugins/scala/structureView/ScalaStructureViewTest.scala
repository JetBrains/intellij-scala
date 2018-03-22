package org.jetbrains.plugins.scala.structureView

import java.util.Comparator
import javax.swing.Icon

import com.intellij.icons.AllIcons.Nodes._
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.ElementBase
import com.intellij.ui.LayeredIcon
import com.intellij.util.PlatformIcons._
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
      Node(VAR, "v: Int"))
  }

  def testMultipleVariables(): Unit = {
    check("""
          var v1, v2: Int = 1
          """,
      Node(VAR, "v1: Int"),
      Node(VAR, "v2: Int"))
  }

  def testMemberVariable(): Unit = {
    check("""
          class Container {
            var v: Int = 1
          }
          """,
      Node(CLASS, "Container",
        Node(FIELD_VAR, "v: Int")))
  }

  def testAbstractMemberVariable(): Unit = {
    check("""
          trait Container {
            var v: Int
          }
          """,
      Node(TRAIT, "Container",
        Node(ABSTRACT_FIELD_VAR, "v: Int")))
  }

  def testFinalMemberVariable(): Unit = {
    check("""
          trait Container {
            final var v: Int = 1
          }
          """,
      Node(TRAIT, "Container",
        Node(layered(FIELD_VAR, FinalMark), "v: Int")))
  }

  def testMemberVariableVisibility(): Unit = {
    check("""
          trait Container {
             private var v: Int = 1
          }
          """,
      Node(TRAIT, "Container",
        Node(FIELD_VAR, PRIVATE_ICON, "v: Int")))
  }

//  def testVariableTypeInference(): Unit = {
//    check("""
//          var v = 1
//          """,
//      Node(VAR, "v: Int"))
//  }

  def testValue(): Unit = {
    check("""
          val v: Int = 1
          """,
      Node(VAL, "v: Int"))
  }

  def testMultipleValues(): Unit = {
    check("""
          val v1, v2: Int = 1
          """,
      Node(VAL, "v1: Int"),
      Node(VAL, "v2: Int"))
  }

  def testMemberValue(): Unit = {
    check("""
          class Container {
            val v: Int = 1
          }
          """,
      Node(CLASS, "Container",
        Node(FIELD_VAL, "v: Int")))
  }

  def testAbstractMemberValue(): Unit = {
    check("""
          trait Container {
            val v: Int
          }
          """,
      Node(TRAIT, "Container",
        Node(ABSTRACT_FIELD_VAL, "v: Int")))
  }

  def testFinalMemberValue(): Unit = {
    check("""
          trait Container {
            final val v: Int = 1
          }
          """,
      Node(TRAIT, "Container",
        Node(layered(FIELD_VAL, FinalMark), "v: Int")))
  }

  def testMemberValueVisibility(): Unit = {
    check("""
          trait Container {
            private val v: Int = 1
          }
          """,
      Node(TRAIT, "Container",
        Node(FIELD_VAL, PRIVATE_ICON, "v: Int")))
  }

//  def testValueTypeInference(): Unit = {
//    check("""
//          val v = 1
//          """,
//      Node(VAL, "v: Int"))
//  }

  def testTypeAlias(): Unit = {
    check("""
          type A = Int
          """,
      Node(TYPE_ALIAS, "A"))
  }

  def testMemberTypeAlias(): Unit = {
    check("""
          trait Container {
            type A = Int
          }
          """,
      Node(TRAIT, "Container",
        Node(TYPE_ALIAS, "A")))
  }

  def testAbstractMemberTypeAlias(): Unit = {
    check("""
          trait Container {
            type A
          }
          """,
      Node(TRAIT, "Container",
        Node(ABSTRACT_TYPE_ALIAS, "A")))
  }

  def testFinalMemberTypeAlias(): Unit = {
    check("""
          trait Container {
            final type A = Int
          }
          """,
      Node(TRAIT, "Container",
        Node(layered(TYPE_ALIAS, FinalMark), "A")))
  }

  def testMemberTypeAliasVisibility(): Unit = {
    check("""
          trait Container {
            private type A = Int
          }
          """,
      Node(TRAIT, "Container",
        Node(TYPE_ALIAS, PRIVATE_ICON, "A")))
  }

  def testFunction(): Unit = {
    check("""
          def m: Int = 1
          """,
      Node(FUNCTION, "m: Int"))
  }

  def testFunctionVisibility(): Unit = {
    check("""
          private def m: Int = 1
          """,
      Node(FUNCTION, PRIVATE_ICON, "m: Int"))
  }

//  def testFunctionTypeInference(): Unit = {
//    check("""
//          def m(p: Any) = 1
//          """,
//      Node(FUNCTION, "m(Any): Int"))
//  }

  def testMethod(): Unit = {
    check("""
          class Container {
            def m: Int = 1
          }
          """,
      Node(CLASS, "Container",
        Node(METHOD_ICON, "m: Int")))
  }

  def testAbstractMethod(): Unit = {
    check("""
          class Container {
            def m: Int
          }
          """,
      Node(CLASS, "Container",
        Node(ABSTRACT_METHOD_ICON, "m: Int")))
  }

  def testFinalMethod(): Unit = {
    check("""
          class Container {
            final def m: Int = 1
          }
          """,
      Node(CLASS, "Container",
        Node(layered(METHOD_ICON, FinalMark), "m: Int")))
  }

  def testTypeParametersInFunction(): Unit = {
    check("""
          def m[A, B]: Int = 1
          """,
      Node(FUNCTION, "m[A, B]: Int"))
  }

//  def testFunctionTypeParameterPresentation(): Unit = {
//    check("""
//          def m[T <: Any]: Int = 1
//          """,
//      Node(FUNCTION, "m[T]: Int"))
//  }

  def testParameterListInFunction(): Unit = {
    check("""
          def m(): Int = 1
          """,
      Node(FUNCTION, "m(): Int"))
  }

  def testMultipleParametersInFunction(): Unit = {
    check("""
          def m(p1: Float, p2: Double): Int = 1
          """,
      Node(FUNCTION, "m(Float, Double): Int"))
  }

  def testMultipleParameterListsInFunction(): Unit = {
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

  def testObjectVisibility(): Unit = {
    check("""
          private object O
          """,
      Node(OBJECT, PRIVATE_ICON, "O"))
  }

  def testPackageObject(): Unit = {
    check("""
          package object O
          """,
      Node(PACKAGE_OBJECT, "O"))
  }

  def testClass(): Unit = {
    check("""
          class C
          """,
      Node(CLASS, "C"))
  }

  def testAbstractClass(): Unit = {
    check("""
          abstract class C
          """,
      Node(ABSTRACT_CLASS, "C"))
  }

  def testFinalClass(): Unit = {
    check("""
          final class C
          """,
      Node(layered(CLASS, FinalMark), "C"))
  }

  def testClassVisibility(): Unit = {
    check("""
          private class C
          """,
      Node(CLASS, PRIVATE_ICON, "C"))
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

  def testTraitVisibility(): Unit = {
    check("""
          private trait T
          """,
      Node(TRAIT, PRIVATE_ICON, "T"))
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

  def testVariablesInPrimaryConstructor(): Unit = {
    check("""
          class C(var p1: Float, var p2: Double)
          """,
      Node(CLASS, "C(Float, Double)",
        Node(FIELD_VAR, "p1: Float"),
        Node(FIELD_VAR, "p2: Double")))
  }

  def testFinalVariableInPrimaryConstructor(): Unit = {
    check("""
          class C(final var p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(layered(FIELD_VAR, FinalMark), "p: Int")))
  }

  def testVariableVisibilityInPrimaryConstructor(): Unit = {
    check("""
          class C(private var p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(FIELD_VAR, PRIVATE_ICON, "p: Int")))
  }

  def testValuesInPrimaryConstructor(): Unit = {
    check("""
          class C(val p1: Float, val p2: Double)
          """,
      Node(CLASS, "C(Float, Double)",
        Node(FIELD_VAL, "p1: Float",
        Node(FIELD_VAL, "p2: Double"))))
  }

  def testFinalValueInPrimaryConstructor(): Unit = {
    check("""
          class C(final val p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(layered(FIELD_VAL, FinalMark), "p: Int")))
  }

  def testValueVisibilityInPrimaryConstructor(): Unit = {
    check("""
          class C(private val p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(FIELD_VAL, PRIVATE_ICON, "p: Int")))
  }

  def testMultipleParameterListsWithMembersInPrimaryConstructor(): Unit = {
    check("""
          class C(val p1: Float)(val p2: Double)
          """,
      Node(CLASS, "C(Float)(Double)",
        Node(FIELD_VAL, "p1: Float",
        Node(FIELD_VAL, "p2: Double"))))
  }

  def testParametersInCaseClass(): Unit = {
    check("""
          case class C(p1: Float, p2: Double)
          """,
      Node(CLASS, "C(Float, Double)",
        Node(FIELD_VAL, "p1: Float"),
        Node(FIELD_VAL, "p2: Double")))
  }

  def testValuesInCaseClass(): Unit = {
    check("""
          case class C(val p1: Float, val p2: Double)
          """,
      Node(CLASS, "C(Float, Double)",
        Node(FIELD_VAL, "p1: Float"),
        Node(FIELD_VAL, "p2: Double")))
  }

  def testFinalValueParameterInCaseClass(): Unit = {
    check("""
          case class C(final val p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(layered(FIELD_VAL, FinalMark), "p: Int")))
  }

  def testPrivateValueParameterInCaseClass(): Unit = {
    check("""
          case class C(private val p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(FIELD_VAL, PRIVATE_ICON, "p: Int")))
  }

  def testVariablesInCaseClass(): Unit = {
    check("""
          case class C(var p1: Float, var p2: Double)
          """,
      Node(CLASS, "C(Float, Double)",
        Node(FIELD_VAR, "p1: Float"),
        Node(FIELD_VAR, "p2: Double")))
  }

  def testFinalVariableParameterInCaseClass(): Unit = {
    check("""
          case class C(final var p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(layered(FIELD_VAR, FinalMark), "p: Int")))
  }

  def testPrivateVariableParameterInCaseClass(): Unit = {
    check("""
          case class C(private var p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(FIELD_VAR, PRIVATE_ICON, "p: Int")))
  }

  def testAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this() { this() }
          }
          """,
      Node(CLASS, "C",
        Node(METHOD_ICON, "this()")))
  }

  def testParameterInAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this(p: Int) { this() }
          }
          """,
      Node(CLASS, "C",
        Node(METHOD_ICON, "this(Int)")))
  }

  def testMultipleParametersInAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this(p1: Float, p2: Double) { this() }
          }
          """,
      Node(CLASS, "C",
        Node(METHOD_ICON, "this(Float, Double)")))
  }

  def testMultipleParameterListsInAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this(p1: Float)(p2: Double) { this() }
          }
          """,
      Node(CLASS, "C",
        Node(METHOD_ICON, "this(Float)(Double)")))
  }

  def testBlock(): Unit = {
    check("""
          {}
          """,
      new Node(CLASS_INITIALIZER, ""))
  }

  def testInsideClass(): Unit = {
    check("""
          class Container {
            {}
            var v1: Int = 1
            val v2: Int = 1
            type A = Int
            def m: Int = 1
            class C
            trait T
            object O
          }
          """,
      Node(CLASS, "Container",
        new Node(CLASS_INITIALIZER, ""),
        Node(FIELD_VAR, "v1: Int"),
        Node(FIELD_VAL, "v2: Int"),
        Node(TYPE_ALIAS, "A"),
        Node(METHOD_ICON, "m: Int"),
        Node(CLASS, "C"),
        Node(TRAIT, "T"),
        Node(OBJECT, "O")))
  }

  def testInsideTrait(): Unit = {
    check("""
          trait Container {
            {}
            var v1: Int = 1
            val v2: Int = 1
            type A = Int
            def m: Int = 1
            class C
            trait T
            object O
          }
          """,
      Node(TRAIT, "Container",
        new Node(CLASS_INITIALIZER, ""),
        Node(FIELD_VAR, "v1: Int"),
        Node(FIELD_VAL, "v2: Int"),
        Node(TYPE_ALIAS, "A"),
        Node(METHOD_ICON, "m: Int"),
        Node(CLASS, "C"),
        Node(TRAIT, "T"),
        Node(OBJECT, "O")))
  }

  def testInsideObject(): Unit = {
    check("""
          object Container {
            {}
            var v1: Int = 1
            val v2: Int = 1
            type A = Int
            def m: Int = 1
            class C
            trait T
            object O
          }
          """,
      Node(OBJECT, "Container",
        new Node(CLASS_INITIALIZER, ""),
        Node(FIELD_VAR, "v1: Int"),
        Node(FIELD_VAL, "v2: Int"),
        Node(TYPE_ALIAS, "A"),
        Node(METHOD_ICON, "m: Int"),
        Node(CLASS, "C"),
        Node(TRAIT, "T"),
        Node(OBJECT, "O")))
  }

  def testInsideBlock(): Unit = {
    check("""
          {
            {}
            var v1: Int = 1
            val v2: Int = 1
            type A = Int
            def m: Int = 1
            class C
            trait T
            object O
          }
          """,
      new Node(CLASS_INITIALIZER, "",
        new Node(CLASS_INITIALIZER, ""),
        Node(FUNCTION, "m: Int"),
        Node(CLASS, "C"),
        Node(TRAIT, "T"),
        Node(OBJECT, "O")))
  }

  def testInsideVariable(): Unit = {
    check("""
          var v: Int = {
            {}
            var v1: Int = 1
            val v2: Int = 1
            type A = Int
            def m: Int = 1
            class C
            trait T
            object O
          }
          """,
      Node(VAR, "v: Int",
        new Node(CLASS_INITIALIZER, ""),
        Node(FUNCTION, "m: Int"),
        Node(CLASS, "C"),
        Node(TRAIT, "T"),
        Node(OBJECT, "O")))
  }

  def testInsideValue(): Unit = {
    check("""
          val v: Int = {
            {}
            var v1: Int = 1
            val v2: Int = 1
            type A = Int
            def m: Int = 1
            class C
            trait T
            object O
          }
          """,
      Node(VAL, "v: Int",
        new Node(CLASS_INITIALIZER, ""),
        Node(FUNCTION, "m: Int"),
        Node(CLASS, "C"),
        Node(TRAIT, "T"),
        Node(OBJECT, "O")))
  }

  def testInsideMethod(): Unit = {
    check("""
          def m: Int = {
            {}
            var v1: Int = 1
            val v2: Int = 1
            type A = Int
            def m: Int = 1
            class C
            trait T
            object O
          }
          """,
      Node(FUNCTION, "m: Int",
        new Node(CLASS_INITIALIZER, ""),
        Node(FUNCTION, "m: Int"),
        Node(CLASS, "C"),
        Node(TRAIT, "T"),
        Node(OBJECT, "O")))
  }

  def testClassAndObject(): Unit = {
    check("""
          class C
          object C
          """,
      Node(CLASS, "C"),
      Node(OBJECT, "C"))
  }

  def testOrdering(): Unit = {
    check("""
          {}
          var r1: Int = 1
          val l1: Int = 1
          type A1 = Int
          def m1: Int = 1
          class C1
          trait T1
          object O1
          object O2
          trait T2
          class C2
          def m2: Int = 1
          type A2 = Int
          val l2: Int = 1
          var r2: Int = 1;
          {}
          """,
      new Node(CLASS_INITIALIZER, ""),
      Node(VAR, "r1: Int"),
      Node(VAL, "l1: Int"),
      Node(TYPE_ALIAS, "A1"),
      Node(FUNCTION, "m1: Int"),
      Node(CLASS, "C1"),
      Node(TRAIT, "T1"),
      Node(OBJECT, "O1"),
      Node(OBJECT, "O2"),
      Node(TRAIT, "T2"),
      Node(CLASS, "C2"),
      Node(FUNCTION, "m2: Int"),
      Node(TYPE_ALIAS, "A2"),
      Node(VAL, "l2: Int"),
      Node(VAR, "r2: Int"),
      new Node(CLASS_INITIALIZER, ""))
  }

  private def check(@Language("Scala") code: String, nodes: Node*): Unit = {
    val actualNode = {
      val file = psiFileOf(code)(getProject)

      val model = new ScalaStructureViewModel(file)

      val sorter: Seq[TreeElement] => Seq[TreeElement] = elements => {
        val comparators = model.getSorters.filterNot(_.isVisible).reverse.map(_.getComparator.asInstanceOf[Comparator[TreeElement]])
        comparators.foldLeft(elements)((elements, comparator) => elements.sortWith((e1, e2) => comparator.compare(e1, e2) <= 0))
      }

      Node(model.getRoot, sorter)
    }

    val expectedNode = new Node(FILE, "foo.scala", nodes: _*)

    Assert.assertEquals(expectedNode.toString, actualNode.toString)
  }
}

private object ScalaStructureViewTest {
  private final val IconFileName = new Regex("(?<=/)[^/]+(?=\\.png)")

  class Node(icon: Icon, name: String, children: Node*) {
    override def toString: String =
      IconFileName.findAllIn(Option(icon).mkString).mkString("[", ", ", "] ") + name + "\n" +
        children.map(node => "  " + node.toString).mkString
  }

  object Node {
    def apply(baseIcon: Icon, visibilityIcon: Icon, name: String, children: Node*): Node =
      new Node(ElementBase.buildRowIcon(baseIcon, visibilityIcon), name, children: _*)

    def apply(icon: Icon, name: String, children: Node*): Node =
      Node(icon, PUBLIC_ICON, name, children: _*)

    def apply(element: StructureViewTreeElement, sorter: Seq[TreeElement] => Seq[TreeElement]): Node = {
      val presentation = element.getPresentation
      val children = sorter(element.getChildren).map { case element: StructureViewTreeElement => Node(element, sorter) }
      new Node(presentation.getIcon(false), presentation.getPresentableText, children: _*)
    }
  }

  def psiFileOf(@Language("Scala") s: String)(project: Project): ScalaFile = {
    PsiFileFactory.getInstance(project)
      .createFileFromText("foo.scala", ScalaFileType.INSTANCE, s)
      .asInstanceOf[ScalaFile]
  }

  def layered(icons: Icon*): Icon = {
    val result = new LayeredIcon(icons.length)
    icons.zipWithIndex.foreach { case (icon, index) => result.setIcon(icon, index)}
    result
  }
}
