package org.jetbrains.plugins.scala
package lang.psi.applicability

import org.jetbrains.plugins.scala.base.{SharedTestProjectToken, SimpleTestCase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.junit.Assert

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

  override protected def sharedProjectToken: SharedTestProjectToken =
    SharedTestProjectToken(classOf[ApplicabilityTestBase])
  
  // following applications f()
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
                    (pattern: PartialFunction[List[ApplicabilityProblem], Unit]): Unit = {
    assertProblems("", definition, application)(pattern)
  }

  def assertProblems(auxiliary: String, definition: String, application: String)
                    (pattern: PartialFunction[List[ApplicabilityProblem], Unit]): Unit = {
    assertProblemsFunction(auxiliary, definition, application)(pattern)
    assertProblemsConstructor(auxiliary, definition, application)(pattern)
  }

  def assertProblemsFunction(auxiliary: String, definition: String, application: String)
                            (pattern: PartialFunction[scala.List[ApplicabilityProblem], Unit]): Unit = {
    val typified = typify(definition, application)

    assertProblemsAre(auxiliary, formatFunction(definition, application))(pattern)
    assertProblemsAre(auxiliary, formatFunction(typified._1, typified._2))(pattern)
  }

  def assertProblemsConstructor(auxiliary: String, definition: String, application: String)
                               (pattern: PartialFunction[scala.List[ApplicabilityProblem], Unit]): Unit = {
    val typified = typify(definition, application)
    assertProblemsAre(auxiliary, formatConstructor(definition, application))(pattern)
    // TODO Uncomment and solve problems with primary constructors substitutors
    //    assertProblemsAre(auxiliary, formatConstructor(typified._1, typified._2))((pattern))
  }

  private def assertProblemsAre(preface: String, code: String)
                    (pattern: PartialFunction[List[ApplicabilityProblem], Unit]): Unit = {
    val line = if(preface.isEmpty) code else preface + "; " + code
    val file = (Header + "\n" + line).parse
    Compatibility.seqClass = file.depthFirst().findByType[ScClass]
    try {
      val message = "\n\n             code: " + line +
        "\n  actual problems: " + problemsIn(file).toString + "\n"
      Assert.assertTrue(message, pattern.isDefinedAt(problemsIn(file)))
    }
    finally {
      Compatibility.seqClass = None
    }
  }
  
  private def problemsIn(file: ScalaFile): List[ApplicabilityProblem] = {
    for (ref <- file.depthFirst().filterByType[ScReference].toList;
         result <- ref.bind().toList;
         problem <- result.problems.filter(_ != ExpectedTypeMismatch))
    yield problem
  }
  
  private def formatFunction(definition: String, application: String) = 
    "def f" + definition + " {}; " + "f" + application
  
  private def formatConstructor(definition: String, application: String) = 
    "class F" + definition + " {}; " + "new F" + application

  private def typify(definition: String, application: String) = {
    val Parameter = """(\w+):\s*([A-Za-z\[\]]+)""".r
    
    val types = for(Parameter(_, t) <- Parameter.findAllIn(definition).toList) yield t
    val ids = (1 to types.size).map("T" + _)

    val id = ids.iterator
    val typedDefinition = Parameter.replaceAllIn(definition, _ match {
      case Parameter(n, t) => n + ": " + id.next()
    })
    
    val typeParameters = "[" + ids.mkString(", ") + "]"
    val typeArguments = "[" + types.mkString(", ") + "]"

    (typeParameters + typedDefinition,  typeArguments + application)
  }
  
  object Expression {
    def unapply(e: ScExpression): Option[String] = e.toOption.map(_.getText)
  }
  
  object Parameter {
    def unapply(e: Parameter): Option[String] = e.toOption.map(_.name)
  }

  object Assignment {
    def unapply(e: ScAssignment): Option[String] = e.toOption.map(_.getText)
  }
    
  object Type {
    def unapply(t: ScType): Option[String] = t.toOption.map(_.presentableText(TypePresentationContext.emptyContext))
  }
}