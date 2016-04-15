package org.jetbrains.plugins.scala.failed.decompiler

import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.{AssertMatches, ScalaFixtureTestCase}
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion
import org.junit.experimental.categories.Category
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Error, Message, ScalaAnnotator}
import org.jetbrains.plugins.scala.extensions.PsiElementExt

import scala.tools.scalap.DecompilerTestBase

/**
  * @author Roman.Shein
  * @since 13.04.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class DecompilerHighlightingTest extends ScalaFixtureTestCase(ScalaSdkVersion._2_11, true) with DecompilerTestBase with AssertMatches {
  override def basePath(separator: Char) = s"${super.basePath(separator)}highlighting$separator"

  override def doTest(fileName: String) = {
    assertNothing(getMessages(fileName, decompile(getClassFilePath(fileName))))
  }

  def getMessages(fileName: String, scalaFileText: String): List[Message] = {
    myFixture.configureByText(fileName.substring(0, fileName.lastIndexOf('.')) + ".scala", scalaFileText.replace("{ /* compiled code */ }", "???"))
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    val mock = new AnnotatorHolderMock
    val annotator = new ScalaAnnotator

    getFile.depthFirst.foreach(annotator.annotate(_, mock))
    mock.annotations.filter {
      case Error(_, null) | Error(null, _) => false
      case Error(_, a) => true
      case _ => false
    }
  }

  def testTypers() = doTest("Typers.class")

  def testDefaultMacroCompiler() = doTest("DefaultMacroCompiler.class")

  def testContext() = doTest("Context.class")

  def testReifiers() = doTest("Reifiers.class")

  def testParsers() = doTest("Parsers.class")

  def testSymbolicXmlBuilder() = doTest("SymbolicXmlBuilder.class")

  def testJavaParsers() = doTest("JavaParsers.class")

  def testCommandLine() = doTest("CommandLine.class")

  def testReference() = doTest("Reference.class")

  def testScaladocModelTest() = doTest("ScaladocModelTest.class")

  def testClassloadVerify() = doTest("ClassloadVerity.class")

  def testScalac() = doTest("Scalac.class")

  def testScaladoc() = doTest("Scaladoc.class")

  def testPathResolver() = doTest("PathResolver.class")

  def testSocketServer() = doTest("SocketServer.class")

  def testFormatInterpolator() = doTest("FormatInterpolator.class")

  def testStdTags() = doTest("StdTags.class")

  def testDirectoryFileLookup() = doTest("DirectoryFileLookup.class")

  def testDocParser() = doTest("DocParser.class")

  def testSettings() = doTest("Settings.class")
}
