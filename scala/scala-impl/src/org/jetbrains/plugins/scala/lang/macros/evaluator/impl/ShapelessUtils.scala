package org.jetbrains.plugins.scala.lang.macros.evaluator.impl
import org.jetbrains.plugins.scala.lang.macros.evaluator.MacroContext
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.DesignatorOwner

trait ShapelessUtils {

  protected val fqDefSymLab = "_root_.shapeless.DefaultSymbolicLabelling"
  protected val fqSelector = "_root_.shapeless.ops.record.Selector"
  protected val fqFieldType = "_root_.shapeless.labelled.FieldType"
  protected val fqKeyTag = "_root_.shapeless.labelled.KeyTag"
  protected val fqTagged = "_root_.shapeless.tag.Tagged"
  protected val fqGeneric = "_root_.shapeless.Generic"
  protected val fqColonColon = "_root_.shapeless.::"
  protected val fqHNil = "_root_.shapeless.HNil"

  protected def extractTargetClass(context: MacroContext): Option[ScClass] = context.expectedType.get match {
    case t: ScParameterizedType => t.typeArguments.head match {
      case t: DesignatorOwner if t.element.isInstanceOf[ScClass] => Some(t.element.asInstanceOf[ScClass])
      case _ => None
    }
    case _ => None
  }
}
