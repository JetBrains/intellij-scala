package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScStableReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaElementVisitor, ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createIdentifier, createReferenceFromText}
import org.jetbrains.plugins.scala.lang.psi.light.isWrapper
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.{isIdentifier, isKeyword}
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

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

  private def patternNeedBackticks(name: String) = name != "" && name.charAt(0).isLower && getParent.isInstanceOf[ScStableReferencePattern]

  override def getElement: ScReference = this

  override def getRangeInElement: TextRange = {
    val start = nameId.getTextRange.getStartOffset - getTextRange.getStartOffset
    val len = getTextLength
    new TextRange(start, len)
  }

  override def getCanonicalText: String = {
    resolve() match {
      case clazz: ScObject if clazz.isStatic => clazz.qualifiedName
      case c: ScTypeDefinition => if (c.containingClass == null) c.qualifiedName else c.name
      case c: PsiClass => c.qualifiedName
      case n: PsiNamedElement => n.name
      case _ => refName
    }
  }

  override def isSoft: Boolean = false

  override def handleElementRename(newElementName: String): PsiElement = {
    val needBackticks = patternNeedBackticks(newElementName) || isKeyword(newElementName)
    val newName = if (needBackticks) "`" + newElementName + "`" else newElementName
    if (!isIdentifier(newName)) return this
    val id = nameId.getNode
    val parent = id.getTreeParent
    parent.replaceChild(id, createIdentifier(newName))
    this
  }

  override def isReferenceTo(element: PsiElement): Boolean = {
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
              case _ =>
            }
          case obj: ScObject if obj.isSyntheticObject =>
            ScalaPsiUtil.getCompanionModule(td) match {
              case Some(typeDef) if typeDef == obj => return true
              case _ =>
            }
          case _ =>
        }

        (td, resolved) match {
          case (obj: ScObject, pkg: PsiPackage)
            if obj.isPackageObject
              && obj.getManager == pkg.getManager
              && obj.qualifiedName == pkg.getQualifiedName =>
            return true
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

  private def isSyntheticForCaseClass(method: ScFunction,
                                      templateDefinition: ScTemplateDefinition): Boolean =
    templateDefinition match {
      case cl: ScClass if cl.isCase && method.isSynthetic =>
        ScalaPsiUtil.getCompanionModule(cl).exists(isReferenceTo)
      case _ => false
    }

  /**
   * Is `resolved` (the resolved target of this reference) itself a reference to `element`, by way of a type alias defined in a object, such as:
   *
   * object Predef { type Throwable = java.lang.Throwable }
   *
   * [[https://youtrack.jetbrains.net/issue/SCL-3132 SCL-3132]]
   *
   * Corresponding references are used in FindUsages, but filtered from Rename
   */
  def isIndirectReferenceTo(@Nullable resolved: PsiElement, element: PsiElement): Boolean = {
    if (resolved == null)
      return false
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
        // https://contributors.scala-lang.org/t/transparent-term-aliases/5553

        val originalElement = element.getOriginalElement
        if (originalElement != element) isReferenceTo(originalElement, resolved, this.bind())
        else false
    }
  }

  def qualifier: Option[ScalaPsiElement]

  //provides the set of possible namespace alternatives based on syntactic position
  def getKinds(incomplete: Boolean, completion: Boolean = false): Set[ResolveTargets.Value]

  def completionVariants(withImplicitConversions: Boolean = false): Array[ScalaResolveResult]

  def getSameNameVariants: Array[ScalaResolveResult]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitReference(this)
  }

  protected def safeBindToElement[T <: ScReference](qualName: String, referenceCreator: (String, Boolean) => T)
                                                   (simpleImport: => PsiElement): PsiElement = {
    val parts: Array[String] = qualName.split('.')
    val last = parts.last
    assert(last.trim.nonEmpty, s"Empty last part with safe bind to element with qualName: '$qualName'")
    val anotherRef: T = referenceCreator(last, true)
    val resolve = anotherRef.multiResolveScala(false)
    def checkForPredefinedTypes(): Boolean = {
      if (resolve.isEmpty) return true

      val hasUsedImports = resolve.exists(_.importsUsed.nonEmpty)
      if (hasUsedImports) return false

      val usedNames = resolve.map(_.name).toSet

      var reject = false
      getContainingFile.accept(new ScalaRecursiveElementVisitor {
        override def visitReference(ref: ScReference): Unit = {
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
                var res = true
                ScImportsHolder(this).accept(new ScalaRecursiveElementVisitor {
                  //Override also visitReferenceExpression! and visitTypeProjection!
                  override def visitReference(ref: ScReference): Unit = {
                    ref.qualifier match {
                      case Some(_) =>
                      case None =>
                        if (!ref.getParent.is[ScImportSelector]) {
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
            val holder = ScImportsHolder.forNewImportInsertion(this)
            holder.addImportForPath(packagePart, this)
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
    inWriteAction {
      if (addImport) {
        if (!isPackageAlreadyImported(qualifiedName)) {
          val holder = ScImportsHolder.forNewImportInsertion(this)
          holder.addImportForPath(qualifiedName, refsContainer = this)
        }
      }

      val refText = if (addImport) pckg.getName else qualifiedName
      this match {
        case stRef: ScStableCodeReference =>
          stRef.replace(createReferenceFromText(refText))
        case ref: ScReferenceExpression =>
          ref.replace(createExpressionFromText(refText, ref))
        case _ => null
      }
    }
  }

  private def isPackageAlreadyImported(packageFqn: String): Boolean = {
    val refStartOffset = this.startOffset
    //using option only for easier debugging
    val alreadyImportedPackage: Option[ScImportExpr] =
      (for {
        importHolder <- this.withParentsInFile.filterByType[ScImportsHolder]
        importStmt   <- importHolder.getImportStatements.iterator.takeWhile(_.startOffset < refStartOffset)
        importExpr   <- importStmt.importExprs.iterator
        importUsed   <- ImportUsed.buildAllFor(importExpr).iterator
        if isImportOfPackage(importUsed, packageFqn)
      } yield importExpr).nextOption()
    alreadyImportedPackage.isDefined
  }

  private def isImportOfPackage(importUsed: ImportUsed, packageFqn: String): Boolean = {
    val refOpt: Option[ScStableCodeReference] = importUsed match {
      case ImportExprUsed(expr)                            => expr.reference
      case ImportSelectorUsed(sel) if !sel.isAliasedImport => sel.reference
      case _ => None
    }

    refOpt.exists { ref =>
      val resolveResult = ref.multiResolveScala(false)
      resolveResult.exists(rr => rr.getElement match {
        case p: ScPackage => p.getQualifiedName == packageFqn
        case p: PsiPackage => p.getQualifiedName == packageFqn
        case _ => false
      })
    }
  }
}

object ScReference {
  def unapply(e: ScReference): Option[PsiElement] = Option(e.resolve())

  object qualifier {
    def unapply(ref: ScReference): Option[ScalaPsiElement] = ref.qualifier
  }
}
