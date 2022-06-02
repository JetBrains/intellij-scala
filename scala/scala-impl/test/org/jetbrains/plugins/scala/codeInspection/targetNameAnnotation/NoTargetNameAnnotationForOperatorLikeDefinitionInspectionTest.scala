package org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}
import org.jetbrains.plugins.scala.extensions.{StringExt, executeWriteActionCommand}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert.{assertEquals, assertNotNull}

class NoTargetNameAnnotationForOperatorLikeDefinitionInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection = classOf[NoTargetNameAnnotationForOperatorLikeDefinitionInspection]
  override protected val description = NoTargetNameAnnotationForOperatorLikeDefinitionInspection.message

  override def setUp(): Unit = {
    super.setUp()
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable)
  }
  
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  private val hint = ScalaInspectionBundle.message("add.targetname.annotation")

  //noinspection SameParameterValue
  private def testQuickFixWithTemplate(code: String,
                                       expectedAfterQuickFix: String,
                                       textToTypeWithTemplate: String,
                                       expectedAfterTemplateFinished: String): Unit = {
    testQuickFix(code, expectedAfterQuickFix, hint)

    val templateState = TemplateManagerImpl.getTemplateState(getEditor)
    assertNotNull("Template state should not be null", templateState)

    val caretPosition = expectedAfterQuickFix.indexOf(CARET)

    assertEquals("Expected one template segment", templateState.getSegmentsCount, 1)
    assertEquals(templateState.getSegmentRange(0), TextRange.from(caretPosition, 0))

    executeWriteActionCommand() {
      myFixture.`type`(textToTypeWithTemplate)
      templateState.gotoEnd(false)
    }(getProject)

    myFixture.checkResult(expectedAfterTemplateFinished.withNormalizedSeparator.trim, true)
  }

  def testSingle(): Unit = {
    val code =
      s"""class $START*$END
         |""".stripMargin
    checkTextHasError(code)

    testQuickFixWithTemplate(
      code,
      expectedAfterQuickFix =
        s"""import scala.annotation.targetName
           |
           |@targetName("$CARET")
           |class *
           |""".stripMargin,
      textToTypeWithTemplate = "testTargetNameUsingTemplate",
      expectedAfterTemplateFinished =
        s"""import scala.annotation.targetName
           |
           |@targetName("testTargetNameUsingTemplate"$CARET)
           |class *
           |""".stripMargin
    )
  }

  def testFixAll(): Unit = {
    val code =
      s"""
         |def $START&&$END = 1
         |
         |val $START&*&$END = 2
         |
         |var $START*&*$END = 3
         |
         |object $START`object *^*^*`$END :
         |  class A(val i: Int, $START%$END : Double)
         |
         |  class $START^^$END(`param`: String)
         |
         |  case class $START&*^$END():
         |    def foo = new A(1, 2.3)
         |
         |trait $START&$END(b: Boolean, $START&^$END : Byte):
         |  type $START***&&&$END
         |  type Tpe
         |  val $START^&*$END : Int
         |  def foo = 42
         |  def $START&^%$END(&&& : Int): Double
         |  var $START&&^*$END : String
         |
         |enum $START^$END(c: Char, $START*&^$END : Int):
         |  case A($START%^$END : Char) extends ^(%^, 42)
         |
         |enum E:
         |  case $START%%$END, B, $START^^$END
         |
         |type $START&^$END = & Either ^
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |@targetName("")
        |def && = 1
        |
        |@targetName("")
        |val &*& = 2
        |
        |@targetName("")
        |var *&* = 3
        |
        |@targetName("")
        |object `object *^*^*` :
        |  class A(val i: Int, @targetName("") % : Double)
        |
        |  @targetName("")
        |  class ^^(`param`: String)
        |
        |  @targetName("")
        |  case class &*^():
        |    def foo = new A(1, 2.3)
        |
        |@targetName("")
        |trait &(b: Boolean, @targetName("") &^ : Byte):
        |  @targetName("")
        |  type ***&&&
        |  type Tpe
        |  @targetName("")
        |  val ^&* : Int
        |  def foo = 42
        |  @targetName("")
        |  def &^%(&&& : Int): Double
        |  @targetName("")
        |  var &&^* : String
        |
        |@targetName("")
        |enum ^(c: Char, @targetName("") *&^ : Int):
        |  case A(@targetName("") %^ : Char) extends ^(%^, 42)
        |
        |enum E:
        |  case %%, B, ^^
        |
        |@targetName("")
        |type &^ = & Either ^
        |""".stripMargin
    testQuickFixAllInFile(code, expected, hint)
  }

}
