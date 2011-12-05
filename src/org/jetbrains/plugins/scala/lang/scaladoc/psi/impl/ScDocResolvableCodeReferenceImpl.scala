package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import com.intellij.lang.ASTNode
import api.ScDocResolvableCodeReference
import org.jetbrains.plugins.scala.lang.resolve.StdKinds._
import lang.psi.impl.base.ScStableCodeReferenceElementImpl
import lang.psi.api.base.ScStableCodeReferenceElement
import lang.psi.impl.ScalaPsiElementFactory
import resolve.processor.BaseProcessor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.{ResolveState, PsiElement, PsiClass}
import lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import lang.psi.api.toplevel.imports.ScImportExpr

/**
 * User: Dmitry Naydanov
 * Date: 11/30/11
 */

class ScDocResolvableCodeReferenceImpl(node: ASTNode) extends ScStableCodeReferenceElementImpl(node) with ScDocResolvableCodeReference {
  override def getKinds(incomplete: Boolean, completion: Boolean) = stableImportSelector

  override def createReplacingElementWithClassName(useFullQualifiedName: Boolean, clazz: PsiClass) = {
    ScalaPsiElementFactory.createDocLinkValue(clazz.getQualifiedName, clazz.getManager)
  }
}