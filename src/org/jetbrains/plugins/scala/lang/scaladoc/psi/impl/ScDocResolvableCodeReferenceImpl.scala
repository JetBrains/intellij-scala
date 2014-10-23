package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import com.intellij.lang.ASTNode
import com.intellij.psi.{JavaPsiFacade, ResolveState}
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.TypeToImport
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiElementFactory}
import org.jetbrains.plugins.scala.lang.resolve.StdKinds._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_10

/**
 * User: Dmitry Naydanov
 * Date: 11/30/11
 */

class ScDocResolvableCodeReferenceImpl(node: ASTNode) extends ScStableCodeReferenceElementImpl(node) with ScDocResolvableCodeReference {
  private def is2_10plus = this.languageLevel >= Scala_2_10
  
  override def getKinds(incomplete: Boolean, completion: Boolean) = stableImportSelector

  override def createReplacingElementWithClassName(useFullQualifiedName: Boolean, clazz: TypeToImport) = 
    if (is2_10plus) super.createReplacingElementWithClassName(true, clazz) 
    else ScalaPsiElementFactory.createDocLinkValue(clazz.qualifiedName, clazz.element.getManager)

  override protected def processQualifier(ref: ScStableCodeReferenceElement, processor: BaseProcessor) {
    if (is2_10plus) super.processQualifier(ref, processor) else pathQualifier match {
      case None =>
        val defaultPackage = ScPackageImpl(JavaPsiFacade.getInstance(getProject).findPackage(""))
        defaultPackage.processDeclarations(processor, ResolveState.initial(), null, ref)
      case Some(q: ScDocResolvableCodeReference) =>
        q.multiResolve(true).foreach(processQualifierResolveResult(_, processor, ref))
      case _ =>
    }
  }
}