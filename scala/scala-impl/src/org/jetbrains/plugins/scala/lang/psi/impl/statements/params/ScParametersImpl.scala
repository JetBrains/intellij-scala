package org.jetbrains.plugins.scala.lang.psi.impl.statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScSignatureClause.{TermClause, TypeClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScInterleavedClausesOwner, ScSignatureClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createClauseFromText
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParamClausesStub

import scala.annotation.nowarn

class ScParametersImpl private (stub: ScParamClausesStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.PARAM_CLAUSES, node) with ScParameters {

  def this(node: ASTNode) = this(null, node)
  def this(stub: ScParamClausesStub) = this(stub, null)

  override def toString: String = "Parameters"

  override def clauses: Seq[ScParameterClause] = _clauses()

  @nowarn("cat=deprecation") // TODO: SCL-23400
  private val _clauses = cached("clauses", ModTracker.anyScalaPsiChange, () => {
    getStubOrPsiChildren(ScalaElementType.PARAM_CLAUSE, JavaArrayFactoryUtil.ScParameterClauseFactory).toSeq
  })

  override def processDeclarations(
    processor:  PsiScopeProcessor,
    state:      ResolveState,
    lastParent: PsiElement,
    place:      PsiElement
  ): Boolean = {
    if (lastParent == null) return true

    val signatureClauses: Seq[ScSignatureClause] = getContext match {
      case owner: ScInterleavedClausesOwner => owner.signatureClauses
      case _                                => clauses.map(TermClause)
    }

    val clausesIterator = signatureClauses.iterator
    var break           = false

    //In scala 3, you are allowed to reference value parameters from the same clause.
    val isScala3        = lastParent.isInScala3File

    while (clausesIterator.hasNext && !break) {
      clausesIterator.next() match {
        case TypeClause(clause) =>
          break = clause == lastParent

        case TermClause(clause) =>
          val isCurrentClause = clause == lastParent

          if (isCurrentClause && !isScala3) break = true
          else {
            val paramsIterator = clause.parameters.iterator

            while (paramsIterator.hasNext && !break) {
              val param = paramsIterator.next()

              //Disallow forward references in the same param clause.
              val isForwardReference =
                isCurrentClause &&
                  PsiTreeUtil.isContextAncestor(param, place, true)

              if (isForwardReference) break = true
              else if (!processor.execute(param, state)) return false
            }

            break = isCurrentClause
          }
      }
    }
    true
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitParameters(this)
  }

  override def add(element: PsiElement): PsiElement = {
    element match {
      case param: ScParameter =>
        clauses.lastOption match {
          case Some(clause) =>
            clause.addParameter(param).parameters.last
          case _ =>
            val clause = createClauseFromText(features = this)
            val newClause = clause.addParameter(param)
            super.add(clause)
            newClause.parameters.last
        }
      case _ => super.add(element)
    }
  }
}
