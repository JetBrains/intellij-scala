package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import org.jetbrains.plugins.scala.lang._
import completion.lookups.LookupElementManager
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
import api.base.patterns.{ScInfixPattern, ScConstructorPattern}
import api.base.types.{ScParameterizedTypeElement, ScInfixTypeElement, ScSimpleTypeElement}
import impl.source.tree.LeafPsiElement
import processor.CompletionProcessor
import api.ScalaElementVisitor
import extensions.{toPsiNamedElementExt, toPsiClassExt}
import api.statements.{ScMacroDefinition, ScTypeAlias}
import api.expr.{ScReferenceExpression, ScSuperReference, ScThisReference}
import settings.ScalaProjectSettings
import annotator.intention.ScalaImportClassFix

/**
 * @author AlexanderPodkhalyuzin
 * Date: 22.02.2008
 */

class ScStableCodeReferenceElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ResolvableStableCodeReferenceElement {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def getVariants: Array[Object] = {
    val isInImport: Boolean = ScalaPsiUtil.getParentOfType(this, classOf[ScImportStmt]) != null
    doResolve(this, new CompletionProcessor(getKinds(true), this)).flatMap {
      case res: ScalaResolveResult =>
        import org.jetbrains.plugins.scala.lang.psi.types.Nothing
        val qualifier = res.fromType.getOrElse(Nothing)
        LookupElementManager.getLookupElement(res, isInImport = isInImport, qualifierType = qualifier, isInStableCodeReference = true)
      case r => Seq(r.getElement)
    }
  }

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

  def getKinds(incomplete: Boolean, completion: Boolean): Set[ResolveTargets.Value] = {
    import StdKinds._

    // The qualified identifer immediately following the `mqcro` keyword
    // may only refer to a method.
    def isInMacroDef = getContext match {
      case _: ScMacroDefinition =>
        prevSiblings.exists {
          case l: LeafPsiElement if l.getNode.getElementType == ScalaTokenTypes.kMACRO => true
          case _ => false
        }
      case _ => false
    }

    getContext match {
      case _ if completion => stableImportSelector
      case _: ScStableCodeReferenceElement => stableQualRef
      case e: ScImportExpr => if (e.selectorSet != None
              //import Class._ is not allowed
              || qualifier == None || e.singleWildcard) stableQualRef else stableImportSelector
      case ste: ScSimpleTypeElement =>
        if (incomplete) noPackagesClassCompletion // todo use the settings to include packages
        else if (ste.getLastChild.isInstanceOf[PsiErrorElement]) stableQualRef

        else if (ste.singleton) stableQualRef
        else stableClass
      case _: ScTypeAlias => stableClass
      case _: ScConstructorPattern => objectOrValue
      case _: ScInfixPattern => objectOrValue
      case _: ScThisReference | _: ScSuperReference => stableClassOrObject
      case _: ScImportSelector => stableImportSelector
      case _: ScInfixTypeElement => stableClass
      case _ if isInMacroDef => methodsOnly
      case _ => stableQualRef
    }
  }

  def nameId: PsiElement = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  //  @throws(IncorrectOperationException)
  def bindToElement(element: PsiElement): PsiElement = {
    if (isReferenceTo(element)) this
    else element match {
      case c: PsiClass => {
        val suitableKinds = getKinds(false)
        if (!ResolveUtils.kindMatches(element, suitableKinds))
          throw new IncorrectOperationException("class does not match expected kind, problem place: " + {
            if (getContext != null)
              if (getContext.getContext != null)
                if (getContext.getContext.getContext != null)
                  getContext.getContext.getContext.getText
                else getContext.getContext.getText
              else getContext.getText
            else getText
          })
        if (nameId.getText != c.name) {
          val ref = ScalaPsiElementFactory.createReferenceFromText(c.name, getManager)
          nameId.getNode.getTreeParent.replaceChild(nameId.getNode, ref.nameId.getNode)
          return ref
        }
        val qname = c.qualifiedName
        if (qname != null) {
          return smartBindToElement(qname) {
            ScalaImportClassFix.getImportHolder(ref = this, project = getProject).
              addImportForClass(c, ref = this)
            this
          }
        }
        this
      }
      case t: ScTypeAlias => this //todo: do something
      case _ => throw new IncorrectOperationException("Cannot bind to anything but class")
    }
  }

  private def smartBindToElement(qualName: String)(simpleImport: => PsiElement): PsiElement = {
    val parts: Array[String] = qualName.split('.')
    val anotherRef: ScStableCodeReferenceElement =
      ScalaPsiElementFactory.createReferenceFromText(parts.last, getContext, this)
    val resolve: Array[ResolveResult] = anotherRef.multiResolve(false)
    if (resolve.isEmpty) {
      simpleImport
    } else {
      if (qualName.contains(".")) {
        var index =
          if (ScalaProjectSettings.getInstance(getProject).isImportShortestPathForAmbiguousReferences) parts.length - 1
          else 0
        while (index >= 0) {
          val packagePart = parts.take(index + 1).mkString(".")
          val toReplace = parts.drop(index).mkString(".")
          val ref: ScStableCodeReferenceElement =
            ScalaPsiElementFactory.createReferenceFromText(toReplace, getContext, this)
          var qual = ref
          while (qual.qualifier != None) qual = qual.qualifier.get.asInstanceOf[ScStableCodeReferenceElement]
          val resolve: Array[ResolveResult] = qual.multiResolve(false)
          def isOk: Boolean = {
            if (resolve.length == 0) true
            else if (resolve.length > 1) false
            else {
              val result: ResolveResult = resolve(0)
              result match {
                case ScalaResolveResult(pack: PsiPackage, _) => pack.getQualifiedName == packagePart
                case ScalaResolveResult(c: PsiClass, _) => c.qualifiedName == packagePart
                case _ => false
              }
            }
          }
          if (isOk) {
            ScalaImportClassFix.getImportHolder(this, getProject).addImportForPath(packagePart, this)
            val ref =
              ScalaPsiElementFactory.createReferenceFromText(toReplace, getManager)
            return this.replace(ref)
          }
          index -= 1
        }
      }
      val ref: ScReferenceExpression =
        ScalaPsiElementFactory.createExpressionFromText("_root_." + qualName, getManager).asInstanceOf[ScReferenceExpression]
      this.replace(ref)
    }
  }

  def getSameNameVariants: Array[ResolveResult] = doResolve(this, new CompletionProcessor(getKinds(true), this, false, Some(refName)))

  override def delete() {
    getContext match {
      case sel: ScImportSelector => sel.deleteSelector()
      case expr: ScImportExpr => expr.deleteExpr()
      case _ => super.delete()
    }
  }
}