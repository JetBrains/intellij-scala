package org.jetbrains.plugins.scala.lang.resolve

import java.io.File
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.{PsiElement, PsiFile, PsiReference}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.TestFixtureProvider
import org.jetbrains.plugins.scala.base.FailableTest
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert


/**
  * @author mucianm
  * @since 05.04.16.
  */
trait SimpleResolveTestBase {
  this: TestFixtureProvider with UsefulTestCase with FailableTest =>

  import SimpleResolveTestBase._

  protected def folderPath: String = TestUtils.getTestDataPath + "/resolve/"

  protected def getSrc(source: String, file: PsiFile): PsiReference = {
    val srcOffset = source.replaceAll(REFTGT, "").indexOf(REFSRC)
    if (srcOffset != -1)
      file.findElementAt(srcOffset).withParents.map(_.getReference).find(_ != null).orNull
    else null
  }

  protected def getTgt(source: String, file: PsiFile): PsiElement = {
    val tgtOffset = source.replaceAll(REFSRC, "").indexOf(REFTGT)
    if (tgtOffset != -1)
      PsiTreeUtil.getParentOfType(file.findElementAt(tgtOffset), classOf[PsiElement])
    else null
  }

  protected def doResolveTest(sources: (String, String)*): Unit =
    doResolveTest(target = None, shouldResolve = true, sources: _*)

  protected def doResolveTest(target: PsiElement, sources: (String, String)*): Unit =
    doResolveTest(target = Some(target), shouldResolve = true, sources: _*)

  protected def setupResolveTest(target: Option[PsiElement], sources: (String, String)*): (PsiReference, PsiElement) = {
    var src: PsiReference = null
    var tgt: PsiElement = target.orNull

    def configureFile(file: (String, String), configureFun: (String, String) => PsiFile): Unit = {
      val (source, fileName) = file
      val trimmed = source.trim.replace("\r", "")
      val psiFile = configureFun(fileName, trimmed.replaceAll(REFSRC, "").replaceAll(REFTGT,""))
      if (src == null) src = getSrc(trimmed, psiFile)
      if (tgt == null) tgt = getTgt(trimmed, psiFile)
    }

    sources.dropRight(1).foreach(configureFile(_, getFixture.addFileToProject)) // add additional files first

    sources.lastOption match {
      case Some(file) =>
        configureFile(file, getFixture.configureByText) // last file is the one to be opened in editor
      case None => Assert.fail("No testdata provided")
    }

    Assert.assertNotNull("Failed to locate source element", src)
    (src, tgt)
  }

  private def doResolveTest(target: Option[PsiElement], shouldResolve: Boolean, sources: (String, String)*): Unit = {
    val (src, tgt) = setupResolveTest(target, sources: _*)
    val result = src.resolve()
    if (shouldPass) {
      if (shouldResolve) Assert.assertNotNull(s"Failed to resolve element - '${src.getElement.getText}'", result)
      else               Assert.assertNull("Reference '${src.getText}' must not resolve", result)
    } else if (result == null) {
      if (!shouldResolve) Assert.fail(failingPassed + ": failed to resolve element")
      else                return
    }

    // we might want to check if reference simply resolves to something
    if (tgt != null)
      if (shouldPass) Assert.assertTrue(s"Reference(${src.getElement.getText}) resolves to wrong place(${result.getText})", tgt == result)
       else           Assert.assertFalse(failingPassed, tgt == result)
  }

  protected def testNoResolve(sources: (String, String)*): Unit =
    doResolveTest(None, shouldResolve = false, sources: _*)

  protected def doResolveTest(source: String, fileName: String = "dummy.scala"): Unit =
    doResolveTest(source -> fileName)

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

