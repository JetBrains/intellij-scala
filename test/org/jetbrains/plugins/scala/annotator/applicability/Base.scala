package org.jetbrains.plugins.scala
package annotator.applicability

import org.jetbrains.plugins.scala.base.SimpleTestCase
import lang.psi.api.base.ScReferenceElement
import lang.psi.types._
import com.intellij.psi.PsiElement
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

  //  override def setUp {
  //    super.setUp()
  //    Compatibility.mockSeqClass("trait Seq[+A]".parse(classOf[ScTrait]))
  //  }

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


  def problems(code: String): Seq[ApplicabilityProblem] = {
    for (ref <- (Header + code).parse.depthFirst.filterByType(classOf[ScReferenceElement]).toSeq;
         result <- ref.advancedResolve.iterator.toSeq;
         problem <- result.problems)
    yield problem
  }

  object Elements {
    def unapplySeq(seq: Seq[PsiElement]) = Some(seq.map(_.getText))
  }
  
  object Element {
    def unapply(e: PsiElement) = Some(e.getText)
  }
    
  object Type {
    def unapply(t: ScType) = Some(t.presentableText)
  }
}