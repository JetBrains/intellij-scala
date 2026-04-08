package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.Message.Error
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

class Scala3NamedTypeArgumentsCompilerFlagAnnotatorTest extends ScalaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val settings = defaultProfile.getSettings
    defaultProfile.setSettings(
      settings.copy(additionalCompilerOptions = settings.additionalCompilerOptions :+ "-language:experimental.namedTypeArguments")
    )
  }

  def testMethodCallWithCompilerFlagAndWithoutFeatureImport(): Unit = assertNoErrors(
    """
      |def construct[Elem, Coll[_]](xs: Elem*): Coll[Elem] = ???
      |
      |val xs = construct[Coll = List, Elem = Int](1, 2, 3)
      |""".stripMargin
  )

  def testTypeConstructorNamedTypeArgsAreForbiddenEvenWithCompilerFlag(): Unit = assertErrors(
    """
      |class C[T]
      |type X = C[T = Int]
      |""".stripMargin,
    Error("T", "Named type arguments are not allowed for type constructors")
  )
}
