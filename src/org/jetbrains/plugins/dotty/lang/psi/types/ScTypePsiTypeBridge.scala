package org.jetbrains.plugins.dotty.lang.psi.types

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.Any
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Success

import scala.collection.JavaConversions._
import scala.collection.immutable.HashSet

/**
  * @author adkozlov
  */
object ScTypePsiTypeBridge extends api.ScTypePsiTypeBridge {
  override implicit lazy val typeSystem = DottyTypeSystem

  override def toScType(`type`: PsiType,
                        treatJavaObjectAsAny: Boolean)
                       (implicit visitedRawTypes: HashSet[PsiClass],
                        paramTopLevel: Boolean): ScType = `type` match {
    case classType: PsiClassType => Any
    case wildcardType: PsiWildcardType => Any
    case disjunctionType: PsiDisjunctionType =>
      DottyOrType(disjunctionType.getDisjunctions.map {
        toScType(_, treatJavaObjectAsAny)
      })
    case _ => super.toScType(`type`, treatJavaObjectAsAny)
  }

  override def toPsiType(`type`: ScType, project: Project, scope: GlobalSearchScope, noPrimitives: Boolean, skolemToWildcard: Boolean): PsiType = {
    def createComponent: ScType => PsiType =
      toPsiType(_, project, scope, noPrimitives, skolemToWildcard)

    `type` match {
      case ScDesignatorType(clazz: PsiClass) => createType(clazz, project)
      case projectionType: ScProjectionType =>
        projectionType.actualElement match {
          case syntheticClass: ScSyntheticClass => toPsiType(syntheticClass.t, project, scope)
          case clazz: PsiClass => createType(clazz, project, raw = true)
          case definition: ScTypeAliasDefinition => definition.aliasedType match {
            case Success(result, _) => createComponent(result)
            case _ => createJavaObject(project, scope)
          }
        }
      case refinedType@DottyRefinedType(ScDesignatorType(clazz: PsiClass), _, _) if clazz.qualifiedName == "scala.Array" =>
        refinedType.typeArguments match {
          case Seq(designator) => new PsiArrayType(createComponent(designator))
          case seq => JavaPsiFacade.getInstance(project).getElementFactory.createType(clazz,
            seq.zip(clazz.getTypeParameters)
              .foldLeft(PsiSubstitutor.EMPTY) {
                case (substitutor, (scType, typeParameter)) => substitutor.put(typeParameter,
                  toPsiType(scType, project, scope, noPrimitives = true, skolemToWildcard = true))
              })
        }
      case _ => super.toPsiType(`type`, project, scope, noPrimitives, skolemToWildcard)
    }
  }
}
