package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import api.ScalaFile
import api.toplevel.packaging.ScPackaging
import caches.ScalaCachesManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang._
import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import psi.api.base._
import psi.types._
import psi.impl.ScalaPsiElementFactory
import resolve._
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.impl._
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import api.toplevel.ScTypedDefinition
import api.statements.ScTypeAlias
import api.expr.{ScSuperReference, ScThisReference}
import result.TypingContext
import api.base.types.{ScInfixTypeElement, ScSimpleTypeElement}
import api.base.patterns.{ScInfixPattern, ScConstructorPattern}

/**
 * @author AlexanderPodkhalyuzin
 * Date: 22.02.2008
 */

class ScStableCodeReferenceElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ResolvableStableCodeReferenceElement {
  def getVariants(): Array[Object] = doResolve(this, new CompletionProcessor(getKinds(true))).map(r => {
    r match {
      case res: ScalaResolveResult => ResolveUtils.getLookupElement(res)
      case _ => r.getElement
    }
  })

  override def toString: String = "CodeReferenceElement"

  def getKinds(incomplete: Boolean): Set[ResolveTargets.Value] = {
    import StdKinds._
    getContext match {
      case _: ScStableCodeReferenceElement => stableQualRef
      case e: ScImportExpr => if (e.selectorSet != None
              //import Class._ is not allowed
              || qualifier == None || e.singleWildcard) stableQualRef else stableImportSelector
      case ste: ScSimpleTypeElement => if (incomplete) noPackagesClassCompletion /* todo use the settings to include packages*/
      else if (ste.singleton) stableQualRef else stableClass
      case _: ScTypeAlias => stableClass
      case _: ScConstructorPattern => classOrObjectOrValues
      case _: ScInfixPattern => classOrObjectOrValues
      case _: ScThisReference | _: ScSuperReference => stableClassOrObject
      case _: ScImportSelector => stableImportSelector
      case _: ScInfixTypeElement => stableClass
      case _ => stableQualRef
    }
  }

  def nameId: PsiElement = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  //  @throws(IncorrectOperationException)
  def bindToElement(element: PsiElement): PsiElement = {
    if (isReferenceTo(element)) return this
    else element match {
      case c: PsiClass => {
        if (!ResolveUtils.kindMatches(element, getKinds(false)))
          throw new IncorrectOperationException("class does not match expected kind")
        if (nameId.getText != c.getName) {
          val ref = ScalaPsiElementFactory.createReferenceFromText(c.getName, getManager)
          nameId.getNode.getTreeParent.replaceChild(nameId.getNode, ref.nameId.getNode)
          return ref
        }
        val qname = c.getQualifiedName
        if (qname != null) org.jetbrains.plugins.scala.annotator.intention.
                ScalaImportClassFix.getImportHolder(ref = this, project = getProject).
                addImportForClass(c, ref = this) //todo: correct handling
        this
      }
      case _ => throw new IncorrectOperationException("Cannot bind to anything but class")
    }
  }

  def getSameNameVariants: Array[ResolveResult] = doResolve(this, new CompletionProcessor(getKinds(true), false, Some(refName)))
}