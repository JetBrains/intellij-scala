package org.jetbrains.plugins.scala.lang

package object dfa {

  def defaultCodeTemplate(returnType: String)(body: String): String =
    s"""
       |import java.util
       |import java.lang.Math.abs
       |
       |class OtherClass {
       |  val otherField: Int = 1244
       |  val yetAnotherField: String = "Hello again"
       |}
       |
       |class TestClass {
       |  def testMethod(arg1: Int, arg2: Int, arg3: Boolean, arg4: String): $returnType = {
       |    $body
       |  }
       |
       |  def anotherMethod(arg1: Int, arg2: Int, arg3: Boolean, arg4: String): Int = arg2 - arg1
       |}
       |""".stripMargin
}
