package org.jetbrains.plugins.scala.lang.psi.types.api
package designator

import com.intellij.psi.{PsiClass, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, ConstraintSystem, ConstraintsResult, LeafType, ScExistentialArgument, ScExistentialType, ScType, ScalaTypeVisitor}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.smartEquivalence

/**
  * This type means normal designator type.
  * It can be whether singleton type (v.type) or simple type (java.lang.String).
  * element can be any stable element, class, value or type alias
  */
final case class ScDesignatorType(override val element: PsiNamedElement) extends DesignatorOwner with LeafType {

  private var static = false

  private def setStatic(): Unit = static = true
  def isStatic: Boolean = static

  override protected def calculateAliasType: Option[AliasType] = {
    element match {
      case ta: ScTypeAlias if ta.typeParameters.isEmpty =>
        Some(AliasType(ta, ta.lowerBound, ta.upperBound))
      case ta: ScTypeAlias => //higher kind case
        ta match {
          case ta: ScTypeAliasDefinition => //hack for simple cases, it doesn't cover more complicated examples
            ta.aliasedType match {
              case Right(tp) if tp == this => // recursive type alias
                return Some(AliasType(ta, Right(this), Right(this)))
              case Right(tp) =>
                tp match {
                  case ParameterizedType(des, typeArgs) =>
                    val taArgs = ta.typeParameters
                    if (taArgs.length == typeArgs.length && taArgs.zip(typeArgs).forall {
                      case (tParam: ScTypeParam, TypeParameterType.ofPsi(param)) if tParam == param => true
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
        Some(AliasType(ta,
          ta.lowerBound.map(scType => ScExistentialType(genericSubst(scType))),
          ta.upperBound.map(scType => ScExistentialType(genericSubst(scType))))
        )
      case _ => None
    }
  }

  def getValType: Option[StdType] = element match {
    case clazz: PsiClass if !clazz.isInstanceOf[ScObject] =>
      clazz.getProject.stdTypes.QualNameToType.get(clazz.qualifiedName)
    case _ => None
  }

  override def equivInner(`type`: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
    def equivSingletons(left: DesignatorOwner, right: DesignatorOwner) =
      left.designatorSingletonType.filter {
        case designatorOwner: DesignatorOwner if designatorOwner.isSingleton => true
        case _                                                               => false
      }.map(
        _.equiv(right, constraints, falseUndef)
      )

    (`type` match {
      case rhs: ScTypePolymorphicType =>
        ScEquivalenceUtil.isDesignatorEqiuivalentToPolyType(this, rhs, constraints, falseUndef)
      case _ if element.isInstanceOf[ScTypeAliasDefinition] =>
        element.asInstanceOf[ScTypeAliasDefinition].aliasedType.toOption.map(
          _.equiv(`type`, constraints, falseUndef)
        )
      case ScDesignatorType(thatElement) if smartEquivalence(element, thatElement) =>
        Option(constraints)
      case that: DesignatorOwner if isSingleton && that.isSingleton =>
        equivSingletons(this, that) match {
          case None   => equivSingletons(that, this)
          case result => result
        }
      case _ => None
    }).getOrElse(ConstraintsResult.Left)
  }

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitDesignatorType(this)
}

object ScDesignatorType {

  def static(element: PsiNamedElement): ScDesignatorType = {
    val des = ScDesignatorType(element)
    des.setStatic()
    des
  }
}
