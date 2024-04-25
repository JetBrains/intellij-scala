package org.jetbrains.plugins.scala.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiComment, PsiFile}
import com.intellij.testFramework.common.ThreadLeakTracker
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.junit.Assert
import org.junit.Assert.{assertNotNull, fail}

import java.io.{File, IOException}
import java.net.URISyntaxException
import java.util

object TestUtils {

  private val LOG = Logger.getInstance("org.jetbrains.plugins.scala.util.TestUtils")

  val CARET_MARKER = "<caret>"
  val BEGIN_MARKER = "<begin>"
  val END_MARKER = "<end>"

  private var TEST_DATA_PATH: String = _

  def getTestDataDir: File =
    new File(getTestDataPath).getCanonicalFile

  def getTestDataPath: String = {
    if (TEST_DATA_PATH == null) {
      try {
        val resource = TestUtils.getClass.getClassLoader.getResource("testdata")
        TEST_DATA_PATH =
          if (resource == null)
            find("scala/scala-impl", "testdata").getAbsolutePath
          else
            new File(resource.toURI).getPath.replace(File.separatorChar, '/')
      } catch {
        case e@(_: URISyntaxException | _: IOException) =>
          LOG.error(e)
          // just rethrowing here because that's a clearer way to make tests fail than some NPE somewhere else
          throw new RuntimeException(e)
      }
    }
    TEST_DATA_PATH
  }

  @throws[IOException]
  def findCommunityRoot: String = {
    // <community-root>/scala/scala-impl/testdata/
    val testDataPath = getTestDataPath
    java.nio.file.Paths.get(testDataPath, "..", "..", "..").normalize.toString + "/"
  }

  @throws[IOException]
  def findTestDataDir(pathname: String): String =
    findTestDataDir(new File(pathname), "testdata")

  @throws[IOException]
  private def find(pathname: String, child: String): File = {
    val file = new File("community/" + pathname, child)
    if (file.exists) file
    else new File(findTestDataDir(pathname))
  }

  /** Go upwards to find testdata, because when running test from IDEA, the launching dir might be some subdirectory. */
  @throws[IOException]
  private def findTestDataDir(parent: File, child: String): String = {
    val testData = new File(parent, child).getCanonicalFile
    if (testData.exists)
      testData.getCanonicalPath
    else {
      val newParent = parent.getCanonicalFile.getParentFile
      if (newParent == null) throw new RuntimeException("no testdata directory found")
      else findTestDataDir(newParent, child)
    }
  }

  def removeBeginMarker(text: String): String = {
    val index = text.indexOf(BEGIN_MARKER)
    text.substring(0, index) + text.substring(index + BEGIN_MARKER.length)
  }

  def removeEndMarker(text: String): String = {
    val index = text.indexOf(END_MARKER)
    text.substring(0, index) + text.substring(index + END_MARKER.length)
  }

  @throws[IOException]
  def readInput(filePath: String): util.List[String] = readInput(new File(filePath), null)

  @throws[IOException]
  def readInput(file: File, encoding: String): util.List[String] = {
    var content = new String(FileUtil.loadFileText(file, encoding))
    Assert.assertNotNull(content)
    val input = new util.ArrayList[String]
    var separatorIndex = 0
    content = StringUtil.replace(content, "\r", "") // for MACs

    // Adding input  before -----
    separatorIndex = content.indexOf("-----")
    while (separatorIndex >= 0) {
      input.add(content.substring(0, separatorIndex - 1))
      content = content.substring(separatorIndex)
      while (StringUtil.startsWithChar(content, '-')) {
        content = content.substring(1)
      }
      if (StringUtil.startsWithChar(content, '\n')) {
        content = content.substring(1)
      }
      separatorIndex = content.indexOf("-----")
    }
    // Result - after -----
    if (content.endsWith("\n")) {
      content = content.substring(0, content.length - 1)
    }
    input.add(content)
    Assert.assertTrue("No data found in source file", input.size > 0)
    input
  }

  def disableTimerThread(): Unit = {
    //This hacky "something" is originated from this commit:
    //  Fixed thread leak in PrivacyPolicyUpdater (it was actually suppressed).
    //  f0e2ac01 Alexander.Podkhalyuzin <alexander.podkhalyuzin@jetbrains.com> on 7/23/2016 at 12:03
    //Not sure if it's still actual though
    ThreadLeakTracker.longRunningThreadCreated(UnloadAwareDisposable.scalaPluginDisposable, "Timer")
    ThreadLeakTracker.longRunningThreadCreated(UnloadAwareDisposable.scalaPluginDisposable, "BaseDataReader")
    ThreadLeakTracker.longRunningThreadCreated(UnloadAwareDisposable.scalaPluginDisposable, "ProcessWaitFor")
  }

  /**
   * @param fileText text of the file without last comment
   * @param expectedResult content of the last comment in teh file
   */
  case class ExpectedResultFromLastComment(fileText: String, expectedResult: String)

  /**
   * A lot of file-based tests contain their test data in the last comment in the file.<br>
   * For example for some formatter tests the file could look like:
   * {{{
   *   class   ClassForFormat   {     }
   *   /*
   *   class ClassForFormat {}
   *   */
   * }}}
   *
   * @param file file which was created from test data which contains comment in the end of the file
   * @return 1. file content without last comment<br>
   *         2. last comment content as an expected result
   */
  def extractExpectedResultFromLastComment(file: PsiFile): ExpectedResultFromLastComment = {
    val fileText = file.getText

    val lastComment = file.findElementAt(fileText.length - 1) match {
      case comment: PsiComment => comment
      case element =>
        fail(s"Last element in the file is expected to be a comment but got: ${element.getClass} with text: ${element.getText}").asInstanceOf[Nothing]
    }

    val fileTextWithoutLastComment = file.getText.substring(0, lastComment.getTextOffset).trim

    val commentText = lastComment.getText
    val commentInnerContent = lastComment.getNode.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT => commentText.substring(2)
      case ScalaTokenTypes.tBLOCK_COMMENT=> commentText.substring(2, commentText.length - 2)
      case ScalaTokenTypes.tDOC_COMMENT => commentText.substring(3, commentText.length - 2)
      case _ =>
        fail("Test result must be in last comment statement.").asInstanceOf[Nothing]
    }
    ExpectedResultFromLastComment(fileTextWithoutLastComment, commentInnerContent.trim)
  }

  def getPathRelativeToProject(file: VirtualFile, project: Project): String = {
    val projectRoot = ProjectUtil.guessProjectDir(project)
    assertNotNull(s"Can't guess project dir", file)
    val pathParent = projectRoot.getPath
    val pathChild = file.getPath
    pathChild.stripPrefix(pathParent).stripPrefix("/")
  }
}