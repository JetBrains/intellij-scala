package org.jetbrains.plugins.scala.lang.typeInference

class ConvertToJavaTypeTest extends TypeInferenceTestBase {
  def testSCL16708(): Unit = doTest(
    s"""
      |trait FBounded[A <: FBounded[A]] {
      |  def self: A
      |}
      |class FBoundedImpl extends FBounded[FBoundedImpl] {
      |  override def self: FBoundedImpl = this
      |}
      |trait Config {
      |  type F <: FBounded[F]
      |  def value: F
      |}
      |object Config {
      |  object FBoundedImplConfig extends Config {
      |    override type F = FBoundedImpl
      |    override def value: FBoundedImpl = new FBoundedImpl
      |  }
      |}
      |object Temp extends App {
      |  val config: Config = Config.FBoundedImplConfig
      |  println { ${START}config.value$END }
      |}
      |//java type: FBounded<Object>
      |""".stripMargin
  )

  def testSCL18628(): Unit = doTest(
    s"""
       |trait A[+T]
       |
       |object Test {
       |  type RecursiveExistential = M forSome {type M <: A[M] {
       |    def simple: String
       |  }}
       |
       |  def foo: RecursiveExistential = ???
       |
       |  ${START}foo$END
       |}
       |//java type: ? extends A<Object>
       |""".stripMargin
  )

}
