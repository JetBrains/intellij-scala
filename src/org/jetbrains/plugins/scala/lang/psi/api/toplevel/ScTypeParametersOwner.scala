package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import parser.ScalaElementTypes

trait ScTypeParametersOwner extends ScalaPsiElement {
  def typeParameters(): Seq[ScTypeParam] = typeParametersClause match {
    case Some(clause) => clause.typeParameters
    case _ => Seq.empty
  }

  def typeParametersClause: Option[ScTypeParamClause] = {
    this match {
      case st: ScalaStubBasedElementImpl[_] => {
        val stub = st.getStub
        if (stub != null) {
          val array = stub.getChildrenByType(ScalaElementTypes.TYPE_PARAM_CLAUSE, new ArrayFactory[ScTypeParamClause] {
            def create(count: Int): Array[ScTypeParamClause] = new Array[ScTypeParamClause](count)
          })
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
      for (tp <- typeParameters) {
        if (!processor.execute(tp, state)) return false
      }
    }
    true
  }
}