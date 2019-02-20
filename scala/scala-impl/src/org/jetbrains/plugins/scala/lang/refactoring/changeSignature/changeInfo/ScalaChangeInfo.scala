package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature.changeInfo

import com.intellij.lang.Language
import com.intellij.psi._
import com.intellij.refactoring.util.CanonicalTypes
import com.intellij.refactoring.util.CanonicalTypes.Type
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScalaConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.ScalaParameterInfo
import org.jetbrains.plugins.scala.lang.refactoring.introduceParameter.ScalaIntroduceParameterData

import scala.beans.BeanProperty

/**
 * Nikolay.Tropin
 * 2014-08-28
 */
case class ScalaChangeInfo(newVisibility: String,
                           function: ScMethodLike,
                           @BeanProperty newName: String,
                           newType: ScType,
                           newParams: Seq[Seq[ScalaParameterInfo]],
                           isAddDefaultArgs: Boolean,
                           addTypeAnnotation: Option[Boolean] = None)
        extends ScalaChangeInfoBase(newParams.flatten.toArray)
        with UnsupportedJavaInfo with VisibilityChangeInfo with ParametersChangeInfo {

  private val project = function.getProject
  private implicit val elementScope = ElementScope(project)

  private var myMethod: PsiMethod = function

  //used in introduce parameter refactoring
  var introducedParameterData: Option[ScalaIntroduceParameterData] = None

  override def getValue(i: Int, callExpression: PsiCallExpression): PsiExpression =
    getNewParameters()(i).getValue(callExpression)

  override def getNewReturnType: Type =
    Option(newType).map {
      _.toPsiType
    }.map {
      CanonicalTypes.createTypeWrapper
    }.orNull

  override val getOldName: String = function match {
    case ScalaConstructor.in(c) => c.name
    case fun: ScFunction        => fun.name
    case _                      => newName
  }

  override def getNewNameIdentifier: PsiIdentifier = JavaPsiFacade.getElementFactory(function.getProject).createIdentifier(newName)

  override def getMethod: PsiMethod = myMethod

  override def updateMethod(psiMethod: PsiMethod): Unit = {
    myMethod = psiMethod
  }

  override val isNameChanged: Boolean = getOldName != newName

  override val isGenerateDelegate: Boolean = false

  override val getLanguage: Language = ScalaLanguage.INSTANCE

  override val isReturnTypeChanged: Boolean = function match {
    case f: ScFunction => f.returnType.toOption.map(_.canonicalText) != Option(newType).map(_.canonicalText)
    case _ => false
  }
}
