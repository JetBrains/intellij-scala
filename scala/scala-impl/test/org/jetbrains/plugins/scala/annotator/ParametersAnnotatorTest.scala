package org.jetbrains.plugins.scala
package annotator

import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.element.{ScParameterAnnotator, ScParametersAnnotator}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}

abstract class ParametersAnnotatorTestBase extends ScalaHighlightingTestBase {
  import Message._

  final val Header = "class A; class B; class C;\n"

  protected def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val result = errorsFromScalaCode(Header + code)
    result
  }

  override def annotate(element: PsiElement)(implicit holder: ScalaAnnotationHolder): Unit =
    element match {
      case parameters: ScParameters => ScParametersAnnotator.annotate(parameters, typeAware = true)
      case param: ScParameter       => ScParameterAnnotator.annotate(param, typeAware = true)
      case _                        =>
    }
}

class ParametersAnnotatorTest extends ParametersAnnotatorTestBase {
  import Message._

  def testFine1(): Unit = assertNothing(messages("def f(a: A) {}"))
  def testFine2(): Unit = assertNothing(messages("def f(a: A*) {}"))
  def testFine3(): Unit = assertNothing(messages("def f(a: A, b: B) {}"))
  def testFine4(): Unit = assertNothing(messages("def f(a: A, b: B*) {}"))
  def testFine5(): Unit = assertNothing(messages("def f(a: A, b: B, c: C*) {}"))

  def testMalformed1(): Unit = {
    assertMatches(messages("def f(a: A*, b: B) {}")) {
      case Error("a: A*", "*-parameter must come last") :: Nil =>
    }
  }

  def testMalformed2(): Unit = {
    assertMatches(messages("def f(a: A, b: B*, c: C) {}")) {
      case Error("b: B*", "*-parameter must come last") :: Nil =>
    }
  }

  def testMalformed3(): Unit = {
    assertMatches(messages("def f(a: A*, b: B*) {}")) {
      case Error("a: A*", "*-parameter must come last") :: Nil =>
    }
  }

  def testMalformed4(): Unit = {
    assertMatches(messages("def f(a: A*, b: B*, c: C) {}")) {
      case Error("a: A*", "*-parameter must come last") ::
        Error("b: B*", "*-parameter must come last") :: Nil =>
    }
  }

  def testMalformed5(): Unit = {
    assertMatches(messages("def f(a: A*, c: C)(b: B*, c: C) {}")) {
      case Error("a: A*", "*-parameter must come last") ::
        Error("b: B*", "*-parameter must come last") :: Nil =>
    }
  }

  def testRepeatedWithDefault(): Unit = {
    assertMatches(messages("def f(i: Int, js: Int* = 1) {}")) {
      case Error("(i: Int, js: Int* = 1)", "Parameter section with *-parameter is not allowed to have default arguments") :: Nil =>
    }
  }

  def testByName_CaseClassParam(): Unit = {
    assertMatches(messages("case class D(a: A, b: => B)")) {
      case Error("b: => B", "case class parameters may not be call-by-name") :: Nil =>
    }
  }

  def testByName_ValParam(): Unit = {
    assertMatches(messages("class D(a: A, val b: => B)")) {
      case Error("val b: => B", "'val' parameters may not be call-by-name") :: Nil =>
    }
  }

  def testByName_VarParam(): Unit = {
    assertMatches(messages("class D(a: A, var b: => B)")) {
      case Error("var b: => B", "'var' parameters may not be call-by-name") :: Nil =>
    }
  }

  def testValInNormalParam(): Unit = {
    assertMatches(messages("def fn(val x: Int) = x")) {
      case Error("val", "'val' can only be used in class parameters") :: Nil =>
    }
  }

  def testVarInNormalParam(): Unit = {
    assertMatches(messages("def fn(var x: Int) = x")) {
      case Error("var", "'var' can only be used in class parameters") :: Nil =>
    }
  }
}

class ParametersAnnotatorTest_without_callByName_implicit_parameter extends ParametersAnnotatorTestBase {
  import Message._

  override protected def supportedIn(version: ScalaVersion): Boolean = version < LatestScalaVersions.Scala_2_13

  def testByName_ImplicitParam(): Unit = {
    assertMatches(messages("def f(a: A)(implicit b: => B) {}")) {
      case Error("b: => B", "implicit parameters may not be call-by-name") :: Nil =>
    }
  }
}


class ParametersAnnotatorTest_with_callByName_implicit_parameter extends ParametersAnnotatorTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_13

  def testByName_ImplicitParam(): Unit = {
    assertNothing(messages("def f(a: A)(implicit b: => B) {}"))
  }
}
