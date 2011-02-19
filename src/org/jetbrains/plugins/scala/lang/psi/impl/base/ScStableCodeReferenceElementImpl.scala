package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import org.jetbrains.plugins.scala.lang._
import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import psi.api.base._
import psi.impl.ScalaPsiElementFactory
import resolve._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import api.statements.ScTypeAlias
import api.expr.{ScSuperReference, ScThisReference}
import api.base.patterns.{ScInfixPattern, ScConstructorPattern}
import api.base.types.{ScParameterizedTypeElement, ScInfixTypeElement, ScSimpleTypeElement}
import processor.{ExpandedExtractorResolveProcessor, CompletionProcessor}
import api.ScalaElementVisitor
import annotator.intention.ScalaImportClassFix
import usages.ImportSelectorUsed
import api.toplevel.packaging.ScPackaging
import util.PsiTreeUtil

/**
 * @author AlexanderPodkhalyuzin
 * Date: 22.02.2008
 */

class ScStableCodeReferenceElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ResolvableStableCodeReferenceElement {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def getVariants(): Array[Object] = doResolve(this, new CompletionProcessor(getKinds(true))).map(r => {
    r match {
      case res: ScalaResolveResult => ResolveUtils.getLookupElement(res)
      case _ => r.getElement
    }
  })

  def getConstructor = {
    getContext match {
      case s: ScSimpleTypeElement => {
        s.getContext match {
          case p: ScParameterizedTypeElement => {
            p.getContext match {
              case constr: ScConstructor => Some(constr)
              case _ => None
            }
          }
          case constr: ScConstructor => Some(constr)
          case _ => None
        }
      }
      case _ => None
    }
  }

  def isConstructorReference = !getConstructor.isEmpty

  override def toString: String = "CodeReferenceElement"

  def getKinds(incomplete: Boolean): Set[ResolveTargets.Value] = {
    import StdKinds._
    getContext match {
      case _: ScStableCodeReferenceElement => stableQualRef
      case e: ScImportExpr => if (e.selectorSet != None
              //import Class._ is not allowed
              || qualifier == None || e.singleWildcard) stableQualRef else stableImportSelector
      case ste: ScSimpleTypeElement => if (incomplete) noPackagesClassCompletion // todo use the settings to include packages
        else if (ste.singleton) stableQualRef else stableClass
      case _: ScTypeAlias => stableClass
      case _: ScConstructorPattern => objectOrValue
      case _: ScInfixPattern => objectOrValue
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
          throw new IncorrectOperationException("class does not match expected kind, problem place: " +
                  getContext.getContext.getContext.getText)
        if (nameId.getText != c.getName) {
          val ref = ScalaPsiElementFactory.createReferenceFromText(c.getName, getManager)
          nameId.getNode.getTreeParent.replaceChild(nameId.getNode, ref.nameId.getNode)
          return ref
        }
        updateImports(c)
        this
      }
      case t: ScTypeAlias => return this //todo: do something
      case _ => throw new IncorrectOperationException("Cannot bind to anything but class")
    }
  }

  //todo: correct handling, this is just a band-aid solution. For example,
  //      there could also have been usages through a wildcard import.
  private def updateImports(newTarget: PsiClass) {
    val qname = newTarget.getQualifiedName
    if (qname != null) {
      val importSelectors = getContainingFile.breadthFirst.collect {
        case selector: ScImportSelector if selector.reference == this => selector
      }.toStream

      if (importSelectors.isEmpty) {
        // The class must have been in the current scope, or imported through a wildcard. Add an import for it.
        val importHolder = ScalaImportClassFix.getImportHolder(ref = this, project = getProject)
        importHolder.addImportForClass(newTarget, ref = this)
      } else {
        // The class was specifically mentioned in an import selector. Move that selector to a new
        // import statement with the new qualifier.
        val (newQualifier, newName) = {
          //todo: Can we get this more cleanly from the PsiClass?
          val i = qname.lastIndexOf('.')
          val (qual, dotAndName) = qname.splitAt(i)
          (qual, dotAndName.drop(1))
        }
        importSelectors.foreach(_.moveSelector(newQualifier, newName))
      }
    }
  }

  def getSameNameVariants: Array[ResolveResult] = doResolve(this, new CompletionProcessor(getKinds(true), false, Some(refName)))
}
