package org.jetbrains.plugins.scala.lang.psi.types.api.designator

import com.intellij.psi.{PsiClass, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{PsiTypeParameterExt, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScExistentialArgument, ScExistentialType, ScType, ScTypeExt, ScUndefinedSubstitutor}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.collection.mutable.ArrayBuffer

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
              case Success(tp, _) =>
                tp match {
                  case ParameterizedType(des, typeArgs) =>
                    val taArgs = ta.typeParameters
                    if (taArgs.length == typeArgs.length && taArgs.zip(typeArgs).forall {
                      case (tParam: ScTypeParam, TypeParameterType(_, _, _, param)) if tParam == param => true
                      case _ => false
                    }) return Some(AliasType(ta, Success(des, Some(element)), Success(des, Some(element))))
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
        val args: ArrayBuffer[ScExistentialArgument] = new ArrayBuffer[ScExistentialArgument]()
        val genericSubst = ScalaPsiUtil.
          typesCallSubstitutor(ta.typeParameters.map(_.nameAndId),
            ta.typeParameters.map(tp => {
              val name = tp.name + "$$"
              val ex = new ScExistentialArgument(name, Nil, Nothing, Any)
              args += ex
              ex
            }))
        Some(AliasType(ta, ta.lowerBound.map(scType => ScExistentialType(genericSubst.subst(scType), args.toList)),
          ta.upperBound.map(scType => ScExistentialType(genericSubst.subst(scType), args.toList))))
      case _ => None
    }
  }

  def getValType: Option[StdType] = element match {
    case clazz: PsiClass => StdType.QualNameToType.get(clazz.qualifiedName)
    case _ => None
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
    (this, r) match {
      case (ScDesignatorType(a: ScTypeAliasDefinition), _) =>
        (a.aliasedType match {
          case Success(tp, _) => tp
          case _ => return (false, uSubst)
        }).equiv(r, uSubst, falseUndef)
      case (_, ScDesignatorType(element1)) =>
        if (ScEquivalenceUtil.smartEquivalence(element, element1)) return (true, uSubst)
        if (isSingleton && r.asInstanceOf[DesignatorOwner].isSingleton) {
          element match {
            case o: ScObject =>
            case bind: ScTypedDefinition if bind.isStable =>
              bind.getType(TypingContext.empty) match {
                case Success(tp: DesignatorOwner, _) if tp.isSingleton =>
                  return tp.equiv(r, uSubst, falseUndef)
                case _ =>
              }
            case _ =>
          }
          element1 match {
            case o: ScObject =>
            case bind: ScTypedDefinition if bind.isStable =>
              bind.getType(TypingContext.empty) match {
                case Success(tp: DesignatorOwner, _) if tp.isSingleton =>
                  return tp.equiv(this, uSubst, falseUndef)
                case _ =>
              }
          }
        }
        (false, uSubst)
      case _ => (false, uSubst)
    }
  }

  override def visitType(visitor: TypeVisitor) = visitor.visitDesignatorType(this)
}

object ScDesignatorType {
  def unapply(`type`: ScType): Option[PsiNamedElement] = `type` match {
    case designatorType: ScDesignatorType => Some(designatorType.element)
    case _ => None
  }
}
