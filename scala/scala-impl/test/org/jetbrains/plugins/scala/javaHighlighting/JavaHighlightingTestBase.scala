package org.jetbrains.plugins.scala.javaHighlighting

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiFile
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.{Message, ScalaHighlightingTestBase}
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
abstract class JavaHighlightingTestBase extends ScalaHighlightingTestBase {
  import Message._

  override protected def supportedIn(version: ScalaVersion): Boolean = version  >= LatestScalaVersions.Scala_2_11

  override protected lazy val jdk: Sdk = SmartJDKLoader.createFilteredJdk(LanguageLevel.JDK_17, Seq("java.base", "java.sql"))

  private var myFilesCreated: Boolean = false

  def errorsFromJavaCode(
    @Language("JAVA") javaFileText: String,
    javaClassName: String
  ): List[Message] =
    errorsFromJavaCode("", javaFileText, javaClassName)

  def errorsFromJavaCode(
    @Language("Scala") scalaFileText: String,
    @Language("JAVA") javaFileText: String,
    javaClassName: String
  ): List[Message] = {
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

  protected def assertNoErrorsInJava(
    @Language("Java") fileText: String,
    javaClassName: String //TODO: don't make it mandatory?
  ): Unit = {
    val actualMessages = errorsFromJavaCode(fileText, javaClassName)
    assertMessagesTextImpl("", actualMessages)
  }

  protected def assertNoErrors(
    @Language("Scala") scalaFileText: String,
    @Language("JAVA") javaFileText: String,
    javaClassName: String
  ): Unit = {
    val actualMessages = errorsFromJavaCode(scalaFileText, javaFileText, javaClassName)
    assertMessagesTextImpl("", actualMessages)
  }

  protected def assertErrorsTextInJava(
    @Language("JAVA") javaCode: String,
    javaClassName: String,
    messagesConcatenated: String,
  ): Unit = {
    val actualMessages = errorsFromJavaCode("", javaCode, javaClassName)
    assertMessagesTextImpl(messagesConcatenated, actualMessages)
  }

  protected def assertErrorsTextInJava(
    @Language("Scala") scalaCode: String,
    @Language("JAVA") javaCode: String,
    javaClassName: String,
    messagesConcatenated: String,
  ): Unit = {
    val actualMessages = errorsFromJavaCode(scalaCode, javaCode, javaClassName)
    assertMessagesTextImpl(messagesConcatenated, actualMessages)
  }

  protected def assertNoErrorsInKotlin(@Language("kotlin") fileText: String): Unit = {
    val actualMessages = errorsFromKotlinCode(fileText)
    assertMessagesTextImpl("", actualMessages)
  }

  protected def errorsFromKotlinCode(@Language("kotlin") fileText: String): List[Message] = {
    if (myFilesCreated)
      throw new AssertionError("Don't add files 2 times in a single test")

    val myFile: PsiFile = myFixture.addFileToProject("dummy.kt", fileText)
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
