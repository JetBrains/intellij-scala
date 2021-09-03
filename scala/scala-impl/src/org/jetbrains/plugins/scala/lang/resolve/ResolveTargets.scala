package org.jetbrains.plugins.scala
package lang
package resolve

object ResolveTargets extends Enumeration {
  //No case class marker to reject case class in static import when it has companion object
  val METHOD, VAR, VAL, OBJECT, CLASS, PACKAGE, ANNOTATION = Value

  /**
   * Marker target, which tells the resolver that we are interested (the processor) only in definitions with a stable type.<r>
   * It's better then infer this information based on the set of other normal resolve targets,
   * because it's different in different scala versions:
   *  - In Scala 2 it's PACKAGE, OBJECT, VAL
   *  - In Scala 3 it's PACKAGE, OBJECT, VAL, VAR, METHOD (see SCL-19477)
   *
   *  NOTE: we could make it a separate parameter of [[lang.resolve.processor.BaseProcessor]] instead<br>
   *  For now I stick to this solution for the simplicity of the change.
   */
  val HAS_STABLE_TYPE_MARKER: ResolveTargets.Value = Value
}