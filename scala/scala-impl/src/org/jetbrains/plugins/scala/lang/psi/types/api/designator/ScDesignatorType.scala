package org.jetbrains.plugins.scala.lang.psi.types.api
package designator

import com.intellij.psi.{PsiClass, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, ConstraintSystem, ConstraintsResult, Context, LeafType, ScType, ScalaTypeVisitor}
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

  override protected def calculateAliasType(implicit context: Context): Option[AliasType] = calculateAliasTypeAux(element, ScSubstitutor.empty)

  def getValType: Option[StdType] = element match {
    case clazz: PsiClass if !clazz.isInstanceOf[ScObject] =>
      projectContext.stdTypes.QualNameToType.get(clazz.qualifiedName)
    case _ => None
  }

  override def equivInner(`type`: ScType, constraints: ConstraintSystem, falseUndef: Boolean)(implicit context: Context): ConstraintsResult = {
    def equivSingletons(left: DesignatorOwner, right: DesignatorOwner) =
      left.designatorSingletonType.filter {
        case designatorOwner: DesignatorOwner if designatorOwner.isSingleton => true
        case _                                                               => false
      }.map(
        _.equiv(right, constraints, falseUndef)
      )

    (`type` match {
      case rhs: ScTypePolymorphicType =>
        ScEquivalenceUtil.isTypeConstructorEquivalentToPolyType(this, rhs, constraints, falseUndef)
      case _ if element.is[ScTypeAliasDefinition] =>
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
