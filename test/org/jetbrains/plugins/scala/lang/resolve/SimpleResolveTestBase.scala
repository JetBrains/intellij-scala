package org.jetbrains.plugins.scala.lang.resolve

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.util.TestUtils


/**
  * @author mucianm 
  * @since 05.04.16.
  */
class SimpleResolveTestBase extends ScalaLightCodeInsightFixtureTestAdapter {
  import SimpleResolveTestBase._

  protected def folderPath = TestUtils.getTestDataPath + "/resolve/"

  protected def doResolveTest(sources: Seq[(String, String)]): Unit = {
    var src: ScReferenceElement = null
    var tgt: PsiElement = null
    for ((source, fileName) <- sources) {
      val trimmed = source.trim
      val srcOffset = trimmed.replaceAll(REFTGT, "").indexOf(REFSRC)
      val tgtOffset = trimmed.replaceAll(REFSRC, "").indexOf(REFTGT)
      val file = myFixture.addFileToProject(fileName, trimmed.replaceAll(REFSRC, "").replaceAll(REFTGT,""))
      src = PsiTreeUtil.getParentOfType(file.findElementAt(srcOffset), classOf[ScReferenceElement])
      tgt = PsiTreeUtil.getParentOfType(file.findElementAt(tgtOffset), classOf[ScalaPsiElement])
    }
    val result = src.resolve()
    assert(tgt == result, s"Src resolves tp wrong place: ${result.getText}")
  }

  protected def doResolveTest(source: String, fileName: String = "dummy.scala"): Unit = doResolveTest(Seq(source -> fileName))

  protected def doResolveTest(): Unit = {
    val filePath = folderPath + getTestName(false) + ".scala"
    val ioFile: File = new File(filePath)
    var fileText: String = FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    fileText = StringUtil.convertLineSeparators(fileText)
    doResolveTest(fileText, ioFile.getName)
  }

}

object SimpleResolveTestBase {
  val REFSRC = "<src>"
  val REFTGT = "<tgt>"
}

