package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.{ClassTypeToImport, TypeAliasToImport, TypeToImport}
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions.{PsiNamedElementExt, ResolveResultEx}
import org.jetbrains.plugins.scala.lang.completion.lookups.LookupElementManager
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScConstructorPattern, ScInfixPattern, ScInterpolationPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScParameterizedTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScSuperReference, ScThisReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScMacroDefinition, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createReferenceFromText
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.macroAnnotations.CachedMappedWithRecursionGuard

/**
 * @author AlexanderPodkhalyuzin
 *         Date: 22.02.2008
 */

class ScStableCodeReferenceElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ResolvableStableCodeReferenceElement {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def getVariants: Array[Object] = {
    allVariantsCached.flatMap {
      case res: ScalaResolveResult =>
        import org.jetbrains.plugins.scala.lang.psi.types.api.Nothing

        val isInImport: Boolean = ScalaPsiUtil.getParentOfType(this, classOf[ScImportStmt]) != null
        val qualifier = res.fromType.getOrElse(Nothing)
        LookupElementManager.getLookupElement(res, isInImport = isInImport, qualifierType = qualifier, isInStableCodeReference = true)
      case r => Seq(r.getElement)
    }
  }

  def getResolveResultVariants: Array[ScalaResolveResult] = {
    allVariantsCached.flatMap {
      case res: ScalaResolveResult => Seq(res)
      case _ => Seq.empty
    }
  }

  def getConstructor: Option[ScConstructor] = {
    getContext match {
      case s: ScSimpleTypeElement =>
        s.getContext match {
          case p: ScParameterizedTypeElement =>
            p.getContext match {
              case constr: ScConstructor => Some(constr)
              case _ => None
            }
          case constr: ScConstructor => Some(constr)
          case _ => None
        }
      case _ => None
    }
  }

  def isConstructorReference: Boolean = getConstructor.nonEmpty

  override def toString: String = "CodeReferenceElement: " + getText

  def getKinds(incomplete: Boolean, completion: Boolean): Set[ResolveTargets.Value] = {
    import org.jetbrains.plugins.scala.lang.resolve.StdKinds._

    // The qualified identifier immediately following the `macro` keyword
    // may only refer to a method.
    def isInMacroDef = getContext match {
      case _: ScMacroDefinition =>
        prevSiblings.exists {
          case l: LeafPsiElement if l.getNode.getElementType == ScalaTokenTypes.kMACRO => true
          case _ => false
        }
      case _ => false
    }

    val result = getContext match {
      case _: ScStableCodeReferenceElement => stableQualRef
      case e: ScImportExpr => if (e.selectorSet.isDefined
              //import Class._ is not allowed
        || qualifier.isEmpty || e.singleWildcard) stableQualRef
      else stableImportSelector
      case ste: ScSimpleTypeElement =>
        if (incomplete) noPackagesClassCompletion // todo use the settings to include packages
        else if (ste.getLastChild.isInstanceOf[PsiErrorElement]) stableQualRef

        else if (ste.singleton) stableQualRef
        else stableClass
      case _: ScTypeAlias => stableClass
      case _: ScInterpolationPattern => stableImportSelector
      case _: ScConstructorPattern => objectOrValue
      case _: ScInfixPattern => objectOrValue
      case _: ScThisReference | _: ScSuperReference => stableClassOrObject
      case _: ScImportSelector => stableImportSelector
      case _: ScInfixTypeElement => stableClass
      case _ if isInMacroDef => methodsOnly
      case _ => stableQualRef
    }
    if (completion) result + ResolveTargets.PACKAGE + ResolveTargets.OBJECT + ResolveTargets.VAL else result
  }

  def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)

  //  @throws(IncorrectOperationException)
  def bindToElement(element: PsiElement): PsiElement = {
    object CheckedAndReplaced {
      def unapply(text: String): Option[PsiElement] = {
        val ref = createReferenceFromText(text, getContext, ScStableCodeReferenceElementImpl.this)
        if (ref.isReferenceTo(element)) {
          val ref = createReferenceFromText(text)
          Some(ScStableCodeReferenceElementImpl.this.replace(ref))
        }
        else None
      }
    }

    if (isReferenceTo(element)) this
    else {
      val aliasedRef: Option[ScReferenceElement] = ScalaPsiUtil.importAliasFor(element, this)
      if (aliasedRef.isDefined) {
        this.replace(aliasedRef.get)
      }
      else {
        def bindToType(c: ScalaImportTypeFix.TypeToImport): PsiElement = {
          val suitableKinds = getKinds(incomplete = false)
          if (!ResolveUtils.kindMatches(element, suitableKinds)) {
            reportWrongKind(c, suitableKinds)
          }
          if (nameId.getText != c.name) {
            val ref = createReferenceFromText(c.name)
            return this.replace(ref).asInstanceOf[ScStableCodeReferenceElement].bindToElement(element)
          }
          val qname = c.qualifiedName
          val isPredefined = ScalaCodeStyleSettings.getInstance(getProject).hasImportWithPrefix(qname)
          if (qualifier.isDefined && !isPredefined) {
            c.name match {
              case CheckedAndReplaced(newRef) => return newRef
              case _ =>
            }
          }
          if (qname != null) {
            val selector: ScImportSelector = PsiTreeUtil.getParentOfType(this, classOf[ScImportSelector])
            val importExpr = PsiTreeUtil.getParentOfType(this, classOf[ScImportExpr])
            if (selector != null) {
              selector.deleteSelector() //we can't do anything here, so just simply delete it
              return importExpr.reference.get //todo: what we should return exactly?
              //              }
            } else if (importExpr != null) {
              if (importExpr == getParent && !importExpr.singleWildcard && importExpr.selectorSet.isEmpty) {
                val holder = PsiTreeUtil.getParentOfType(this, classOf[ScImportsHolder])
                importExpr.deleteExpr()
                c match {
                  case ClassTypeToImport(clazz) => holder.addImportForClass(clazz)
                  case ta => holder.addImportForPath(ta.qualifiedName)
                }
                //todo: so what to return? probable PIEAE after such code invocation
              } else {
                //qualifier reference in import expression
                qname match {
                  case CheckedAndReplaced(newRef) => return newRef
                  case _ =>
                }
              }
            }
            else {
              return safeBindToElement(qname, {
                case (qual, true) => createReferenceFromText(qual, getContext, this)
                case (qual, false) => createReferenceFromText(qual)
              }) {
                c match {
                  case ClassTypeToImport(clazz) =>
                    ScalaImportTypeFix.getImportHolder(ref = this, project = getProject).
                      addImportForClass(clazz, ref = this)
                  case ta =>
                    ScalaImportTypeFix.getImportHolder(ref = this, project = getProject).
                      addImportForPath(ta.qualifiedName, ref = this)
                }
                if (qualifier.isDefined) {
                  //let's make our reference unqualified
                  val ref: ScStableCodeReferenceElement = createReferenceFromText(c.name)
                  this.replace(ref).asInstanceOf[ScReferenceElement]
                }
                this
              }
            }
          }
          this
        }
        element match {
          case c: PsiClass => bindToType(ClassTypeToImport(c))
          case ta: ScTypeAlias =>
            if (ta.containingClass != null && ScalaPsiUtil.hasStablePath(ta)) {
              bindToType(TypeAliasToImport(ta))
            } else {
              //todo: nothing to do yet, probably in future it would be great to implement something context-specific
              this
            }
          case binding: ScBindingPattern =>
            binding.nameContext match {
              case member: ScMember =>
                val containingClass = member.containingClass
                val refToClass = bindToElement(containingClass)
                val refToMember = createReferenceFromText(refToClass.getText + "." + binding.name)
                this.replace(refToMember).asInstanceOf[ScReferenceElement]
            }
          case fun: ScFunction if Seq("unapply", "unapplySeq").contains(fun.name) && ScalaPsiUtil.hasStablePath(fun) =>
            bindToElement(fun.containingClass)
          case fun: ScFunction if fun.isConstructor =>
            bindToElement(fun.containingClass)
          case m: PsiMethod if m.isConstructor =>
            bindToElement(m.getContainingClass)
          case pckg: PsiPackage => bindToPackage(pckg)
          case _ => throw new IncorrectOperationException(s"Cannot bind to $element")
        }
      }
    }
  }

  private def reportWrongKind(c: TypeToImport, suitableKinds: Set[ResolveTargets.Value]): Nothing = {
    val contextText = if (getContext != null)
      if (getContext.getContext != null)
        if (getContext.getContext.getContext != null)
          getContext.getContext.getContext.getText
        else getContext.getContext.getText
      else getContext.getText
    else getText
    throw new IncorrectOperationException(
      s"""${c.element} does not match expected kind,
         |kinds: ${suitableKinds.mkString(", ")}
         |problem place: $refName in
         |$contextText""".stripMargin)
  }

  def getSameNameVariants: Array[ResolveResult] = allVariantsCached.collect {
    case rr @ ResolveResultEx(named: PsiNamedElement) if named.name == refName => rr
  }

  @CachedMappedWithRecursionGuard(this, Array.empty, CachesUtil.enclosingModificationOwner(this))
  private def allVariantsCached: Array[ResolveResult] = {
    val refThis = ScStableCodeReferenceElementImpl.this
    doResolve(refThis, new CompletionProcessor(getKinds(incomplete = true), refThis))
  }

  override def delete() {
    getContext match {
      case sel: ScImportSelector => sel.deleteSelector()
      case expr: ScImportExpr => expr.deleteExpr()
      case _ => super.delete()
    }
  }
}
