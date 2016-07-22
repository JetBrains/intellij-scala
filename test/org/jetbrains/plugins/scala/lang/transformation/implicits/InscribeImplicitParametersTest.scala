package org.jetbrains.plugins.scala.lang.transformation.implicits

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class InscribeImplicitParametersTest extends TransformerTest(new InscribeImplicitParameters(),
  "implicit val v: A = _") {

  def testReferenceExpression() = check(
    "def f(implicit p: A) {}",
    "f",
    "f(v)"
  )

  def testMethodCall() = check(
    "def f(p1: A)(implicit p2: A) {}",
    "f(A)",
    "f(A)(v)"
  )

  def testInfixExpression() = check(
    """
     object O {
       def f(p1: A)(implicit p2: A) {}
     }
    """,
    "O f A",
    "(O f A)(v)"
  )

  def testPostfixExpression() = check(
    """
     object O {
       def f(implicit p: A) {}
     }
    """,
    "O f",
    "(O f)(v)"
  )

  def testUnimported() = check(
    """
       class O
       object O {
         implicit val v: O = _
       }
       def f(implicit p: O) {}
    """,
    "f",
    "f(O.v)"
  )

  def testIndirection() = check(
    """
      class T {
        implicit val v: O = _
      }
      class O
      object O extends T
      def f(implicit p: O) {}
    """,
    "f",
    "f(O.v)"
  )

  // TODO inscribe something like "implicitly[ClassTag[T]]"?
  def testReflect() = check(
    """
     object O {
       def f(implicit p: scala.reflect.ClassTag[A]) {}
     }
    """,
    "O f",
    "O f"
  )

// TODO inscibe type arguments
//  def testTypeArguments() = check(
//    "Seq(1).map(_ + 1)",
//    "Seq(1).map(_ + 1)(Seq.canBuildFrom[Int])"
//  )

  // TODO test optional qualifier
  // TODO test multiple parameters
  // TODO replace "implicitly[]" completely?
  // TODO expand all implicit parameters at once, as the iterative expansion might produce different results
}
