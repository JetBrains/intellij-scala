package org.jetbrains.plugins.scala.javaHighlighting

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.annotator.{Message, ScalaHighlightingTestBase}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
abstract class JavaHighlightingTestBase extends ScalaHighlightingTestBase {
  import Message._

  override protected def supportedIn(version: ScalaVersion): Boolean = version  >= LatestScalaVersions.Scala_2_11

  private var myFilesCreated: Boolean = false

  def errorsFromJavaCode(scalaFileText: String, javaFileText: String, javaClassName: String): List[Message] = {
    if (myFilesCreated) throw new AssertionError("Don't add files 2 times in a single test")

    myFixture.addFileToProject("dummy.scala", scalaFileText)
    val myFile: PsiFile = myFixture.addFileToProject(javaClassName + JavaFileType.DOT_DEFAULT_EXTENSION, javaFileText)
    myFixture.openFileInEditor(myFile.getVirtualFile)
    val allInfo = myFixture.doHighlighting()

    myFilesCreated = true

    import scala.jdk.CollectionConverters._
    allInfo.asScala.toList.collect {
      case highlightInfo if highlightInfo.`type`.getSeverity(null) == HighlightSeverity.ERROR =>
        Error(highlightInfo.getText, highlightInfo.getDescription)
    }
  }

  protected def addDummyJavaFile(javaFileText: String): Unit = {
    myFixture.addFileToProject("dummy.java", javaFileText)
  }
}
