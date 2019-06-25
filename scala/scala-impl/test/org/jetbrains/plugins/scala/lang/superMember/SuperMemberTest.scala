package org.jetbrains.plugins.scala.lang.superMember

import java.io.File

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtil, VfsUtilCore}
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase._
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.util.TestUtils
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
/**
 * User: Alexander Podkhalyuzin
 * Date: 15.11.2008
 */
class SuperMemberTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  val CARET_MARKER = "<caret>"

  override protected def sourceRootPath: String = TestUtils.getTestDataPath + "/supers/"

  private def removeMarker(text: String) = {
    val index = text.indexOf(CARET_MARKER)
    text.substring(0, index) + text.substring(index + CARET_MARKER.length)
  }

  def testToString(): Unit = {
    val name = "objectMethods/toString.scala"
    runTest(name)
  }

  def testHashCode(): Unit = {
    val name = "objectMethods/hashCode.scala"
    runTest(name)
  }

  def testTraitSuper(): Unit = {
    val name = "traits/traitSuper.scala"
    runTest(name)
  }

  def testClassAliasSuper(): Unit = {
    val name = "class/ClassAliasDependent.scala"
    runTest(name)
  }

  def testSelfType(): Unit = {
    val name = "selfType/SelfType.scala"
    runTest(name)
  }

  private def runTest(name: String) = {
    import LightPlatformCodeInsightTestCase.myFile

    var filePath = sourceRootPath + name
    val vFile = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assertNotNull("file " + filePath + " not found", vFile)
    var text = StringUtil.convertLineSeparators(VfsUtilCore.loadText(vFile), "\n")
    val fileName = vFile.getName
    val offset = text.indexOf(CARET_MARKER)
    text = removeMarker(text)
    myFile = LightPlatformTestCase.createFile(fileName, text)
    filePath = filePath.replaceFirst("[.][s][c][a][l][a]", ".test")
    val answerFile = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assertNotNull("file " + filePath + " not found", answerFile)
    val resText = StringUtil.convertLineSeparators(VfsUtilCore.loadText(answerFile), "\n")
    assertEquals(resText, SuperMethodTestUtil.transform(myFile, offset))
  }
}
