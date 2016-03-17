package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature.changeInfo

import com.intellij.lang.Language
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.util.CanonicalTypes
import com.intellij.refactoring.util.CanonicalTypes.Type
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
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
                           isAddDefaultArgs: Boolean)
        extends ScalaChangeInfoBase(newParams.flatten.toArray)
        with UnsupportedJavaInfo with VisibilityChangeInfo with ParametersChangeInfo {

  val project = function.getProject
  private var myMethod: PsiMethod = function
  private def psiType = {
    if (newType != null) newType.toPsiType(project, GlobalSearchScope.allScope(project))
    else null
  }

  //used in introduce parameter refactoring
  var introducedParameterData: Option[ScalaIntroduceParameterData] = None

  override def getValue(i: Int, callExpression: PsiCallExpression): PsiExpression =
    getNewParameters()(i).getValue(callExpression)

  override def getNewReturnType: Type = if (newType != null) CanonicalTypes.createTypeWrapper(psiType) else null

  override val getOldName: String = function match {
    case fun: ScFunction =>
      if (fun.isConstructor) fun.containingClass.name
      else fun.name
    case pc: ScPrimaryConstructor => pc.containingClass.name
    case _ => newName
  }

  override def getNewNameIdentifier = JavaPsiFacade.getElementFactory(project).createIdentifier(newName)

  override def getMethod: PsiMethod = myMethod

  override def updateMethod(psiMethod: PsiMethod): Unit = {
    myMethod = psiMethod
  }

  override val isNameChanged: Boolean = getOldName != newName

  override val isGenerateDelegate: Boolean = false

  override val getLanguage: Language = ScalaFileType.SCALA_LANGUAGE

  override val isReturnTypeChanged: Boolean = function match {
    case f: ScFunction => f.returnType.toOption.map(_.canonicalText) != Option(newType).map(_.canonicalText)
    case _ => false
  }
}
