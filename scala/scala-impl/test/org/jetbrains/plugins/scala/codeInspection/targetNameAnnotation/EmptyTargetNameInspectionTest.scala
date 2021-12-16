package org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class EmptyTargetNameInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection = classOf[EmptyTargetNameInspection]
  override protected val description = EmptyTargetNameInspection.message

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testEmptyExtName(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class A:
         |  @targetName($START""$END)
         |  val *^*^* = 42
         |""".stripMargin
    checkTextHasError(code)
  }

}
