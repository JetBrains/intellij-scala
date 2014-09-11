package org.jetbrains.plugins.scala
package refactoring.changeSignature

import com.intellij.refactoring.changeSignature.{ChangeSignatureProcessorBase, ParameterInfo}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.ScalaParameterInfo

/**
 * Nikolay.Tropin
 * 2014-09-11
 */
class ChangeSignatureInScalaTest extends ChangeSignatureTestBase {
  override def folderPath: String = baseRootPath() + "changeSignature/inScala/"

  override def processor(newVisibility: String,
                         newName: String,
                         newReturnType: String,
                         newParams: => Seq[Seq[ParameterInfo]]): ChangeSignatureProcessorBase = {
    scalaProcessor(newVisibility, newName, newReturnType, newParams, isAddDefaultValue = false)
  }

  override def mainFileName(testName: String): String = testName + ".scala"
  override def mainFileAfterName(testName: String): String = testName + "_after.scala"
  override def secondFileName(testName: String): String = null
  override def secondFileAfterName(testName: String): String = null

  private def parameterInfo(name: String, oldIdx: Int, tpe: ScType, defVal: String = "", isRep: Boolean = false, isByName: Boolean = false) = {
    new ScalaParameterInfo(name, oldIdx, tpe, getProjectAdapter, isRep, isByName, defVal)
  }

  def testVisibility(): Unit = {
    val params = Seq(parameterInfo("i", -1, types.Int, "1"))
    doTest("protected", "foo", null, Seq(params))
  }

  def testAddRepeatedParam(): Unit = {
    val params = Seq(parameterInfo("i", 0, types.Int), parameterInfo("b", 1, types.Boolean),
      parameterInfo("xs", -1, types.Int, isRep = true, defVal = "1"))
    doTest(null, "foo", null, Seq(params))
  }

  def testAddRepeatedWithoutDefault(): Unit = {
    val params = Seq(parameterInfo("i", 0, types.Int), parameterInfo("xs", -1, types.Int, isRep = true))
    doTest(null, "foo", null, Seq(params))
  }

  def testMakeRepeatedParam(): Unit = {
    val params = Seq(parameterInfo("i", 0, types.Int), parameterInfo("b", 1, types.Boolean, isRep = true))
    doTest(null, "foo", null, Seq(params))
  }

  def testRemoveRepeatedParam(): Unit = {
    val params = Seq(parameterInfo("i", 0, types.Int), parameterInfo("b", 1, types.Boolean))
    doTest(null, "foo", null, Seq(params))
  }

}
