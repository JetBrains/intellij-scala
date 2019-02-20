package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.impl.source.JavaDummyHolder
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.{ClassTypeToImport, TypeAliasToImport, TypeToImport}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.macros.MacroDef
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScConstructorPattern, ScInfixPattern, ScInterpolationPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReferenceExpression, ScSuperReference, ScThisReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScMacroDefinition._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScMacroDefinition, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScPackaging, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScReferenceImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor._
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, CompletionProcessor, ExtractorResolveProcessor}
import org.jetbrains.plugins.scala.lang.resolve.{StableCodeReferenceResolver, _}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocResolvableCodeReference, ScDocSyntaxElement}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}
import org.jetbrains.plugins.scala.worksheet.ammonite.AmmoniteUtil

/**
 * @author AlexanderPodkhalyuzin
 *         Date: 22.02.2008
 */

class ScStableCodeReferenceImpl(node: ASTNode) extends ScReferenceImpl(node) with ScStableCodeReference {

  def getResolveResultVariants: Array[ScalaResolveResult] =
    doResolve(new CompletionProcessor(getKinds(incomplete = true), this))

  def getConstructor: Option[ScConstructor] =
    getContext.asOptionOf[ScSimpleTypeElement]
      .flatMap(_.findConstructor)

  def isConstructorReference: Boolean = getConstructor.nonEmpty

  override def toString: String = "CodeReferenceElement: " + ifReadAllowed(getText)("")

  def getKinds(incomplete: Boolean, completion: Boolean): Set[ResolveTargets.Value] = {
    import org.jetbrains.plugins.scala.lang.resolve.StdKinds._

    val result = getContext match {
      case contextRef: ScStableCodeReference =>
        //Since scala 2.11 it's possible macro implementations not only as static methods,
        //but also inside certain classes, so qualifier of a macro impl reference may resolve to a class
        //see http://docs.scala-lang.org/overviews/macros/bundles.html
        if (isMacroImplReference(contextRef)) stableQualOrClass
        else stableQualRef
      case e: ScImportExpr => if (e.selectorSet.isDefined
              //import Class._ is not allowed
        || qualifier.isEmpty || e.isSingleWildcard) stableQualRef
      else stableImportSelector
      case ste: ScSimpleTypeElement =>
        if (incomplete) noPackagesClassCompletion // todo use the settings to include packages
        else if (ste.getLastChild.isInstanceOf[PsiErrorElement]) stableQualRef

        else if (ste.singleton) stableQualRef
        else if (ste.annotation) annotCtor
        else stableClass
      case _: ScTypeAlias => stableClass
      case _: ScInterpolationPattern => stableImportSelector
      case _: ScConstructorPattern => objectOrValue
      case _: ScInfixPattern => objectOrValue
      case _: ScThisReference | _: ScSuperReference => stableClassOrObject
      case _: ScImportSelector => stableImportSelector
      case _: ScInfixTypeElement => stableClass
      case _: ScMacroDefinition => methodsOnly //reference in macro definition may be to method only
      case _: ScDocSyntaxElement => stableImportSelector
      case _ => stableQualRef
    }
    if (completion) result + ResolveTargets.PACKAGE + ResolveTargets.OBJECT + ResolveTargets.VAL else result
  }

  def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)

  //  @throws(IncorrectOperationException)
  def bindToElement(element: PsiElement): PsiElement = {
    def isCorrectReference(text: String): Option[ScStableCodeReference] = {
      val ref = createReferenceFromText(text, getContext, ScStableCodeReferenceImpl.this)

      if (ref.isReferenceTo(element)) Some(ref)
      else None
    }

    def replaceThisBy(ref: ScStableCodeReference): PsiElement =
      ScStableCodeReferenceImpl.this.replace(ref)

    //this method may remove unnecessary imports
    def bindImportReference(c: ScalaImportTypeFix.TypeToImport): Boolean = {
      val selector: ScImportSelector = PsiTreeUtil.getParentOfType(this, classOf[ScImportSelector])
      val importExpr = PsiTreeUtil.getParentOfType(this, classOf[ScImportExpr])

      if (selector == null && importExpr == null)
        return false

      if (selector != null) {
        selector.deleteSelector()
      }
      else if (importExpr != null) {
        if (importExpr == getParent && !importExpr.isSingleWildcard && importExpr.selectorSet.isEmpty) {
          val holder = PsiTreeUtil.getParentOfType(this, classOf[ScImportsHolder])
          importExpr.deleteExpr()
          c match {
            case ClassTypeToImport(clazz) => holder.addImportForClass(clazz)
            case ta => holder.addImportForPath(ta.qualifiedName)
          }
        } else {
          //qualifier reference in import expression
          isCorrectReference(c.name)
            .orElse(isCorrectReference(c.qualifiedName))
            .map(replaceThisBy)
        }
      }
      true
    }


    if (isReferenceTo(element)) this
    else {
      val aliasedRef: Option[ScReference] = ScalaPsiUtil.importAliasFor(element, this)
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
            return this.replace(ref).asInstanceOf[ScStableCodeReference].bindToElement(element)
          }
          val qname = c.qualifiedName
          val isPredefined = ScalaCodeStyleSettings.getInstance(getProject).hasImportWithPrefix(qname)

          if (bindImportReference(c)) {
            //so we may return invalidated reference when current reference was part of import expression
            //probably it's better than null if import statement or selector was removed completely
            this
          }
          else {
            if (qualifier.isDefined && !isPredefined) {
              isCorrectReference(c.name) match {
                case Some(newRef) => return replaceThisBy(newRef)
                case _ =>
              }
            }
            if (qname != null) {
              safeBindToElement(qname, {
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
                  val ref: ScStableCodeReference = createReferenceFromText(c.name)
                  this.replace(ref).asInstanceOf[ScReference]
                }
                this
              }
            }
            else this
          }
        }
        element match {
          case c: PsiClass =>
            val suitableKinds = getKinds(incomplete = false)
            if (!ResolveUtils.kindMatches(element, suitableKinds)) {
              ScalaPsiUtil.getCompanionModule(c) match {
                case Some(companion) => bindToType(ClassTypeToImport(companion))
                case None => bindToType(ClassTypeToImport(c))
              }
            }
            else bindToType(ClassTypeToImport(c))
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
                this.replace(refToMember).asInstanceOf[ScReference]
            }
          case fun: ScFunction if Seq("unapply", "unapplySeq").contains(fun.name) && ScalaPsiUtil.hasStablePath(fun) =>
            bindToElement(fun.containingClass)
          case AuxiliaryConstructor(constr)  =>
            bindToElement(constr.containingClass)
          case JavaConstructor(constr) =>
            bindToElement(constr.containingClass)
          case pckg: PsiPackage => bindToPackage(pckg)
          case _ => throw new IncorrectOperationException(s"Cannot bind to $element")
        }
      }
    }
  }

  private def contextsElementKinds: String = {
    val contextsFromFile = this.withContexts.takeWhile(!_.isInstanceOf[PsiFile]).toList.reverse
    val elementTypes = contextsFromFile.map(_.getNode.getElementType.toString)
    elementTypes.zipWithIndex.map {
      case (name, idx) => "  " * idx + name
    }.mkString("\n")
  }

  private def reportWrongKind(c: TypeToImport, suitableKinds: Set[ResolveTargets.Value]): Nothing = {
    val contextText = contextsElementKinds
    throw new IncorrectOperationException(
      s"""${c.element} does not match expected kind,
         |kinds: ${suitableKinds.mkString(", ")}
         |problem place: $refName in
         |$contextText""".stripMargin)
  }

  def getSameNameVariants: Array[ScalaResolveResult] = doResolve(new CompletionProcessor(getKinds(incomplete = true), this) {
    override protected val forName = Some(refName)
  })

  override def delete() {
    getContext match {
      case sel: ScImportSelector => sel.deleteSelector()
      case expr: ScImportExpr => expr.deleteExpr()
      case _ => super.delete()
    }
  }

  @CachedWithRecursionGuard(this, ScalaResolveResult.EMPTY_ARRAY, ModCount.getBlockModificationCount)
  override def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult] = {
    val resolver = new StableCodeReferenceResolver(ScStableCodeReferenceImpl.this, false, false, false)
    resolver.resolve(ScStableCodeReferenceImpl.this, incomplete)
  }

  protected def processQualifier(processor: BaseProcessor): Array[ScalaResolveResult] = {
    _qualifier() match {
      case None =>
        @scala.annotation.tailrec
        def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
          ProgressManager.checkCanceled()
          place match {
            case null =>
            case p: ScTypeElement if p.analog.isDefined =>
              // this allows the type elements in a context or view bound to be path-dependent types, based on parameters.
              // See ScalaPsiUtil.syntheticParamClause and StableCodeReferenceElementResolver#computeEffectiveParameterClauses
              if (!p.processDeclarations(processor, ResolveState.initial(), lastParent, this)) return
              treeWalkUp(p.analog.get, lastParent)
                // annotation should not walk through it's own annotee while resolving
            case p: ScAnnotationsHolder
              if processor.kinds.contains(ResolveTargets.ANNOTATION) && PsiTreeUtil.isContextAncestor(p, this, true) =>
                treeWalkUp(place.getContext, place)
            case p =>
              if (!p.processDeclarations(processor, ResolveState.initial, lastParent, this)) return
              place match {
                case _: ScTemplateBody | _: ScExtendsBlock => // template body and inherited members are at the same level.
                case _ => if (!processor.changedLevel) return
              }
              treeWalkUp(place.getContext, place)
          }
        }

        val startingPlace = getContext match {
          // when processing Foo.this.Bar or Foo.super[Bar].Baz, positioned in the extends block,
          // it is important to skip the contexts up to the actual outer type definition, or else
          // we may end up with weird self-references if the name is not unique (#SCL-14707, #SCL-14922)
          case ctx @ (_: ScSuperReference | _: ScThisReference) =>
            ResolveUtils.enclosingTypeDef(ctx).fold(this: PsiElement)(_.getContext)
          case _                                                => this
        }

        treeWalkUp(startingPlace, null)
        processor.candidates
      case Some(p: ScInterpolationPattern) =>
        val expr =
          createExpressionWithContextFromText(s"""_root_.scala.StringContext("").$refName""", p, this)
        expr match {
          case ref: ScReferenceExpression =>
            ref.doResolve(processor)
          case _ =>
        }
        processor.candidates
      case Some(q: ScDocResolvableCodeReference) =>
        q.multiResolveScala(incomplete = true)
          .flatMap(processQualifierResolveResult(q, _, processor))
      case Some(q: ScStableCodeReference) =>
        q.bind() match {
          case Some(res) =>
            processQualifierResolveResult(q, res, processor)
          case _ =>
            processor.candidates
        }
      case Some(thisQ: ScThisReference) =>
        for (ttype <- thisQ.`type`()) processor.processType(ttype, this)
        processor.candidates
      case Some(superQ: ScSuperReference) =>
        ResolveUtils.processSuperReference(superQ, processor, this).candidates
      case Some(qual) =>
        assert(assertion = false, s"Weird qualifier: ${qual.getClass}")
        processor.candidates
    }
  }

  protected def processQualifierResolveResult(qualifier: ScStableCodeReference,
                                              res: ScalaResolveResult,
                                              processor: BaseProcessor): Array[ScalaResolveResult] = {
    var withDynamicResult: Option[Array[ScalaResolveResult]] = None
    res match {
      case r@ScalaResolveResult(td: ScTypeDefinition, substitutor) =>
        val state = ResolveState.initial.put(ScSubstitutor.key, substitutor)

        td match {
          case obj: ScObject =>
            val fromType = r.fromType match {
              case Some(fType) => Right(ScProjectionType(fType, obj))
              case _ => td.`type`().map(substitutor)
            }
            fromType match {
              case Right(qualType) =>
                val stateWithType = state.put(BaseProcessor.FROM_TYPE_KEY, qualType)
                processor.processType(qualType, this, stateWithType)

                withDynamicResult = withDynamic(qualType, stateWithType, processor)
              case _ =>
                td.processDeclarations(processor, state, null, this)
            }
          case _: ScClass | _: ScTrait =>
            td.processDeclarations(processor, state, null, this)
        }
      case ScalaResolveResult(fun: ScFunction, _) =>
        val macroEvaluator = ScalaMacroEvaluator.getInstance(fun.getProject)
        val typeFromMacro = macroEvaluator.checkMacro(fun, MacroContext(qualifier, None))
        typeFromMacro.foreach(processor.processType(_, qualifier))
      case ScalaResolveResult((_: ScTypedDefinition) && Typeable(tp), s) =>
        val fromType = s(tp)
        val state = ResolveState.initial().put(BaseProcessor.FROM_TYPE_KEY, fromType)
        processor.processType(fromType, this, state)
        withDynamicResult = withDynamic(fromType, state, processor)
        processor match {
          case _: ExtractorResolveProcessor =>
            if (processor.candidatesS.isEmpty) {
              //check implicit conversions
              val expr =
                createExpressionWithContextFromText(getText, getContext, this)
              //todo: this is really hacky solution... Probably can be joint somehow with interpolated pattern.
              expr match {
                case ref: ScReferenceExpression =>
                  ref.doResolve(processor)
                case _ =>
              }
            }
          case _ => //do nothing
        }
      case ScalaResolveResult(field: PsiField, s) =>
        processor.processType(s(field.getType.toScType()), this)
      case ScalaResolveResult(clazz: PsiClass, _) =>
        processor.processType(ScDesignatorType.static(clazz), this) //static Java import
      case ScalaResolveResult(pack: ScPackage, s) =>
        pack.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, s),
          null, this)
      case other: ScalaResolveResult =>
        other.element.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, other.substitutor),
          null, this)
      case _ =>
    }

    withDynamicResult.getOrElse(processor.candidates)
  }

  private def withDynamic(qualType: ScType, state: ResolveState, processor: BaseProcessor): Option[Array[ScalaResolveResult]] = {
    if (processor.candidatesS.isEmpty && conformsToDynamic(qualType, getResolveScope)) {
      createExpressionWithContextFromText(getText, getContext, this) match {
        case rExpr @ ScReferenceExpression.withQualifier(qual) =>
          val dynamicProcessor = dynamicResolveProcessor(rExpr, qual, processor)
          dynamicProcessor.processType(qualType, qual, state)
          val candidatesWithMacro = dynamicProcessor.candidates.collect {
            case r @ ScalaResolveResult(MacroDef(_), _) => r //regular method call cannot be in a type position
          }
          Some(candidatesWithMacro).filter(_.length == 1)
        case _ => None
      }
    }
    else None
  }

  private def _qualifier() = {
    getContext match {
      case p: ScInterpolationPattern => Some(p)
      case sel: ScImportSelector =>
        sel.getContext /*ScImportSelectors*/ .getContext.asInstanceOf[ScImportExpr].reference
      case _ => pathQualifier
    }
  }

  def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean = true): Array[ScalaResolveResult] = {
    def candidatesFilter(result: ScalaResolveResult) = {
      result.element match {
        case c: PsiClass if c.name == c.qualifiedName => c.getContainingFile match {
          case _: ScalaFile => true // scala classes are available from default package
          /** in completion in [[ScalaFile]] [[JavaDummyHolder]] usually used as file */
          case dummyHolder: JavaDummyHolder
            if Option(dummyHolder.getContext).map(_.getContainingFile).exists(_.isInstanceOf[ScalaFile]) =>
            true
          // Other classes from default package are available only for top-level Scala statements
          case _ => PsiTreeUtil.getContextOfType(this, true, classOf[ScPackaging]) == null
        }
        case _ => true
      }
    }

    getContainingFile match {
      case ammoniteScript: ScalaFile if AmmoniteUtil.isAmmoniteFile(ammoniteScript) =>
        def psi2result(psiElement: PsiNamedElement) = Array(new ScalaResolveResult(psiElement))

        val fsi = AmmoniteUtil.scriptResolveQualifier(this)

        (fsi, fsi.flatMap(AmmoniteUtil.file2Object)) match {
          case (_, Some(obj)) =>
            return psi2result(obj)
          case (Some(dir), _) =>
            return psi2result(dir)
          case _ =>
            AmmoniteUtil.scriptResolveSbtDependency(this) match {
              case Some(dir) => return psi2result(dir)
              case _ =>
            }
        }
      case _ =>
    }

    val importStmt = PsiTreeUtil.getContextOfType(this, true, classOf[ScImportStmt])

    if (importStmt != null) {
      val importHolder = PsiTreeUtil.getContextOfType(importStmt, true, classOf[ScImportsHolder])
      if (importHolder != null) {
        val importExprs = importHolder.getImportStatements
          .takeWhile(_ != importStmt)
          .flatMap(_.importExprs)
          .filter(_.isSingleWildcard)
          .iterator

        while (importExprs.hasNext) {
          val expr = importExprs.next()
          expr.reference match {
            case Some(reference) => reference.resolve()
            case None => expr.qualifier.resolve()
          }
        }
      }
    }
    if (!accessibilityCheck) processor.doNotCheckAccessibility()
    var x = false
    //performance improvement
    ScalaPsiUtil.fileContext(this) match {
      case s: ScalaFile if s.isCompiled =>
        x = true
        //todo: improve checking for this and super
        val refText: String = getText
        if (!refText.contains("this") && !refText.contains("super") && (
          refText.contains(".") || getContext.isInstanceOf[ScStableCodeReference]
          )) {
          //so this is full qualified reference => findClass, or findPackage
          val facade = JavaPsiFacade.getInstance(getProject)
          val manager = ScalaPsiManager.instance(getProject)
          val classes = manager.getCachedClasses(getResolveScope, refText)
          val pack = facade.findPackage(refText)
          if (pack != null) processor.execute(pack, ResolveState.initial)
          for (clazz <- classes) processor.execute(clazz, ResolveState.initial)
          val candidates = processor.candidatesS
          val filtered = candidates.filter(candidatesFilter)

          if (filtered.nonEmpty) {
            return filtered.mapToArray(identity)
          }
        }

      case _ =>
    }

    val candidates = processQualifier(processor)
    val filtered = candidates.filter(candidatesFilter)

    if (accessibilityCheck && filtered.isEmpty) doResolve(processor, accessibilityCheck = false)
    else filtered
  }


}
