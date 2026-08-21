package org.jetbrains.plugins.scala.lang.typeInference
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

/** Verifies package-prefix implicit scope modes relevant to [[https://youtrack.jetbrains.com/issue/SCL-25850 SCL-25850]]. */
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

trait ImplicitParametersFromPackagePrefixTestWithCompilerOptions extends ImplicitParametersFromPackagePrefixTest {
  protected def compilerOptions: Seq[String]

  override def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      additionalCompilerOptions = compilerOptions
    )
    defaultProfile.setSettings(newSettings)
  }
}

class ImplicitParametersFromPackagePrefixTestScala3Test extends ImplicitParametersFromPackagePrefixTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_LTS
}

class ImplicitParametersFromPackagePrefixTestScala2Test extends ImplicitParametersFromPackagePrefixTest {
  override protected def supportedIn(version: ScalaVersion) = version <= LatestScalaVersions.Scala_2_13
  override protected def shouldPass: Boolean = false
}

/** [[https://youtrack.jetbrains.com/issue/SCL-25850 SCL-25850]]: `-Xsource:3-cross` enables package-prefix-implicits. */
class ImplicitParametersFromPackagePrefixTestXSourceTest extends ImplicitParametersFromPackagePrefixTestWithCompilerOptions {
  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2_13
  override protected val compilerOptions: Seq[String] = Seq("-Xsource:3-cross")
}

/** [[https://youtrack.jetbrains.com/issue/SCL-25850 SCL-25850]]: plain `-Xsource:3` retains package-prefix implicits. */
class ImplicitParametersFromPackagePrefixTestXSource3Test extends ImplicitParametersFromPackagePrefixTestWithCompilerOptions {
  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2_13
  override protected def shouldPass: Boolean = false
  override protected val compilerOptions: Seq[String] = Seq("-Xsource:3")
}

/** [[https://youtrack.jetbrains.com/issue/SCL-25850 SCL-25850]]: the isolated feature excludes package-prefix implicits. */
class ImplicitParametersFromPackagePrefixTestPackagePrefixImplicitsTest extends ImplicitParametersFromPackagePrefixTestWithCompilerOptions {
  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2_13
  override protected val compilerOptions: Seq[String] = Seq("-Xsource-features:package-prefix-implicits")
}

/** [[https://youtrack.jetbrains.com/issue/SCL-25850 SCL-25850]]: `implicit-resolution` alone does not alter package-prefix scope. */
class ImplicitParametersFromPackagePrefixTestXSourceFeaturesTest extends ImplicitParametersFromPackagePrefixTestWithCompilerOptions {
  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2_13
  override protected def shouldPass: Boolean = false
  override protected val compilerOptions: Seq[String] = Seq("-Xsource-features:implicit-resolution")
}

/** [[https://youtrack.jetbrains.com/issue/SCL-25850 SCL-25850]]: Scala 3 migration mode restores package-prefix scope. */
class ImplicitParametersFromPackagePrefixTestScala3MigrationTest extends ImplicitParametersFromPackagePrefixTestWithCompilerOptions {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_LTS
  override protected def shouldPass: Boolean = false
  override protected val compilerOptions: Seq[String] = Seq("-source:3.0-migration")
}
