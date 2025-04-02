package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.api.NamedTupleType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

/**
 * Represents a named constructor argument pattern in Scala pattern matching.
 * This pattern is used in constructor patterns where arguments are named,
 * similar to pattern matching for named tuples.
 *
 * Example: case Point(x = _, y = _)
 *                     ↑↑↑↑↑  ↑↑↑↑↑
 */
trait ScNamedConstructorArgPattern extends ScPattern with ScNamedElement {
  /** Returns the parent constructor pattern that contains this named argument. */
  final def constructorPattern: ScConstructorPattern = getContext.asInstanceOf[ScConstructorPattern]

  final def subPattern: Option[ScPattern] = findChild[ScPattern]

  final def nameLiteralType: TypeResult =
    this.flatMap(nameElement) { nameElement =>
      Right(NamedTupleType.NameType(nameElement.getText, psiElement = this))
    }

  def nameElement: Option[PsiElement]
}

object ScNamedConstructorArgPattern {
  def unapply(arg: ScNamedConstructorArgPattern): Option[(String, ScPattern)] = {
    arg.nameElement.map(_.getText).zip(arg.subPattern)
  }
}