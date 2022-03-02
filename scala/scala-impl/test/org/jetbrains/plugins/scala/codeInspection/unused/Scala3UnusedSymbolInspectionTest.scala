package org.jetbrains.plugins.scala.codeInspection.unused
import org.jetbrains.plugins.scala.ScalaVersion

/**
  * Created by Svyatoslav Ilinskiy on 11.07.16.
  */
class Scala3UnusedSymbolInspectionTest extends ScalaUnusedSymbolInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

// Should be enabled when implementing Scala 3 unused symbol inspection and enabling
// it in ScalaUnusedSymbolInspect#shouldProcessElement
//  def testPrivateField(): Unit = {
//    val code =
//      s"""
//         |extension (s: Int)
//         |  private def ${START}blub$END: Int = 3
//      """.stripMargin
//    checkTextHasError(code)
//    val before =
//      """
//        |object Test {
//        |  extension (s: Int)
//        |    private def blub: int = 3
//        |}
//      """.stripMargin
//    val after =
//      """
//        |object Test {
//        |  extension (s: Int)
//        |}
//      """.stripMargin
//    testQuickFix(before, after, hint)
//  }

  def testExtension(): Unit = checkTextHasNoErrors(
    s"""extension (s: Int)
       |  def blub: Int = s + 3
       |6.blub
       |""".stripMargin
  )

  def testThatShouldFailToPreventAutoMerge(): Unit = assert(false)
}

