package org.jetbrains.plugins.scala.lang.highlighting.decompiler

import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Error, Message, ScalaAnnotator}
import org.jetbrains.plugins.scala.base.{AssertMatches, ScalaFixtureTestCase}
import org.jetbrains.plugins.scala.decompiler.DecompilerTestBase
import org.jetbrains.plugins.scala.extensions.PsiElementExt

/**
  * @author Roman.Shein
  * @since 31.05.2016.
  */
abstract class DecompilerHighlightingTestBase extends ScalaFixtureTestCase with DecompilerTestBase with AssertMatches {

  override protected val includeReflectLibrary: Boolean = true

  override def basePath = s"${super.basePath}/highlighting/"

  override def doTest(fileName: String) = {
    assertNothing(getMessages(fileName, decompile(getClassFilePath(fileName))))
  }

  def getMessages(fileName: String, scalaFileText: String): List[Message] = {
    myFixture.configureByText(fileName.substring(0, fileName.lastIndexOf('.')) + ".scala", scalaFileText.replace("{ /* compiled code */ }", "???"))
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    val mock = new AnnotatorHolderMock(getFile)
    val annotator = ScalaAnnotator.forProject

    getFile.depthFirst().foreach(annotator.annotate(_, mock))
    mock.annotations.filter {
      case Error(_, null) | Error(null, _) => false
      case Error(_, a) => true
      case _ => false
    }
  }
}
