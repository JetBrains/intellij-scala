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

abstract class ApplicabilityTestBase extends SimpleTestCase {
  private val Header = """
  class Seq[+A] 
  object Seq { def apply[A](a: A) = new Seq[A] } 
  trait L; 
  trait A; trait B; trait C; 
  object A extends L with A
  object B extends L with B
  object C extends L with C
  """
 
  
  // following applications f()()
  // function value applications
  
  // calls with no braces
  // parametrized (shortage, excess, miss, etc)
  // synthetic
  // unresolved args
  // constructors, java methods
  // partially applied
  
  // implicit conversions of partially applied to function value
  
  // auto-tupling
  
  // named with repeated
  // named with implicits
  // named with defaults
  
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

  def assertProblems(definition: String, application: String)
                    (pattern: PartialFunction[List[ApplicabilityProblem], Unit]) {
    assertProblems("", definition, application)(pattern)
  }
  
  def assertProblems(auxiliary: String, definition: String, application: String)
                    (pattern: PartialFunction[List[ApplicabilityProblem], Unit]) {
    val typified = typify(definition, application)
    assertProblemsAre(auxiliary, formatFunction(definition, application))(pattern)
    assertProblemsAre(auxiliary, formatFunction(typified._1, typified._2))((pattern))
//    assertProblemsAre(auxiliary, formatConstructor(typified._1, typified._2))(pattern)
//    assertProblemsAre(auxiliary, formatFunction(typified._1, typified._2))((pattern))
  }
  
  private def assertProblemsAre(auxiliary: String, code: String)
                    (pattern: PartialFunction[List[ApplicabilityProblem], Unit]) {
    val file = (Header + "\n" + auxiliary + "\n" + code).parse
    val seq = file.depthFirst.findByType(classOf[ScClass])
    Compatibility.mockSeqClass(seq.get)
    val ps = problemsIn(file)
    val message = "\n\n             code: " + code + "\n  actual problems: " + ps.toString + "\n"
    Assert.assertTrue(message, pattern.isDefinedAt(ps))
  }
  
  private def problemsIn(file: ScalaFile): List[ApplicabilityProblem] = {
    for (ref <- file.depthFirst.filterByType(classOf[ScReferenceElement]).toList;
         result <- ref.advancedResolve.toList;
         problem <- result.problems)
    yield problem
  }
  
  private def formatFunction(definition: String, application: String) = 
    "def f" + definition + " {}; " + "f" + application
  
  private def formatConstructor(definition: String, application: String) = 
    "class F" + definition + " {}; " + "new F" + application

  private def typify(definition: String, application: String) = {
    val Parameter = """(\w+):\s*(\w+)""".r
    
    val types = for(Parameter(_, t) <- Parameter.findAllIn(definition).toList) yield t
    val ids = (1 to types.size).map("T" + _)

    val id = ids.toIterator
    val typedDefinition = Parameter.replaceAllIn(definition, _ match { 
      case Parameter(n, t) => n + ": " + id.next    
    })
    
    val typeParameters = "[" + ids.mkString(", ") + "]"
    val typeArguments = "[" + types.mkString(", ") + "]"

    (typeParameters + typedDefinition,  typeArguments + application)
  }
  
  object Expression {
    def unapply(e: ScExpression) = e.toOption.map(_.getText)
  }
  
  object Parameter {
    def unapply(e: Parameter) = e.toOption.map(_.name)
  }

  object Assignment {
    def unapply(e: ScAssignStmt) = e.toOption.map(_.getText)
  }
    
  object Type {
    def unapply(t: ScType) = t.toOption.map(_.presentableText)
  }
}