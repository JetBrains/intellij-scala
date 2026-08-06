package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}


class UnitReturnTypeInspectionTest extends ScalaInspectionTestBase {

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[AccessorLikeMethodInspection.UnitReturnType]

  protected override val description: String =
    ScalaInspectionBundle.message("method.signature.accessor.unit.return.type")


  def test_definition(): Unit = {
    checkTextHasError(
      text = s"def ${START}getStuff$END: Unit = ()"
    )


    checkTextHasError(
      text = s"def ${START}getStuff$END = ()"
    )
  }


  def test_declaration(): Unit = {
    checkTextHasError(
      text = s"def ${START}getStuff$END: Unit"
    )

    checkTextHasError(
      text = s"def ${START}getStuff$END"
    )
  }


  def test_ok(): Unit = {
    checkTextHasNoErrors(
      text = s"def getStuff(): Int"
    )

    checkTextHasNoErrors(
      text = s"def getStuff(): Int = 0"
    )
  }

  def test_with_base_class(): Unit = {
    checkTextHasError(
      s"""
         |class Impl extends Base {
         |  def getStuff(): Unit = ()
         |}
         |
         |trait Base {
         |  def ${START}getStuff$END(): Unit
         |}
      """.stripMargin
    )
  }
}
