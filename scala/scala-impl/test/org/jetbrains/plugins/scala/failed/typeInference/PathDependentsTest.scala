package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * @author Alefas
  * @since 22/03/16
  */
class PathDependentsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL7954(): Unit = doTest()

  def testSCL6143(): Unit = doTest()

  def testSCL8394(): Unit = {
    val text =
      s"""class Data{
        |  override def clone(): this.type = new Data().asInstanceOf[this.type]
        |}
        |class Handshake[T <: Data](dataType: T){
        |  val data = dataType.clone()
        |}
        |
        |object Test{
        |  def doit[T <: Data](handshake : Handshake[T]): Handshake[T] ={
        |    val ret = new Handshake(handshake.data)
        |    return ${START}ret$END
        |    // <- ret is marked red "Expression of type Handshake[that.dataType.type] doesn't conform to expected type Handshake[T]
        |    //If i write    return  new Handshake(handshake.data)  all is ok
        |  }
        |}
        |
        |//Handshake[T]""".stripMargin
    doTest(text)
  }
}
