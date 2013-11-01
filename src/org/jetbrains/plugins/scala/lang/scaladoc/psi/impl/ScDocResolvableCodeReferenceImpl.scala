package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.resolve.StdKinds._
import lang.psi.impl.base.ScStableCodeReferenceElementImpl
import lang.psi.api.base.ScStableCodeReferenceElement
import resolve.processor.BaseProcessor
import com.intellij.openapi.progress.ProgressManager
import lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import lang.psi.api.toplevel.imports.ScImportExpr
import lang.psi.types.result.TypingContext
import lang.psi.api.expr.{ScSuperReference, ScThisReference}
import resolve.ResolveUtils
import api.{ScDocComment, ScDocResolvableCodeReference}
import com.intellij.psi.scope.PsiScopeProcessor
import lang.psi.impl.ScPackageImpl._
import com.intellij.psi.{JavaPsiFacade, ResolveState, PsiElement, PsiClass}
import lang.psi.impl.{ScPackageImpl, ScalaPsiElementFactory}
import extensions.toPsiClassExt
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.TypeToImport

/**
 * User: Dmitry Naydanov
 * Date: 11/30/11
 */

class ScDocResolvableCodeReferenceImpl(node: ASTNode) extends ScStableCodeReferenceElementImpl(node) with ScDocResolvableCodeReference {
  override def getKinds(incomplete: Boolean, completion: Boolean) = stableImportSelector

  override def createReplacingElementWithClassName(useFullQualifiedName: Boolean, clazz: TypeToImport) = {
    ScalaPsiElementFactory.createDocLinkValue(clazz.qualifiedName, clazz.element.getManager)
  }

  override protected def processQualifier(ref: ScStableCodeReferenceElement, processor: BaseProcessor) {
    pathQualifier match {
      case None =>
        val defaultPackage = ScPackageImpl(JavaPsiFacade.getInstance(getProject).findPackage(""))
        defaultPackage.processDeclarations(processor, ResolveState.initial(), null, ref)
      case Some(q: ScDocResolvableCodeReference) =>
        q.multiResolve(true).foreach(processQualifierResolveResult(_, processor, ref))
      case _ =>
    }
  }
}