package org.jetbrains.plugins.scala
package annotator.applicability

import org.jetbrains.plugins.scala.base.SimpleTestCase
import lang.psi.api.base.ScReferenceElement
import lang.psi.types._
import lang.psi.api.statements.params.ScParameter
import lang.psi.api.toplevel.ScNamedElement
import com.intellij.psi.{PsiNameIdentifierOwner, PsiElement}
import lang.psi.api.expr.ScExpression
import nonvalue.Parameter

/**
 * Pavel.Fatin, 18.05.2010
 */

abstract class Base extends SimpleTestCase {
  val Header = """
  trait L; trait A; trait B; trait C; 
  object A extends L with A
  object B extends L with B
  object C extends L with C
  """

  // parametrized
  // synthetic
  // unresolved args
  // constructors, java methods
  // partially applied
  // _*
  // duplicates, most specific
  // highlight malformed definition itself
  // setters like foo_=
  //  def f(implicit p: Int, a: Int) {}
  //  def f(p: Int*, a: Int) {}

  // no args for method with def or impl args: def f(); f 
  // * must be last
  // positional then by name
  // by name duplicates  

  // return signature
  // multiple *, expanding
  // type parameters
  // too many args
  // not enough arguments
  // type mismatch
  // default
  // named
  // implicits
  // nfix
  // constructor 
  // inside block expression
  // java interop
  // syntetic methods (apply, unapply)
  // braces
  
  // complex (missed + mismatches, etc)


  def problems(code: String): List[ApplicabilityProblem] = {
    for (ref <- (Header + code).parse.depthFirst.filterByType(classOf[ScReferenceElement]).toList;
         result <- ref.advancedResolve.toList;
         problem <- result.problems)
    yield problem
  }
  
  object Expression {
    def unapply(e: ScExpression) = e.toOption.map(_.getText)
  }
  
  object Named {
    def unapply(e: Parameter) = e.toOption.map(_.name)
  }
    
  object Type {
    def unapply(t: ScType) = t.toOption.map(_.presentableText)
  }
}