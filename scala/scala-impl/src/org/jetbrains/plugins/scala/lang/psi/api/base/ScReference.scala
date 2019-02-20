package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.TypeToImport
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScStableReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportExprUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createIdentifier, createReferenceFromText}
import org.jetbrains.plugins.scala.lang.psi.light.isWrapper
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.{isIdentifier, isKeyword}
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.collection.Set

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

trait ScReference extends ScalaPsiElement with PsiPolyVariantReference {
  override def getReference: ScReference = this

  def nameId: PsiElement

  def refName: String = {
    assert(nameId != null, s"nameId is null for reference with text $getText; parent: ${getParent.getText}")
    nameId.getText
  }

  def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult]

  @deprecated("Is required for compatibility. Prefer `multiResolveScala` for better type inference.", "2018.1")
  override final def multiResolve(incomplete: Boolean): Array[ResolveResult] = multiResolveScala(incomplete).toArray

  def bind(): Option[ScalaResolveResult]

  private def isBackQuoted = {
    val id: PsiElement = nameId
    assert(id != null, s"nameId is null for reference with text: $getText")
    val text: String = id.getText
    text.charAt(0) == '`' && text.length > 1
  }

  private def patternNeedBackticks(name: String) = name != "" && name.charAt(0).isLower && getParent.isInstanceOf[ScStableReferencePattern]

  def getElement: ScReference = this

  def getRangeInElement: TextRange = {
    val start = nameId.getTextRange.getStartOffset - getTextRange.getStartOffset
    val len = getTextLength
    new TextRange(start, len)
  }

  def getCanonicalText: String = {
    resolve() match {
      case clazz: ScObject if clazz.isStatic => clazz.qualifiedName
      case c: ScTypeDefinition => if (c.containingClass == null) c.qualifiedName else c.name
      case c: PsiClass => c.qualifiedName
      case n: PsiNamedElement => n.name
      case _ => refName
    }
  }

  def isSoft: Boolean = false

  def handleElementRename(newElementName: String): PsiElement = {
    val needBackticks = patternNeedBackticks(newElementName) || isKeyword(newElementName)
    val newName = if (needBackticks) "`" + newElementName + "`" else newElementName
    if (!isIdentifier(newName)) return this
    val id = nameId.getNode
    val parent = id.getTreeParent
    parent.replaceChild(id, createIdentifier(newName))
    this
  }

  def isReferenceTo(element: PsiElement): Boolean = {
    element match {
      case _: ScClassParameter =>
      case param: ScParameter if !PsiTreeUtil.isContextAncestor(param.owner, this, true) =>
        getParent match {
          case ScAssignment(left, _) if left == this =>
          case _ => return false
        }
      case _ =>
    }
    val iterator = multiResolveScala(false).iterator
    while (iterator.hasNext) {
      val resolved = iterator.next()
      if (isReferenceTo(element, resolved.getElement, Some(resolved))) return true
    }
    false
  }

  def createReplacingElementWithClassName(useFullQualifiedName: Boolean, clazz: TypeToImport): ScReference =
    createReferenceFromText(if (useFullQualifiedName) clazz.qualifiedName else clazz.name)(clazz.element.getManager)

  def isReferenceTo(element: PsiElement, resolved: PsiElement, rr: Option[ScalaResolveResult]): Boolean = {
    if (ScEquivalenceUtil.smartEquivalence(resolved, element)) return true
    resolved match {
      case isWrapper(named) => return isReferenceTo(element, named, rr)
      case _ =>
    }
    element match {
      case isWrapper(named) => return isReferenceTo(named, resolved, rr)
      case td: ScTypeDefinition =>
        resolved match {
          case Constructor(constr) =>
            if (ScEquivalenceUtil.smartEquivalence(td, constr.containingClass)) return true
          case method: ScFunction if Set("apply", "unapply", "unapplySeq").contains(method.name) =>
            if (isSyntheticForCaseClass(method, td)) return true

            rr match {
              case Some(srr) =>
                srr.getActualElement match {
                  case c: PsiClass if c.sameOrInheritor(td) => return true
                  case _ =>
                }
              case _ if method.containingClass.sameOrInheritor(td) => return true
            }
          case obj: ScObject if obj.isSyntheticObject =>
            ScalaPsiUtil.getCompanionModule(td) match {
              case Some(typeDef) if typeDef == obj => return true
              case _ =>
            }
          case _ =>
        }
      case c: PsiClass =>
        resolved match {
          case Constructor(constr) =>
            if (c == constr.containingClass) return true
          case _ =>
        }
      case _: ScTypeAliasDefinition if resolved.isInstanceOf[ScPrimaryConstructor] =>
        this.bind() match {
          case Some(r) =>
            r.parentElement match {
              case Some(ta: ScTypeAliasDefinition) if ScEquivalenceUtil.smartEquivalence(ta, element) => return true
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }
    isIndirectReferenceTo(resolved, element)
  }

  private def isSyntheticForCaseClass(method: ScFunction, td: ScTemplateDefinition): Boolean = {
    td match {
      case cl: ScClass if cl.isCase && method.isSynthetic =>
        ScalaPsiUtil.getCompanionModule(td).exists(isReferenceTo)
      case _ => false
    }
  }

  /**
   * Is `resolved` (the resolved target of this reference) itself a reference to `element`, by way of a type alias defined in a object, such as:
   *
   * object Predef { type Throwable = java.lang.Throwable }
   *
   * [[http://youtrack.jetbrains.net/issue/SCL-3132 SCL-3132]]
   *
   * Corresponding references are used in FindUsages, but filtered from Rename
   */
  def isIndirectReferenceTo(resolved: PsiElement, element: PsiElement): Boolean = {
    if (resolved == null) return false
    (resolved, element) match {
      case (typeAlias: ScTypeAliasDefinition, cls: PsiClass) =>
        typeAlias.isExactAliasFor(cls)
      case (_: ScPrimaryConstructor, cls: PsiClass) =>
        this.bind() match {
          case Some(r) =>
            r.parentElement match {
              case Some(ta: ScTypeAliasDefinition) => ta.isExactAliasFor(cls)
              case _ => false
            }
          case None => false
        }
      case _ =>
        // TODO indirect references via vals, e.g. `package object scala { val List = scala.collection.immutable.List }` ?

        val originalElement = element.getOriginalElement
        if (originalElement != element) isReferenceTo(originalElement, resolved, this.bind())
        else false
    }
  }

  def qualifier: Option[ScalaPsiElement]

  //provides the set of possible namespace alternatives based on syntactic position
  def getKinds(incomplete: Boolean, completion: Boolean = false): Set[ResolveTargets.Value]

  def completionVariants(implicits: Boolean = false): Seq[ScalaLookupItem]

  def getSameNameVariants: Array[ScalaResolveResult]

  override protected def acceptScala(visitor: ScalaElementVisitor) {
    visitor.visitReference(this)
  }

  protected def safeBindToElement[T <: ScReference](qualName: String, referenceCreator: (String, Boolean) => T)
                                                   (simpleImport: => PsiElement): PsiElement = {
    val parts: Array[String] = qualName.split('.')
    val last = parts.last
    assert(!last.trim.isEmpty, s"Empty last part with safe bind to element with qualName: '$qualName'")
    val anotherRef: T = referenceCreator(last, true)
    val resolve = anotherRef.multiResolveScala(false)
    def checkForPredefinedTypes(): Boolean = {
      if (resolve.isEmpty) return true

      val hasUsedImports = resolve.exists(_.importsUsed.nonEmpty)
      if (hasUsedImports) return false

      val usedNames = resolve.map(_.name).toSet

      var reject = false
      getContainingFile.accept(new ScalaRecursiveElementVisitor {
        override def visitReference(ref: ScReference) {
          if (reject) return
          if (usedNames.contains(ref.refName)) {
            ref.bind() match {
              case Some(r) if ref != ScReference.this && r.importsUsed.isEmpty =>
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
    val prefixImport = ScalaCodeStyleSettings.getInstance(getProject).hasImportWithPrefix(qualName)
    if (!prefixImport && checkForPredefinedTypes()) {
      simpleImport
    } else {
      if (qualName.contains(".")) {
        var index =
          if (ScalaCodeStyleSettings.getInstance(getProject).isImportShortestPathForAmbiguousReferences) parts.length - 2
          else 0
        while (index >= 0) {
          val packagePart = parts.take(index + 1).mkString(".")
          val toReplace = parts.drop(index).mkString(".")
          val ref: T = referenceCreator(toReplace, true)
          var qual = ref
          while (qual.qualifier.isDefined) qual = qual.qualifier.get.asInstanceOf[T]
          val resolve = qual.multiResolveScala(false)
          def isOk: Boolean = {
            if (packagePart == "java.util") return true //todo: fix possible clashes?
            if (resolve.length == 0) true
            else if (resolve.length > 1) false
            else {
              val result = resolve(0)
              def smartCheck: Boolean = {
                val holder = ScalaImportTypeFix.getImportHolder(this, getProject)
                var res = true
                holder.accept(new ScalaRecursiveElementVisitor {
                  //Override also visitReferenceExpression! and visitTypeProjection!
                  override def visitReference(ref: ScReference) {
                    ref.qualifier match {
                      case Some(_) =>
                      case None =>
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
            ScalaImportTypeFix.getImportHolder(this, getProject).addImportForPath(packagePart, this)
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

  def bindToPackage(pckg: PsiPackage, addImport: Boolean = false): PsiElement = {
    val qualifiedName = pckg.getQualifiedName
    extensions.inWriteAction {
      val refText =
        if (addImport) {
          val importHolder = ScalaImportTypeFix.getImportHolder(ref = this, project = getProject)
          val imported = importHolder.getAllImportUsed.exists {
            case ImportExprUsed(expr) => expr.reference.exists { ref =>
              ref.multiResolveScala(false).exists(rr => rr.getElement match {
                case p: ScPackage => p.getQualifiedName == qualifiedName
                case p: PsiPackage => p.getQualifiedName == qualifiedName
                case _ => false
              })
            }
            case _ => false
          }
          if (!imported) importHolder.addImportForPath(qualifiedName, ref = this)
          pckg.getName
        } else qualifiedName
      this match {
        case stRef: ScStableCodeReference =>
          stRef.replace(createReferenceFromText(refText))
        case ref: ScReferenceExpression =>
          ref.replace(createExpressionFromText(refText))
        case _ => null
      }
    }
  }

}

object ScReference {
  def unapply(e: ScReference): Option[PsiElement] = Option(e.resolve())

  object qualifier {
    def unapply(ref: ScReference): Option[ScalaPsiElement] = ref.qualifier
  }
}
