package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScParameterOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScParamClausesStub, ScTypeParamClauseStub}

import scala.jdk.CollectionConverters.ListHasAsScala

trait ScTypeParametersOwner extends ScalaPsiElement {

  def typeParameters: Seq[ScTypeParam] = _typeParameters()

  private val _typeParameters = cached("ScTypeParametersOwner.typeParameters", ModTracker.anyScalaPsiChange, () => {
    typeParametersClause match {
      case Some(clause) => clause.typeParameters
      case _ => Seq.empty
    }
  })

  def typeParametersClause: Option[ScTypeParamClause] = {
    this match {
      // SCL-21205
      case st: ScalaStubBasedElementImpl[_, _] with ScParameterOwner =>
        Option(st.getStub).map(
          _.getChildrenStubs.asScala
            .takeWhile(!_.isInstanceOf[ScParamClausesStub])
            .find(_.isInstanceOf[ScTypeParamClauseStub])
            .map(_.getPsi.asInstanceOf[ScTypeParamClause]))
          .getOrElse(findChild[ScTypeParamClause])
      case st: ScalaStubBasedElementImpl[_, _] =>
        Option(st.getStubOrPsiChild(ScalaElementType.TYPE_PARAM_CLAUSE))
      case _ =>
        findChild[ScTypeParamClause]
    }
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