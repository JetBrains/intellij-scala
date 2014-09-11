package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import java.util

import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.MethodDescriptor
import com.intellij.refactoring.changeSignature.MethodDescriptor.ReadWriteOption
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

import scala.collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 2014-08-29
 */
class ScalaMethodDescriptor(val fun: ScFunction) extends MethodDescriptor[ScalaParameterInfo, String] {
  override def getName: String = fun.name

  override def canChangeName: Boolean = !fun.isConstructor

  override def canChangeVisibility: Boolean = !fun.isLocal

  val parameters = fun.parameters.map(new ScalaParameterInfo(_))
  override def getParameters: util.List[ScalaParameterInfo] = parameters.asJava

  override def getParametersCount: Int = fun.parameters.size

  override def canChangeReturnType: ReadWriteOption =
    if (fun.isConstructor) ReadWriteOption.None else ReadWriteOption.ReadWrite

  override def canChangeParameters: Boolean = true

  override def getMethod: PsiElement = fun

  override def getVisibility: String = fun.getModifierList.accessModifier.fold("")(_.getText)

  def returnTypeText = fun.returnType.getOrAny.presentableText
}
