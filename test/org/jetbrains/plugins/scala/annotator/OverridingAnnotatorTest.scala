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

  def testSyntheticUnapply(): Unit = {
    assertMatches(messages(
      """
        |trait Test {
        |  trait Tree
        |  trait Name
        |  abstract class SelectExtractor {
        |    def apply(qualifier: Tree, name: Name): Select
        |    def unapply(select: Select): Option[(Tree, Name)]
        |  }
        |  case class Select(qualifier: Tree, name: Name)
        |    extends Tree {
        |  }
        |  object Select extends SelectExtractor {} // object creation impossible, unapply not defined...
        |
        |  def test(t: Tree) = t match {
        |    case Select(a, b) => // cannot resolve extractor
        |  }
        |}
      """.stripMargin)) {
      case Nil =>
    }
  }
  
  def testPrivateVal(): Unit = {
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

  def testClassParameter(): Unit = {
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

  def testVal(): Unit = {
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
      case List(Error("something", "Value 'something' needs override modifier")) =>
    }
  }

  def testNotConcreteMember(): Unit = {
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

  def testOverrideFinalMethod(): Unit = {
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
      case List(Error("foo", "Method 'foo' cannot override final member")) =>
    }
  }

  def testOverrideFinalVal(): Unit = {
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
      case List(Error("foo", "Value 'foo' cannot override final member")) =>
    }
  }

  def testOverrideFinalVar(): Unit = {
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
      case List(Error("foo", "Variable 'foo' cannot override final member")) =>
    }
  }

  def testOverrideFinalAlias(): Unit = {
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
      case List(Error("foo", "Type 'foo' cannot override final member")) =>
    }
  }

  //SCL-3258
  def testOverrideVarWithFunctions(): Unit = {
    val code =
      """
        |
        |abstract class Parent {
        |  var id: Int
        |}
        |
        |class Child extends Parent {
        |  def id = 0
        |  def id_=(v: Int) {
        |  }
        |}
      """.stripMargin
    assertMatches(messages(code)) {
      case Nil =>
    }
  }

  //SCL-4036
  def testDefOverrideValVar(): Unit = {
    val code =
    """
      |object ppp {
      |class A(val oof = 42, var rab = 24) {
      |  val foo = 42
      |  var bar = 24
      |}
      |
      |class B extends A {
      |  override def foo = 999
      |  override def bar = 1000
      |  override def oof = 999
      |  override def rab = 999
      |}
      |}
    """.stripMargin
    assertMatches(messages(code)) {
      case List(Error("foo", "method foo needs to be a stable, immutable value"),
                Error("bar", "method bar cannot override a mutable variable"),
                Error("oof", "method oof needs to be a stable, immutable value"),
                Error("rab", "method rab cannot override a mutable variable")) =>
    }
  }

  def messages(code: String): List[Message] = {
    val annotator = new OverridingAnnotator() {}
    val mock = new AnnotatorHolderMock

    val element: PsiElement = (Header + code).parse

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitFunction(fun: ScFunction): Unit = {
        if (fun.getParent.isInstanceOf[ScTemplateBody]) {
          annotator.checkOverrideMethods(fun, mock, isInSources = false)
        }
        super.visitFunction(fun)
      }

      override def visitTypeDefinition(typedef: ScTypeDefinition): Unit = {
        if (typedef.getParent.isInstanceOf[ScTemplateBody]) {
          annotator.checkOverrideTypes(typedef, mock)
        }
        super.visitTypeDefinition(typedef)
      }

      override def visitTypeAlias(alias: ScTypeAlias): Unit = {
        if (alias.getParent.isInstanceOf[ScTemplateBody]) {
          annotator.checkOverrideTypes(alias, mock)
        }
        super.visitTypeAlias(alias)
      }

      override def visitVariable(varr: ScVariable): Unit = {
        if (varr.getParent.isInstanceOf[ScTemplateBody] ||
          varr.getParent.isInstanceOf[ScEarlyDefinitions]) {
          annotator.checkOverrideVars(varr, mock, isInSources = false)
        }
        super.visitVariable(varr)
      }

      override def visitValue(v: ScValue): Unit = {
        if (v.getParent.isInstanceOf[ScTemplateBody] ||
          v.getParent.isInstanceOf[ScEarlyDefinitions]) {
          annotator.checkOverrideVals(v, mock, isInSources = false)
        }
        super.visitValue(v)
      }

      override def visitClassParameter(parameter: ScClassParameter): Unit = {
        annotator.checkOverrideClassParameters(parameter, mock)
        super.visitClassParameter(parameter)
      }
    }

    element.accept(visitor)

    mock.annotations
  }
}
