package org.jetbrains.plugins.scala
package annotator.applicability

import org.jetbrains.plugins.scala.base.SimpleTestCase
import lang.psi.api.base.ScReferenceElement
import lang.psi.types._
import lang.psi.api.statements.params.ScParameter
import lang.psi.api.toplevel.ScNamedElement
import nonvalue.Parameter
import lang.psi.api.expr.{ScAssignStmt, ScExpression}
import junit.framework.Assert
import lang.psi.api.ScalaFile
import com.intellij.psi.{PsiClass, PsiNameIdentifierOwner, PsiElement}
import lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}

/**
 * Pavel.Fatin, 18.05.2010
 */

abstract class Applicability extends SimpleTestCase {
  private val Header = """
  class Seq[+A] 
  object Seq { def apply[A](a: A) = new Seq[A] } 
  trait L; 
  trait A; trait B; trait C; 
  object A extends L with A
  object B extends L with B
  object C extends L with C
  """

//  override def setUp {
//    super.setUp()
//    Compatibility.mockSeqClass("trait Seq[+A]".parse(classOf[ScTrait]))
//  }
  
  // _* for repeated, error - _* is not last
  
  // calls with no braces
  // parametrized
  // synthetic
  // unresolved args
  // constructors, java methods
  // partially applied
  
  // implicit conversions of partially applied to function value
  
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

  protected def format(definition: String, application: String): String

  private def problemsIn(file: ScalaFile): List[ApplicabilityProblem] = {
    for (ref <- file.depthFirst.filterByType(classOf[ScReferenceElement]).toList;
         result <- ref.advancedResolve.toList;
         problem <- result.problems)
    yield problem
  }
  
  def assertProblems(definition: String, application: String)(pattern: PartialFunction[List[ApplicabilityProblem], Unit]) {
    val code = format(definition, application)
    val file = (Header + code).parse
    val seq = file.depthFirst.findByType(classOf[ScClass])
    Compatibility.mockSeqClass(seq.get)
    val ps = problemsIn(file)
    val message = "\n\n             code: " + code + "\n  actual problems: " + ps.toString + "\n"
    Assert.assertTrue(message, pattern.isDefinedAt(ps))
  }
  
  object Expression {
    def unapply(e: ScExpression) = e.toOption.map(_.getText)
  }
  
  object Named {
    def unapply(e: Parameter) = e.toOption.map(_.name)
  }

  object Assignment {
    def unapply(e: ScAssignStmt) = e.toOption.map(_.getText)
  }
    
  object Type {
    def unapply(t: ScType) = t.toOption.map(_.presentableText)
  }
}