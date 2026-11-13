package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion

/**
 * Members of the operands of intersection types, with the types inferred by Scala 3.7.4 and 3.3.7.
 * In Scala 3, `A with B` is an intersection type as well.
 */
class Scala3IntersectionTypesTest extends TypeInferenceTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  private val definitions =
    """trait Parent[TypeParam] {
      |  def foo: TypeParam = ???
      |  type Member = TypeParam
      |}
      |
      |trait Child[TypeParam] extends Parent[TypeParam]
      |
      |trait ChildRenamed[B] extends Parent[B]
      |
      |trait GrandChild[C] extends ChildRenamed[List[C]]
      |
      |class MyClass
      |""".stripMargin

  private def doTest(intersection: String, selected: String, expected: String): Unit = doTest(
    s"""$definitions
       |object Test {
       |  lazy val e: $intersection = null
       |
       |  $START$selected$END
       |}
       |//$expected
       |""".stripMargin
  )

  def testMemberOfOperand(): Unit = doTest("MyClass with Parent[MyClass]", "e.foo", "MyClass")

  // A member that the class of an operand inherits
  def testSCL23844(): Unit = doTest("MyClass with Child[MyClass]", "e.foo", "MyClass")

  def testInheritedMemberOfOperand(): Unit = doTest("MyClass & Child[MyClass]", "e.foo", "MyClass")

  def testInheritedMemberOfFirstOperand(): Unit = doTest("Child[MyClass] & MyClass", "e.foo", "MyClass")

  def testInheritedMemberWithRenamedTypeParameter(): Unit = doTest("MyClass & ChildRenamed[MyClass]", "e.foo", "MyClass")

  def testMemberInheritedTwoLevelsUp(): Unit = doTest("MyClass & GrandChild[MyClass]", "e.foo", "List[MyClass]")

  def testInheritedTypeMemberOfOperand(): Unit = assertNoErrors(
    s"""$definitions
       |object Test {
       |  lazy val e: MyClass & Child[MyClass] = null
       |  val member: MyClass = (??? : e.Member)
       |}
       |""".stripMargin
  )

  def testInheritedMemberOfSelfType(): Unit = doTest(
    s"""$definitions
       |trait UsesSelfType { self: MyClass & Child[MyClass] =>
       |  def g = ${START}foo$END
       |}
       |//MyClass
       |""".stripMargin
  )
}
