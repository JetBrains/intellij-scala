package org.jetbrains.plugins.dotty.lang.psi.types

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.Any

import scala.collection.JavaConversions._
import scala.collection.immutable.HashSet

/**
  * @author adkozlov
  */
object ScTypePsiTypeBridge extends api.ScTypePsiTypeBridge {
  override implicit lazy val typeSystem = DottyTypeSystem

  override def toScType(`type`: PsiType,
                        project: Project,
                        scope: GlobalSearchScope,
                        visitedRawTypes: HashSet[PsiClass],
                        paramTopLevel: Boolean,
                        treatJavaObjectAsAny: Boolean): ScType = {
    def createComponent: PsiType => ScType =
      toScType(_, project, scope, visitedRawTypes, paramTopLevel, treatJavaObjectAsAny)

    `type` match {
      case classType: PsiClassType => Any
      case wildcard: PsiWildcardType => Any
      case wildcard: PsiCapturedWildcardType => Any
      case disjunction: PsiDisjunctionType => DottyOrType(disjunction.getDisjunctions.map(createComponent))
      case intersection: PsiIntersectionType => DottyAndType(intersection.getConjuncts.map(createComponent))
      case _ => super.toScType(`type`, project, scope, visitedRawTypes, paramTopLevel, treatJavaObjectAsAny)
    }
  }
}
