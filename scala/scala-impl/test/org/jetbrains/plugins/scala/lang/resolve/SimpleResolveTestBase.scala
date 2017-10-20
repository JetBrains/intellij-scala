package org.jetbrains.plugins.scala.lang.resolve

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.TestFixtureProvider
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert


/**
  * @author mucianm 
  * @since 05.04.16.
  */
trait SimpleResolveTestBase {
  this: TestFixtureProvider with UsefulTestCase =>

  import SimpleResolveTestBase._

  protected def folderPath = TestUtils.getTestDataPath + "/resolve/"

  protected def doResolveTest(sources: Seq[(String, String)]): Unit = {
    var src: ScReferenceElement = null
    var tgt: PsiElement = null
    def configureFile(file: (String, String), configureFun: (String, String) => PsiFile) = {
      val (source, fileName) = file
      val trimmed = source.trim.replace("\r", "")
      val srcOffset = trimmed.replaceAll(REFTGT, "").indexOf(REFSRC)
      val tgtOffset = trimmed.replaceAll(REFSRC, "").indexOf(REFTGT)
      val psiFile = configureFun(fileName, trimmed.replaceAll(REFSRC, "").replaceAll(REFTGT,""))
      if (src == null)
        src = PsiTreeUtil.getParentOfType(psiFile.findElementAt(srcOffset), classOf[ScReferenceElement])
      if (tgt == null)
        tgt = PsiTreeUtil.getParentOfType(psiFile.findElementAt(tgtOffset), classOf[PsiElement])
    }
    sources.dropRight(1).foreach(configureFile(_, getFixture.addFileToProject)) // add additional files first
    sources.lastOption match {
      case Some(file) => configureFile(file, getFixture.configureByText)  // last file is the one to be opened in editor
      case None => Assert.fail("No testdata provided")
    }
    Assert.assertNotNull("Failed to locate source element", src)
    val result = src.resolve()
    Assert.assertNotNull(s"Failed to resolve element - '${src.getText}'", result)
    // we might want to check if reference simply resolves to something
    if (tgt != null)
      Assert.assertTrue(s"Reference(${src.getText}) resolves to wrong place(${result.getText})", tgt == result)
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

