package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * Common PSI API for concrete `val` and `var` definitions.
 *
 * This trait is shared by [[ScPatternDefinition]] (`val`) and [[ScVariableDefinition]] (`var`).
 *
 * @example {{{
 *   val name = user.name
 *   var left, right = 0
 *   val (key, value) = entry
 * }}}
 */
trait ScValueOrVariableDefinition extends ScValueOrVariable with ScDefinitionWithAssignment {
  /**
   * By definition the "definition" is concrete, not abstract
   */
  override final def isAbstract: Boolean = false

  /**
   * Returns the pattern list introduced by the `val` or `var` keyword.
   *
   * @example {{{
   * val (name, age) = user
   * // pList.getText == "(name, age)"
   *
   * var count = 0
   * // pList.getText == "count"
   * }}}
   */
  def pList: ScPatternList

  /**
   * Returns all binding patterns declared by [[pList]].
   *
   * @example {{{
   * val first, second = 0
   * // bindings.map(_.name) == Seq("first", "second")
   *
   * val (key, value) = entry
   * // bindings.map(_.name) == Seq("key", "value")
   *
   * val _ = 42
   * // bindings.isEmpty
   * }}}
   */
  def bindings: Seq[ScBindingPattern]

  /**
   * Returns the right-hand side expression assigned to this definition.
   *
   * @example {{{
   * val answer: Int = 42
   * // expr.exists(_.getText == "42")
   *
   * var cached = compute()
   * // expr.exists(_.getText == "compute()")
   * }}}
   *
   * @return `None` for incomplete PSI, such as `val answer =` while the file is being edited.
   */
  def expr: Option[ScExpression]

  /**
   * Returns `true` when [[pList]] contains only simple reference patterns and declares exactly one binding.
   *
   * @example {{{
   * val count = 1
   * // isSimple == true
   *
   * var left, right = 0
   * // isSimple == false
   *
   * val (name, age) = user
   * // isSimple == false
   * }}}
   */
  def isSimple: Boolean
}

object ScValueOrVariableDefinition {
  object withExpr {
    /**
     * Extracts the right-hand side expression from a value or variable definition.
     *
     * @example {{{
     * definition match {
     *   case ScValueOrVariableDefinition.withExpr(rhs) =>
     *     println(rhs.getText)
     *   case _ =>
     * }
     * }}}
     */
    def unapply(v: ScValueOrVariableDefinition): Option[ScExpression] = Option(v).flatMap(_.expr)
  }
}
