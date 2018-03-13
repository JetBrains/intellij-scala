package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import java.util

import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.MethodDescriptor
import com.intellij.refactoring.changeSignature.MethodDescriptor.ReadWriteOption
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring._

import scala.collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 2014-08-29
 */
class ScalaMethodDescriptor(val fun: ScMethodLike) extends MethodDescriptor[ScalaParameterInfo, String] {
  override def getName: String = fun match {
    case fun: ScFunction =>
      if (fun.isConstructor) fun.containingClass.name
      else fun.name
    case pc: ScPrimaryConstructor => pc.containingClass.name
    case _ => ""
  }

  override def canChangeName: Boolean = !fun.isConstructor

  override def canChangeVisibility: Boolean = !fun.isLocal

  val parameters = parametersInner
  override def getParameters: util.List[ScalaParameterInfo] = parameters.flatten.asJava

  override def getParametersCount: Int = parameters.flatten.size

  override def canChangeReturnType: ReadWriteOption =
    if (fun.isConstructor) ReadWriteOption.None else ReadWriteOption.ReadWrite

  override def canChangeParameters: Boolean = true

  override def getMethod: PsiElement = fun

  override def getVisibility: String = fun.getModifierList.accessModifier.fold("")(_.getText)

  def returnTypeText: String = fun match {
    case f: ScFunction => f.returnType.getOrAny.codeText
    case _ => ""
  }

  protected def parametersInner: Seq[Seq[ScalaParameterInfo]] = ScalaParameterInfo.allForMethod(fun)
}
