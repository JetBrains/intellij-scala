package org.jetbrains.plugins.scala.codeInspection.feature

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.util.RevertableChange

final class AllInOneLanguageFeatureInspectionTest extends LanguageFeatureInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12

  override protected def description: String = "Advanced language feature: ..." // this is only used in assertion errors

  override protected def descriptionMatches(s: String): Boolean = s.startsWith("Advanced language feature:")

  private val AllInOneCode =
    s"""object Main {
      |  object PostfixNotation {
      |    import scala.concurrent.duration.DurationInt
      |    4 ${START}seconds$END
      |  }
      |
      |  object ImplicitConversion {
      |     ${START}implicit$END def i2s(i: Int): String = i.toString
      |  }
      |
      |  def reflectiveCall(duck: { def quack(value: String): String; def walk(): String }): Unit = {
      |    println(duck.${START}quack$END("Quack"))
      |  }
      |sup
      |  def higherKind[T${START}[_]$END]: String = ???
      |}
      |""".stripMargin

  def testAllInOne_LanguageWildcardCompilerSettingShouldEnableAllLanguageFeatures_SingleDash(): Unit = {
    RevertableChange.withCompilerSettingsModified(getModule, s => s.copy(additionalCompilerOptions = s.additionalCompilerOptions :+ "-language:_")) {
      checkTextHasNoErrors(AllInOneCode)
    }
  }

  def testAllInOne_LanguageWildcardCompilerSettingShouldEnableAllLanguageFeatures_DoubleDash(): Unit = {
    RevertableChange.withCompilerSettingsModified(getModule, s => s.copy(additionalCompilerOptions = s.additionalCompilerOptions :+ "--language:_")) {
      checkTextHasNoErrors(AllInOneCode)
    }
  }

  def testAllInOne(): Unit = {
    checkTextHasError(AllInOneCode)
  }
}
