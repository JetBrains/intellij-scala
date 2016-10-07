package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScParameterOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

/**
 * Pavel.Fatin, 18.05.2010
 */

class ParametersAnnotatorTest extends SimpleTestCase {
  final val Header = "class A; class B; class C;\n"
  
  def testFine(): Unit = {
    assertMatches(messages("def f(a: A) {}")) {
      case Nil =>
    }
    assertMatches(messages("def f(a: A*) {}")) {
      case Nil =>
    }
    assertMatches(messages("def f(a: A, b: B) {}")) {
      case Nil =>
    }
    assertMatches(messages("def f(a: A, b: B*) {}")) {
      case Nil =>
    }
    assertMatches(messages("def f(a: A, b: B, c: C*) {}")) {
      case Nil =>
    }
  }
  
  def testMalformed(): Unit = {
    assertMatches(messages("def f(a: A*, b: B) {}")) {
      case Error("a: A*", "*-parameter must come last") :: Nil =>
    }
    assertMatches(messages("def f(a: A, b: B*, c: C) {}")) {
      case Error("b: B*", "*-parameter must come last") :: Nil =>
    }
    assertMatches(messages("def f(a: A*, b: B*) {}")) {
      case Error("a: A*", "*-parameter must come last") :: Nil =>
    }
    assertMatches(messages("def f(a: A*, b: B*, c: C) {}")) {
      case Error("a: A*", "*-parameter must come last") :: 
              Error("b: B*", "*-parameter must come last") :: Nil =>
    }
    assertMatches(messages("def f(a: A*, c: C)(b: B*, c: C) {}")) {
      case Error("a: A*", "*-parameter must come last") :: 
              Error("b: B*", "*-parameter must come last") :: Nil =>
    }
  }

  def testRepeatedWithDefault(): Unit = {
    assertMatches(messages("def f(i: Int, js: Int* = 1) {}")) {
      case Error("(i: Int, js: Int* = 1)", "Parameter section with *-parameter cannot have default arguments") :: Nil =>
    }
  }

  def testByName(): Unit = {
    assertMatches(messages("def f(a: A)(implicit b: => B) {}")) {
      case Error("b: => B", "implicit parameters may not be call-by-name") :: Nil =>
    }
    assertMatches(messages("case class D(a: A, b: => B)")) {
      case Error("b: => B", "case class parameters may not be call-by-name") :: Nil =>
    }
    assertMatches(messages("class D(a: A, val b: => B)")) {
      case Error("val b: => B", "'val' parameters may not be call-by-name") :: Nil =>
    }
    assertMatches(messages("class D(a: A, var b: => B)")) {
      case Error("var b: => B", "'var' parameters may not be call-by-name") :: Nil =>
    }
  }

  def testMissingTypeAnnotation(): Unit = {
    assertMatches(messages("def test(p1: String, p2 = \"default\") = p1 concat p2")) { //SCL-3799
      case Error("p2 = \"default\"", "Missing type annotation for parameter: p2") :: Nil =>
    }
  }
   
  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val annotator = new ParametersAnnotator() {}
    val file = (Header + code).parse

    val mock = new AnnotatorHolderMock(file)
    val owner = file.depthFirst.filterByType(classOf[ScParameterOwner]).collectFirst {
      case named: ScNamedElement if !Set("A", "B", "C").contains(named.name) => named
    }.get

    annotator.annotateParameters(owner.clauses.get, mock)
    for (p <- owner.parameters) {
      annotator.annotateParameter(p, mock)
    }
    mock.annotations
  }
}