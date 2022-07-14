package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.impl.light.{LightReferenceListBuilder, LightTypeParameter}
import com.intellij.psi.{PsiClassType, PsiReferenceList}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.types.api.StdType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType}

private class ScLightTypeParam(scTypeParam: ScTypeParam, subst: ScSubstitutor)
  extends LightTypeParameter(scTypeParam) {

  @volatile
  private var extendsList: PsiReferenceList = _

  override def getExtendsList: PsiReferenceList = {
    if (extendsList == null) {
      extendsList = computeExtendsList
    }
    extendsList
  }

  private def computeExtendsList: PsiReferenceList = {
    val refList = new LightReferenceListBuilder(scTypeParam.getManager, PsiReferenceList.Role.EXTENDS_BOUNDS_LIST)

    def addReference(tp: ScType): Unit = {
      tp.toPsiType match {
        case classType: PsiClassType => refList.addReference(classType)
        case _ =>
      }
    }

    scTypeParam.upperBound.map(subst) match {
      case Right(tp: ScCompoundType) => tp.components.foreach(addReference)
      case Right(_: StdType)         => ()
      case Right(scType)             => addReference(scType)
      case _                         => ()
    }

    refList
  }
}
