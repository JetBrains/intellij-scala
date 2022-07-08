package scala.meta.annotations

import com.intellij.testFramework.TestActionEvent
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaFileType}
import org.junit.{Assert, Ignore}

@Ignore("Deprecated logic") // TODO remove deprecated logic and test
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

  def DISABLEDtestOutOfDateGutterIcon(): Unit = {

    addMetaSource(
      """
        |import scala.meta._
        |
        |class main extends scala.annotation.StaticAnnotation {
        |  inline def apply(defn: Any): Any = meta { defn }
        |}
      """.stripMargin
    )
    val fileText =
      s"""
         |@main
         |class Foo
      """.stripMargin
    myFixture.addFileToProject("test/Foo.scala", fileText)
    myFixture.configureByFile("test/Foo.scala")
    Assert.assertEquals("Wrong tooltip text: out of date not detected", ScalaBundle.message("scala.meta.recompile"), getGutter.getTooltipText)
    enableParadisePlugin()
    getGutter.getClickAction.actionPerformed(new TestActionEvent())
    Assert.assertEquals("Wrong tooltip text: compiled class not detected", ScalaBundle.message("scala.meta.expand"), getGutter.getTooltipText)
//    typeInAnnotationFile()
//    Assert.assertEquals("Wrong tooltip text: out of date not detected after file edit", ScalaBundle.message("scala.meta.recompile"), getGutter.getTooltipText)
  }

  def testInjectSuper(): Unit = {
    val superName = "Foo"
    val superMethodName = "foo"
    compileMetaSource(
      s"""
        |import scala.meta._
        |class addSuper extends scala.annotation.StaticAnnotation {
        |  inline def apply(defn: Any): Any = meta {
        |    val q"class $$name" = defn
        |    q"class $$name extends $superName"
        |  }
        |}
      """.stripMargin
    )
    myFixture.configureByText("Test.scala",
    s"""
      |class $superName { def $superMethodName = 42 }
      |
      |@addSuper
      |class Test
    """.stripMargin)

    val clazz = myFixture.findClass("Test").asInstanceOf[ScClass]
    Assert.assertTrue("Method from injected superclass not found", clazz.findMethodsByName(superMethodName, true).nonEmpty)
  }

  def testRemoveMethod(): Unit = {
    val removedMethodName = "foo"
    compileMetaSource(mkAnnot(annotName,
      """
        |val q"class $name { ..$_ }" = defn
        |q"class $name { def fooBar = 42 }"
      """.stripMargin)
    )
    myFixture.configureByText(s"$testClassName.scala",
      s"""
      |@$annotName
      |class $testClassName { def $removedMethodName = 42 }
      |
      |new $testClassName().$removedMethodName<caret>
    """.stripMargin)

    elementAtCaret match {
      case ref: ScReferenceExpression if ref.bind().isDefined => Assert.fail("Deleted method still resolves")
      case _ =>
    }
    myFixture.completeBasic()
    myFixture.getElementAtCaret match {
      case fun: ScFunctionDefinition if fun.name == "fooBar" =>
      case _ => Assert.fail("Generated method does not resolve")
    }
  }

}
