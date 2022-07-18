package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi._
import com.intellij.psi.impl.source.resolve.JavaResolveUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScTypeElement, ScTypeVariableTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAccessModifier, ScFieldId, ScModifierList, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticClass, ScSyntheticValue}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitConversionResolveResult
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor.conformsToDynamic
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, MethodResolveProcessor}
import org.jetbrains.plugins.scala.macroAnnotations.CachedWithRecursionGuard
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.areClassesEquivalent

import scala.annotation.tailrec

object ResolveUtils {
  def kindMatches(element: PsiElement, kinds: Set[ResolveTargets.Value]): Boolean = kinds == null ||
          (element match {
            case _: PsiPackage | _: ScPackaging       => kinds contains PACKAGE
            case obj: ScObject if obj.isPackageObject => kinds contains PACKAGE
            case _: ScObject                          => (kinds contains OBJECT) || (kinds contains METHOD)
            case _: ScTypeVariableTypeElement         => kinds contains CLASS
            case _: ScTypeParam                       => kinds contains CLASS
            case _: ScTypeAlias                       => kinds contains CLASS
            case _: ScTypeDefinition                  => kinds contains CLASS
            case _: ScSyntheticClass                  => kinds contains CLASS
            case c: PsiClass =>
              if (kinds contains CLASS) true
              else {
                @tailrec
                def isStaticCorrect(clazz: PsiClass): Boolean = {
                  val cclazz = clazz.getContainingClass
                  cclazz == null || (clazz.hasModifierProperty(PsiModifier.STATIC) && isStaticCorrect(cclazz))
                }
                (kinds contains OBJECT) && isStaticCorrect(c)
              }
            case patt: ScBindingPattern =>
              val value = patt.nonStrictParentOfType(Seq(classOf[ScVariable], classOf[ScValue])) match {
                case Some(_: ScVariable) => VAR
                case _ => VAL
              }
              kinds.contains(value)
            case patt: ScFieldId =>
              if (patt.getParent /*list of ids*/ .getParent.isInstanceOf[ScVariable])
                kinds contains VAR else kinds contains VAL
            case classParam: ScClassParameter =>
              if (classParam.isVar) kinds.contains(VAR) else kinds.contains(VAL)
            case _: ScParameter => kinds contains VAL
            case _: ScSelfTypeElement => kinds contains VAL
            case _: PsiMethod => kinds contains METHOD
            case _: ScFun => kinds contains METHOD
            case _: ScSyntheticValue => kinds contains VAL
            case f: PsiField => (kinds contains VAR) || (f.hasModifierPropertyScala(PsiModifier.FINAL) && kinds.contains(VAL))
            case _: PsiParameter => kinds contains VAL //to enable named Parameters resolve in Play 2.0 routing file for java methods
            case _ => false
          })

  def isAccessible(memb: PsiMember, _place: PsiElement, forCompletion: Boolean = false): Boolean =
    isAccessibleWithNewModifiers(memb, _place, memb.getModifierList, forCompletion)


  def isAccessibleWithNewModifiers(memb: PsiMember, _place: PsiElement, modifierList: PsiModifierList, forCompletion: Boolean = false): Boolean = {
    if (!memb.isValid || !_place.isValid) {
      return false
    }

    var place = _place
    memb match {
      case b: ScBindingPattern =>
        b.nameContext match {
          case memb: ScMember => return isAccessibleWithNewModifiers(memb, place, modifierList)
          case _ => return true
        }
      //todo: ugly workaround, probably FakePsiMethod is better to remove?
      case FakePsiMethod(method: PsiMember) => return isAccessibleWithNewModifiers(method, place, modifierList)
      case _: FakePsiMethod =>
      case _ =>
    }
    if (place.getLanguage == JavaLanguage.INSTANCE) {
      return JavaResolveUtil.isAccessible(memb, memb.containingClass, modifierList, place, null, null)
    }

    import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getPlaceTd
    //this is to make place and member on same level (resolve from library source)
    var member: PsiMember = memb
    memb.getContainingFile match {
      case file: ScalaFile if file.isCompiled =>
        place.getContainingFile match {
          case file: ScalaFile if file.isCompiled =>
          case _ if !member.isInstanceOf[ScMember] =>
            member = member.getOriginalElement.asInstanceOf[PsiMember]
          case _ => //todo: is it neccessary? added to avoid performance and other problems
        }
      case _ =>
    }
    if (forCompletion && place != null) {
      val originalFile: PsiFile = place.getContainingFile.getOriginalFile
      if (originalFile == member.getContainingFile) {
        val newPlace = originalFile.findElementAt(place.getTextRange.getStartOffset)
        place = newPlace
      }
    }

    def checkProtected(td: PsiClass, withCompanion: Boolean): Boolean = {
      val isConstr = member match {
        case m: PsiMethod => m.isConstructor
        case _ => false
      }
      var placeTd: ScTemplateDefinition = getPlaceTd(place, isConstr)
      if (isConstr) {
        if (placeTd != null && !placeTd.is[ScTypeDefinition] && placeTd.extendsBlock.templateBody.isEmpty) {
          placeTd = getPlaceTd(placeTd)
        } else if (placeTd != null) {
          if (td != null && isInheritorOrSelfOrSame(placeTd, td)) return true
        }
        while (placeTd != null) {
          if (td == placeTd) return true
          if (getCompanionModule(placeTd).contains(td)) return true
          placeTd = getPlaceTd(placeTd)
        }
        return false
      }
      while (placeTd != null) {
        if (td != null && isInheritorOrSelfOrSame(placeTd, td)) return true
        if (withCompanion &&
          td != null
          && getCompanionModule(placeTd).exists(isInheritorDeep(_, td))) return true
        placeTd = getPlaceTd(placeTd)
      }
      false
    }

    (member, modifierList) match {
      case (scMember: ScMember, scModifierList: ScModifierList) =>
        // if member is a scala member, the modifier list also have to be a scala one
        scModifierList.accessModifier match {
          case None => true
          case Some(am: ScAccessModifier) =>
            if (am.isPrivate) {
              if (am.isThis) {
                val containingClass = scMember.containingClass
                if (containingClass == null) return true

                if (scModifierList.hasModifierProperty("implicit"))
                  return PsiTreeUtil.isContextAncestor(containingClass, place, false)
                /*
                ScalaRefernce.pdf:
                  A member M marked with this modifier can be accessed only from
                  within the object in which it is defined.
                */
                place match {
                  case ref: ScReference =>
                    ref.qualifier match {
                      case None =>
                        return PsiTreeUtil.isContextAncestor(containingClass, place, false)
                      case Some(t: ScThisReference) =>
                        return t.refTemplate match {
                          case Some(templ) => templ == containingClass
                          case _ => PsiTreeUtil.isContextAncestor(containingClass, place, false)
                        }
                      case Some(ref: ScReference) =>
                        val resolve = ref.resolve()
                        if (containingClass.extendsBlock.selfTypeElement.contains(resolve)) return true
                        else return false
                      case _ => return false
                    }
                  case _ =>
                    return PsiTreeUtil.isContextAncestor(containingClass, place, false)
                }
              }
              val ref = am.getReference
              if (ref != null) {
                val bind = ref.resolve
                if (bind == null) return true
                def processPackage(packageName: String): Boolean = {
                  def context(place: PsiElement): PsiElement =
                    PsiTreeUtil.getContextOfType(place, true,
                      classOf[ScPackaging], classOf[ScObject], classOf[ScalaFile])
                  var placeEnclosing: PsiElement = context(place)
                  while (placeEnclosing != null && placeEnclosing.is[ScObject] &&
                           !placeEnclosing.asInstanceOf[ScObject].isPackageObject)
                    placeEnclosing = context(placeEnclosing)
                  if (placeEnclosing == null) return false //not Scala
                  val placePackageName = placeEnclosing match {
                    case _: ScalaFile => ""
                    case obj: ScObject => obj.qualifiedName
                    case pack: ScPackaging => pack.fullPackageName
                  }
                  packageContains(packageName, placePackageName)
                }
                bind match {
                  case td: ScTemplateDefinition if smartContextAncestor(td, place, checkCompanion = true) =>
                    true
                  case obj: ScObject =>
                    obj.isPackageObject && processPackage(obj.qualifiedName)
                  case pack: PsiPackage =>
                    val packageName = pack.getQualifiedName
                    processPackage(packageName)
                  case _ => true
                }
              }
              else {
                /*
                ScalaReference.pdf:
                  Such members can be accessed only from within the directly enclosing
                  template and its companion module or companion class
                */
                val enclosing = PsiTreeUtil.getContextOfType(scMember, true,
                  classOf[ScalaFile], classOf[ScPackaging], classOf[ScTemplateDefinition])
                enclosing match {
                  case td: ScTemplateDefinition =>
                    smartContextAncestor(td, place, checkCompanion = true)
                  case file: ScalaFile if file.isScriptFile =>
                    PsiTreeUtil.isContextAncestor(file, place, false)
                  case _ =>
                    place.contexts.find {
                      case _: ScPackaging | _: ScalaFile => true
                      case o: ScObject if o.isPackageObject => true
                      case _ => false
                    } match {
                      case None => false // not Scala
                      case Some(placeEnclosing) =>
                        def packaging(element: PsiElement) = element match {
                          case p: ScPackaging => p.fullPackageName
                          case o: ScObject =>
                            val packageName = o.getParent.asOptionOf[ScPackaging].map(_.fullPackageName).mkString
                            s"$packageName.${o.name}"
                          case _ => ""
                        }

                        packageContains(packaging(enclosing), packaging(placeEnclosing))
                    }
                }
              }
            } else { //todo: it's wrong if reference after not appropriate class type
              val withCompanion = !am.isThis
              val ref = am.getReference
              if (ref != null) {
                val bind = ref.resolve
                if (bind == null) return true
                def processPackage(packageName: String): Option[Boolean] = {
                  def context(place: PsiElement): PsiElement =
                    PsiTreeUtil.getContextOfType(place, true, classOf[ScPackaging],
                      classOf[ScObject], classOf[ScalaFile])
                  var placeEnclosing: PsiElement = context(place)
                  while (placeEnclosing != null && placeEnclosing.is[ScObject] &&
                           !placeEnclosing.asInstanceOf[ScObject].isPackageObject)
                    placeEnclosing = context(placeEnclosing)
                  if (placeEnclosing == null) return Some(false) //not Scala
                  val placePackageName = placeEnclosing match {
                    case _: ScalaFile => ""
                    case obj: ScObject => obj.qualifiedName
                    case pack: ScPackaging => pack.fullPackageName
                  }
                  if (packageContains(packageName, placePackageName)) return Some(true)
                  None
                }
                bind match {
                  case td: ScTemplateDefinition =>
                    if (smartContextAncestor(td, place, checkCompanion = true)) return true
                    td match {
                      case o: ScObject if o.isPackageObject =>
                        processPackage(o.qualifiedName) match {
                          case Some(x) => return x
                          case None =>
                        }
                      case _ =>
                    }
                  case pack: PsiPackage => //like private (nothing related to real life)
                    val packageName = pack.getQualifiedName
                    processPackage(packageName) match {
                      case Some(x) => return x
                      case None =>
                    }
                  case _ => return true
                }
              }
              val enclosing = PsiTreeUtil.getContextOfType(scMember, true,
                classOf[ScalaFile], classOf[ScTemplateDefinition], classOf[ScPackaging])
              assert(enclosing != null, s"Enclosing is null in file ${scMember.getContainingFile.getName}:\n${scMember.getContainingFile.getText}")
              if (am.isThis) {
                place match {
                  case ref: ScReference =>
                    ref.qualifier match {
                      case None =>
                      case Some(_: ScThisReference) =>
                      case Some(_: ScSuperReference) =>
                      case Some(ResolvesTo(_: ScSelfTypeElement)) =>
                        val enclosing = PsiTreeUtil.getContextOfType(scMember, true, classOf[ScTemplateDefinition])
                        if (enclosing == null) return false
                      case _ => return false
                    }
                  case _ =>
                }
              }
              enclosing match {
                case td: ScTypeDefinition =>
                  if (smartContextAncestor(td, place, withCompanion)) return true
                  checkProtected(td, withCompanion)
                case td: ScTemplateDefinition =>
                  //it'd anonymous class, has access only inside
                  PsiTreeUtil.isContextAncestor(td, place, false)
                case _ =>
                  //same as for private
                  val packageName = enclosing match {
                    case _: ScalaFile => ""
                    case packaging: ScPackaging => packaging.fullPackageName
                  }
                  val placeEnclosing: PsiElement = PsiTreeUtil.getContextOfType(place, true, classOf[ScPackaging], classOf[ScalaFile])
                  if (placeEnclosing == null) return false //not Scala
                  val placePackageName = placeEnclosing match {
                    case _: ScalaFile => ""
                    case pack: ScPackaging => pack.fullPackageName
                  }
                  packageContains(packageName, placePackageName)
              }
            }
      }
      case _ =>
        if (modifierList.hasModifierProperty("public")) true
        else if (modifierList.hasModifierProperty("private")) false
        else if (modifierList.hasModifierProperty("protected") &&
                checkProtected(member.containingClass, withCompanion = true)) true
        else {
          val packageName = member.getContainingFile match {
            case _: ScalaFile => ""
            case f: PsiClassOwner => f.getPackageName
            case _ => return false
          }
          val placeEnclosing: PsiElement = PsiTreeUtil.getContextOfType(place, true, classOf[ScPackaging], classOf[ScalaFile])
          if (placeEnclosing == null) return false
          val placePackageName = placeEnclosing match {
            case _: ScalaFile => ""
            case pack: ScPackaging => pack.fullPackageName
          }
          packageContains(packageName, placePackageName)
        }
    }
  }

  def processSuperReference(superRef: ScSuperReference, processor : BaseProcessor, place : ScalaPsiElement): BaseProcessor = {
    if (superRef.isHardCoded) {
      superRef.drvTemplate.foreach(c => processor.processType(ScThisType(c), place))
    } else {
      superRef.staticSuper match {
        case Some(t) => processor.processType(t, place)
        case None => superRef.drvTemplate match {
          case Some(c) =>
            TypeDefinitionMembers.processSuperDeclarations(c, processor, ScalaResolveState.withSubstitutor(ScSubstitutor.empty), null, place)
          case None =>
        }
      }
    }
    processor
  }

  private def isInheritorOrSame(tp: ScType, cl: PsiClass): Boolean = tp match {
    case ScCompoundType(comps, _, _) =>
      comps.exists(isInheritorOrSame(_, cl))
    case tpt: TypeParameterType =>
      isInheritorOrSame(tpt.upperType, cl)
    case _ =>
      tp.extractClass.exists(_.sameOrInheritor(cl))
  }

  private def isInheritorOrSelfOrSame(placeTd: ScTemplateDefinition, td: PsiClass): Boolean = {
    if (placeTd.sameOrInheritor(td)) return true

    placeTd.selfTypeElement match {
      case Some(te: ScSelfTypeElement) => te.typeElement match {
        case Some(te: ScTypeElement) =>
          te.`type`()
            .exists(isInheritorOrSame(_, td))
        case _ => false
      }
      case _ => false
    }
  }

  private def smartContextAncestor(td: ScTemplateDefinition, place: PsiElement, checkCompanion: Boolean): Boolean = {
    val companion = if (checkCompanion) getCompanionModule(td) else None

    place.withContexts.exists {
      case contextTd: ScTemplateDefinition =>
        areClassesEquivalent(td, contextTd) || companion.exists(areClassesEquivalent(_, contextTd))
      case _ => false
    }
  }

  private def packageContains(packageName: String, potentialChild: String): Boolean = {
    ScalaNamesUtil.equivalentFqn(potentialChild, packageName) || potentialChild.startsWith(packageName + ".")
  }

  /**
    * Returns smallest enclosing scope defined by some type definition `Foo`
    * in which Foo.this/Foo.super makes sense, e.g. if invoked on `Foo.this` in
    * {{{
    * trait Foo {
    *   trait Bar
    *   trait Foo extends Foo.this.Bar
    * }
    * }}}
    * will return outer `Foo` trait.
    */
  def enclosingTypeDef(e: PsiElement): Option[ScTypeDefinition] = {
    def enclosingTdef(e: PsiElement): ScTypeDefinition =
      PsiTreeUtil.getContextOfType(e, true, classOf[ScTypeDefinition])

    val isInTemplateParents = PsiTreeUtil.getContextOfType(e, true, classOf[ScTemplateParents])

    if (isInTemplateParents != null) enclosingTdef(enclosingTdef(isInTemplateParents)).toOption
    else                             enclosingTdef(e).toOption
  }

  implicit class ScExpressionForExpectedTypesEx(private val expr: ScExpression) extends AnyVal {
    @CachedWithRecursionGuard(expr, Array.empty[ScalaResolveResult], BlockModificationTracker(expr))
    def shapeResolveApplyMethod(
      tp:    ScType,
      exprs: Seq[ScExpression],
      call:  Option[MethodInvocation]
    ): Array[ScalaResolveResult] = {
      val applyProc =
        new MethodResolveProcessor(
          expr,
          "apply",
          List(exprs),
          Seq.empty,
          Seq.empty /* todo: ? */,
          StdKinds.methodsOnly,
          isShapeResolve = true
        )

      applyProc.processType(tp, expr, ScalaResolveState.withFromType(tp))
      var cand = applyProc.candidates
      if (cand.isEmpty && call.isDefined) {
        val expr = call.get.getEffectiveInvokedExpr

        ImplicitConversionResolveResult.processImplicitConversionsAndExtensions(
          Some("apply"),
          expr,
          applyProc,
          precalculatedType = Some(tp)
        )(identity)(expr)

        cand = applyProc.candidates
      }
      if (cand.isEmpty && conformsToDynamic(tp, expr.resolveScope) && call.isDefined) {
        cand = ScalaPsiUtil.processTypeForUpdateOrApplyCandidates(
          call.get,
          tp,
          isShape   = true,
          isDynamic = true
        )
      }
      cand
    }
  }

  object ExtensionMethod {
    def unapply(fdef: ScFunction): Boolean = fdef.isExtensionMethod
  }

  def isExtensionMethod(e: PsiElement): Boolean = e match {
    case ExtensionMethod() => true
    case _                 => false
  }

  /**
   * Is `invokedExpr` an invocation of an extension method `fun`
   * of shape `x.fun`, i.e. an actual extension and not regular method call.
   */
  def isExtensionMethodCall(invokedExpr: PsiElement, fun: PsiNamedElement): Boolean =
    isExtensionMethod(fun) && (invokedExpr match {
      case ref: ScReferenceExpression => ref.isQualified
      case _                          => false
    })
}
