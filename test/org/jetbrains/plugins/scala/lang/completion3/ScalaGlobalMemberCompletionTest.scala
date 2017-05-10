package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.junit.Assert

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaGlobalMemberCompletionTest extends ScalaCodeInsightTestBase {
  protected override def rootPath(): String = baseRootPath() + "completion3/globalMember"

  def testGlobalMember1() {
    val fileText =
      """
        |class TUI {
        |  rawObj<caret>
        |}
      """

    val resultText =
      """
        |import rawObject.RawObject1
        |
        |class TUI {
        |  RawObject1.rawObject()
        |}
      """

    doCompletionTest(fileText, resultText, "rawObject", time = 2)
  }

  def testGlobalMember2() {
    val fileText =
      """
        |class TUI {
        |  globalVal<caret>
        |}
      """

    val resultText =
      """
        |import rawObject.RawObject2
        |
        |class TUI {
        |  RawObject2.globalValue
        |}
      """

    doCompletionTest(fileText, resultText, "globalValue", time = 2)
  }

  def testGlobalMember3() {
    val fileText =
      """
        |class TUI {
        |  globalVar<caret>
        |}
      """

    val resultText =
      """
        |import rawObject.RawObject3
        |
        |class TUI {
        |  RawObject3.globalVariable
        |}
      """

    doCompletionTest(fileText, resultText, "globalVariable", time = 2)
  }

  def testGlobalMember4() {
    val fileText =
      """
        |class TUI {
        |  patternVal<caret>
        |}
      """

    val resultText =
      """
        |import rawObject.RawObject4
        |
        |class TUI {
        |  RawObject4.patternValue
        |}
      """

    doCompletionTest(fileText, resultText, "patternValue", time = 2)
  }

  def testGlobalMember5() {
    val fileText =
      """
        |class TUI {
        |  patternVar<caret>
        |}
      """

    val resultText =
      """
        |import rawObject.RawObject5
        |
        |class TUI {
        |  RawObject5.patternVariable
        |}
      """

    doCompletionTest(fileText, resultText, "patternVariable", time = 2)
  }

  def testGlobalMember6() {
    val fileText =
      """
        |import rawObject.RawObject6.importedDef
        |
        |class TUI {
        |  importDe<caret>
        |}
      """

    val resultText =
      """
        |import rawObject.RawObject6.importedDef
        |
        |class TUI {
        |  importedDef()
        |}
        |
      """

    doCompletionTest(fileText, resultText, "importedDef", time = 2)
  }

  def testGlobalMember7() {
    val fileText =
"""
class TUI {
  imposToR<caret>
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy7.scala", fileText)
    val lookups = complete(completionType = CompletionType.BASIC, time = 2)
    assert(lookups.isEmpty)

    val resultText =
"""
class TUI {
  imposToR<caret>
}
""".replaceAll("\r", "").trim()

    checkResultByText(resultText)
  }

  def testGlobalMemberJava() {
    val fileText =
      """
        |class TUI {
        |  activeCoun<caret>
        |}
      """

    val resultText =
      """
        |class TUI {
        |  Thread.activeCount()<caret>
        |}
      """

    doCompletionTest(fileText, resultText, "activeCount", time = 2)
  }

  def testGlobalMember8() {
    val fileText =
"""
object BlahBlahBlahContainer {
  private def doSmthPrivate() {}
  def doSmthPublic() {}
}

class Test {
  def test() {
    dsp<caret>
  }
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy7.scala", fileText)
    val lookups = complete(completionType = CompletionType.BASIC, time = 2)
    Assert.assertTrue(!lookups.exists(_.getLookupString == "doSmthPrivate"))
  }

  def testGlobalMember9() {
    val fileText =
      """
      object BlahBlahBlahContainer {
        private def doSmthPrivate() {}
        def doSmthPublic() {}
      }

      class Test {
        def test() {
          dsp<caret>
        }
      }
      """.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy7.scala", fileText)
    val lookups = complete(completionType = CompletionType.BASIC, time = 3)
    Assert.assertTrue(lookups.exists(_.getLookupString == "doSmthPrivate"))
  }

  def testGlobalMemberInherited() {
    val fileText =
      """
      class Base {
        def zeeGlobalDefInherited = 0
        val zeeGlobalValInherited = 0
      }

      object D1 extends Base {
        def zeeGlobalDef = 0
        def zeeGlobalVal = 0
      }

      package object D2 extends Base

      class Test {
        def test() {
          zeeGlobal<caret>
        }
      }
      """.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummyGlobalMemberInherited.scala", fileText)
    val lookups = complete(completionType = CompletionType.BASIC, time = 3).collect {
      case sli: ScalaLookupItem => sli.containingClass.name + "." + sli.getLookupString
    }
    val expected = Set("D1.zeeGlobalDefInherited", "D1.zeeGlobalValInherited", "D1.zeeGlobalDef", "D1.zeeGlobalVal", "D2.zeeGlobalDefInherited", "D2.zeeGlobalValInherited")
    Assert.assertEquals(expected, lookups.toSet)
  }

  def testJavaConverters() {
    val fileText =
      """
        |val ja = new java.util.ArrayList[Int]
        |ja.asSc<caret>
      """.stripMargin.replaceAll("\r", "").trim
    configureFromFileTextAdapter("javaConverters.scala", fileText)
    val lookups = complete(completionType = CompletionType.BASIC, time = 2)

    val resultText =
      """
        |import scala.collection.JavaConverters.asScalaBufferConverter
        |
        |val ja = new java.util.ArrayList[Int]
        |ja.asScala
      """.stripMargin.replaceAll("\r", "").trim

    val result2Text =
      """
        |import scala.collection.JavaConverters.collectionAsScalaIterableConverter
        |
        |val ja = new java.util.ArrayList[Int]
        |ja.asScala
      """.stripMargin.replaceAll("\r", "").trim

    val result3Text =
      """
        |import scala.collection.JavaConverters.iterableAsScalaIterableConverter
        |
        |val ja = new java.util.ArrayList[Int]
        |ja.asScala
      """.stripMargin.replaceAll("\r", "").trim

    lookups.find(le => le.getLookupString == "asScala")
      .foreach(finishLookup(_))
    val resultFileText = getFileAdapter.getText
    resultFileText.trim match {
      case `resultText` =>
      case `result2Text` =>
      case `result3Text` =>
      case _ =>
        Assert.assertEquals(resultFileText, resultText)
    }
  }
}