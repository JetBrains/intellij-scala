package org.jetbrains.plugins.scala
package lang
package transformation
package implicits

class InscribeImplicitParametersTest extends TransformerTest(new InscribeImplicitParameters()) {

  override protected val header: String =
    "implicit val v: A = _"

  def testReferenceExpression(): Unit = check(
    before = "f",
    after = "f(v)"
  )(header = "def f(implicit p: A): Unit = {}")

  def testMethodCall(): Unit = check(
    before = "f(A)",
    after = "f(A)(v)"
  )(header = "def f(p1: A)(implicit p2: A): Unit = {}")

  def testInfixExpression(): Unit = check(
    before = "O f A",
    after = "(O f A)(v)"
  )(header =
    """
     object O {
       def f(p1: A)(implicit p2: A): Unit = {}
     }
    """)

  def testPostfixExpression(): Unit = check(
    before = "O f",
    after = "(O f)(v)"
  )(header =
    """
     object O {
       def f(implicit p: A): Unit = {}
     }
    """)

  def testNotImported(): Unit = check(
    before = "f",
    after = "f(O.v)"
  )(header =
    """
       class O
       object O {
         implicit val v: O = _
       }
       def f(implicit p: O): Unit = {}
    """)

  def testIndirection(): Unit = check(
    before = "f",
    after = "f(O.v)"
  )(header =
    """
      class T {
        implicit val v: O = _
      }
      class O
      object O extends T
      def f(implicit p: O): Unit = {}
    """)

  // TODO inscribe something like "implicitly[ClassTag[T]]"?
  def testReflect(): Unit = check(
    before = "O f",
    after = "O f"
  )(header =
    """
     object O {
       def f(implicit p: scala.reflect.ClassTag[A]): Unit = {}
     }
    """)

  // TODO inscibe type arguments
  //  def testTypeArguments(): Unit = check(
  //    before = "Seq(1).map(_ + 1)",
  //    after = "Seq(1).map(_ + 1)(Seq.canBuildFrom[Int])"
  //  )()

  // TODO test optional qualifier
  // TODO test multiple parameters
  // TODO replace "implicitly[]" completely?
  // TODO expand all implicit parameters at once, as the iterative expansion might produce different results
}
