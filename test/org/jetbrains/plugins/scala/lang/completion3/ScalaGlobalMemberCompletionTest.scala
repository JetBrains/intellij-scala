package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaGlobalMemberCompletionTest extends ScalaCompletionTestBase {
  protected override def rootPath(): String = baseRootPath() + "completion3/globalMember"

  def testGlobalMember1() {
    val fileText =
"""
class TUI {
  rawObj<caret>
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy1.scala", fileText)
    val (activeLookup, _) = complete(completionType = CompletionType.CLASS_NAME)

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
    val (activeLookup, _) = complete(completionType = CompletionType.CLASS_NAME)

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
    val (activeLookup, _) = complete(completionType = CompletionType.CLASS_NAME)

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
    val (activeLookup, _) = complete(completionType = CompletionType.CLASS_NAME)

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
    val (activeLookup, _) = complete(completionType = CompletionType.CLASS_NAME)

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
    val (activeLookup, _) = complete(completionType = CompletionType.CLASS_NAME)

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
    val (activeLookup, _) = complete(completionType = CompletionType.CLASS_NAME)
    assert(activeLookup == null)

    val resultText =
"""
class TUI {
  imposToR<caret>
}
""".replaceAll("\r", "").trim()

    checkResultByText(resultText)
  }
}