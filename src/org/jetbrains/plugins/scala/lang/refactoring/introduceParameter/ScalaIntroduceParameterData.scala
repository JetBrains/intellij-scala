package org.jetbrains.plugins.scala.lang.refactoring.introduceParameter

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.refactoring.introduceParameter.{IntroduceParameterData, JavaExpressionWrapper}
import gnu.trove.TIntArrayList
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo

/**
 * @author Nikolay.Tropin
 */
case class ScalaIntroduceParameterData(methodLike: ScMethodLike,
                                       methodToSearchFor: ScMethodLike,
                                       elems: Seq[PsiElement],
                                       paramName: String,
                                       possibleTypes: Array[ScType],
                                       tp: ScType,
                                       occurrences: Array[TextRange],
                                       mainOcc: TextRange,
                                       replaceAll: Boolean,
                                       defaultArg: String,
                                       functionalArgParams: Option[String] = None) extends IntroduceParameterData {


  def getParametersToRemove: TIntArrayList = new TIntArrayList()

  def getForcedType: PsiType = tp.toPsiType(getProject, methodLike.getResolveScope)

  def getScalaForcedType: ScType = tp

  def isGenerateDelegate: Boolean = false

  def isDeclareFinal: Boolean = false

  def getReplaceFieldsWithGetters: Int = 0

  def getParameterName: String = paramName

  def getParameterInitializer =
    new JavaExpressionWrapper(
      JavaPsiFacade.getElementFactory(methodLike.getProject).createExpressionFromText(getParameterName, elems.head.getContext)
    )

  def getMethodToSearchFor: PsiMethod = methodToSearchFor

  def getMethodToReplaceIn: PsiMethod = methodLike

  def getProject: Project = methodLike.getProject
}

object isIntroduceParameter {
  def unapply(scInfo: ScalaChangeInfo): Option[ScalaIntroduceParameterData] = {
    scInfo.introducedParameterData
  }
}