package scala.meta.annotations

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.testFramework.TestActionEvent
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaFileType}
import org.junit.Assert
import org.junit.Assert.assertEquals

/**
  * @author mutcianm
  * @since 31.10.16.
  */
class MetaAnnotationsTest extends MetaAnnotationTestBase {

  def testAddMethodToObject(): Unit = {
    val mynewcoolmethod = "myNewCoolMethod"
    compileMetaSource(
      s"""
        |import scala.meta._
        |
        |class main extends scala.annotation.StaticAnnotation {
        |  inline def apply(defn: Any): Any = meta {
        |    val q"object $$name { ..$$stats }" = defn
        |    val main = q"def $mynewcoolmethod (args: Array[String]): Unit = { ..$$stats }"
        |    q"object $$name { $$main }"
        |  }
        |}
      """.stripMargin
    )
    myFixture.configureByText(ScalaFileType.INSTANCE,
      s"""
         |@main
         |object Foo {
         |  println("bar")
         |}
         |Foo.<caret>
      """.stripMargin)
    val result = myFixture.completeBasic()
    Assert.assertTrue(s"Method '$mynewcoolmethod' hasn't been injected", result.exists(_.getLookupString == mynewcoolmethod))
  }

  def testOutOfDateGutterIcon(): Unit = {
    def getGutter: GutterIconRenderer = {
      val gutters = myFixture.findAllGutters()
      Assert.assertEquals("Wrong number of gutters", 1, gutters.size())
      gutters.get(0).asInstanceOf[GutterIconRenderer]
    }

    def typeInAnnotationFile() = {
      myFixture.openFileInEditor(myFixture.findClass("main").getContainingFile.getVirtualFile)
      myFixture.`type`("\n\n\n")
      myFixture.openFileInEditor(myFixture.findClass("Foo").getContainingFile.getVirtualFile)
    }

    addMetaSource(
      """
        |import scala.meta._
        |
        |class main extends scala.annotation.StaticAnnotation {
        |  inline def apply(defn: Any): Any = meta { defn }
        |}
      """.stripMargin
    )
    myFixture.configureByText(ScalaFileType.INSTANCE,
      s"""
         |@main
         |class Foo
      """.stripMargin)
    Assert.assertEquals("Wrong tooltip text: out of date not detected", ScalaBundle.message("scala.meta.recompile"), getGutter.getTooltipText)
    runMake()
    Assert.assertEquals("Wrong tooltip text: compiled class not detected", ScalaBundle.message("scala.meta.expand"), getGutter.getTooltipText)
//    typeInAnnotationFile()
//    Assert.assertEquals("Wrong tooltip text: out of date not detected after file edit", ScalaBundle.message("scala.meta.recompile"), getGutter.getTooltipText)
  }

}
