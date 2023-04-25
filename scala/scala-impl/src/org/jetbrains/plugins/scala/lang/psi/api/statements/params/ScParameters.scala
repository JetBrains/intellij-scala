package org.jetbrains.plugins.scala.lang.psi.api.statements
package params

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType.extendsAnyVal

trait ScParameters extends ScalaPsiElement with PsiParameterList {

  def params: Seq[ScParameter] = clauses.flatMap((clause: ScParameterClause) => clause.parameters)

  def clauses: Seq[ScParameterClause]

  def addClause(clause: ScParameterClause): ScParameters = {
    getNode.addChild(clause.getNode)
    this
  }

  override def getParameterIndex(p: PsiParameter): Int = params.indexOf(p)

  override def getParametersCount: Int = params.length

  /**
   * Note that this method is only called in non-Scala contexts. For Scala contexts ScParameters#params is used.
   *
   * With that out of the way, I have added a special case below for public function definitions in implicit classes
   * that extend AnyVal. For example:
   * {{{
   * implicit class RichInt(i: Int) extends AnyVal {
   *   def addOne(): Int = i + 1
   * }
   * }}}
   *
   * In such cases, from the perspective of non-Scala JVM languages, addOne should be treated like it accepts the one
   * class parameter of RichInt, congruent with the optimization mentioned here:
   * https://docs.scala-lang.org/overviews/core/value-classes.html#extension-methods
   */
  override def getParameters: Array[PsiParameter] = {
    val anyValParams = this.getContext match {
      case f: ScFunctionDefinition if !f.isPrivate && !f.isProtected =>

        val maybeAnyValClass =
          Option(f.containingClass).collect { case c: ScClass if c.getModifierList.isImplicit && extendsAnyVal(c) => c }.toSeq

        maybeAnyValClass.flatMap { c => c.parameters.headOption.toSeq }

      case _ => Seq.empty
    }

    (params ++ anyValParams).toArray
  }
}