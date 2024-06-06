package org.jetbrains.plugins.scala
package refactoring.changeSignature

import com.intellij.psi.PsiMember
import com.intellij.refactoring.changeSignature.{ChangeSignatureProcessorBase, ParameterInfo}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.{ScalaChangeSignatureHandler, ScalaParameterInfo}
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
import org.junit.Assert._

abstract class ChangeSignatureInScalaTestBase extends ChangeSignatureTestBase {
  override def folderPath: String = super.folderPath + "changeSignature/inScala/"

  override def findTargetElement: PsiMember = {
    val element = new ScalaChangeSignatureHandler().findTargetMember(getFile, getEditor)
    element match {
      case method: ScMethodLike => method
      case _ => fail("<caret> is not on method name").asInstanceOf[Nothing]
    }
  }

  override def processor(newVisibility: String,
                         newName: String,
                         newReturnType: String,
                         newParams: => Seq[Seq[ParameterInfo]]): ChangeSignatureProcessorBase = {
    scalaProcessor(newVisibility, newName, newReturnType, newParams, isAddDefaultValue)
  }

  override def mainFileName(testName: String): String = testName + ".scala"
  override def mainFileAfterName(testName: String): String = testName + "_after.scala"
  override def secondFileName(testName: String): String = null
  override def secondFileAfterName(testName: String): String = null

  protected def parameterInfo(name: String, oldIdx: Int, tpe: ScType, defVal: String = "", isRep: Boolean = false, isByName: Boolean = false) = {
    new ScalaParameterInfo(name, oldIdx, tpe, getProject, isRep, isByName, defVal)
  }
}

final class ChangeSignatureInScalaTest extends ChangeSignatureInScalaTestBase {
  def testVisibility(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", -1, Int, "1"))
    doTest("protected", "foo", null, Seq(params))
  }

  def testAddRepeatedParam(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("b", 1, Boolean),
      parameterInfo("xs", -1, Int, isRep = true, defVal = "1"))
    doTest(null, "foo", null, Seq(params))
  }

  def testAddRepeatedWithoutDefault(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("xs", -1, Int, isRep = true))
    doTest(null, "foo", null, Seq(params))
  }

  def testMakeRepeatedParam(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("b", 1, Boolean, isRep = true))
    doTest(null, "foo", null, Seq(params))
  }

  def testRemoveRepeatedParam(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("b", 1, Boolean))
    doTest(null, "foo", null, Seq(params))
  }

  def testNoDefaultArg(): Unit = {
    isAddDefaultValue = true
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("j", -1, Int))
    doTest(null, "foo", null, Seq(params))
  }

  def testNoDefaultArg2(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("j", -1, Int))
    doTest(null, "foo", null, Seq(params))
  }

  def testAnonFunWithDefaultArg(): Unit = {
    isAddDefaultValue = true
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("j", -1, Int, "0"))
    doTest(null, "foo", null, Seq(params))
  }

  def testAnonFunModifyCall(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("j", -1, Int, "0"))
    doTest(null, "foo", null, Seq(params))
  }

  def testAnonFunManyParams(): Unit = {
    isAddDefaultValue = true
    val params = Seq(parameterInfo("j", 1, Int),
      parameterInfo("b", 2, Boolean),
      parameterInfo("s", -1, AnyRef, "\"\""))
    doTest(null, "foo", null, Seq(params))
  }

  def testLocalFunction(): Unit = {
    isAddDefaultValue = true
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("s", -1, Boolean, "true"))
    val settings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))

    doTest(null, "local", null, Seq(params), settings = TypeAnnotationSettings.noTypeAnnotationForLocal(settings))
  }

  def testImported(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", -1, Int, "0"))
    doTest(null, "foo", null, Seq(params))
  }

  def testAddClauseConstructorVararg(): Unit = {
    isAddDefaultValue = false
    val params = Seq(Seq(parameterInfo("b", 0, Boolean)), Seq(parameterInfo("x", -1, Int, "10"), parameterInfo("i", 1, Int, isRep = true)))
    doTest(null, "AddClauseConstructorVararg", null, params)
  }

  def testCaseClass(): Unit = {
    isAddDefaultValue = true
    val params = Seq(
      Seq(parameterInfo("ii", 0, Int), parameterInfo("argss", 2, Int, isRep = true)),
      Seq(parameterInfo("cc", 1, Char), parameterInfo("b", -1, Boolean, "true"))
    )
    doTest(null, "CClass", null, params)
  }

  def testSelfInvocation(): Unit = {
    isAddDefaultValue = false
    val params = Seq(
      Seq(parameterInfo(name = "d", oldIdx = -1, tpe = Double, defVal = "1.23")),
      Seq(parameterInfo(name = "foo", oldIdx = 1, tpe = Boolean), parameterInfo(name = "x", oldIdx = 0, tpe = Int)),
    )
    doTest(newName = "Color", newParams = params, newVisibility = null, newReturnType = null)
  }
}

final class ChangeSignatureInScalaTest_Scala3 extends ChangeSignatureInScalaTestBase {
  override def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  override def folderPath: String = super.folderPath + "scala3/"

  // SCL-22597
  def testRenameParamWithScala3Wildcard(): Unit = {
    isAddDefaultValue = false
    doTest(newVisibility = null, newName = "bar", newReturnType = null,
      settings = TypeAnnotationSettings.noTypeAnnotationForPublic(ScalaCodeStyleSettings.getInstance(getProject)),
      newParams = {
        targetMethod match {
          case fun: ScFunction =>
            fun.parameters match {
              case Seq(Typeable(oldParamType)) =>
                Seq(Seq(parameterInfo("boo", 0, oldParamType)))
              case _ =>
                fail("Expected a single parameter function").asInstanceOf[Nothing]
            }
          case _ => fail("Expected a function").asInstanceOf[Nothing]
        }
      })
  }
}
