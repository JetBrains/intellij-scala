package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.intention.ScalaAddImportAction
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.lexer.{ScalaModifier, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScPrimaryConstructor, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaElementVisitor, ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createExpressionWithContextFromText}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticValue
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor.ScTypeForDynamicProcessorEx
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, Measure, ModCount}

import scala.collection.mutable

/**
  * @author AlexanderPodkhalyuzin
  *         Date: 06.03.2008
  */
class ScReferenceExpressionImpl(node: ASTNode) extends ScReferenceImpl(node) with ScReferenceExpression {

  private[this] var maybeAssignment: Option[ScAssignment] = None

  override def toString: String = "ReferenceExpression: " + ifReadAllowed(getText)("")

  override def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitReferenceExpression(this)
  }

  override final def assignment: ScAssignment = maybeAssignment.orNull

  override final def assignment_=(statement: ScAssignment): Unit = {
    maybeAssignment = Some(statement)
  }

  override def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult] =
    maybeAssignment.fold(multiResolveImpl(incomplete)) {
      _.resolveAssignment.toArray
    }

  override def shapeResolve: Array[ScalaResolveResult] = {
    ProgressManager.checkCanceled()
    maybeAssignment.fold(shapeResolveImpl) {
      _.shapeResolveAssignment.toArray
    }
  }

  override def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean = true): Array[ScalaResolveResult] =
    new ReferenceExpressionResolver().doResolve(this, processor, accessibilityCheck)

  override def bindToElement(element: PsiElement): PsiElement = bindToElement(element, None)

  override def bindToElement(element: PsiElement, containingClass: Option[PsiClass]): PsiElement = {
    def tail(qualName: String)(simpleImport: => PsiElement): PsiElement = {
      safeBindToElement(qualName, {
        case (qual, true) =>
          createExpressionWithContextFromText(qual, getContext, this).asInstanceOf[ScReferenceExpression]
        case (qual, false) =>
          createExpressionFromText(qual).asInstanceOf[ScReferenceExpression]
      })(simpleImport)
    }

    if (isReferenceTo(element)) return this
    element match {
      case _: ScTrait | _: ScClass =>
        ScalaPsiUtil.getCompanionModule(element.asInstanceOf[ScTypeDefinition]) match {
          case Some(obj: ScObject) => bindToElement(obj, containingClass)
          case _ => this
        }
      case c: PsiClass =>
        val kinds = getKinds(incomplete = false)
        if (!ResolveUtils.kindMatches(element, kinds))
          throw new IncorrectOperationException(s"class $c does not match expected kind,\nexpected: ${kinds.mkString(", ")}")
        if (!ScalaNamesUtil.equivalent(refName, c.name))
          throw new IncorrectOperationException(s"class $c does not match expected name $refName")
        val qualName = c.qualifiedName
        if (qualName != null) {
          return tail(qualName) {
            ScalaAddImportAction.getImportHolder(ref = this, project = getProject).addImportForClass(c, ref = this)
            //need to use unqualified reference with new import
            if (!this.isQualified) this
            else this.replace(createExpressionFromText(this.refName).asInstanceOf[ScReferenceExpression])
            //todo: conflicts with other classes with same name?
          }
        }
        this
      case _: ScTypeAlias =>
        throw new IncorrectOperationException("type does not match expected kind")
      case fun: ScFunction if ScalaPsiUtil.hasStablePath(fun) && fun.name == "apply" =>
        bindToElement(fun.containingClass)
      case pack: ScPackage =>
        val qualName = pack.getQualifiedName
        tail(qualName) {
          ScalaAddImportAction.getImportHolder(this, getProject).addImportForPath(qualName, this)
          this
        }
      case elem: PsiNamedElement =>
        if (!ScalaNamesUtil.equivalent(refName, elem.name))
          throw new IncorrectOperationException(s"named element $elem does not match expected name $refName")
        ScalaPsiUtil.nameContext(elem) match {
          case memb: PsiMember =>
            val cClass = containingClass.getOrElse(memb.containingClass)
            if (cClass != null && cClass.qualifiedName != null) {
              val qualName: String = cClass.qualifiedName + "." + elem.name
              return tail(qualName) {
                ScalaAddImportAction.getImportHolder(this, getProject).addImportForPsiNamedElement(elem, this, Some(cClass))
                this
              }
            }
          case _ =>
        }
        this
      case _ => throw new IncorrectOperationException("Cannot bind to element: " + element)
    }
  }

  override def getVariants: Array[Object] = completionVariants(implicits = true).toArray

  override def completionVariants(implicits: Boolean): Seq[ScalaLookupItem] =
    getSimpleVariants(incomplete = true, completion = false, implicits).flatMap(toLookupItem)

  override def getSameNameVariants: Array[ScalaResolveResult] = this.doResolve(
    new CompletionProcessor(getKinds(incomplete = true), this, isImplicit = true) {

      override protected val forName: Option[String] = Some(refName)
    })

  override def getKinds(incomplete: Boolean, completion: Boolean = false): _root_.org.jetbrains.plugins.scala.lang.resolve.ResolveTargets.ValueSet = {
    getContext match {
      case _ if completion => StdKinds.refExprQualRef // SC-3092
      case _: ScReferenceExpression => StdKinds.refExprQualRef
      case postf: ScPostfixExpr if this == postf.operation || this == postf.getBaseExpr => StdKinds.refExprQualRef
      case pref: ScPrefixExpr if this == pref.operation || this == pref.getBaseExpr => StdKinds.refExprQualRef
      case inf: ScInfixExpr if this == inf.operation || this == inf.getBaseExpr => StdKinds.refExprQualRef
      case _ => StdKinds.refExprLastRef
    }
  } // See SCL-3092

  override def multiType: Array[TypeResult] = {
    val buffer = mutable.ArrayBuffer[TypeResult]()
    val iterator = multiResolveScala(incomplete = false).iterator
    while (iterator.hasNext) {
      buffer += convertBindToType(iterator.next())
    }
    buffer.toArray
  }

  protected override def innerType: TypeResult = {
    this.bind() match {
      case Some(srr) => convertBindToType(srr)
      case _ if getContainingFile.asOptionOf[ScalaFile].exists(_.isMultipleDeclarationsAllowed) =>
        val priorDeclarations = multiResolveScala(false).filter(
          result => result.element.getContainingFile == getContainingFile && result.element.getTextOffset < getTextOffset
        )

        if (priorDeclarations.nonEmpty) convertBindToType(priorDeclarations.maxBy(_.element.getTextOffset)) else resolveFailure
      case _ => resolveFailure
    }
  }

  override def shapeType: TypeResult = {
    shapeResolve match {
      case Array(bind) if bind.isApplicable() => convertBindToType(bind)
      case _ => resolveFailure
    }
  }

  override def shapeMultiType: Array[TypeResult] = {
    val buffer = mutable.ArrayBuffer[TypeResult]()
    val iterator = shapeResolve.iterator
    while (iterator.hasNext) {
      buffer += convertBindToType(iterator.next())
    }
    buffer.toArray
  }

  private def isMetaInlineDefn(p: ScParameter): Boolean = {
    p.owner match {
      case f: ScFunctionDefinition if f.getModifierList != null =>
        f.getModifierList.hasModifierProperty(ScalaModifier.INLINE)
      case _ => false
    }
  }

  /**
    * SLS 6.4
    *
    * 1. The expected type `pt` is stable
    * 2. Type `tpe` of the entity reffered to by `p` does not conform to `pt` AND either:
    *      * `pt` is an abstract type with a stable type as lower bound OR
    *      *  (not in the spec, but in the impl) `pt` denotes type refinement
    */
  private[this] def isStableContext(t: ScType): Boolean = {
    val expectedStable = this.expectedType() match {
      case Some(downer: DesignatorOwner)     => downer.isStable
      case Some(other) if !t.conforms(other) =>
        other match {
          case AliasType(_, Right(lower: DesignatorOwner), _)                   => lower.isStable
          case AliasType(_: ScTypeAliasDefinition, Right(c: ScCompoundType), _) => isRefinement(c)
          case c: ScCompoundType                                                => isRefinement(c)
          case _                                                                => false
        }
      case _ => false
    }

    val isParamToDepMethod = this.expectedTypeEx().collect {
      case (_, Some(te)) =>
        (for {
          param     <- te.contexts.take(2).instanceOf[ScParameter] //parameter is first context for stub elements and second context for ast
          if !param.getDefaultExpression.contains(this)
          method     <- param.owner.asOptionOf[ScFunction]
        } yield isReferencedInReturnType(method, param)).getOrElse(false)
    }.getOrElse(false)

    //The path p occurs as the prefix of a selection and it does not designate a constant
    //todo: It seems that designating constant is not a problem, while we haven't type like Int(1)
    expectedStable || (getContext match {
      case i: ScSugarCallExpr         if this == i.getBaseExpr        => true
      case m: ScMethodCall            if this == m.getInvokedExpr     => true
      case ref: ScReferenceExpression if ref.qualifier.contains(this) => true
      case _                                                          => false
    }) || isParamToDepMethod
  }

  private[this] def isRefinement(compound: ScCompoundType): Boolean =
    compound.signatureMap.nonEmpty || compound.typesMap.nonEmpty

  private[this] def isReferencedInReturnType(f: ScFunction, p: ScParameter): Boolean = {
    var found = false
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitSimpleTypeElement(simple: ScSimpleTypeElement): Unit = {
        if (simple.singleton) {
          simple.reference match {
            case Some(ref) if ref.refName == p.name && ref.resolve() == p => found = true
            case _                                                        => ()
          }
        }
        super.visitSimpleTypeElement(simple)
      }
    }
    f.returnTypeElement.foreach(_.accept(visitor))
    found
  }

  protected def convertBindToType(bind: ScalaResolveResult): TypeResult = {
    val fromType: Option[ScType] = bind.fromType
    val unresolvedTypeParameters: Seq[TypeParameter] = bind.unresolvedTypeParameters.getOrElse(Seq.empty)

    val inner: ScType = bind match {
      case ScalaResolveResult(fun: ScFun, s) =>
        fun.polymorphicType(s)
      //prevent infinite recursion for recursive pattern reference
      case ScalaResolveResult(self: ScSelfTypeElement, _) =>
        val clazz = PsiTreeUtil.getContextOfType(self, true, classOf[ScTemplateDefinition])
        ScThisReferenceImpl.getThisTypeForTypeDefinition(clazz, this) match {
          case Right(value) => value
          case failure => return failure
        }
      case r@ScalaResolveResult(refPatt: ScBindingPattern, s) =>
        ScalaPsiUtil.nameContext(refPatt) match {
          case pd: ScPatternDefinition if PsiTreeUtil.isContextAncestor(pd, this, true) => pd.declaredType match {
            case Some(t) => t
            case None => return Failure(ScalaBundle.message("no.declared.type.found"))
          }
          case vd: ScVariableDefinition if PsiTreeUtil.isContextAncestor(vd, this, true) => vd.declaredType match {
            case Some(t) => t
            case None => return Failure(ScalaBundle.message("no.declared.type.found"))
          }
          case _ =>
            val result = refPatt.`type`()

            refPatt.`type`().map { tp =>
              if (isStableContext(tp) && refPatt.isStable) {
                r.fromType match {
                  case Some(fT) => ScProjectionType(fT, refPatt)
                  case None     => ScalaType.designator(refPatt)
                }
              } else s(tp)
            }.getOrElse(return result)
        }
      case ScalaResolveResult(param: ScParameter, _) if isMetaInlineDefn(param) =>
        ScalaPsiElementFactory.createTypeFromText("scala.meta.Stat", param.getContext, null).get
      case r @ ScalaResolveResult(param: ScParameter, s) =>
        val owner = param.owner match {
          case f: ScPrimaryConstructor => f.containingClass
          case _: ScFunctionExpr       => null
          case f                       => f
        }

        def isMethodDependent(function: ScFunction): Boolean = {
          def checkte(te: ScTypeElement): Boolean = {
            var res = false
            te.accept(new ScalaRecursiveElementVisitor {
              override def visitReference(ref: ScReference): Unit = {
                if (ref.resolve() == param) res = true
                super.visitReference(ref)
              }
            })
            res
          }

          function.returnTypeElement match {
            case Some(te) if checkte(te) => return true
            case _ =>
          }
          !function.parameters.forall { param =>
            param.typeElement match {
              case Some(te) => !checkte(te)
              case _        => true
            }
          }
        }

        val stableTypeRequired = param.getRealParameterType.exists(isStableContext)

        r.fromType match {
          case Some(fT) if param.isVal && stableTypeRequired => ScProjectionType(fT, param)
          case Some(ScThisType(clazz)) if owner != null && PsiTreeUtil.isContextAncestor(owner, this, true) &&
            stableTypeRequired && owner.isInstanceOf[ScTypeDefinition] && owner == clazz => ScalaType.designator(param) //todo: think about projection from this type?
          case _ if owner != null && PsiTreeUtil.isContextAncestor(owner, this, true) &&
            stableTypeRequired && !owner.isInstanceOf[ScTypeDefinition] => ScalaType.designator(param)
          case _ =>
            owner match {
              case function: ScFunction if PsiTreeUtil.isContextAncestor(function, this, true) &&
                isMethodDependent(function) => ScalaType.designator(param)
              case _ =>
                val result = param.getRealParameterType
                s(result match {
                  case Right(tp) => tp
                  case _ => return result
                })
            }
        }
      case ScalaResolveResult(value: ScSyntheticValue, _) => value.tp
      case ScalaResolveResult(fun: ScFunction, s) if fun.isProbablyRecursive =>
        val maybeResult = fun.definedReturnType.toOption
        fun.polymorphicType(s, maybeResult)
      case result@ScalaResolveResult(fun: ScFunction, s) =>
        fun.polymorphicType(s).updateTypeOfDynamicCall(result.isDynamic)
      case ScalaResolveResult(param: ScParameter, s) if param.isRepeatedParameter =>
        val result = param.`type`()
        val computeType = s(result match {
          case Right(tp) => tp
          case _ => return result
        })
        computeType.tryWrapIntoSeqType
      case ScalaResolveResult(obj: ScObject, _) =>
        def tail = {
          fromType match {
            case Some(tp) => ScProjectionType(tp, obj)
            case _ => ScalaType.designator(obj)
          }
        }
        //hack to add Eta expansion for case classes
        if (obj.isSyntheticObject) {
          ScalaPsiUtil.getCompanionModule(obj) match {
            case Some(clazz) if clazz.isCase && !clazz.hasTypeParameters =>
              this.expectedType() match {
                case Some(tp) =>
                  if (FunctionType.isFunctionType(tp)) {
                    val tp = tail
                    val processor =
                      new MethodResolveProcessor(this, "apply", Nil, Nil, Nil)
                    processor.processType(tp, this)
                    val candidates = processor.candidates
                    if (candidates.length != 1) tail
                    else convertBindToType(candidates(0)).getOrElse(tail)
                  } else tail
                case _ => tail
              }
            case _ => tail
          }
        } else tail
      case r@ScalaResolveResult(f: ScFieldId, s) =>
        val result = f.`type`()

        result.map { tp =>
          if (isStableContext(tp) && f.isStable) {
            r.fromType match {
              case Some(fT) => ScProjectionType(fT, f)
              case None     => ScalaType.designator(f)
            }
          } else s(tp)
        }.getOrElse(return result)
      case ScalaResolveResult(typed: ScTypedDefinition, s) =>
        val result = typed.`type`()
        result match {
          case Right(tp) => s(tp)
          case _ => return result
        }
      case ScalaResolveResult(pack: PsiPackage, _) => ScalaType.designator(pack)
      case ScalaResolveResult(clazz: ScClass, s) if clazz.isCase =>
        val constructor =
          clazz.constructor
            .getOrElse(return Failure(ScalaBundle.message("case.class.has.no.primary.constructor")))
        constructor.polymorphicType(s)
      case ScalaResolveResult(clazz: ScTypeDefinition, s) if clazz.typeParameters.nonEmpty =>
        s(ScParameterizedType(ScalaType.designator(clazz),
          clazz.typeParameters.map(TypeParameterType(_))))
      case ScalaResolveResult(clazz: PsiClass, _) => ScDesignatorType.static(clazz) //static Java class
      case ScalaResolveResult(field: PsiField, s) =>
        s(field.getType.toScType())
      case ScalaResolveResult(method: PsiMethod, s) =>
        val returnType = Option(method.containingClass).filter {
          method.getName == "getClass" && _.getQualifiedName == "java.lang.Object"
        }.flatMap { _ =>
          val maybeReference = qualifier.orElse {
            val result: Option[Typeable] = getContext match {
              case infixExpr: ScInfixExpr if infixExpr.operation == this => Some(infixExpr.left)
              case postfixExpr: ScPostfixExpr if postfixExpr.operation == this => Some(postfixExpr.operand)
              case _ => ScalaPsiUtil.drvTemplate(this)
            }
            result
          }

          def getType(element: PsiNamedElement): Option[ScType] = Option(element).collect {
            case pattern: ScBindingPattern => pattern
            case fieldId: ScFieldId => fieldId
            case parameter: ScParameter => parameter
          }.flatMap {
            _.`type`().toOption
          }

          def removeTypeDesignator(`type`: ScType): ScType = {
            val maybeType = `type` match {
              case ScDesignatorType(element) =>
                getType(element)
              case projectionType: ScProjectionType =>
                getType(projectionType.actualElement).map(projectionType.actualSubst)
              case _ => None
            }
            maybeType.map(removeTypeDesignator).getOrElse(`type`)
          }

          def convertQualifier(jlClass: PsiClass): ScType = {
            val maybeType = maybeReference.flatMap {
              _.`type`().toOption
            }

            val upperBound = maybeType.flatMap {
              case ScThisType(clazz) => Some(ScDesignatorType(clazz))
              case ScDesignatorType(_: ScObject) => None
              case ScCompoundType(comps, _, _) => comps.headOption.map(removeTypeDesignator)
              case tp => Some(tp).map(removeTypeDesignator)
            }.getOrElse(Any)

            val argument = ScExistentialArgument("_$1", Nil, Nothing, upperBound)
            ScExistentialType(ScParameterizedType(ScDesignatorType(jlClass), Seq(argument)))
          }

          elementScope.getCachedClass("java.lang.Class")
            .map(convertQualifier)
        }
        method
          .methodTypeProvider(elementScope)
          .polymorphicType(s, returnType)
      case _ => return resolveFailure
    }
    qualifier match {
      case Some(_: ScSuperReference) =>
      case None => //infix, prefix and postfix
        getContext match {
          case sugar: ScSugarCallExpr if sugar.operation == this =>
            sugar.getBaseExpr.getNonValueType() match {
              case Right(ScTypePolymorphicType(_, typeParams)) =>
                inner match {
                  case ScTypePolymorphicType(internal, typeParams2) =>
                    return Right(ScalaPsiUtil.removeBadBounds(ScTypePolymorphicType(internal, typeParams ++ typeParams2 ++ unresolvedTypeParameters)))
                  case _ =>
                    return Right(ScTypePolymorphicType(inner, typeParams ++ unresolvedTypeParameters))
                }
              case _ if unresolvedTypeParameters.nonEmpty =>
                inner match {
                  case ScTypePolymorphicType(internal, typeParams) =>
                    return Right(ScTypePolymorphicType(internal, unresolvedTypeParameters ++ typeParams))
                  case _ =>
                    return Right(ScTypePolymorphicType(inner, unresolvedTypeParameters))
                }
              case _ =>
            }
          case _ =>
        }
      case Some(qualifier) =>
        qualifier.getNonValueType() match {
          case Right(ScTypePolymorphicType(_, typeParams)) =>
            inner match {
              case ScTypePolymorphicType(internal, typeParams2) =>
                return Right(ScalaPsiUtil.removeBadBounds(ScTypePolymorphicType(internal, typeParams ++ typeParams2 ++ unresolvedTypeParameters)))
              case _ =>
                return Right(ScTypePolymorphicType(inner, typeParams ++ unresolvedTypeParameters))
            }
          case _ if unresolvedTypeParameters.nonEmpty =>
            inner match {
              case ScTypePolymorphicType(internal, typeParams) =>
                return Right(ScTypePolymorphicType(internal, unresolvedTypeParameters ++ typeParams))
              case _ =>
                return Right(ScTypePolymorphicType(inner, unresolvedTypeParameters))
            }
          case _ =>
        }
    }
    Right(inner)
  }

  override def getPrevTypeInfoParams: Seq[TypeParameter] = {
    val maybeExpression = qualifier match {
      case Some(_: ScSuperReference) => None
      case None => getContext match {
        case ScSugarCallExpr(baseExpression, operation, _) if operation == this => Some(baseExpression)
        case _ => None
      }
      case result => result
    }

    maybeExpression.flatMap(_.getNonValueType().toOption).collect {
      case ScTypePolymorphicType(_, parameters) => parameters
    }.getOrElse(Seq.empty)
  }

  private def resolveFailure = Failure(ScalaBundle.message("cannot.resolve.expression"))

  @CachedWithRecursionGuard(this, ScalaResolveResult.EMPTY_ARRAY, ModCount.getBlockModificationCount)
  private[this] def multiResolveImpl(incomplete: Boolean): Array[ScalaResolveResult] =
    new ReferenceExpressionResolver().resolve(this, shapesOnly = false, incomplete)

  @CachedWithRecursionGuard(this, ScalaResolveResult.EMPTY_ARRAY, ModCount.getBlockModificationCount)
  private[this] def shapeResolveImpl: Array[ScalaResolveResult] =
    new ReferenceExpressionResolver().resolve(this, shapesOnly = true, incomplete = false)
}
