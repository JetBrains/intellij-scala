package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class YimportsResolveTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_2_13

  private[this] def setUpDefaultImports(imports: String*): Unit =
    setCompilerOptions(s"-Yimports:${imports.mkString(",")}")

  private[this] def setCompilerOptions(options: String*): Unit = {
    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings    = defaultProfile.getSettings.copy(
      additionalCompilerOptions = options
    )
    defaultProfile.setSettings(newSettings)
  }

  def testResolvePrimitiveTypesPos(): Unit = {
    setUpDefaultImports("java.lang", "scala")

    doResolveTest(s"val a: I${REFSRC}nt = 132")
    doResolveTest(s"val a: D${REFSRC}ouble = 2d")
    doResolveTest(s"""val s: Str${REFSRC}ing = "123" """)
    doResolveTest(s"val n: N${REFSRC}othing = ???")
    doResolveTest(s"val n: A${REFSRC}ny = 1")
  }

  def testResolvePrimitiveTypesNeg(): Unit = {
    setUpDefaultImports("-Yno-imports")

    testNoResolve(s"val a: I${REFSRC}nt = 132" -> "Int.scala")
    testNoResolve(s"val a: D${REFSRC}ouble = 2d" -> "Double.scala")
    testNoResolve(s"""val s: Str${REFSRC}ing = "123" """ -> "String.scala")
    testNoResolve(s"val n: N${REFSRC}othing = ???" -> "Nothing.scala")
    testNoResolve(s"val n: A${REFSRC}ny = 1" -> "Any.scala")
  }

  def testNoPredef(): Unit = {
    setCompilerOptions("-Yno-predef")

    testNoResolve(s"??$REFSRC?" -> "NoPredef1.scala")
    testNoResolve(s"""123 $REFSRC+ "123" """ -> "NoPredef2.scala")
  }

  def testImportPackage(): Unit = {
    setUpDefaultImports("scala.collection")

    doResolveTest(s"val x = mu${REFSRC}table.HashSet.empty[scala.Int]")
    doResolveTest(s"Buil${REFSRC}dFrom.buildFromView")
  }

  def testImportObject(): Unit = {
    setUpDefaultImports("scala.collection.BuildFrom")
    doResolveTest(s"b${REFSRC}uildFromView[scala.Int, java.lang.String]")
  }

  def testPriority(): Unit = {
    setUpDefaultImports("java.lang", "scala", "scala.Predef", "a.Bar", "b.Baz")

    myFixture.configureByText("Bar.scala",
      """
        |package a
        |object Bar {
        | def x: Int = 123
        | def y: Int = 456
        |
        | def println(x: Any): Unit = ???
        |}
        |""".stripMargin
    )

    myFixture.configureByText("Baz.scala",
      """
        |package b
        |object Baz {
        |  def x: Int = 456
        |
        |  implicit class RichInt(val i: Int) extends AnyVal {
        |    def times(n: Int): Int = i * n
        |  }
        |
        |  def times(n: Int, k: Int): Int = n * k
        |}
        |""".stripMargin
    )

    doResolveTest(s"""println(${REFSRC}x)""" -> "FromB.scala")
    doResolveTest(s"123.t${REFSRC}imes(10)" -> "Times.scala")
    doResolveTest(s"""p${REFSRC}rintln(123)""" -> "FromA.scala")
  }
}
