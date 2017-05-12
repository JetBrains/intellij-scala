package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.testFramework.EditorTestUtil.{CARET_TAG => CARET}
import junit.framework.ComparisonFailure
import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, SourcesLoader}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.junit.Assert.{assertArrayEquals, assertTrue}

/**
  * @author Alexander Podkhalyuzin
  */
class ScalaGlobalMemberCompletionTest extends ScalaCodeInsightTestBase {

  import ScalaCodeInsightTestBase._

  override def getTestDataPath: String =
    s"${super.getTestDataPath}globalMember"

  override def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+ SourcesLoader(getTestDataPath)

  def testGlobalMember1(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  rawObj$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |import rawObject.RawObject1
        |
        |class TUI {
        |  RawObject1.rawObject()
        |}
      """.stripMargin,
    item = "rawObject",
    time = 2
  )

  def testGlobalMember2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  globalVal$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |import rawObject.RawObject2
        |
        |class TUI {
        |  RawObject2.globalValue
        |}
      """.stripMargin,
    item = "globalValue",
    time = 2
  )

  def testGlobalMember3(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  globalVar$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |import rawObject.RawObject3
        |
        |class TUI {
        |  RawObject3.globalVariable
        |}
      """.stripMargin,
    item = "globalVariable",
    time = 2
  )

  def testGlobalMember4(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  patternVal$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |import rawObject.RawObject4
        |
        |class TUI {
        |  RawObject4.patternValue
        |}
      """.stripMargin,
    item = "patternValue",
    time = 2
  )

  def testGlobalMember5(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  patternVar$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |import rawObject.RawObject5
        |
        |class TUI {
        |  RawObject5.patternVariable
        |}
      """.stripMargin,
    item = "patternVariable", time = 2
  )

  def testGlobalMember6(): Unit = doCompletionTest(
    fileText =
      s"""
         |import rawObject.RawObject6.importedDef
         |
        |class TUI {
         |  importDe$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |import rawObject.RawObject6.importedDef
        |
        |class TUI {
        |  importedDef()
        |}
        |
      """.stripMargin,
    item = "importedDef",
    time = 2
  )

  def testGlobalMember7(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class TUI {
         |  imposToR$CARET
         |}
       """.stripMargin,
    time = 2,
    completionType = DEFAULT_COMPLETION_TYPE
  ) {
    _ => true
  }

  def testGlobalMemberJava(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  activeCoun$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class TUI {
         |  Thread.activeCount()$CARET
         |}
      """.stripMargin,
    item = "activeCount",
    time = 2
  )

  def testGlobalMember8(): Unit = checkNoCompletion(
    fileText =
      s"""
         |object BlahBlahBlahContainer {
         |  private def doSmthPrivate() {}
         |  def doSmthPublic() {}
         |}
         |
         |class Test {
         |  def test() {
         |    dsp$CARET
         |  }
         |}
       """.stripMargin,
    item = "doSmthPrivate",
    time = 2
  )

  def testGlobalMember9(): Unit = {
    val lookups = configureTest(
      fileText =
        s"""
           |object BlahBlahBlahContainer {
           |  private def doSmthPrivate() {}
           |  def doSmthPublic() {}
           |}
           |
           |class Test {
           |  def test() {
           |    dsp$CARET
           |  }
           |}
       """.stripMargin,
      time = 3
    ) {
      hasLookupString(_, "doSmthPrivate")
    }

    assertTrue(lookups.nonEmpty)
  }

  def testGlobalMemberInherited(): Unit = {
    val lookups = configureTest(
      fileText =
        s"""
           |class Base {
           |  def zeeGlobalDefInherited = 0
           |  val zeeGlobalValInherited = 0
           |}
           |
           |object D1 extends Base {
           |  def zeeGlobalDef = 0
           |  def zeeGlobalVal = 0
           |}
           |
           |package object D2 extends Base
           |
           |class Test {
           |  def test() {
           |    zeeGlobal$CARET
           |  }
           |}
       """.stripMargin,
      time = 3
    )()

    val actual = lookups.collect {
      case lookup: ScalaLookupItem => s"${lookup.containingClass.name}.${lookup.getLookupString}"
    }.toSet

    val expected = Set(
      "D1.zeeGlobalDefInherited",
      "D1.zeeGlobalValInherited",
      "D1.zeeGlobalDef",
      "D1.zeeGlobalVal",
      "D2.zeeGlobalDefInherited",
      "D2.zeeGlobalValInherited"
    )
    assertArrayEquals(expected.toArray[AnyRef], actual.toArray[AnyRef])
  }
}

class ScalaGlobalMemberCompletionTest2 extends ScalaCodeInsightTestBase {

  import ScalaGlobalMemberCompletionTest2._

  def testJavaConverters(): Unit = doCompletionTest(
    fileText =
      s"""
         |val ja = new java.util.ArrayList[Int]
         |ja.asSc$CARET
      """.stripMargin,
    resultText =
      """
        |val ja = new java.util.ArrayList[Int]
        |ja.asScala
      """.stripMargin,
    item = "asScala",
    time = 2
  )

  override protected def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean): Unit = {
    def runCheck(fileText: String) = try {
      super.checkResultByText(fileText, ignoreTrailingSpaces)
      true
    } catch {
      case _: ComparisonFailure => false
    }

    val expected = CONVERTERS_NAMES.map(expectedText(_, expectedFileText))
    assertTrue(expected.exists(runCheck))
  }
}

object ScalaGlobalMemberCompletionTest2 {

  private val CONVERTERS_NAMES = Seq(
    "asScalaBufferConverter",
    "collectionAsScalaIterableConverter",
    "iterableAsScalaIterableConverter"
  )

  private def expectedText(className: String, expectedFileText: String): String =
    s"""
       |import scala.collection.JavaConverters.$className
       |$expectedFileText
       """.stripMargin
}