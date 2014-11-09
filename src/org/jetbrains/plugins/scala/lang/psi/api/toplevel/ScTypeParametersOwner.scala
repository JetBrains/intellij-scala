package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

trait ScTypeParametersOwner extends ScalaPsiElement {
  def typeParameters: Seq[ScTypeParam] = {
    typeParametersClause match {
      case Some(clause) => clause.typeParameters
      case _ => Seq.empty
    }
  }

  def typeParametersClause: Option[ScTypeParamClause] = {
    this match {
      case st: ScalaStubBasedElementImpl[_] =>
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