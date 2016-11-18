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
class TUI {
  rawObj<caret>
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy1.scala", fileText)
    val (activeLookup, _) = complete(completionType = CompletionType.BASIC, time = 2)

    val resultText =
"""
import rawObject.RawObject1

class TUI {
  RawObject1.rawObject()
}
""".replaceAll("\r", "").trim()

    if (activeLookup != null)
      completeLookupItem(activeLookup.find(le => le.getLookupString == "rawObject").get)
    checkResultByText(resultText)
  }

  def testGlobalMember2() {
    val fileText =
"""
class TUI {
  globalVal<caret>
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy2.scala", fileText)
    val (activeLookup, _) = complete(completionType = CompletionType.BASIC, time = 2)

    val resultText =
"""
import rawObject.RawObject2

class TUI {
  RawObject2.globalValue
}
""".replaceAll("\r", "").trim()

    if (activeLookup != null)
      completeLookupItem(activeLookup.find(le => le.getLookupString == "globalValue").get)
    checkResultByText(resultText)
  }

  def testGlobalMember3() {
    val fileText =
"""
class TUI {
  globalVar<caret>
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy3.scala", fileText)
    val (activeLookup, _) = complete(completionType = CompletionType.BASIC, time = 2)

    val resultText =
"""
import rawObject.RawObject3

class TUI {
  RawObject3.globalVariable
}
""".replaceAll("\r", "").trim()

    if (activeLookup != null)
      completeLookupItem(activeLookup.find(le => le.getLookupString == "globalVariable").get)
    checkResultByText(resultText)
  }

  def testGlobalMember4() {
    val fileText =
"""
class TUI {
  patternVal<caret>
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy4.scala", fileText)
    val (activeLookup, _) = complete(completionType = CompletionType.BASIC, time = 2)

    val resultText =
"""
import rawObject.RawObject4

class TUI {
  RawObject4.patternValue
}
""".replaceAll("\r", "").trim()

    if (activeLookup != null)
      completeLookupItem(activeLookup.find(le => le.getLookupString == "patternValue").get)
    checkResultByText(resultText)
  }

  def testGlobalMember5() {
    val fileText =
"""
class TUI {
  patternVar<caret>
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy5.scala", fileText)
    val (activeLookup, _) = complete(completionType = CompletionType.BASIC, time = 2)

    val resultText =
"""
import rawObject.RawObject5

class TUI {
  RawObject5.patternVariable
}
""".replaceAll("\r", "").trim()

    if (activeLookup != null)
      completeLookupItem(activeLookup.find(le => le.getLookupString == "patternVariable").get)
    checkResultByText(resultText)
  }

  def testGlobalMember6() {
    val fileText =
"""
import rawObject.RawObject6.importedDef

class TUI {
  importDe<caret>
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy6.scala", fileText)
    val (activeLookup, _) = complete(completionType = CompletionType.BASIC, time = 2)

    val resultText =
"""
import rawObject.RawObject6.importedDef

class TUI {
  importedDef()
}
""".replaceAll("\r", "").trim()

    if (activeLookup != null)
      completeLookupItem(activeLookup.find(le => le.getLookupString == "importedDef").get)
    checkResultByText(resultText)
  }

  def testGlobalMember7() {
    val fileText =
"""
class TUI {
  imposToR<caret>
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy7.scala", fileText)
    val (activeLookup, _) = complete(completionType = CompletionType.BASIC, time = 2)
    assert(activeLookup == null)

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
class TUI {
  activeCoun<caret>
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy7.scala", fileText)
    val (activeLookup, _) = complete(completionType = CompletionType.BASIC, time = 2)

    val resultText =
"""
class TUI {
  Thread.activeCount()<caret>
}
""".replaceAll("\r", "").trim()

    if (activeLookup != null)
      completeLookupItem(activeLookup.find(le => le.getLookupString == "activeCount").get)

    checkResultByText(resultText)
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
    val (activeLookup, _) = complete(completionType = CompletionType.BASIC, time = 2)
    Assert.assertTrue(!activeLookup.exists(_.getLookupString == "doSmthPrivate"))
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
    val (activeLookup, _) = complete(completionType = CompletionType.BASIC, time = 3)
    Assert.assertTrue(activeLookup.exists(_.getLookupString == "doSmthPrivate"))
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
    val (activeLookup, _) = complete(completionType = CompletionType.BASIC, time = 3)
    val lookups = activeLookup.collect {
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
      """.stripMargin.trim
    configureFromFileTextAdapter("javaConverters.scala", fileText)
    val (activeLookup, _) = complete(completionType = CompletionType.BASIC, time = 2)

    val resultText =
      """
        |import scala.collection.JavaConverters.asScalaBufferConverter
        |
        |val ja = new java.util.ArrayList[Int]
        |ja.asScala
      """.stripMargin.trim

    val result2Text =
      """
        |import scala.collection.JavaConverters.collectionAsScalaIterableConverter
        |
        |val ja = new java.util.ArrayList[Int]
        |ja.asScala
      """.stripMargin.trim

    val result3Text =
      """
        |import scala.collection.JavaConverters.iterableAsScalaIterableConverter
        |
        |val ja = new java.util.ArrayList[Int]
        |ja.asScala
      """.stripMargin.trim

    if (activeLookup != null)
      completeLookupItem(activeLookup.find(le => le.getLookupString == "asScala").get)
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