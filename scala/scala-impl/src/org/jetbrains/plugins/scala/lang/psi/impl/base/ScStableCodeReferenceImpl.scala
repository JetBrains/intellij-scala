package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.autoImport.quickFix.{ClassToImport, ElementToImport, MemberToImport}
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.macros.MacroDef
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder.ImportPath
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause, ScConstructorPattern, ScInfixPattern, ScInterpolationPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMatch, ScReferenceExpression, ScSuperReference, ScThisReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScMacroDefinition._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScMacroDefinition, ScTypeAlias, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScDerivesClause, ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScPackaging, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScPackage, ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.{PatternTypeInference, ScReferenceImpl}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType}
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor._
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, CompletionProcessor, ExtractorResolveProcessor}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocResolvableCodeReference, ScDocSyntaxElement}

import scala.annotation.tailrec

class ScStableCodeReferenceImpl(node: ASTNode) extends ScReferenceImpl(node) with ScStableCodeReference {
  refThis =>

  override def toString: String = s"CodeReferenceElement${debugKind.fold("")(" (" + _ + ")")}: ${ifReadAllowed(getText)("")}"
  protected def debugKind: Option[String] = None

  override def getResolveResultVariants: Array[ScalaResolveResult] =
    doResolve(new CompletionProcessor(getKinds(incomplete = true), this))

  override def getConstructorInvocation: Option[ScConstructorInvocation] =
    getContext.asOptionOf[ScSimpleTypeElement]
      .flatMap(_.findConstructorInvocation)

  override def isConstructorReference: Boolean = getConstructorInvocation.nonEmpty

  override def getKinds(incomplete: Boolean, completion: Boolean): Set[ResolveTargets.Value] = {
    import org.jetbrains.plugins.scala.lang.resolve.StdKinds._

    val result = getContext match {
      case contextRef: ScStableCodeReference =>
        //Since scala 2.11 it's possible macro implementations not only as static methods,
        //but also inside certain classes, so qualifier of a macro impl reference may resolve to a class
        //see https://docs.scala-lang.org/overviews/macros/bundles.html
        if (isMacroImplReference(contextRef)) stableQualOrClass
        else stableQualRef

      case e: ScImportExpr => if (e.selectorSet.isDefined
        //import Class._ is not allowed
        || qualifier.isEmpty || e.hasWildcardSelector) stableQualRef
      else stableImportSelector

      case ste: ScSimpleTypeElement =>
        if (incomplete)
          noPackagesClassCompletion // todo use the settings to include packages
        else if (ste.getLastChild.is[PsiErrorElement])
          stableQualRef
        else if (ste.isSingleton) {
          val candidates = stableQualRefCandidates
          candidates + ResolveTargets.HAS_STABLE_TYPE
        }
        else if (ste.annotation) annotCtor
        else stableClass

      case _: ScTypeAlias                           => stableClass
      case _: ScInterpolationPattern                => stableImportSelector
      case _: ScConstructorPattern                  => objectOrValue
      case _: ScInfixPattern                        => objectOrValue
      case _: ScThisReference | _: ScSuperReference => stableClassOrObject
      case _: ScImportSelector                      => stableImportSelector
      case _: ScInfixTypeElement                    => stableClass
      case _: ScMacroDefinition                     => methodsOnly //reference in macro definition may be to method only
      case _: ScDocSyntaxElement                    => stableImportSelector
      case _: ScDerivesClause                       => stableClass
      case _                                        => stableQualRef
    }
    if (completion) result + ResolveTargets.PACKAGE + ResolveTargets.OBJECT + ResolveTargets.VAL
    else result
  }

  override def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)

  @throws(classOf[IncorrectOperationException])
  override def bindToElement(element: PsiElement): PsiElement = {
    def createReferenceAndEnsureIsCorrect(text: String): Option[ScStableCodeReference] = {
      val newReference = ScalaPsiElementFactory.createReferenceFromText(text, getContext, ScStableCodeReferenceImpl.this)

      if (newReference.isReferenceTo(element)) Some(newReference)
      else None
    }

    def replaceThisBy(ref: ScStableCodeReference): PsiElement =
      ScStableCodeReferenceImpl.this.replace(ref)

    /**
     * @note this method may remove unnecessary imports
     * @note effected ranges are modified after refactoring is finished in
     *       [[com.intellij.psi.impl.source.PostprocessReformattingAspect.doPostponedFormatting]]<br>
     *       so if we touch some whitespace during import modification, the whitespace might be collapsed
     *       according to code style settings (min blank lines before/after/imports)
     *
     *
     */
    def bindImportReference(elementToImport: ElementToImport): Boolean = {
      val importExpr = ScalaPsiUtil.getParentOfTypeInsideImport(this, classOf[ScImportExpr], strict = true)
      val isImport = null != ScalaPsiUtil.getParentOfTypeInsideImport(importExpr, classOf[ScImportStmt], strict = true)
      val importSelector = ScalaPsiUtil.getParentOfTypeInsideImport(this, classOf[ScImportSelector], strict = true)

      def bindImportSelectorOrExpression(aliasName: Option[String], importExpressionOrSelectorToDelete: PsiElement): Unit = {
        val importsHolder = PsiTreeUtil.getParentOfType(importExpr, classOf[ScImportsHolder])
        val importPath = ImportPath(elementToImport.qualifiedName, aliasName)
        importsHolder.bindImportSelectorOrExpression(importExpr, importExpressionOrSelectorToDelete, importPath)
      }

      if (!isImport) {
        // it's probably an export statement
        false
      }
      else if (importSelector != null) {
        bindImportSelectorOrExpression(importSelector.aliasName, importSelector)
        true
      }
      else if (importExpr != null) {
        val isRootImportRefInImportExpr = importExpr == this.getParent // example: `a.b.c` in `import a.b.c`
        if (isRootImportRefInImportExpr && !importExpr.hasWildcardSelector && importExpr.selectorSet.isEmpty) {
          bindImportSelectorOrExpression(None, importExpr)
        }
        else {
          //qualifier reference in import expression
          // e.g. when we move some object `O` and we have `import a.b.O.method` we are rebinding `a.b.O` reference
          val newRefOpt1 = createReferenceAndEnsureIsCorrect(elementToImport.name)
          val newRefOpt2 = newRefOpt1.orElse(createReferenceAndEnsureIsCorrect(elementToImport.qualifiedName))
          newRefOpt2 match {
            case Some(newRef) =>
              replaceThisBy(newRef)
            case _ =>
          }
        }
        true
      }
      else
        false
    }

    if (isReferenceTo(element))
      this
    else {
      val aliasedRef: Option[ScReference] = ScalaPsiUtil.importAliasFor(element, this)
      if (aliasedRef.isDefined) {
        this.replace(aliasedRef.get)
      }
      else {
        def bindToType(elementToImport: ElementToImport): PsiElement = {
          val suitableKinds = getKinds(incomplete = false)
          if (!ResolveUtils.kindMatches(elementToImport.element, suitableKinds)) {
            reportWrongKind(elementToImport, suitableKinds)
          }

          if (!nameId.textMatches(elementToImport.name)) {
            val refNew = ScalaPsiElementFactory.createReferenceFromText(elementToImport.name)
            val refNewReplaced = this.replace(refNew).asInstanceOf[ScStableCodeReferenceImpl]
            val result = refNewReplaced.bindToElement(elementToImport.element)
            return result
          }

          val qname = elementToImport.qualifiedName
          val isPredefined = ScalaCodeStyleSettings.getInstance(getProject).hasImportWithPrefix(qname)

          val bindImportReferenceResult = bindImportReference(elementToImport)
          if (bindImportReferenceResult) {
            //so we may return invalidated reference when current reference was part of import expression
            //probably it's better than null if import statement or selector was removed completely
            this
          }
          else {
            if (qualifier.isDefined && !isPredefined) {
              createReferenceAndEnsureIsCorrect(elementToImport.name) match {
                case Some(newRef) =>
                  return replaceThisBy(newRef)
                case _ =>
              }
            }
            if (qname != null) {
              safeBindToElement(qname, referenceCreator = {
                case (qual, true) => ScalaPsiElementFactory.createReferenceFromText(qual, getContext, this)
                case (qual, false) => ScalaPsiElementFactory.createReferenceFromText(qual)
              }) {
                val importsHolder = ScImportsHolder(this)
                importsHolder.addImportForElement(elementToImport, ref = this)

                if (qualifier.isDefined) {
                  //let's make our reference unqualified
                  val ref: ScStableCodeReference = ScalaPsiElementFactory.createReferenceFromText(elementToImport.name)
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
                case Some(companion) => bindToType(ClassToImport(companion))
                case None => bindToType(ClassToImport(c))
              }
            }
            else bindToType(ClassToImport(c))
          case ta: ScTypeAlias =>
            if (ta.containingClass != null && ScalaPsiUtil.hasStablePath(ta)) {
              bindToType(MemberToImport(ta, ta.containingClass))
            } else {
              //todo: nothing to do yet, probably in future it would be great to implement something context-specific
              this
            }
          case binding: ScBindingPattern =>
            binding.nameContext match {
              case member: ScMember =>
                val containingClass = member.containingClass
                val refToClass = bindToElement(containingClass)
                val refToMember = ScalaPsiElementFactory.createReferenceFromText(refToClass.getText + "." + binding.name)
                this.replace(refToMember).asInstanceOf[ScReference]
            }
          case fun: ScFunction if Seq("unapply", "unapplySeq").contains(fun.name) && ScalaPsiUtil.hasStablePath(fun) =>
            bindToElement(fun.containingClass)
          case AuxiliaryConstructor(constr)  =>
            bindToElement(constr.containingClass)
          case JavaConstructor(constr) =>
            bindToElement(constr.containingClass)
          case pckg: PsiPackage =>
            bindToPackage(pckg)
          case _ =>
            throw new IncorrectOperationException(s"Cannot bind to $element")
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

  private def reportWrongKind(elementToImport: ElementToImport, suitableKinds: Set[ResolveTargets.Value]): Nothing = {
    val contextText = contextsElementKinds
    throw new IncorrectOperationException(
      s"""${elementToImport.element} does not match expected kind,
         |kinds: ${suitableKinds.mkString(", ")}
         |problem place: $refName in
         |$contextText""".stripMargin)
  }

  override def getSameNameVariants: Array[ScalaResolveResult] = doResolve(new CompletionProcessor(getKinds(incomplete = true), this) {
    override protected val forName: Option[String] = Some(refName)
  })

  override def delete(): Unit = {
    getContext match {
      case sel: ScImportSelector => sel.deleteSelector(removeRedundantBraces = true)
      case expr: ScImportExpr    => expr.deleteExpr()
      case _                     => super.delete()
    }
  }

  override def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult] = cachedWithRecursionGuard("multiResolveScala", this, ScalaResolveResult.EMPTY_ARRAY, BlockModificationTracker(this), Tuple1(incomplete)) {
    val resolver = new StableCodeReferenceResolver(ScStableCodeReferenceImpl.this, false, false, false)
    resolver.resolve()
  }

  private def processQualifier(processor: BaseProcessor): Array[ScalaResolveResult] = {
    val qualifierResult = _qualifier()
    qualifierResult match {
      case None =>
        @scala.annotation.tailrec
        def treeWalkUp(place: PsiElement, lastParent: PsiElement, state: ResolveState): Unit = {
          ProgressManager.checkCanceled()
          place match {
            case null =>
            case p: ScTypeElement if p.analog.isDefined =>
              // this allows the type elements in a context or view bound to be path-dependent types, based on parameters.
              // See ScalaPsiUtil.syntheticParamClause and StableCodeReferenceElementResolver#computeEffectiveParameterClauses
              if (!p.processDeclarations(processor, ScalaResolveState.empty, lastParent, this))
                return
              treeWalkUp(p.analog.get, lastParent, state)
              // annotation should not walk through it's own annotee while resolving
            case p: ScAnnotationsHolder
              if processor.kinds.contains(ResolveTargets.ANNOTATION) && PsiTreeUtil.isContextAncestor(p, this, true) =>
                treeWalkUp(place.getContext, place, state)
            case export: ScExportStmt =>
              val clsContext = PsiTreeUtil.getContextOfType(export, classOf[PsiClass])
              val nodes      = MixinNodes.currentlyProcessedSigs.value.get(clsContext)

              if (nodes ne null) {
                val forName = nodes.forName(refName)
                val state   = ScalaResolveState.empty.withFromType(ScalaType.designator(clsContext))
                if (!forName.isEmpty) {
                  forName.iterator.filter { sig =>
                    sig.namedElement match {
                      case clsParam: ScClassParameter => !clsParam.isVar
                      case inNameContext(_: ScValue)  => true
                      case _: ScObject                => true
                      case _                          => false
                    }
                  }.forall(sig => processor.execute(sig.namedElement, state))
                  return
                }
              }
              treeWalkUp(place.getContext, place, state)
            case p =>
              // Do not call PatternTypeInference, if this reference is itself located inside a target pattern
              def containsThisTypeElementInPattern(cc: ScCaseClause): Boolean = {
                var contains = false

                cc.pattern.foreach(_.acceptChildren(new ScalaRecursiveElementVisitor {
                  override def visitReference(ref: ScReference): Unit =
                    if (ref eq refThis) contains = true
                }))

                contains
              }

              val newState = place match {
                case (cc: ScCaseClause) & Parent(Parent(m: ScMatch)) if !containsThisTypeElementInPattern(cc) =>
                  //@TODO: partial functions as well???
                  val subst = PatternTypeInference.doForMatchClause(m, cc)
                  val oldSubst = state.matchClauseSubstitutor
                  state.withMatchClauseSubstitutor(oldSubst.followed(subst))
                case _ => state
              }

              if (!p.processDeclarations(processor, newState, lastParent, this))
                return
              place match {
                case _: ScTemplateBody | _: ScExtendsBlock => // template body and inherited members are at the same level.
                case _ =>
                  if (!processor.changedLevel)
                    return
              }
              treeWalkUp(place.getContext, place, newState)
          }
        }

        // when processing Foo.this.Bar or Foo.super[Bar].Baz, positioned in the extends block,
        // it is important to skip the contexts up to the actual outer type definition, or else
        // we may end up with weird self-references if the name is not unique (#SCL-14707, #SCL-14922)
        val enclosingTypeDef = getContext match {
          case ctx @ (_: ScSuperReference | _: ScThisReference) => ResolveUtils.enclosingTypeDef(ctx)
          case _                                                => None
        }
        val lastParent = enclosingTypeDef.getOrElse(this)
        val startingPlace = lastParent.getContext

        treeWalkUp(startingPlace, lastParent, ScalaResolveState.empty)
        processor.candidates
      case Some(p: ScInterpolationPattern) =>
        val expr =
          ScalaPsiElementFactory.createExpressionWithContextFromText(s"""_root_.scala.StringContext("").$refName""", p, this)
        expr match {
          case ref: ScReferenceExpression =>
            ref.doResolve(processor)
          case _ =>
        }
        processor.candidates
      case Some(q: ScDocResolvableCodeReference) =>
        val result = q.multiResolveScala(incomplete = true)
        val result2 = result.flatMap(processQualifierResolveResult(q, _, processor))
        result2
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
        val state = ScalaResolveState.withSubstitutor(substitutor)

        td match {
          case obj: ScObject =>
            val fromType = r.fromType match {
              case Some(fType) => Right(ScProjectionType(fType, obj))
              case _ => td.`type`().map(substitutor)
            }
            fromType match {
              case Right(qualType) =>
                val stateWithType = state.withFromType(qualType)
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
      case ScalaResolveResult((_: ScTypedDefinition) & Typeable(tp), s) =>
        val fromType = s(tp)
        val state = ScalaResolveState.withFromType(fromType)
        processor.processType(fromType, this, state)
        withDynamicResult = withDynamic(fromType, state, processor)
        processor match {
          case _: ExtractorResolveProcessor =>
            if (processor.candidatesS.isEmpty) {
              //check implicit conversions
              val expr =
                ScalaPsiElementFactory.createExpressionWithContextFromText(getText, getContext, this)
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
        pack.processDeclarations(processor, ScalaResolveState.withSubstitutor(s),
          null, this)
      case other: ScalaResolveResult =>
        other.element.processDeclarations(processor, ScalaResolveState.withSubstitutor(other.substitutor),
          null, this)
      case _ =>
    }

    withDynamicResult.getOrElse(processor.candidates)
  }

  private def withDynamic(qualType: ScType, state: ResolveState, processor: BaseProcessor): Option[Array[ScalaResolveResult]] = {
    if (processor.candidatesS.isEmpty && conformsToDynamic(qualType, getResolveScope)) {
      ScalaPsiElementFactory.createExpressionWithContextFromText(getText, getContext, this) match {
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

  override def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean = true): Array[ScalaResolveResult] = {
    def candidatesFilter(result: ScalaResolveResult): Boolean = {
      result.element match {
        case c: PsiClass if c.name == c.qualifiedName =>
          c.getContainingFile match {
            case _: ScalaFile => true // scala classes are available from default package
            /** in completion in [[ScalaFile]] [[DummyHolder]] usually used as file */
            case dummyHolder: DummyHolder
              if Option(dummyHolder.getContext).map(_.getContainingFile).exists(_.is[ScalaFile]) =>
              true
            // Other classes from default package are available only for top-level Scala statements
            case _ => PsiTreeUtil.getContextOfType(this, true, classOf[ScPackaging]) == null
          }
        case _ => true
      }
    }

    ScStableCodeReferenceExtraResolver.resolveWithFileCheck(this) match {
      case Some(element) =>
        return Array(new ScalaResolveResult(element))
      case None =>
    }

    val enclosingImportOrExport = getEnclosingImportStatement
    val isExport = enclosingImportOrExport.is[ScExportStmt]

    if (enclosingImportOrExport != null && !isExport) {
      val importHolder = PsiTreeUtil.getContextOfType(enclosingImportOrExport, true, classOf[ScImportsHolder])
      if (importHolder != null) {
        val importExprsSeq = importHolder.getImportStatements
          .takeWhile(_ != enclosingImportOrExport)
          .flatMap(_.importExprs)
          .filter(_.hasWildcardSelector)

        val importExprs = importExprsSeq.iterator
        while (importExprs.hasNext) {
          val expr = importExprs.next()
          expr.reference.foreach(_.resolve())
        }
      }
    }

    if (!accessibilityCheck)
      processor.doNotCheckAccessibility()

    // SCL-21037
    val refText = getText
    if (refText == "_root_" || refText.startsWith("_root_.")) {
      // TODO # type projections
      val fqn = if (refText == "_root_") "" else refText.stripPrefix("_root_.")
      val manager = ScalaPsiManager.instance(getProject)
      val classes = manager.getCachedClasses(getResolveScope, fqn)
      val pack    = manager.getCachedPackage(fqn)

      ScalaPsiUtil.fileContext(this) match {
        case file: ScFile if file.isCompiled =>
          processor.doNotCheckAccessibility()
        case _ =>
      }

      pack.foreach(processor.execute(_, ScalaResolveState.empty))
      classes.foreach(processor.execute(_, ScalaResolveState.empty))

      val candidates = processor.candidatesS
      val filtered = candidates.filter(candidatesFilter)

      if (filtered.nonEmpty) {
        return filtered.mapToArray(identity)
      }
    }

    val candidates = processQualifier(processor)
    val filtered = candidates.filter(candidatesFilter)

    val result = if (accessibilityCheck && filtered.isEmpty)
      doResolve(processor, accessibilityCheck = false)
    else
      filtered
    result
  }

  private def getEnclosingImportStatement: ScImportOrExportStmt = {
    @tailrec
    def inner(element: PsiElement): ScImportOrExportStmt = {
      val context = element.getContext
      context match {
        case _: ScStableCodeReference   => inner(context)
        case _: ScImportExpr |
             _: ScImportSelector |
             _: ScImportSelectors       => inner(context)
        case importClause: ScImportStmt => importClause
        case exportClause: ScExportStmt => exportClause
        case _                          => null
      }
    }

    inner(this)
  }
}
