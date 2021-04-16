package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * @author Alefas
  * @since 22/03/16
  */
class SyntheticSugarCallsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL7503(): Unit = doTest()

  def testSCL7112(): Unit = doTest(
    """
      |case class Property[T](val name: String="", private val default: Option[T]=None) {
      |
      |  private var currentValue = default
      |
      |  def value = currentValue
      |  def value_=(newValue: Option[T]): Boolean = {
      |    val oldValue = currentValue
      |    currentValue = newValue
      |    if(oldValue != newValue) {}
      |    false
      |  }
      |}
      |
      |class WPModel {
      |
      |  val slid = new Property[String]("slid") {
      |
      |    override def value_=(user: Option[String]) = {
      |      /*start*/super.value = user.map(_.toUpperCase())/*end*/
      |    }
      |  }
      |}
      |//Boolean
    """.stripMargin.trim
  )
}
