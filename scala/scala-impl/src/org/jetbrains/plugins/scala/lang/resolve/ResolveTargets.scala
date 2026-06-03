package org.jetbrains.plugins.scala.lang.resolve

object ResolveTargets extends Enumeration {
  val METHOD, VAR, VAL, OBJECT, CLASS, PACKAGE, ANNOTATION = Value

  /**
   * Marker resolve target, which tells the resolver that a particular processor is only interested in definitions with a stable type.
   * Not that definition type is not enough to determine whether definition has stable type or not
   *
   * In Scala 2 only PACKAGE, OBJECT, VAL are considered to be stable<br>
   * However in Scala 3 VAR and METHOD can also be stable if they have some stable type element
   *
   * @see SCL-19477 for the details
   */
  val HAS_STABLE_TYPE: ResolveTargets.Value = Value
}