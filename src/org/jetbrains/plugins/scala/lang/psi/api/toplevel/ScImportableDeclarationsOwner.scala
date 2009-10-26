package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{ResolveState, PsiElement}
import imports.ScImportStmt
import types.ScType
import types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.Any

/**
 * @author ilyas
 */

trait ScImportableDeclarationsOwner extends ScalaPsiElement {
  self: ScTypedDefinition =>

  /**
   * Declarations may be taken from stable elements only
   */
  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement) =
    if (isStable) lastParent match {
      case _: ScImportStmt => {
        ScType.extractClassType(getType(TypingContext.empty).getOrElse(Any)) match {
          case Some((c, _)) => c.processDeclarations(processor, state, null, place)
          case _ => true
        }
      }
      case _ => true
    } else true

}