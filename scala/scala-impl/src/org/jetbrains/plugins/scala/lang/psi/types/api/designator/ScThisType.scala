package org.jetbrains.plugins.scala.lang.psi.types.api
package designator

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, Context, LeafType, ScType, ScalaTypeVisitor}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

final case class ScThisType(override val element: ScTemplateDefinition) extends DesignatorOwner with LeafType {
  element.getClass
  //throw NPE if clazz is null...

  override val isSingleton = true

  override private[types] def designatorSingletonType = None

  override def equivInner(`type`: ScType, constraints: ConstraintSystem, falseUndef: Boolean)(implicit context: Context): ConstraintsResult = {
    (this, `type`) match {
      case (ScThisType(clazz1), ScThisType(clazz2)) =>
        if (ScEquivalenceUtil.areClassesEquivalent(clazz1, clazz2)) constraints
        else ConstraintsResult.Left
      case (ScThisType(obj1: ScObject), ScDesignatorType(obj2: ScObject)) =>
        if (ScEquivalenceUtil.areClassesEquivalent(obj1, obj2)) constraints
        else ConstraintsResult.Left
      case (_, ScDesignatorType(_: ScObject)) =>
        ConstraintsResult.Left
      case (_, ScDesignatorType(typed: ScTypedDefinition)) if typed.isStable =>
        typed.`type`() match {
          case Right(tp: DesignatorOwner) if tp.isSingleton =>
            this.equiv(tp, constraints, falseUndef)
          case _ =>
            ConstraintsResult.Left
        }
      case (_, ScProjectionType(_, _: ScObject)) => ConstraintsResult.Left
      case (_, p@ScProjectionType(tp, elem: ScTypedDefinition)) if elem.isStable =>
        elem.`type`() match {
          case Right(singleton: DesignatorOwner) if singleton.isSingleton =>
            val newSubst = p.actualSubst.followed(ScSubstitutor(tp))
            this.equiv(newSubst(singleton), constraints, falseUndef)
          case _ => ConstraintsResult.Left
        }
      case _ => ConstraintsResult.Left
    }
  }

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitThisType(this)
}
