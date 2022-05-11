package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.project._

class PartialUnificationImplicitClassTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override def setUp(): Unit = {
    super.setUp()
    val profile = getModule.scalaCompilerSettingsProfile
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = Seq("-Ypartial-unification")
    )
    profile.setSettings(newSettings)
  }

  def testSCL14548(): Unit = doResolveTest(
    s"""
       |implicit class FooOps[F[_], A](self: F[A]) {
       |  def f${REFTGT}oo: Int = 0
       |}
       |
       |(null: Either[String, Int]).fo${REFSRC}o
     """.stripMargin
  )
}
