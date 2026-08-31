package org.jetbrains.plugins.scala.lang.resolve

/**
 * The self invocation of a secondary constructor is typechecked in the scope in effect at the point
 * of the enclosing class definition, augmented by the type parameters of that class (SLS 5.3.1), so
 * neither the members nor the constructor parameters of the class are in scope in it, even though
 * they are in scope everywhere else in its body.
 */
class SelfInvocationScopeResolveTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  def testConstructorParameterIsNotInScope(): Unit = testNoResolve(
    s"""
       |class A(i: Int) {
       |  def this(b: Boolean) = this(${REFSRC}i)
       |}
       |""".stripMargin
  )

  def testMemberIsNotInScope(): Unit = testNoResolve(
    s"""
       |class A(i: Int) {
       |  val j: Int = 1
       |  def this(b: Boolean) = this(${REFSRC}j)
       |}
       |""".stripMargin
  )

  def testInheritedMemberIsNotInScope(): Unit = testNoResolve(
    s"""
       |class Base { val j: Int = 1 }
       |
       |class A(i: Int) extends Base {
       |  def this(b: Boolean) = this(${REFSRC}j)
       |}
       |""".stripMargin
  )

  def testTypeParameterIsInScope(): Unit = doResolveTest(
    s"""
       |class A[${REFTGT}T](t: T) {
       |  def this(a: Any) = this(a.asInstanceOf[${REFSRC}T])
       |}
       |""".stripMargin
  )

  def testParameterOfTheConstructorIsInScope(): Unit = doResolveTest(
    s"""
       |class A(i: Int) {
       |  def this(${REFTGT}j: Int, k: Int) = this(${REFSRC}j)
       |}
       |""".stripMargin
  )

  def testDefinitionEnclosingTheClassIsInScope(): Unit = doResolveTest(
    s"""
       |object O {
       |  val ${REFTGT}outer: Int = 1
       |
       |  class A(i: Int) {
       |    def this(b: Boolean) = this(${REFSRC}outer)
       |  }
       |}
       |""".stripMargin
  )

  /**
   * The scope of the body of the enclosing class is in effect at the point a nested class is defined,
   * so the self invocation of the nested class does see the members of the enclosing one.
   */
  def testMemberOfTheEnclosingClassIsInScope(): Unit = doResolveTest(
    s"""
       |class Outer {
       |  val ${REFTGT}j: Int = 1
       |
       |  class Inner(i: Int) {
       |    def this(b: Boolean) = this(${REFSRC}j)
       |  }
       |}
       |""".stripMargin
  )

  /** Only the self invocation itself is restricted, the rest of the constructor is not. */
  def testMemberIsInScopeAfterTheSelfInvocation(): Unit = doResolveTest(
    s"""
       |class A(i: Int) {
       |  val ${REFTGT}j: Int = 1
       |
       |  def this(b: Boolean) = {
       |    this(1)
       |    println(${REFSRC}j)
       |  }
       |}
       |""".stripMargin
  )
}
