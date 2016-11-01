package scala.meta.annotations

import org.jetbrains.plugins.scala.ScalaFileType
import org.junit.Assert

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
    myFixture.configureByText(ScalaFileType.SCALA_FILE_TYPE,
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

}
