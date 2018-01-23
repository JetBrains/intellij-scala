package org.jetbrains.plugins.scala.lang.psi.types.api.designator

import com.intellij.psi.{PsiClass, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.{ScExistentialArgument, ScExistentialType, ScSubstitutor, ScType, ScTypeExt, ScUndefinedSubstitutor}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.smartEquivalence

/**
  * This type means normal designator type.
  * It can be whether singleton type (v.type) or simple type (java.lang.String).
  * element can be any stable element, class, value or type alias
  */
case class ScDesignatorType(element: PsiNamedElement, isStatic: Boolean = false) extends DesignatorOwner {

  override protected def isAliasTypeInner: Option[AliasType] = {
    element match {
      case ta: ScTypeAlias if ta.typeParameters.isEmpty =>
        Some(AliasType(ta, ta.lowerBound, ta.upperBound))
      case ta: ScTypeAlias => //higher kind case
        ta match {
          case ta: ScTypeAliasDefinition => //hack for simple cases, it doesn't cover more complicated examples
            ta.aliasedType match {
              case Right(tp) =>
                tp match {
                  case ParameterizedType(des, typeArgs) =>
                    val taArgs = ta.typeParameters
                    if (taArgs.length == typeArgs.length && taArgs.zip(typeArgs).forall {
                      case (tParam: ScTypeParam, TypeParameterType(_, _, _, param)) if tParam == param => true
                      case _ => false
                    }) return Some(AliasType(ta, Right(des), Right(des)))
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
        val existentialArgs = ta.typeParameters
          .map(tp => ScExistentialArgument(tp.name + "$$", Nil, Nothing, Any))
          .toList

        val genericSubst = ScSubstitutor.bind(ta.typeParameters, existentialArgs)
        Some(AliasType(ta, ta.lowerBound.map(scType => ScExistentialType(genericSubst.subst(scType), existentialArgs)),
          ta.upperBound.map(scType => ScExistentialType(genericSubst.subst(scType), existentialArgs))))
      case _ => None
    }
  }

  def getValType: Option[StdType] = element match {
    case clazz: PsiClass if !clazz.isInstanceOf[ScObject] =>
      projectContext.stdTypes.QualNameToType.get(clazz.qualifiedName)
    case _ => None
  }

  override def equivInner(`type`: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    def equivSingletons(left: DesignatorOwner, right: DesignatorOwner) = left.designatorSingletonType.filter {
      case designatorOwner: DesignatorOwner if designatorOwner.isSingleton => true
      case _ => false
    }.map {
      _.equiv(right, substitutor, falseUndef)
    }

    (element match {
      case definition: ScTypeAliasDefinition =>
        definition.aliasedType.toOption.map {
          _.equiv(`type`, substitutor, falseUndef)
        }
      case _ =>
        `type` match {
          case ScDesignatorType(thatElement) if smartEquivalence(element, thatElement) =>
            Some((true, substitutor))
          case that: DesignatorOwner if isSingleton && that.isSingleton =>
            equivSingletons(this, that) match {
              case None => equivSingletons(that, this)
              case result => result
            }
          case _ => None
        }
    }).getOrElse {
      (false, substitutor)
    }
  }

  override def visitType(visitor: TypeVisitor): Unit = visitor.visitDesignatorType(this)
}

object ScDesignatorType {
  def unapply(`type`: ScType): Option[PsiNamedElement] = `type` match {
    case designatorType: ScDesignatorType => Some(designatorType.element)
    case _ => None
  }
}
