package org.jetbrains.plugins.scala.lang.highlighting.decompiler

import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Error, Message, ScalaAnnotator}
import org.jetbrains.plugins.scala.base.{AssertMatches, ScalaFixtureTestCase}
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion
import scala.tools.scalap.DecompilerTestBase
import org.jetbrains.plugins.scala.extensions.PsiElementExt

/**
  * @author Roman.Shein
  * @since 31.05.2016.
  */
abstract class DecompilerHighlightingTestBase extends ScalaFixtureTestCase(ScalaSdkVersion._2_11, true) with DecompilerTestBase with AssertMatches {
  override def basePath(separator: Char) = s"${super.basePath(separator)}highlighting$separator"

  override def doTest(fileName: String) = {
    assertNothing(getMessages(fileName, decompile(getClassFilePath(fileName))))
  }

  def getMessages(fileName: String, scalaFileText: String): List[Message] = {
    myFixture.configureByText(fileName.substring(0, fileName.lastIndexOf('.')) + ".scala", scalaFileText.replace("{ /* compiled code */ }", "???"))
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    val mock = new AnnotatorHolderMock(getFile)
    val annotator = new ScalaAnnotator

    getFile.depthFirst.foreach(annotator.annotate(_, mock))
    mock.annotations.filter {
      case Error(_, null) | Error(null, _) => false
      case Error(_, a) => true
      case _ => false
    }
  }
}
