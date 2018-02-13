package org.jetbrains.plugins.scala.lang.macros.evaluator.impl
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.macros.evaluator.MacroContext
import org.jetbrains.plugins.scala.lang.macros.evaluator.impl.ShapelessMaterializeGeneric.{fqColonColon, fqHNil}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}

trait ShapelessUtils {

  protected val fqDefSymLab = "_root_.shapeless.DefaultSymbolicLabelling"
  protected val fqSelector = "_root_.shapeless.ops.record.Selector"
  protected val fqFieldType = "_root_.shapeless.labelled.FieldType"
  protected val fqKeyTag = "_root_.shapeless.labelled.KeyTag"
  protected val fqTagged = "_root_.shapeless.tag.Tagged"
  protected val fqGeneric = "_root_.shapeless.Generic"
  protected val fqColonColon = "_root_.shapeless.::"
  protected val fqHNil = "_root_.shapeless.HNil"

  private val tupleN = "Tuple"

  /**
    *  Target classed of <Labelled>Generic.Aux[L, K, ...] are encoded as the first argument
    */
  protected def extractTargetType(context: MacroContext): ScType = context.expectedType.get match {
    case t: ScParameterizedType => t.typeArguments.head
    case _ => StdTypes.instance(context.place.projectContext).Any
  }

  private def extractFiledsFromClass(c: ScClass):Seq[(String, ScType)] = {
    c.constructor.map(_.parameters.map(p=> (p.name, p.`type`().getOrAny))).getOrElse(Seq.empty)
  }

  /**
    * Extracts case class field names and types or element types from tuples to be used by HList generator
    */
  protected def extractFields(tp: ScType): Seq[(String, ScType)] = tp match {
    case ScParameterizedType(ScDesignatorType(c: ScClass), args) if c.name.startsWith(tupleN) =>
      args.zipWithIndex.map{e => (s"_${e._2}", e._1)}
    case ScDesignatorType(c: ScClass) if c.isCase =>
      extractFiledsFromClass(c)
    case ScProjectionType(_, elem: ScClass) if elem.isCase =>
      extractFiledsFromClass(elem)
    case _ => Seq.empty
  }

  protected def hlistText(componentTypes: Seq[ScType]): String =
    componentTypes.foldRight(fqHNil)((p, suffix) => s"$fqColonColon[${p.canonicalText}, $suffix]")
}
