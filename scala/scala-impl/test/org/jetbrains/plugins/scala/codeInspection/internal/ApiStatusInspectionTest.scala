package org.jetbrains.plugins.scala
package codeInspection
package internal

import com.intellij.codeInspection.LocalInspectionTool

class ApiStatusInspectionTest extends ScalaInspectionTestBase {

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ApiStatusInspection]

  override protected val description: String = ""

  override protected def descriptionMatches(s: String): Boolean = Option(s).exists(_.contains("is marked as internal"))

  override def createTestText(text: String): String = {
    myFixture.addFileToProject("org/jetbrains/annotations/ApiStatus.java",
      """package org.jetbrains.annotations;
        |
        |public final class ApiStatus {
        |    public ApiStatus() {
        |    }
        |
        |    @Documented
        |    @Retention(RetentionPolicy.CLASS)
        |    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE})
        |    public @interface Internal {
        |    }
        |}
        |""".stripMargin)

    s"""object Test {
       |  import org.jetbrains.annotations._
       |  $text
       |}
       |""".stripMargin
  }

  def doTestApiStatus(definition: String, usage: String, fileType: String = "scala"): Unit = {
    myFixture.addFileToProject(s"Test.$fileType",
      s"""import org.jetbrains.annotations.ApiStatus;
         |
         |$definition
         |""".stripMargin
    )
    checkTextHasError(usage)
  }

  def test_funccall(): Unit = doTestApiStatus(
    s"""
       |object Def {
       |  @ApiStatus.Internal
       |  def test(): Unit = ()
       |}
       |""".stripMargin,
    s"""
       |Def.${START}test${END}()
       |""".stripMargin
  )

  def test_value(): Unit = doTestApiStatus(
    s"""
       |object Def {
       |  @ApiStatus.Internal
       |  val value = 3
       |}
       |""".stripMargin,
    s"""
       |Def.${START}value${END}
       |""".stripMargin
  )

  def test_class(): Unit = doTestApiStatus(
    s"""
       |@ApiStatus.Internal
       |case class Blub(x: Int)
       |""".stripMargin,
    s"""
       |${START}Blub$END(3)
       |new ${START}Blub$END(3)
       |""".stripMargin
  )

  def test_constructor(): Unit = doTestApiStatus(
    s"""
       |public class Blub {
       |  public Blub() {}
       |
       |  @ApiStatus.Internal
       |  public Blub(int i) {}
       |}
       |""".stripMargin,
    s"""
       |new Blub()
       |new ${START}Blub${END}(3)
       |""".stripMargin,
    fileType = "java"
  )
}