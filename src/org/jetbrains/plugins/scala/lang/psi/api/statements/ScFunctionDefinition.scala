package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi._
import com.intellij.util.containers.ConcurrentHashMap
import light.{PsiClassWrapper, StaticTraitScFunctionWrapper}
import expr.ScExpression
import api.base.ScReferenceElement

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:49:36
*/

trait ScFunctionDefinition extends ScFunction with ScControlFlowOwner {
  def body: Option[ScExpression]

  def parameters: Seq[ScParameter]

  def hasAssign: Boolean

  def assignment: Option[PsiElement]

  def removeAssignment()

  def getReturnUsages: Array[PsiElement]

  def canBeTailRecursive: Boolean

  def hasTailRecursionAnnotation: Boolean

  def recursiveReferences: Seq[RecursiveReference]

  def recursionType: RecursionType

  def isSecondaryConstructor: Boolean

  private var staticTraitFunctionWrapper: ConcurrentHashMap[(PsiClassWrapper), (StaticTraitScFunctionWrapper, Long)] =
    new ConcurrentHashMap()

  def getStaticTraitFunctionWrapper(cClass: PsiClassWrapper): StaticTraitScFunctionWrapper = {
    val curModCount = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    val r = staticTraitFunctionWrapper.get(cClass)
    if (r != null && r._2 == curModCount) {
      return r._1
    }
    val res = new StaticTraitScFunctionWrapper(this, cClass)
    staticTraitFunctionWrapper.put(cClass, (res, curModCount))
    res
  }
}

case class RecursiveReference(element: ScReferenceElement, isTailCall: Boolean)

trait RecursionType

object RecursionType {
  case object NoRecursion extends RecursionType
  case object OrdinaryRecursion extends RecursionType
  case object TailRecursion extends RecursionType
}