package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.PsiClassImplUtil
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiUtil
import com.intellij.psi._
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.TypeToImport
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiElementFactory}
import org.jetbrains.plugins.scala.lang.resolve.StdKinds._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_10

import scala.collection.immutable.HashMap
import scala.collection.mutable

/**
 * User: Dmitry Naydanov
 * Date: 11/30/11
 */

class ScDocResolvableCodeReferenceImpl(node: ASTNode) extends ScStableCodeReferenceElementImpl(node) with ScDocResolvableCodeReference {
  private def is2_10plus: Boolean = {
    if (getContainingFile.getVirtualFile == null) return true  //in case of synthetic elements

    val module = ScalaPsiUtil.getModule(this)

    if (module == null) return true // in case of worksheet

    module.scalaSdk.flatMap(_.compilerVersion) exists {
      case version =>
        try {
          val numbers = version.split('.').take(2).map(c => Integer parseInt c) //Will we ever see Scala 3.X ?
          numbers.length > 1 && numbers(1) >= 10
        } catch {
          case _: NumberFormatException => false
        }
    }
  }

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

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    super.processDeclarations(processor, state, lastParent, place) && {
      qualifier match {
        case Some(ref: PsiReference) =>
          val el = ref.resolve()
          el match {
            case clazz: ScClass =>
              PsiClassImplUtil.processDeclarationsInClass(clazz, processor, state, null, lastParent, place, PsiUtil.getLanguageLevel(place), false)
            case _ => true
          }
        case _ => true
      }
    }
  }
}