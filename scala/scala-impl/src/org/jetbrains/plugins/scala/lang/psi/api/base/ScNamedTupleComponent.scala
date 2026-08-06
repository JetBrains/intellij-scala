package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.api.NamedTupleType
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, Typeable}

/**
 * Either a ScNamedTupleExprComponent or a ScNamedTupleTypeComponent or a ScNamedTuplePatternComponent
 */
trait ScNamedTupleComponent extends ScNamedElement with Typeable {
  final def nameLiteralType: TypeResult =
    this.flatMap(nameElement) { nameElement =>
      Right(NamedTupleType.NameType(nameElement.getText, psiElement = this))
    }

  def nameElement: Option[PsiElement]
}
