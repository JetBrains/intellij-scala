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
}
