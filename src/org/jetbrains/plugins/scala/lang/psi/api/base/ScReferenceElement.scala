package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import _root_.org.jetbrains.plugins.scala.lang.resolve._
import _root_.scala.collection.Set
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._
import com.intellij.openapi.util.TextRange
import refactoring.util.ScalaNamesUtil
import statements.{ScTypeAliasDefinition, ScFunction}
import toplevel.typedef._
import psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil
import extensions.{toPsiMemberExt, toPsiNamedElementExt, toPsiClassExt}
import settings.ScalaProjectSettings
import annotator.intention.ScalaImportClassFix
import collection.mutable.HashSet
import toplevel.imports.ScImportSelector

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

trait ScReferenceElement extends ScalaPsiElement with ResolvableReferenceElement {
  override def getReference = this

  def nameId: PsiElement

  def refName: String = {
    val text: String = nameId.getText
    if (text.charAt(0) == '`' && text.length > 1) text.substring(1, text.length - 1)
    else text
  }

  def getElement = this

  def getRangeInElement: TextRange =
    new TextRange(nameId.getTextRange.getStartOffset - getTextRange.getStartOffset, getTextLength)

  def getCanonicalText: String = null

  def isSoft: Boolean = false

  def handleElementRename(newElementName: String): PsiElement = {
    if (!ScalaNamesUtil.isIdentifier(newElementName)) return this
    val isQuoted = nameId.getText.startsWith("`")
    val id = nameId.getNode
    val parent = id.getTreeParent
    parent.replaceChild(id,
      ScalaPsiElementFactory.createIdentifier(if (isQuoted) "`" + newElementName + "`" else newElementName, getManager))
    this
  }

  def isReferenceTo(element: PsiElement): Boolean = {
    val iterator = multiResolve(false).iterator
    while (iterator.hasNext) {
      val resolved = iterator.next()
      if (isReferenceTo(element, resolved.getElement)) return true
    }
    false
  }

  def createReplacingElementWithClassName(useFullQualifiedName: Boolean, clazz: PsiClass): ScReferenceElement =
    ScalaPsiElementFactory.createReferenceFromText(
      if (useFullQualifiedName) clazz.qualifiedName else clazz.name, clazz.getManager)

  def isReferenceTo(element: PsiElement, resolved: PsiElement): Boolean = {
    if (ScEquivalenceUtil.smartEquivalence(resolved, element)) return true
    element match {
      case td: ScTypeDefinition => {
        resolved match {
          case method: PsiMethod if method.isConstructor =>
            if (ScEquivalenceUtil.smartEquivalence(td, method.containingClass)) return true
          case method: ScFunction if td.name == refName && Set("apply", "unapply", "unapplySeq").contains(method.name) => {
            var break = false
            val methods = td.allMethods
            for (n <- methods if !break) {
              if (n.method.name == method.name) {
                val methodContainingClass: ScTemplateDefinition = method.containingClass
                val nodeMethodContainingClass: PsiClass = n.method.containingClass
                val classesEquiv: Boolean = ScEquivalenceUtil.smartEquivalence(methodContainingClass, nodeMethodContainingClass)
                if (classesEquiv)
                  break = true
              }
            }

            if (!break && method.getText.contains("throw new Error()") && td.isInstanceOf[ScClass] &&
              td.asInstanceOf[ScClass].isCase) {
              ScalaPsiUtil.getCompanionModule(td) match {
                case Some(td) => return isReferenceTo(td)
                case _ =>
              }
            }
            if (break) return true
          }
          case obj: ScObject if td.name == refName && obj.isSyntheticObject => {
            ScalaPsiUtil.getCompanionModule(td) match {
              case Some(td) if td == obj => return true
              case _ =>
            }
          }
          case _ =>
        }
      }
      case c: PsiClass if c.name == refName => {
        resolved match {
          case method: PsiMethod if method.isConstructor =>
            if (c == method.containingClass) return true
          case _ =>
        }
      }
      case _ =>
    }
    isIndirectReferenceTo(resolved, element)
  }

  /**
   * Is `resolved` (the resolved target of this reference) itself a reference to `element`, by way of a type alias defined in a object, such as:
   *
   * object Predef { type Throwable = java.lang.Throwable }
   *
   * [[http://youtrack.jetbrains.net/issue/SCL-3132 SCL-3132]]
   */
  def isIndirectReferenceTo(resolved: PsiElement, element: PsiElement): Boolean = {
    if (resolved == null) return false
    (resolved, element) match {
      case (typeAlias: ScTypeAliasDefinition, cls: PsiClass) =>
        typeAlias.isExactAliasFor(cls)
      case _ =>
        // TODO indirect references via vals, e.g. `package object scala { val List = scala.collection.immutable.List }` ?

        val originalElement = element.getOriginalElement
        if (originalElement != element) isReferenceTo(originalElement, resolved)
        else false
    }
  }

  def qualifier: Option[ScalaPsiElement]

  //provides the set of possible namespace alternatives based on syntactic position
  def getKinds(incomplete: Boolean, completion: Boolean = false): Set[ResolveTargets.Value]

  def getVariants(implicits: Boolean, filterNotNamedVariants: Boolean): Array[Object] = getVariants()

  def getSameNameVariants: Array[ResolveResult]

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitReference(this)
  }

  protected def safeBindToElement[T <: ScReferenceElement](qualName: String, referenceCreator: (String, Boolean) => T)
                                                        (simpleImport: => PsiElement): PsiElement = {
    val parts: Array[String] = qualName.split('.')
    val anotherRef: T = referenceCreator(parts.last, true)
    val resolve: Array[ResolveResult] = anotherRef.multiResolve(false)
    def checkForPredefinedTypes(): Boolean = {
      if (resolve.isEmpty) return true
      val usedNames = new HashSet[String]()
      val res = resolve.forall {
        case r: ScalaResolveResult if r.importsUsed.isEmpty => usedNames += r.name; true
        case _ => false
      }
      if (!res) return false
      var reject = false
      getContainingFile.accept(new ScalaRecursiveElementVisitor {
        override def visitReference(ref: ScReferenceElement) {
          if (reject) return
          if (usedNames.contains(ref.refName)) {
            ref.bind() match {
              case Some(r: ScalaResolveResult) if ref != ScReferenceElement.this && r.importsUsed.isEmpty =>
                reject = true
                return
              case _ =>
            }
          }
          super.visitReference(ref)
        }
      })
      !reject
    }
    val prefixImport = ScalaProjectSettings.getInstance(getProject).hasImportWithPrefix(qualName)
    if (!prefixImport && checkForPredefinedTypes()) {
      simpleImport
    } else {
      if (qualName.contains(".")) {
        var index =
          if (ScalaProjectSettings.getInstance(getProject).isImportShortestPathForAmbiguousReferences) parts.length - 2
          else 0
        while (index >= 0) {
          val packagePart = parts.take(index + 1).mkString(".")
          val toReplace = parts.drop(index).mkString(".")
          val ref: T = referenceCreator(toReplace, true)
          var qual = ref
          while (qual.qualifier != None) qual = qual.qualifier.get.asInstanceOf[T]
          val resolve: Array[ResolveResult] = qual.multiResolve(false)
          def isOk: Boolean = {
            if (packagePart == "java.util") return true //todo: fix possible clashes?
            if (resolve.length == 0) true
            else if (resolve.length > 1) false
            else {
              val result: ResolveResult = resolve(0)
              def smartCheck: Boolean = {
                val holder = ScalaImportClassFix.getImportHolder(this, getProject)
                var res = true
                holder.accept(new ScalaRecursiveElementVisitor {
                  //Override also visitReferenceExpression! and visitTypeProjection!
                  override def visitReference(ref: ScReferenceElement) {
                    ref.qualifier match {
                      case Some(qual) =>
                      case _ =>
                        if (!ref.getParent.isInstanceOf[ScImportSelector]) {
                          if (ref.refName == parts(index)) res = false
                        }
                    }
                  }
                })
                res
              }
              result match {
                case r@ScalaResolveResult(pack: PsiPackage, _) =>
                  if (pack.getQualifiedName == packagePart) true
                  else if (r.importsUsed.isEmpty) smartCheck
                  else false
                case r@ScalaResolveResult(c: PsiClass, _) =>
                  if (c.qualifiedName == packagePart) true
                  else if (r.importsUsed.isEmpty) smartCheck
                  else false
                case _ => smartCheck
              }
            }
          }
          if (isOk) {
            ScalaImportClassFix.getImportHolder(this, getProject).addImportForPath(packagePart, this)
            val ref = referenceCreator(toReplace, false)
            return this.replace(ref)
          }
          index -= 1
        }
      }
      val ref: T = referenceCreator("_root_." + qualName, false)
      this.replace(ref)
    }
  }
}
