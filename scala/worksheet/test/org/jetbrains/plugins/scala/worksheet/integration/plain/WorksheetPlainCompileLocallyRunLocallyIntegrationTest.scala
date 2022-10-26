package org.jetbrains.plugins.scala.worksheet.integration.plain

import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import org.jetbrains.plugins.scala.LatestScalaVersions
import org.jetbrains.plugins.scala.worksheet.integration.{WorksheetIntegrationBaseTest, WorksheetRunTestSettings}
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType

class WorksheetPlainCompileLocallyRunLocallyIntegrationTest
  extends WorksheetIntegrationBaseTest
    with WorksheetRunTestSettings
    with WorksheetPlainCheckRuntimeVersionScalaTests {

  override def runType: WorksheetExternalRunType = WorksheetExternalRunType.PlainRunType

  override def useCompileServer = false

  // the value doesn't actually matter, cause compile server isn't used anyway
  override def runInCompileServerProcess = false

  // 1 test should be enough, not-using compile server os something legacy and in future we will probably
  // leave only one option with using compile server
  def testHealthCheck(): Unit = {
    doRenderTest(
      """import java.io.PrintStream
        |import scala.concurrent.duration._;
        |import scala.collection.Seq;
        |
        |println(Seq(1, 2, 3))
        |println(1)
        |
        |()
        |23
        |"str"
        |
        |def foo = "123" + 1
        |def foo0 = 1
        |def foo1() = 1
        |def foo2: Int = 1
        |def foo3(): Int = 1
        |def foo4(p: String) = 1
        |def foo5(p: String): Int = 1
        |def foo6(p: String, q: Short): Int = 1
        |def foo7[T] = 1
        |def foo8[T]() = 1
        |def foo9[T]: Int = 1
        |def foo10[T](): Int = 1
        |def foo11[T](p: String) = 1
        |def foo12[T](p: String): Int = 1
        |def foo13[T](p: String, q: Short): Int = 1
        |
        |val _ = 1
        |val x = 2
        |val y = x.toString + foo
        |val x2: PrintStream = null
        |val q1 = new DurationInt(3)
        |var q2 = new DurationInt(4)
        |
        |def f = 11
        |var v1 = 6
        |var v2 = v1 + f
        |v2 = v1
        |
        |class A
        |trait B
        |object B""".stripMargin,
      s"""import java.io.PrintStream
        |import scala.concurrent.duration._
        |import scala.collection.Seq
        |
        |${foldStart}List(1, 2, 3)
        |res0: Unit = ()$foldEnd
        |${foldStart}1
        |res1: Unit = ()$foldEnd
        |
        |res2: Unit = ()
        |res3: Int = 23
        |res4: String = str
        |
        |foo: foo[] => String
        |foo0: foo0[] => Int
        |foo1: foo1[]() => Int
        |foo2: foo2[] => Int
        |foo3: foo3[]() => Int
        |foo4: foo4[](val p: String) => Int
        |foo5: foo5[](val p: String) => Int
        |foo6: foo6[](val p: String,val q: Short) => Int
        |foo7: foo7[T] => Int
        |foo8: foo8[T]() => Int
        |foo9: foo9[T] => Int
        |foo10: foo10[T]() => Int
        |foo11: foo11[T](val p: String) => Int
        |foo12: foo12[T](val p: String) => Int
        |foo13: foo13[T](val p: String,val q: Short) => Int
        |
        |
        |x: Int = 2
        |y: String = 21231
        |x2: java.io.PrintStream = null
        |q1: concurrent.duration.DurationInt = scala.concurrent.duration.package$$DurationInt@3
        |q2: concurrent.duration.DurationInt = scala.concurrent.duration.package$$DurationInt@4
        |
        |f: f[] => Int
        |v1: Int = 6
        |v2: Int = 17
        |v2: Int = 6
        |
        |defined class A
        |defined trait B
        |defined object B""".stripMargin,
      isCompilerMessageAllowed = { (msg: CompilerMessage) =>
        version == LatestScalaVersions.Scala_2_13 &&
          msg.getCategory == CompilerMessageCategory.WARNING &&
          msg.getMessage.startsWith("Pattern definition introduces Unit-valued")
      }
    )
  }
}
