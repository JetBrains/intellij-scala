package org.jetbrains.plugins.scala.lang.psi.types.api.designator

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeVisitor
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType, ScTypeExt, ScUndefinedSubstitutor}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

/**
  * @author adkozlov
  */

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
case class ScThisType(element: ScTemplateDefinition) extends DesignatorOwner {
  element.getClass
  //throw NPE if clazz is null...

  override val isSingleton = true

  override private[types] def designatorSingletonType = None

  override def equivInner(`type`: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    (this, `type`) match {
      case (ScThisType(clazz1), ScThisType(clazz2)) =>
        (ScEquivalenceUtil.areClassesEquivalent(clazz1, clazz2), substitutor)
      case (ScThisType(obj1: ScObject), ScDesignatorType(obj2: ScObject)) =>
        (ScEquivalenceUtil.areClassesEquivalent(obj1, obj2), substitutor)
      case (_, ScDesignatorType(_: ScObject)) =>
        (false, substitutor)
      case (_, ScDesignatorType(typed: ScTypedDefinition)) if typed.isStable =>
        typed.`type`() match {
          case Right(tp: DesignatorOwner) if tp.isSingleton =>
            this.equiv(tp, substitutor, falseUndef)
          case _ =>
            (false, substitutor)
        }
      case (_, ScProjectionType(_, _: ScObject)) => (false, substitutor)
      case (_, p@ScProjectionType(tp, elem: ScTypedDefinition)) if elem.isStable =>
        elem.`type`() match {
          case Right(singleton: DesignatorOwner) if singleton.isSingleton =>
            val newSubst = p.actualSubst.followed(ScSubstitutor(tp))
            this.equiv(newSubst.subst(singleton), substitutor, falseUndef)
          case _ => (false, substitutor)
        }
      case _ => (false, substitutor)
    }
  }

  override def visitType(visitor: TypeVisitor): Unit = visitor.visitThisType(this)
}
