package org.jetbrains.plugins.scala.lang.refactoring.introduceParameter

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.refactoring.introduceParameter.{IntroduceParameterData, JavaExpressionWrapper}
import it.unimi.dsi.fastutil.ints.{IntList, IntLists}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo

import scala.collection.immutable.ArraySeq

case class ScalaIntroduceParameterData(methodLike: ScMethodLike,
                                       methodToSearchFor: ScMethodLike,
                                       elems: Iterable[PsiElement],
                                       paramName: String,
                                       possibleTypes: ArraySeq[ScType],
                                       tp: ScType,
                                       occurrences: Seq[TextRange],
                                       mainOcc: TextRange,
                                       replaceAll: Boolean,
                                       defaultArg: String,
                                       functionalArgParams: Option[String] = None)
  extends IntroduceParameterData {

  override def getParameterListToRemove: IntList = IntLists.emptyList()

  override def getForcedType: PsiType = tp.toPsiType

  override def isGenerateDelegate: Boolean = false

  override def isDeclareFinal: Boolean = false

  override def getReplaceFieldsWithGetters: Int = 0

  override def getParameterName: String = paramName

  override def getParameterInitializer =
    new JavaExpressionWrapper(
      JavaPsiFacade.getElementFactory(methodLike.getProject).createExpressionFromText(getParameterName, elems.head.getContext)
    )

  override def getMethodToSearchFor: PsiMethod = methodToSearchFor

  override def getMethodToReplaceIn: PsiMethod = methodLike

  override def getProject: Project = methodLike.getProject
}

object isIntroduceParameter {
  def unapply(scInfo: ScalaChangeInfo): Option[ScalaIntroduceParameterData] = {
    scInfo.introducedParameterData
  }
}