package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * Created by mucianm on 23.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class PrimitivesTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "bugs5/"
  def testSCL2045() = doTest(
    """    def returnsANumber = {
      |      if (1==1) {
      |        /*start*/0/*end*/
      |      } else {
      |        0.0
      |      }
      |    }
      |    
      |    //Double""".stripMargin)

  def testSCL7101(): Unit = doTest {
    """
      |object SCL7101 {
      |  def fun(x: Byte): Byte = x
      |
      |  def fun(x: Boolean): Boolean = x
      |
      |  /*start*/fun((10))/*end*/
      |}
      |//Byte
    """.stripMargin.trim
  }
  
  def testSCL7923(): Unit = doTest {
    """
      |object Scl7923 {
      |  import java.lang.{Long => JLong}
      |
      |  class Test {
      |
      |    def withJavaLong(number: JLong): Unit = {}
      |
      |    def test(): Unit = {
      |      val num: Int = 5
      |      withJavaLong(number = /*start*/num/*end*/)
      |    }
      |  }
      |}//Long""".stripMargin
  }
}
