package org.jetbrains.plugins.scala
package lang.resolve

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile, PsiReference}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{Parent, PathExt, PsiElementExt, PsiNamedElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._
import org.junit.experimental.categories.Category

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.annotation.nowarn
import scala.util.{Failure, Success, Try}

@Category(Array(classOf[TypecheckerTests]))
abstract class SimpleResolveTestBase extends ScalaLightCodeInsightFixtureTestCase {

  import SimpleResolveTestBase._

  protected def folderPath: String = TestUtils.getTestDataPath + "/resolve/"

  protected case class SrcTgtOptions(targetIsLeaf: Boolean)
  protected object SrcTgtOptions {
    implicit val defaultSrcTgtOptions: SrcTgtOptions = SrcTgtOptions(targetIsLeaf = false)
  }

  protected def getSrc(source: String, file: PsiFile)(implicit opts: SrcTgtOptions): PsiReference = {
    val srcOffset = source.replaceAll(REFTGT, "").indexOf(REFSRC)
    if (srcOffset != -1)
      file.findElementAt(srcOffset).withParents.map(_.getReference).find(_ != null).orNull
    else null
  }

  protected def getTgt(source: String, file: PsiFile)(implicit opts: SrcTgtOptions): PsiElement = {
    val tgtOffset = source.replaceAll(REFSRC, "").indexOf(REFTGT)
    val res = if (tgtOffset != -1) {
      val leaf = file.findElementAt(tgtOffset)
      if (opts.targetIsLeaf) leaf else PsiTreeUtil.getParentOfType(leaf, classOf[PsiElement])
    } else {
      null
    }

    res match {
      //In example `(using MyContext)`
      //there are 3 elements with same range, we want to select the most-outer element representing the whole parameter
      case Parent(Parent(Parent(p: ScParameter))) if p.isAnonymous => p
      case _ => res
    }
  }

  protected def doResolveTest(sources: (String, String)*)(implicit opts: SrcTgtOptions): Unit =
    doResolveTest(target = None, shouldResolve = true, sources: _*)

  protected def doResolveTest(target: PsiElement, sources: (String, String)*)(implicit opts: SrcTgtOptions): Unit =
    doResolveTest(target = Some(target), shouldResolve = true, sources: _*)

  protected def setupResolveTest(target: Option[PsiElement], sources: (String, String)*)(implicit opts: SrcTgtOptions): (PsiReference, PsiElement) = {
    var src: PsiReference = null
    var tgt: PsiElement = target.orNull

    def configureFile(fileTextWithFileName: (String, String), configureFun: (String, String) => PsiFile): Unit = {
      val (source, fileName) = fileTextWithFileName
      val trimmed = source.trim.replace("\r", "")

      val psiFile = configureFun(fileName, trimmed.replaceAll(REFSRC, "").replaceAll(REFTGT, ""))

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

  private def doResolveTest(target: Option[PsiElement], shouldResolve: Boolean, sources: (String, String)*)(implicit opts: SrcTgtOptions): Unit = {
    val (src, expectedResolvedElement) = setupResolveTest(target, sources: _*)

    val resolveResultMightBeSynthetic = src.resolve()
    //handle synthetic elements, for example reference to scala3 `enum` is resolved to synthetic element
    val resolveResult = resolveResultMightBeSynthetic match {
      case p: ScParameter => ScalaPsiUtil.findSyntheticContextBoundInfo(p).flatMap(_.bound.nameIdOpt).getOrElse(p)
      case m: ScMember => Option(m.syntheticNavigationElement).getOrElse(m)
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

    val document = BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation[Document] { () =>
      PsiDocumentManager.getInstance(element.getProject).getDocument(file)
    }: @nowarn("cat=deprecation") // TODO: SCL-25196 Rewrite call on a background thread.

    s"location: ${vFile.getPath}:${document.getLineNumber(element.startOffset)}"
  }

  protected def testNoResolve(sources: (String, String)*)(implicit opts: SrcTgtOptions): Unit =
    doResolveTest(None, shouldResolve = false, sources: _*)

  protected def testNoResolve(source: String, fileName: String = "dummy.scala")(implicit opts: SrcTgtOptions): Unit =
    testNoResolve(source -> fileName)

  protected def doResolveTest(source: String, fileName: String = "dummy.scala")(implicit opts: SrcTgtOptions): Unit =
    doResolveTest(source -> fileName)

  protected def doResolveTest()(implicit opts: SrcTgtOptions): Unit = {
    val fileName = getTestName(false)
    val nioFile = Path.of(folderPath, s"$fileName.scala")
    val fileText = nioFile.readAllBytesToString(StandardCharsets.UTF_8).withNormalizedSeparator
    doResolveTest(fileText, fileName)
  }

}

object SimpleResolveTestBase {
  val REFSRC = "<src>"
  val REFTGT = "<tgt>"
}
