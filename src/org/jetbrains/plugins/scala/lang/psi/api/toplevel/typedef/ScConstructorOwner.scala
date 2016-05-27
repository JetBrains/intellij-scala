package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiMethod, ResolveState}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner}
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

/**
  * @author adkozlov
  */
trait ScConstructorOwner extends ScParameterOwner with ScTemplateDefinition {
  def constructor: Option[ScPrimaryConstructor] = findChild(classOf[ScPrimaryConstructor])

  def secondaryConstructors: Seq[ScFunction] = functions filter {
    _.isConstructor
  }

  def constructors: Seq[PsiMethod] = secondaryConstructors ++ constructor

  override def getConstructors = ((secondaryConstructors flatMap {
    _.getFunctionWrappers(isStatic = false, isInterface = false, Some(this))
  }) ++
    (constructor.toSeq flatMap {
      _.getFunctionWrappers
    })).toArray

  def clauses = constructor.map(_.parameterList)

  def parameters = constructor.toSeq.flatMap {
    _.effectiveParameterClauses.flatMap(_.unsafeClassParameters)
  }

  override def members = super.members ++ constructor

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement) =
    super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)

  override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                                  state: ResolveState,
                                                  lastParent: PsiElement,
                                                  place: PsiElement): Boolean = {
    if (DumbService.getInstance(getProject).isDumb) return true
    if (!super[ScTemplateDefinition].processDeclarationsForTemplateBody(processor, state, lastParent, place)) return false

    constructor match {
      case Some(primaryConstructor) if place != null && PsiTreeUtil.isContextAncestor(primaryConstructor, place, false) =>
      //ignore, should be processed in ScParameters
      case _ =>
        for (parameter <- parameters) {
          ProgressManager.checkCanceled()
          if (processor.isInstanceOf[BaseProcessor]) {
            // don't expose class parameters to Java.
            if (!processor.execute(parameter, state)) return false
          }
        }
    }

    true
  }
}
