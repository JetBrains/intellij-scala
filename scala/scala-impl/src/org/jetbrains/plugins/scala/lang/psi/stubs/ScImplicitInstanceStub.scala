package org.jetbrains.plugins.scala.lang.psi.stubs

import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScCompoundTypeElement, ScDesugarizableTypeElement, ScInfixTypeElement, ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

import scala.annotation.tailrec

trait ScImplicitInstanceStub {

  /**
   * Class name of a return type of implicit function or val,
   * or first super class of an implicit object.
   * It is in the same form as written in source or decompiled class file, so it may have prefix.
   */
  def implicitType: Option[String]
}

object ScImplicitInstanceStub {
  def implicitType(psi: ScModifierListOwner, typeElement: => Option[ScTypeElement]): Option[String] = {
    if (psi.getModifierList.isImplicit)
      typeElement.flatMap(mainClassName)
    else None
  }

  def mainSuperClassName(obj: ScObject): Option[String] = {
    obj.extendsBlock.templateParents.toSeq
      .flatMap(_.typeElements.map(_.getText))
      .filterNot(defaultBaseClasses.contains)
      .headOption
  }

  @tailrec
  final def mainClassName(te: ScTypeElement): Option[String] = te match {
    case s: ScSimpleTypeElement => Option(s.getText)
    case p: ScParameterizedTypeElement => mainClassName(p.typeElement)
    case i: ScInfixTypeElement => Option(i.operation.getText)
    case c: ScCompoundTypeElement =>
      c.components match {
        case Seq(first, second, _*) if defaultBaseClasses.contains(first.getText)  => mainClassName(second)
        case Seq(first, _*)         if !defaultBaseClasses.contains(first.getText) => mainClassName(first)
        case _ => None
      }
    case d: ScDesugarizableTypeElement =>
      d.computeDesugarizedType match {
        case Some(tp) => mainClassName(tp)
        case _ => None
      }
    case _ => None
  }

  private val defaultBaseClasses = Array("scala.AnyRef", "java.lang.Object")
}