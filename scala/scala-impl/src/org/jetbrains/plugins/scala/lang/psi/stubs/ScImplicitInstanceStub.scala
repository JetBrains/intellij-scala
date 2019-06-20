package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.stubs.IndexSink
import com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScCompoundTypeElement, ScDesugarizableTypeElement, ScInfixTypeElement, ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement, ScTypeProjection}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.stubs.index.{ImplicitConversionIndex, ImplicitIndex, ImplicitInstanceIndex}

trait ScImplicitInstanceStub {

  /**
   * Non-trivial class names of a return type of implicit function or val,
   * or super classes of an implicit object.
   * It is in the same form as written in source or decompiled class file, so it may have prefix.
   */
  def implicitClassNames: Array[String]

  def isImplicitConversion: Boolean = false

  def indexImplicits(sink: IndexSink): Unit =
    implicitIndex.occurrences(sink, implicitClassNames)

  private def implicitIndex: ImplicitIndex =
    if (isImplicitConversion) ImplicitConversionIndex else ImplicitInstanceIndex
}

object ScImplicitInstanceStub {
  def implicitClassNames(psi: ScModifierListOwner, typeElement: => Option[ScTypeElement]): Array[String] = {
    if (psi.getModifierList.isImplicit)
      typeElement.toArray.flatMap(classNames)
    else EMPTY_STRING_ARRAY
  }

  def superClassNames(obj: ScObject): Array[String] = {
    for {
      templateParent <- obj.extendsBlock.templateParents.toArray
      typeElement    <- templateParent.typeElements
      className      <- classNames(typeElement)
    } yield {
      className
    }
  }

  final def classNames(te: ScTypeElement): Array[String] = {
    val allNames = te match {
      case s: ScSimpleTypeElement => Array(s.getText)
      case p: ScParameterizedTypeElement => classNames(p.typeElement)
      case i: ScInfixTypeElement => Array(i.operation.getText)
      case c: ScCompoundTypeElement =>
        c.components.toArray.flatMap(classNames)
      case d: ScDesugarizableTypeElement =>
        d.computeDesugarizedType match {
          case Some(tp) => classNames(tp)
          case _ => EMPTY_STRING_ARRAY
        }
      case tp: ScTypeProjection => Array(tp.refName)
      case _ => EMPTY_STRING_ARRAY
    }

    allNames.filterNot(defaultBaseClasses.contains)
  }

  private val defaultBaseClasses = Array("scala.AnyRef", "java.lang.Object")
}