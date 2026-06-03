package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import com.intellij.lang.jvm.JvmElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

trait ScClassParameter
  extends ScParameter
    with ScMember
    with ScMember.WithBaseIconProvider {

  /** Returns true if parameter has explicit `val` keyword in code */
  def isVal: Boolean

  /** Returns true if parameter has explicit `var` keyword in code */
  def isVar: Boolean

  /** Returns true if parameter has explicit `val` keyword OR is located in the first parameter clause
   * of a case class, and doesn't have an explicit `var` keyword (see [[isCaseClassPrimaryParameter]]) */
  def isValEffectively: Boolean = isVal || !isVar && isCaseClassPrimaryParameter

  /**
   * Returns true if parameter is located in the first parameter clause of a case class.
   * For such parameters, scala compiler automatically generates a `val` field even if parameter
   * doesn't have an explicit `val` keyword.
   *
   * Also, for such parameters scala compiler auto-generates:
   *          - `unapply` method in the companion
   *          - `equals` & `hashCode` methods
   *          - `toString` method
   *
   * All parameters in other (optional) parameter clause are ignored in this method.
   *
   * Scala compiler also auto-generates:
   *           - `apply` method in the companion object
   *           - `copy` method
   *
   * However, for these methods it takes into account all parameter clauses, not only the first one.
   */
  def isCaseClassPrimaryParameter: Boolean

  /**
   * Returns true if scala compiler generates a field for the parameter
   *
   * ATTENTION: there is one case when this method returns `false` even though scala compiler generates a field:<br>
   * When a parameter is used outside class constructor,
   * the compiler still generates a field with `private[this]` modifier
   * However, we don't treat it as a member in most of the method usage place because from the class user POV the field is not accessibl
   */
  def isClassMember: Boolean = isVal || isVar || isCaseClassPrimaryParameter

  /** Returns true if parameter is a class member ([[isClassMember]]) and has `private[this]` modifier */
  def isPrivateThis: Boolean

  override def accept[T](visitor: JvmElementVisitor[T]): T = super[WithBaseIconProvider].accept(visitor)
}