package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticValue
import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.LookupElementManager
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterType}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaElementVisitor, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScTypePolymorphicType, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.{CompletionProcessor, MethodResolveProcessor}

/**
 * @author AlexanderPodkhalyuzin
 * Date: 06.03.2008
 */
class ScReferenceExpressionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ResolvableReferenceExpression {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "ReferenceExpression: " + getText

  def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitReferenceExpression(this)
  }

  def bindToElement(element: PsiElement): PsiElement = bindToElement(element, None)

  def bindToElement(element: PsiElement, containingClass: Option[PsiClass]): PsiElement = {
    def tail(qualName: String)(simpleImport: => PsiElement): PsiElement = {
      safeBindToElement(qualName, {
        case (qual, true) =>
          ScalaPsiElementFactory.createExpressionWithContextFromText(qual, getContext, this).
            asInstanceOf[ScReferenceExpression]
        case (qual, false) =>
          ScalaPsiElementFactory.createExpressionFromText(qual, getManager).asInstanceOf[ScReferenceExpression]
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
        if (!ResolveUtils.kindMatches(element, getKinds(incomplete = false)))
          throw new IncorrectOperationException("class does not match expected kind")
        if (refName != c.name)
          throw new IncorrectOperationException("class does not match expected name")
        val qualName = c.qualifiedName
        if (qualName != null) {
          return tail(qualName) {
            ScalaImportTypeFix.getImportHolder(ref = this, project = getProject).addImportForClass(c, ref = this)
            //need to use unqualified reference with new import
            if (!this.isQualified) this
            else this.replace(ScalaPsiElementFactory.createExpressionFromText(this.refName, getManager).asInstanceOf[ScReferenceExpression])
            //todo: conflicts with other classes with same name?
          }
        }
        this
      case t: ScTypeAlias =>
        throw new IncorrectOperationException("type does not match expected kind")
      case fun: ScFunction if ScalaPsiUtil.hasStablePath(fun) && fun.name == "apply" =>
        bindToElement(fun.containingClass)
      case pack: ScPackage =>
        val qualName = pack.getQualifiedName
        tail(qualName) {
          ScalaImportTypeFix.getImportHolder(this, getProject).addImportForPath(qualName, this)
          this
        }
      case elem: PsiNamedElement =>
        if (refName != elem.name)
          throw new IncorrectOperationException("named element does not match expected name")
        ScalaPsiUtil.nameContext(elem) match {
          case memb: PsiMember =>
            val cClass = containingClass.getOrElse(memb.containingClass)
            if (cClass != null && cClass.qualifiedName != null) {
              val qualName: String = cClass.qualifiedName + "." + elem.name
              return tail(qualName) {
                ScalaImportTypeFix.getImportHolder(this, getProject).addImportForPsiNamedElement(elem, this, Some(cClass))
                this
              }
            }
          case _ =>
        }
        this
      case _ => throw new IncorrectOperationException("Cannot bind to element: " + element)
    }
  }

  def getVariants: Array[Object] = getVariants(implicits = true, filterNotNamedVariants = false)

  /**
   * Important! Do not change types of Object values, this can cause errors due to bad architecture.
   */
  override def getVariants(implicits: Boolean, filterNotNamedVariants: Boolean): Array[Object] = {
    val isInImport: Boolean = ScalaPsiUtil.getParentOfType(this, classOf[ScImportStmt]) != null

    getSimpleVariants(implicits, filterNotNamedVariants).flatMap {
      case res: ScalaResolveResult =>
        import org.jetbrains.plugins.scala.lang.psi.types.Nothing
        val qualifier = res.fromType.getOrElse(Nothing)
        LookupElementManager.getLookupElement(res, isInImport = isInImport, qualifierType = qualifier, isInStableCodeReference = false)
      case r => Seq(r.getElement)
    }
  }

  def getSimpleVariants(implicits: Boolean, filterNotNamedVariants: Boolean): Array[ResolveResult] = {
    doResolve(this, new CompletionProcessor(getKinds(incomplete = true), this, implicits)).filter(r => {
      if (filterNotNamedVariants) {
        r match {
          case res: ScalaResolveResult => res.isNamedParameter
          case _ => false
        }
      } else true
    })
  }

  def getSameNameVariants: Array[ResolveResult] = doResolve(this, new CompletionProcessor(getKinds(incomplete = true), this, true, Some(refName)))

  def getKinds(incomplete: Boolean, completion: Boolean = false) = {
    getContext match {
      case _ if completion => StdKinds.refExprQualRef // SC-3092
      case _: ScReferenceExpression => StdKinds.refExprQualRef
      case postf: ScPostfixExpr if this == postf.operation || this == postf.getBaseExpr => StdKinds.refExprQualRef
      case pref: ScPrefixExpr if this == pref.operation || this == pref.getBaseExpr => StdKinds.refExprQualRef
      case inf: ScInfixExpr if this == inf.operation || this == inf.getBaseExpr => StdKinds.refExprQualRef
      case _ => StdKinds.refExprLastRef
    }
  } // See SCL-3092

  def multiType: Array[TypeResult[ScType]] = {
    multiResolve(incomplete = false).filter(_.isInstanceOf[ScalaResolveResult]).
      map(r => convertBindToType(Some(r.asInstanceOf[ScalaResolveResult])))
  }

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    convertBindToType(this.bind())
  }

  def shapeType = {
    convertBindToType(shapeResolve match {
      case Array(bind: ScalaResolveResult) if bind.isApplicable() => Some(bind)
      case _ => None
    })
  }

  def shapeMultiType: Array[TypeResult[ScType]] = {
    shapeResolve.filter(_.isInstanceOf[ScalaResolveResult]).
      map(r => convertBindToType(Some(r.asInstanceOf[ScalaResolveResult])))
  }

  protected def convertBindToType(bind: Option[ScalaResolveResult]): TypeResult[ScType] = {
    val fromType: Option[ScType] = bind.map(_.fromType).getOrElse(None)
    val unresolvedTypeParameters: Seq[TypeParameter] = bind.map(_.unresolvedTypeParameters).getOrElse(None).getOrElse(Seq.empty)

    def stableTypeRequired: Boolean = {
      //SLS 6.4

      //The expected type pt is a stable type or
      //The expected type pt is an abstract type with a stable type as lower bound,
      // and the type T of the entity referred to by p does not conforms to pt,
      expectedTypeEx() match {
        case Some((tp, typeElementOpt)) =>
          (tp match {
            case ScAbstractType(_, lower, _) => lower
            case _ => tp
          }).isAliasType match {
            case Some(AliasType(_, lower, _)) if lower.isDefined && lower.get.isStable => return true
            case _ =>
              if (tp.isStable) return true
              typeElementOpt match {
                case Some(te) =>
                  te.getContext match {
                    case pt: ScParameterType =>
                      pt.getContext match {
                        case p: ScParameter if !p.getDefaultExpression.contains(this) =>
                          p.owner match {
                            case f: ScFunction =>
                              var found = false
                              val visitor = new ScalaRecursiveElementVisitor {
                                override def visitSimpleTypeElement(simple: ScSimpleTypeElement): Unit = {
                                  if (simple.singleton) {
                                    simple.reference match {
                                      case Some(ref) if ref.refName == p.name && ref.resolve() == p => found = true
                                      case _ =>
                                    }
                                  }
                                  super.visitSimpleTypeElement(simple)
                                }
                              }
                              f.returnTypeElement.foreach(_.accept(visitor))
                              if (found) return true
                            case _ => //looks like it's not working for classes, so do nothing here.
                          }
                        case _ =>
                      }
                    case _ =>
                  }
                case _ =>
              }
          }
        case _ =>
      }
      //The path p occurs as the prefix of a selection and it does not designate a constant
      //todo: It seems that designating constant is not a problem, while we haven't type like Int(1)
      getContext match {
        case i: ScSugarCallExpr if this == i.getBaseExpr => true
        case m: ScMethodCall if this == m.getInvokedExpr => true
        case ref: ScReferenceExpression if ref.qualifier.contains(this) => true
        case _ => false
      }
    }

    val inner: ScType = bind match {
      case Some(ScalaResolveResult(fun: ScFun, s)) =>
        s.subst(fun.polymorphicType)
      //prevent infinite recursion for recursive pattern reference
      case Some(ScalaResolveResult(self: ScSelfTypeElement, _)) =>
        val clazz = PsiTreeUtil.getContextOfType(self, true, classOf[ScTemplateDefinition])
        ScThisReferenceImpl.getThisTypeForTypeDefinition(clazz, this) match {
          case success: Success[ScType] => success.get
          case failure => return failure
        }
      case Some(r@ScalaResolveResult(refPatt: ScBindingPattern, s)) =>
        ScalaPsiUtil.nameContext(refPatt) match {
          case pd: ScPatternDefinition if PsiTreeUtil.isContextAncestor(pd, this, true) => pd.declaredType match {
            case Some(t) => t
            case None => return Failure("No declared type found", Some(this))
          }
          case vd: ScVariableDefinition if PsiTreeUtil.isContextAncestor(vd, this, true) => vd.declaredType match {
            case Some(t) => t
            case None => return Failure("No declared type found", Some(this))
          }
          case _ =>
            if (stableTypeRequired && refPatt.isStable) {
              r.fromType match {
                case Some(fT) => ScProjectionType(fT, refPatt, superReference = false)
                case None => ScType.designator(refPatt)
              }
            } else {
              val result = refPatt.getType(TypingContext.empty)
              result match {
                case Success(tp, _) => s.subst(tp)
                case _ => return result
              }
            }
        }
      case Some(r@ScalaResolveResult(param: ScParameter, s)) =>
        val owner = param.owner match {
          case f: ScPrimaryConstructor => f.containingClass
          case f: ScFunctionExpr => null
          case f => f
        }
        r.fromType match {
          case Some(fT) if param.isVal && stableTypeRequired => ScProjectionType(fT, param, superReference = false)
          case Some(ScThisType(clazz)) if owner != null && PsiTreeUtil.isContextAncestor(owner, this, true) &&
            stableTypeRequired && owner.isInstanceOf[ScTypeDefinition] && owner == clazz => ScType.designator(param) //todo: think about projection from this type?
          case _ if owner != null && PsiTreeUtil.isContextAncestor(owner, this, true) &&
                  stableTypeRequired && !owner.isInstanceOf[ScTypeDefinition] => ScType.designator(param)
          case _ =>
            val result = param.getRealParameterType(TypingContext.empty)
            s.subst(result match {
              case Success(tp, _) => tp
              case _ => return result
            })
        }
      case Some(ScalaResolveResult(value: ScSyntheticValue, _)) => value.tp
      case Some(ScalaResolveResult(fun: ScFunction, s)) if fun.isProbablyRecursive =>
        val optionResult: Option[ScType] = {
          fun.definedReturnType match {
            case s: Success[ScType] => Some(s.get)
            case fail: Failure => None
          }
        }
        s.subst(fun.polymorphicType(optionResult))
      case Some(result@ScalaResolveResult(fun: ScFunction, s)) =>
        val functionType = s.subst(fun.polymorphicType())
        if (result.isDynamic) ResolvableReferenceExpression.getDynamicReturn(functionType)
        else functionType
      case Some(ScalaResolveResult(param: ScParameter, s)) if param.isRepeatedParameter =>
        val seqClass = ScalaPsiManager.instance(getProject).getCachedClass("scala.collection.Seq", getResolveScope, ScalaPsiManager.ClassCategory.TYPE)
        val result = param.getType(TypingContext.empty)
        val computeType = s.subst(result match {
          case Success(tp, _) => tp
          case _ => return result
        })
        if (seqClass != null) {
          ScParameterizedType(ScType.designator(seqClass), Seq(computeType))
        } else computeType
      case Some(ScalaResolveResult(obj: ScObject, s)) =>
        def tail = {
          fromType match {
            case Some(tp) => ScProjectionType(tp, obj, superReference = false)
            case _ => ScType.designator(obj)
          }
        }
        //hack to add Eta expansion for case classes
        if (obj.isSyntheticObject) {
          ScalaPsiUtil.getCompanionModule(obj) match {
            case Some(clazz) if clazz.isCase && !clazz.hasTypeParameters =>
              expectedType() match {
                case Some(tp) =>
                  if (ScFunctionType.isFunctionType(tp)) {
                    val tp = tail
                    val processor =
                      new MethodResolveProcessor(this, "apply", Nil, Nil, Nil)
                    processor.processType(tp, this)
                    val candidates = processor.candidates
                    if (candidates.length != 1) tail
                    else convertBindToType(Some(candidates(0))).getOrElse(tail)
                  } else tail
                case _ => tail
              }
            case _ => tail
          }
        } else tail
      case Some(r@ScalaResolveResult(f: ScFieldId, s)) =>
        if (stableTypeRequired && f.isStable) {
          r.fromType match {
            case Some(fT) => ScProjectionType(fT, f, superReference = false)
            case None => ScType.designator(f)
          }
        } else {
          val result = f.getType(TypingContext.empty)
          result match {
            case Success(tp, _) => s.subst(tp)
            case _ => return result
          }
        }
      case Some(ScalaResolveResult(typed: ScTypedDefinition, s)) =>
        val result = typed.getType(TypingContext.empty)
        result match {
          case Success(tp, _) => s.subst(tp)
          case _ => return result
        }
      case Some(ScalaResolveResult(pack: PsiPackage, _)) => ScType.designator(pack)
      case Some(ScalaResolveResult(clazz: ScClass, s)) if clazz.isCase =>
        s.subst(clazz.constructor.
                getOrElse(return Failure("Case Class hasn't primary constructor", Some(this))).polymorphicType)
      case Some(ScalaResolveResult(clazz: ScTypeDefinition, s)) if clazz.typeParameters.nonEmpty =>
        s.subst(ScParameterizedType(ScType.designator(clazz),
          clazz.typeParameters.map(new ScTypeParameterType(_, s))))
      case Some(ScalaResolveResult(clazz: PsiClass, _)) => new ScDesignatorType(clazz, true) //static Java class
      case Some(ScalaResolveResult(field: PsiField, s)) =>
        s.subst(field.getType.toScType(field.getProject, getResolveScope))
      case Some(ScalaResolveResult(method: PsiMethod, s)) =>
        if (method.getName == "getClass" && method.containingClass != null &&
          method.containingClass.getQualifiedName == "java.lang.Object") {
          val jlClass = ScalaPsiManager.instance(getProject).getCachedClass("java.lang.Class", getResolveScope,
            ScalaPsiManager.ClassCategory.TYPE)
          def convertQualifier(typeResult: TypeResult[ScType]): Option[ScType] = {
            if (jlClass != null) {
              typeResult match {
                case Success(tp, _) =>
                  val actualType = tp match {
                    case ScThisType(clazz) => ScDesignatorType(clazz)
                    case ScDesignatorType(o: ScObject) => Any
                    case ScCompoundType(comps, _, _) =>
                      if (comps.isEmpty) Any
                      else ScTypeUtil.removeTypeDesignator(comps.head).getOrElse(Any)
                    case _ => ScTypeUtil.removeTypeDesignator(tp).getOrElse(Any)
                  }
                  Some(ScExistentialType(ScParameterizedType(ScDesignatorType(jlClass),
                    Seq(ScTypeVariable("_$1"))), List(ScExistentialArgument("_$1", Nil, Nothing, actualType))))
                case _ => None
              }
            } else None
          }
          val returnType: Option[ScType] = qualifier match {
            case Some(qual) =>
              convertQualifier(qual.getType(TypingContext.empty))
            case None =>
              getContext match {
                case i: ScInfixExpr if i.operation == this =>
                  convertQualifier(i.lOp.getType(TypingContext.empty))
                case i: ScPostfixExpr if i.operation == this =>
                  convertQualifier(i.operand.getType(TypingContext.empty))
                case _ =>
                  for {
                    clazz <- ScalaPsiUtil.drvTemplate(this)
                    qualifier <- convertQualifier(clazz.getType(TypingContext.empty))
                  } yield qualifier
              }
          }
          ResolveUtils.javaPolymorphicType(method, s, getResolveScope, returnType)
        } else {
          ResolveUtils.javaPolymorphicType(method, s, getResolveScope)
        }
      case _ => return Failure("Cannot resolve expression", Some(this))
    }
    qualifier match {
      case Some(s: ScSuperReference) =>
      case None => //infix, prefix and postfix
        getContext match {
          case sugar: ScSugarCallExpr if sugar.operation == this =>
            sugar.getBaseExpr.getNonValueType(TypingContext.empty) match {
              case Success(ScTypePolymorphicType(_, typeParams), _) =>
                inner match {
                  case ScTypePolymorphicType(internal, typeParams2) =>
                    return Success(ScalaPsiUtil.removeBadBounds(ScTypePolymorphicType(internal, typeParams ++ typeParams2 ++ unresolvedTypeParameters)), Some(this))
                  case _ =>
                    return Success(ScTypePolymorphicType(inner, typeParams ++ unresolvedTypeParameters), Some(this))
                }
              case _ if unresolvedTypeParameters.nonEmpty =>
                inner match {
                  case ScTypePolymorphicType(internal, typeParams) =>
                    return Success(ScTypePolymorphicType(internal, unresolvedTypeParameters ++ typeParams), Some(this))
                  case _ =>
                    return Success(ScTypePolymorphicType(inner, unresolvedTypeParameters), Some(this))
                }
              case _ =>
            }
          case _ =>
        }
      case Some(qualifier) =>
        qualifier.getNonValueType(TypingContext.empty) match {
          case Success(ScTypePolymorphicType(_, typeParams), _) =>
            inner match {
              case ScTypePolymorphicType(internal, typeParams2) =>
                return Success(ScalaPsiUtil.removeBadBounds(ScTypePolymorphicType(internal, typeParams ++ typeParams2 ++ unresolvedTypeParameters)), Some(this))
              case _ =>
                return Success(ScTypePolymorphicType(inner, typeParams ++ unresolvedTypeParameters), Some(this))
            }
          case _ if unresolvedTypeParameters.nonEmpty =>
            inner match {
              case ScTypePolymorphicType(internal, typeParams) =>
                return Success(ScTypePolymorphicType(internal, unresolvedTypeParameters ++ typeParams), Some(this))
              case _ =>
                return Success(ScTypePolymorphicType(inner, unresolvedTypeParameters), Some(this))
            }
          case _ =>
        }
    }
    Success(inner, Some(this))
  }

  def getPrevTypeInfoParams: Seq[TypeParameter] = {
    qualifier match {
      case Some(s: ScSuperReference) => Seq.empty
      case Some(qual) =>
        qual.getNonValueType(TypingContext.empty).map {
          case t: ScTypePolymorphicType => t.typeParameters
          case _ => Seq.empty
        }.getOrElse(Seq.empty)
      case _ => getContext match {
        case sugar: ScSugarCallExpr if sugar.operation == this =>
          sugar.getBaseExpr.getNonValueType(TypingContext.empty).map {
            case t: ScTypePolymorphicType => t.typeParameters
            case _ => Seq.empty
          }.getOrElse(Seq.empty)
        case _ => Seq.empty
      }
    }
  }
}
