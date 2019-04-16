package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.impl.light.{LightReferenceListBuilder, LightTypeParameter}
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.intellij.psi.{PsiReferenceList, PsiSubstitutor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, StdType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
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
      val psiClassType = tp match {
        case tpt: TypeParameterType => Some(new PsiImmediateClassType(tpt.psiTypeParameter, PsiSubstitutor.EMPTY))
        case ExtractClass(clazz)    => Some(new PsiImmediateClassType(clazz, PsiSubstitutor.EMPTY))
        case _                      => None
      }
      psiClassType.foreach(refList.addReference)
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
