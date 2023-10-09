package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class SyntheticSugarCallsTest extends TypeInferenceTestBase {

  override def folderPath: String = super.folderPath + "bugs5/"

  override protected def shouldPass: Boolean = false

  def testSCL7112(): Unit = assertNoErrors(
    """//noinspection CaseClassParam
      |case class Property[T](val name: String = "", private val default: Option[T] = None) {
      |  private var currentValue = default
      |
      |  def value: Option[T] = currentValue
      |
      |  def value_=(newValue: Option[T]): Boolean = {
      |    val oldValue = currentValue
      |    currentValue = newValue
      |    if (oldValue != newValue) {}
      |    false
      |  }
      |}
      |
      |//noinspection ScalaUnusedSymbol,TypeAnnotation
      |class WPModel {
      |  val slid = new Property[String]("slid") {
      |    override def value_=(user: Option[String]): Boolean = {
      |      super.value = user.map(_.toUpperCase())
      |    }
      |  }
      |}
      |""".stripMargin.trim
  )
}
