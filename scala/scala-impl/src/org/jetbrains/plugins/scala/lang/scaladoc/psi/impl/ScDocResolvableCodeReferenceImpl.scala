package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.TypeToImport
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createDocLinkValue
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.StdKinds._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference
import org.jetbrains.plugins.scala.project._

/**
 * User: Dmitry Naydanov
 * Date: 11/30/11
 */

class ScDocResolvableCodeReferenceImpl(node: ASTNode) extends ScStableCodeReferenceElementImpl(node) with ScDocResolvableCodeReference {
  private def is2_10plus: Boolean = this.scalaLanguageLevel.forall(_ >= ScalaLanguageLevel.Scala_2_10)

  override def multiResolve(incomplete: Boolean): Array[ResolveResult] = {
    val s = super.multiResolve(incomplete)
    s.zipWithIndex.collect {
      case (ScalaResolveResult(cstr: ScPrimaryConstructor, _), ind) if cstr.containingClass != null => (new ScalaResolveResult(cstr.containingClass), ind)
    } foreach {
      case (rr, idx) => s(idx) = rr
    }

    s
  }

  override def getKinds(incomplete: Boolean, completion: Boolean): _root_.org.jetbrains.plugins.scala.lang.resolve.ResolveTargets.ValueSet = stableImportSelector

  override def createReplacingElementWithClassName(useFullQualifiedName: Boolean, clazz: TypeToImport): ScReferenceElement =
    if (is2_10plus) super.createReplacingElementWithClassName(true, clazz)
    else createDocLinkValue(clazz.qualifiedName)(clazz.element.getManager)

  override protected def processQualifier(processor: BaseProcessor): Unit = {
    if (is2_10plus) super.processQualifier(processor) else pathQualifier match {
      case None =>
        val defaultPackage = ScPackageImpl(JavaPsiFacade.getInstance(getProject).findPackage(""))
        defaultPackage.processDeclarations(processor, ResolveState.initial(), null, this)
      case Some(q: ScDocResolvableCodeReference) =>
        q.multiResolve(true).foreach(processQualifierResolveResult(_, processor))
      case _ =>
    }
  }
}