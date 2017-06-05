package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel

import com.intellij.lang.ASTNode
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScMemberFactory
import org.jetbrains.plugins.scala.lang.TokenSets.MEMBERS
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes.EARLY_DEFINITIONS
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.stubs.ScEarlyDefinitionsStub

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */
class ScEarlyDefinitionsImpl private(stub: ScEarlyDefinitionsStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, EARLY_DEFINITIONS, node) with ScEarlyDefinitions {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScEarlyDefinitionsStub) = this(stub, null)

  override def toString: String = "EarlyDefinitions"

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    var maybeElement = Option(lastParent)
    while (maybeElement.isDefined) {
      val processed = maybeElement.toSeq flatMap {
        case definition: ScPatternDefinition => definition.bindings
        case definition: ScVariableDefinition => definition.bindings
        case _ => Seq.empty
      } map {
        processor.execute(_, state)
      } forall { result =>
        result
      }

      if (!processed) return false

      maybeElement = maybeElement flatMap { element =>
        Option(ScalaPsiUtil.getPrevStubOrPsiElement(element))
      }
    }
    true
  }

  def members: Seq[ScMember] =
    getStubOrPsiChildren(MEMBERS, ScMemberFactory)
}