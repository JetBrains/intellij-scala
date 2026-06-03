package org.jetbrains.plugins.scala.annotator

/**
 * Check customized behavior for Mill build files
 */
class MillHighlightingTests extends ScalaHighlightingTestBase {
  //SCL-23198
  val code = """package foo{
               |  object `package`
               |}
               |
               |package bar{
               |  object qux{
               |    val reference = foo
               |  }
               |}
               |""".stripMargin
  def testBuildMillFileAllowsPackageReference(): Unit = {
    scala.Predef.assert(errorsFromScalaCode(code, "build.mill").isEmpty)
  }
  def testPackageFileAllowsPackageReference(): Unit = {
    scala.Predef.assert(errorsFromScalaCode(code, "package.mill").isEmpty)
  }
  def testBuildMillScalaFileAllowsPackageReference(): Unit = {
    scala.Predef.assert(errorsFromScalaCode(code, "build.mill.scala").isEmpty)
  }
  def testNormalScalaFileDisAllowsPackageReference(): Unit = {
    scala.Predef.assert(errorsFromScalaCode(code, "build.scala").nonEmpty)
  }
}
