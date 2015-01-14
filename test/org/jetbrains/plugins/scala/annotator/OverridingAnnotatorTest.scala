package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.01.12
 */

class OverridingAnnotatorTest extends SimpleTestCase {
  final val Header = "\n"
  
  def testPrivateVal() {
    assertMatches(messages(
      """
        |object ppp {
        |class Base {
        |  private val something = 5
        |}
        |
        |class Derived extends Base {
        |  private val something = 8
        |}
        |}
      """.stripMargin)) {
      case Nil =>
    }
  }

  def testClassParameter() {
    assertMatches(messages(
      """
        |object ppp {
        |class A(x: Int)
        |class B(val x: Int) extends A(x)
        |case class C(x: Int) extends A(x)
        |}
      """.stripMargin)) {
      case Nil =>
    }
  }

  def testVal() {
    assertMatches(messages(
      """
        |object ppp {
        |class Base {
        |  val something = 5
        |}
        |
        |class Derived extends Base {
        |  val something = 8
        |}
        |}
      """.stripMargin)) {
      case List(Error(something, "Value 'something' needs override modifier")) =>
    }
  }

  def testNotConcreteMember() {
    assertMatches(messages(
      """
        |object ppp {
        |class Base {
        |  def foo() = 1
        |}
        |
        |abstract class Derived extends Base {
        |  def foo(): Int
        |}
        |}
      """.stripMargin)) {
      case Nil =>
    }
  }

  def testOverrideFinalMethod() {
    assertMatches(messages(
      """
        |object ppp {
        | class Base {
        |   final def foo() = 1
        | }
        |
        | class Derived extends Base {
        |   override def foo() = 2
        | }
        |}
      """.stripMargin)) {
      case List(Error(foo, "Method 'foo' cannot override final member")) =>
    }
  }

  def testOverrideFinalVal() {
    assertMatches(messages(
      """
        |object ppp {
        | class Base {
        |   final val foo = 1
        | }
        |
        | class Derived extends Base {
        |   override val foo = 2
        | }
        |}
      """.stripMargin)) {
      case List(Error(foo, "Value 'foo' cannot override final member")) =>
    }
  }

  def testOverrideFinalVar() {
    assertMatches(messages(
      """
        |object ppp {
        | class Base {
        |   final var foo = 1
        | }
        |
        | class Derived extends Base {
        |   override var foo = 2
        | }
        |}
      """.stripMargin)) {
      case List(Error(foo, "Variable 'foo' cannot override final member")) =>
    }
  }

  def testOverrideFinalAlias() {
    assertMatches(messages(
      """
        |object ppp {
        | class Base {
        |   final type foo = Int
        | }
        |
        | class Derived extends Base {
        |   override type foo = String
        | }
        |}
      """.stripMargin)) {
      case List(Error(foo, "Type 'foo' cannot override final member")) =>
    }
  }

  def messages(code: String): List[Message] = {
    val annotator = new OverridingAnnotator() {}
    val mock = new AnnotatorHolderMock

    val element: PsiElement = (Header + code).parse

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitFunction(fun: ScFunction) {
        if (fun.getParent.isInstanceOf[ScTemplateBody]) {
          annotator.checkOverrideMethods(fun, mock, isInSources = false)
        }
        super.visitFunction(fun)
      }

      override def visitTypeDefinition(typedef: ScTypeDefinition) {
        if (typedef.getParent.isInstanceOf[ScTemplateBody]) {
          annotator.checkOverrideTypes(typedef, mock)
        }
        super.visitTypeDefinition(typedef)
      }

      override def visitTypeAlias(alias: ScTypeAlias) {
        if (alias.getParent.isInstanceOf[ScTemplateBody]) {
          annotator.checkOverrideTypes(alias, mock)
        }
        super.visitTypeAlias(alias)
      }

      override def visitVariable(varr: ScVariable) {
        if (varr.getParent.isInstanceOf[ScTemplateBody] ||
          varr.getParent.isInstanceOf[ScEarlyDefinitions]) {
          annotator.checkOverrideVars(varr, mock, isInSources = false)
        }
        super.visitVariable(varr)
      }

      override def visitValue(v: ScValue) {
        if (v.getParent.isInstanceOf[ScTemplateBody] ||
          v.getParent.isInstanceOf[ScEarlyDefinitions]) {
          annotator.checkOverrideVals(v, mock, isInSources = false)
        }
        super.visitValue(v)
      }

      override def visitClassParameter(parameter: ScClassParameter) {
        annotator.checkOverrideClassParameters(parameter, mock)
        super.visitClassParameter(parameter)
      }
    }

    element.accept(visitor)

    mock.annotations
  }
}
