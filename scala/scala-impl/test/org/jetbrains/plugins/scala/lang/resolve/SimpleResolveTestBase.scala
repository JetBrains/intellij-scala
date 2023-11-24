package org.jetbrains.plugins.scala
package lang.resolve

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile, PsiReference}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{Parent, PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._
import org.junit.experimental.categories.Category

import java.io.File
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

@Category(Array(classOf[TypecheckerTests]))
abstract class SimpleResolveTestBase extends ScalaLightCodeInsightFixtureTestCase {

  import SimpleResolveTestBase._

  //keeping hard refs to AST nodes to avoid flaky tests (as a workaround for SCL-20527 (see solution proposals))
  private val myASTHardRefs: ArrayBuffer[ASTNode] = ArrayBuffer.empty

  override def tearDown(): Unit = {
    super.tearDown()
    myASTHardRefs.clear()
  }

  protected def folderPath: String = TestUtils.getTestDataPath + "/resolve/"

  protected def getSrc(source: String, file: PsiFile): PsiReference = {
    val srcOffset = source.replaceAll(REFTGT, "").indexOf(REFSRC)
    if (srcOffset != -1)
      file.findElementAt(srcOffset).withParents.map(_.getReference).find(_ != null).orNull
    else null
  }

  protected def getTgt(source: String, file: PsiFile): PsiElement = {
    val tgtOffset = source.replaceAll(REFSRC, "").indexOf(REFTGT)
    val res = if (tgtOffset != -1)
      PsiTreeUtil.getParentOfType(file.findElementAt(tgtOffset), classOf[PsiElement])
    else
      null
    res match {
      //In example `(using MyContext)`
      //there are 3 elements with same range, we want to select the most-outer element representing the whole parameter
      case Parent(Parent(Parent(p: ScParameter))) if p.isAnonimousContextParameter => p
      case _ => res
    }
  }

  protected def doResolveTest(sources: (String, String)*): Unit =
    doResolveTest(target = None, shouldResolve = true, sources: _*)

  protected def doResolveTest(target: PsiElement, sources: (String, String)*): Unit =
    doResolveTest(target = Some(target), shouldResolve = true, sources: _*)

  protected def setupResolveTest(target: Option[PsiElement], sources: (String, String)*): (PsiReference, PsiElement) = {
    var src: PsiReference = null
    var tgt: PsiElement = target.orNull

    def configureFile(fileTextWithFileName: (String, String), configureFun: (String, String) => PsiFile): Unit = {
      val (source, fileName) = fileTextWithFileName
      val trimmed = source.trim.replace("\r", "")

      val psiFile = configureFun(fileName, trimmed.replaceAll(REFSRC, "").replaceAll(REFTGT, ""))
      myASTHardRefs += psiFile.getNode

      if (src == null) src = getSrc(trimmed, psiFile)
      if (tgt == null) tgt = getTgt(trimmed, psiFile)
    }

    sources.dropRight(1).foreach(configureFile(_, myFixture.addFileToProject)) // add additional files first

    val lastSource = sources.lastOption
    lastSource match {
      case Some(file) =>
        configureFile(file, myFixture.configureByText) // last file is the one to be opened in editor
      case None =>
        fail("No testdata provided")
    }

    assertNotNull(s"Failed to locate source element in file:\n$lastSource", src)
    (src, tgt)
  }

  private def doResolveTest(target: Option[PsiElement], shouldResolve: Boolean, sources: (String, String)*): Unit = {
    val (src, expectedResolvedElement) = setupResolveTest(target, sources: _*)

    val resolveResultMightBeSynthetic = src.resolve()
    //handle synthetic elements, for example reference to scala3 `enum` is resolved to synthetic element
    val resolveResult = resolveResultMightBeSynthetic match {
      case m: ScMember => Option(m.syntheticNavigationElement).getOrElse(resolveResultMightBeSynthetic)
      case _ => resolveResultMightBeSynthetic
    }

    val srcRefText = src.getElement.getText

    val testRunResult: Try[Unit] = Try {
      if (shouldResolve) {
        if (resolveResult == null) {
          val multiResolveResult: Array[ScalaResolveResult] = src match {
            case scRef: ScReference => scRef.multiResolveScala(false)
            case _ => Array.empty
          }
          val multiResolveResolveText = if (multiResolveResult.isEmpty) "" else {
            val texts: Array[String] = multiResolveResult.map(_.element).map { namedElement =>
              namedElement.name + " - " + elementLocationDescriptor(namedElement)
            }
            val textsConcat = texts.zipWithIndex.map { case (text, idx) => s"$idx : $text" }.map("  " + _).mkString("\n")
            s"\nmultiResolveResolve:\n$textsConcat"
          }
          fail(s"Failed to resolve single element - '$srcRefText'.$multiResolveResolveText")
        }
      }
      else {
        if (resolveResult != null) {
          fail(s"Reference '$srcRefText' must not resolve.")
        }
      }

      // we might want to check if reference simply resolves to something
      if (shouldResolve && expectedResolvedElement != null) {
        val actualLocation = elementLocationDescriptor(resolveResult)
        val expectedLocation = elementLocationDescriptor(expectedResolvedElement)
        assertEquals(
          s"""Reference($srcRefText) resolves to wrong place: $actualLocation,
             |actual resolved element text   : ${resolveResult.getText}
             |expected resolved element text : ${expectedResolvedElement.getText}
             |actual resolved location       : $actualLocation
             |expected resolved location     : $expectedLocation
             |""".stripMargin,
          expectedResolvedElement,
          resolveResult
        )
      }

      ()
    }

    testRunResult match {
      case Success(_) =>
        if (shouldPass) {
          // ok. test passed passed
        }
        else {
          fail(failingPassed)
        }
      case Failure(_: AssertionError) if !shouldPass =>
      //ok, test failed with some assertion
      case Failure(ex) =>
        throw ex
    }
  }

  private def elementLocationDescriptor(element: PsiElement): String = {
    val file = element.getContainingFile
    assertNotNull("file is null", file)
    val vFile = file.getVirtualFile
    assertNotNull(s"vFile is null for file ${file.getName}", vFile)
    val document = PsiDocumentManager.getInstance(element.getProject).getDocument(file)
    s"location: ${vFile.getPath}:${document.getLineNumber(element.startOffset)}"
  }

  protected def testNoResolve(sources: (String, String)*): Unit =
    doResolveTest(None, shouldResolve = false, sources: _*)

  protected def testNoResolve(source: String, fileName: String = "dummy.scala"): Unit =
    testNoResolve(source -> fileName)

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

