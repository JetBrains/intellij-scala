package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi._
import parser.ScalaElementTypes
import com.intellij.openapi.progress.ProgressManager

trait ScTypeParametersOwner extends ScalaPsiElement {
  @volatile
  private var res: Seq[ScTypeParam] = null
  @volatile
  private var modCount: Long = 0L
  
  def typeParameters: Seq[ScTypeParam] = {
    def inner(): Seq[ScTypeParam] = {
      typeParametersClause match {
        case Some(clause) => clause.typeParameters
        case _ => Seq.empty
      }
    }
    
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (res != null && curModCount == modCount) return res
    
    res = inner()
    modCount = curModCount

    res
  }

  def typeParametersClause: Option[ScTypeParamClause] = {
    this match {
      case st: ScalaStubBasedElementImpl[_] => {
        val stub = st.getStub
        if (stub != null) {
          val array = stub.getChildrenByType(ScalaElementTypes.TYPE_PARAM_CLAUSE,
            JavaArrayFactoryUtil.ScTypeParamClauseFactory)
          if (array.length == 0) {
            return None
          } else {
            return Some(array.apply(0))
          }
        }
      }
      case _ =>
    }
    findChild(classOf[ScTypeParamClause])
  }

  import com.intellij.psi.scope.PsiScopeProcessor
  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (lastParent != null) {
      var i = 0
      while (i < typeParameters.length) {
        ProgressManager.checkCanceled()
        if (!processor.execute(typeParameters.apply(i), state)) return false
        i = i + 1
      }
    }
    true
  }
}