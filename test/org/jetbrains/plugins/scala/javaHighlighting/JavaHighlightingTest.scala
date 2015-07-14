package org.jetbrains.plugins.scala
package javaHighlighting

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ScalaAnnotator, _}
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.junit.Assert


/**
 * Author: Svyatoslav Ilinskiy
 * Date: 7/8/15
 */
class JavaHighlightingTest extends ScalaFixtureTestCase {

  def testProtected() = {
    val scala =
      """
        |class MeaningOfLifeSpec {
        |  val c = new UltimateQuestion {}
        |  def meaningOfLifeScala() {
        |    c.meaningOfLife()
        |  }
        |}
      """.stripMargin
    val java =
      """
        |public class UltimateQuestion {
        |    protected int meaningOfLife() {
        |        return 42; //Answer to the Ultimate Question of Life, the Universe, and Everything
        |    }
        |}
      """.stripMargin
    assertMatches(messagesFromScalaCode(scala, java)) {
      case Nil =>
    }
  }

  def testOptionApply(): Unit = {
    val java =
      """
        |import scala.Option;
        |
        |public abstract class OptionApply {
        |
        |    public OptionApply() {
        |        setAction(Option.apply("importVCardFile"));
        |    }
        |
        |    public abstract void setAction(Option<String> bar);
        |}
      """.stripMargin
    assertMatches(messagesFromJavaCode(scalaFileText = "", java, javaClassName = "OptionApply")) {
      case Nil =>
    }
  }

  def testAccessBacktick(): Unit = {
    val scala =
      """
        |import scala.beans.BeanProperty
        |
        |case class TestAccessBacktick(@BeanProperty `type`:String)
      """.stripMargin

    val java =
      """
        |public class TestJavaAAA {
        |    public static void main(String[] args) {
        |        TestAccessBacktick t = new TestAccessBacktick("42");
        |        t.type();
        |        t.getType();
        |        t.get$u0060type$u0060();
        |    }
        |}
      """.stripMargin

    assertMatches(messagesFromJavaCode(scala, java, javaClassName = "TestJavaAAA")) {
      case Error("get$u0060type$u0060", CannotResolveMethod()) :: Nil =>
    }
  }

  def messagesFromJavaCode(scalaFileText: String, javaFileText: String, javaClassName: String): List[Message] = {
    myFixture.addFileToProject("dummy.scala", scalaFileText)
    val myFile: PsiFile = myFixture.addFileToProject(javaClassName + JavaFileType.DOT_DEFAULT_EXTENSION, javaFileText)
    myFixture.openFileInEditor(myFile.getVirtualFile)
    val allInfo = myFixture.doHighlighting()

    import scala.collection.JavaConverters._
    allInfo.asScala.toList.collect {
      case highlightInfo if highlightInfo.`type`.getSeverity(null) == HighlightSeverity.ERROR =>
        val elementText = myFile.findElementAt(highlightInfo.getStartOffset).getText
        new Error(elementText, highlightInfo.getDescription)
    }
  }

  def messagesFromScalaCode(scalaFileText: String, javaFileText: String): List[Message] = {
    myFixture.addFileToProject("dummy.java", javaFileText)
    myFixture.configureByText("dummy.scala", scalaFileText)
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    val mock = new AnnotatorHolderMock
    val annotator = new ScalaAnnotator

    getFile.depthFirst.foreach(annotator.annotate(_, mock))
    mock.annotations.filter {
      case Error(_, null) | Error(null, _) => false
      case Error(_, _) => true
      case _ => false
    }
  }

  def assertMatches[T](actual: T)(pattern: PartialFunction[T, Unit]) {
    Assert.assertTrue("actual: " + actual.toString, pattern.isDefinedAt(actual))
  }

  val CannotResolveMethod = ContainsPattern("Cannot resolve method")

  case class ContainsPattern(fragment: String) {
    def unapply(s: String) = s.contains(fragment)
  }
}

