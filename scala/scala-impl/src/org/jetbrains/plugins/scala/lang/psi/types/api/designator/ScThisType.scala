package org.jetbrains.plugins.scala
package lang
package psi
package types
package api
package designator

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

/**
  * This type means type, which depends on place, where you want to get expression type.
  * For example
  *
  * class A       {
  * def foo: this.type = this
  * }
  *
  * class B extneds A       {
  * val z = foo // <- type in this place is B.this.type, not A.this.type
  * }
  *
  * So when expression is typed, we should replace all such types be return value.
  */
final case class ScThisType(override val element: ScTemplateDefinition) extends DesignatorOwner with LeafType {
  element.getClass
  //throw NPE if clazz is null...

  override val isSingleton = true

  override private[types] def designatorSingletonType = None

  override def equivInner(`type`: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
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
