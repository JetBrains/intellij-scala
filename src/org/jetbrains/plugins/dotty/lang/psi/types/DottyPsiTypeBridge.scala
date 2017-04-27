package org.jetbrains.plugins.dotty.lang.psi.types

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, ScalaArrayOf}
import org.jetbrains.plugins.scala.lang.psi.types.result.Success

import scala.collection.JavaConversions._


/**
  * @author adkozlov
  */
trait DottyPsiTypeBridge extends api.PsiTypeBridge {
  typeSystem: api.TypeSystem =>

  override def toScType(`type`: PsiType,
                        treatJavaObjectAsAny: Boolean)
                       (implicit visitedRawTypes: Set[PsiClass],
                        paramTopLevel: Boolean): ScType = `type` match {
    case _: PsiClassType => Any
    case _: PsiWildcardType => Any
    case disjunctionType: PsiDisjunctionType =>
      DottyOrType(disjunctionType.getDisjunctions.map {
        toScType(_, treatJavaObjectAsAny)
      })
    case _ => super.toScType(`type`, treatJavaObjectAsAny)
  }

  override def toPsiType(`type`: ScType, noPrimitives: Boolean, skolemToWildcard: Boolean)
                        (implicit elementScope: ElementScope): PsiType = {
    def createComponent: ScType => PsiType =
      toPsiType(_, noPrimitives, skolemToWildcard)

    `type` match {
      case ScDesignatorType(clazz: PsiClass) => createType(clazz)
      case projectionType: ScProjectionType =>
        projectionType.actualElement match {
          case syntheticClass: ScSyntheticClass => toPsiType(syntheticClass.stdType)
          case clazz: PsiClass => createType(clazz, raw = true)
          case definition: ScTypeAliasDefinition => definition.aliasedType match {
            case Success(result, _) => createComponent(result)
            case _ => createJavaObject
          }
          case _ => createJavaObject
        }
      case refinedType@DottyRefinedType(ScDesignatorType(clazz: PsiClass), _, _) if clazz.qualifiedName == "scala.Array" =>
        refinedType.typeArguments match {
          case Seq(designator) => new PsiArrayType(createComponent(designator))
          case seq => createType(clazz,
            seq.zip(clazz.getTypeParameters)
              .foldLeft(PsiSubstitutor.EMPTY) {
                case (substitutor, (scType, typeParameter)) => substitutor.put(typeParameter,
                  toPsiType(scType, noPrimitives = true, skolemToWildcard = true))
              })
        }
      case ScalaArrayOf(arg) => new PsiArrayType(toPsiType(arg))
      case _ => super.toPsiType(`type`, noPrimitives, skolemToWildcard)
    }
  }
}
