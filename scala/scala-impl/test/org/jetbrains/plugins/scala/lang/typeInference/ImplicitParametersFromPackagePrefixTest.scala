package org.jetbrains.plugins.scala.lang.typeInference
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
trait ImplicitParametersFromPackagePrefixTest extends ImplicitParametersTestBase {
  def testPackagePrefix(): Unit = {
    configureFromFileText(
      "c.scala",
      """
        |package p.t
        |
        |object o {
        |  implicit val b: Ordering[C] = ???
        |  type C
        |}
        |""".stripMargin
    )

    configureFromFileText(
      "package.scala",
      """
        |package p
        |package object t {
        |  implicit val a: Ordering[o.C] = ???
        |}
        |""".stripMargin
    )

    checkNoImplicitParameterProblems(
      s"""
         |import p.t.o.C
         |
         |object A {
         |  ${START}implicitly[Ordering[C]]$END
         |}
         |""".stripMargin
    )
  }
}

class ImplicitParametersFromPackagePrefixTestScala3Test extends ImplicitParametersFromPackagePrefixTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_LTS
}

class ImplicitParametersFromPackagePrefixTestScala2Test extends ImplicitParametersFromPackagePrefixTest {
  override protected def supportedIn(version: ScalaVersion) = version <= LatestScalaVersions.Scala_2_13
  override protected def shouldPass: Boolean = false
}

class ImplicitParametersFromPackagePrefixTestXSourceTest extends ImplicitParametersFromPackagePrefixTest {
  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2_13

  override def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      additionalCompilerOptions = Seq("-Xsource:3-cross")
    )
    defaultProfile.setSettings(newSettings)
  }
}

class ImplicitParametersFromPackagePrefixTestXSourceFeaturesTest extends ImplicitParametersFromPackagePrefixTest {
  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2_13

  override def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      additionalCompilerOptions = Seq("-Xsource-features:implicit-resolution")
    )
    defaultProfile.setSettings(newSettings)
  }
}