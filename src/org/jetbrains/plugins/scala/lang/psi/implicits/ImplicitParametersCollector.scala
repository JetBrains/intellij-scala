package org.jetbrains.plugins.scala.lang.psi.implicits

import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import processor.{ImplicitProcessor, MostSpecificUtil}
import result.{Success, TypingContext}
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import params.{ScClassParameter, ScParameter, ScTypeParam}
import util.PsiTreeUtil
import collection.immutable.HashSet
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScMember}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

/**
 * @param place        The call site
 * @param tp           Search for an implicit definition of this type. May have type variables.
 *
 * User: Alexander Podkhalyuzin
 * Date: 23.11.2009
 */
class ImplicitParametersCollector(place: PsiElement, tp: ScType) {
  def collect: Seq[ScalaResolveResult] = {
    var processor = new ImplicitParametersProcessor(false)
    def treeWalkUp(placeForTreeWalkUp: PsiElement, lastParent: PsiElement) {
      if (placeForTreeWalkUp == null) return
      if (!placeForTreeWalkUp.processDeclarations(processor,
        ResolveState.initial(), lastParent, place)) return
      place match {
        case (_: ScTemplateBody | _: ScExtendsBlock) => //template body and inherited members are at the same level
        case _ => if (!processor.changedLevel) return
      }
      treeWalkUp(placeForTreeWalkUp.getContext, placeForTreeWalkUp)
    }
    treeWalkUp(place, null) //collecting all references from scope

    val candidates = processor.candidatesS.toSeq
    if (!candidates.isEmpty) return candidates

    processor = new ImplicitParametersProcessor(true)

    for (obj <- ScalaPsiUtil.collectImplicitObjects(tp, place)) {
      processor.processType(obj, place, ResolveState.initial())
    }

    processor.candidatesS.toSeq
  }

  class ImplicitParametersProcessor(withoutPrecedence: Boolean) extends ImplicitProcessor(StdKinds.refExprLastRef, withoutPrecedence) {
    protected def getPlace: PsiElement = place

    def execute(element: PsiElement, state: ResolveState): Boolean = {
      if (!kindMatches(element)) return true
      val named = element.asInstanceOf[PsiNamedElement]
      val subst = getSubst(state)
      named match {
        case o: ScObject if o.hasModifierProperty("implicit") =>
          if (!ResolveUtils.isAccessible(o, getPlace)) return true
          addResult(new ScalaResolveResult(o, subst, getImports(state)))
        case param: ScParameter if param.isImplicitParameter =>
          param match {
            case c: ScClassParameter =>
              if (!ResolveUtils.isAccessible(c, getPlace)) return true
            case _ =>
          }
          addResult(new ScalaResolveResult(param, subst, getImports(state)))
        case patt: ScBindingPattern => {
          val memb = ScalaPsiUtil.getContextOfType(patt, true, classOf[ScValue], classOf[ScVariable])
          memb match {
            case memb: ScMember if memb.hasModifierProperty("implicit") =>
              if (!ResolveUtils.isAccessible(memb, getPlace)) return true
              addResult(new ScalaResolveResult(named, subst, getImports(state)))
            case _ =>
          }
        }
        case function: ScFunction if function.hasModifierProperty("implicit") => {
          if (!ResolveUtils.isAccessible(function, getPlace)) return true
          addResult(new ScalaResolveResult(named, subst.followed(ScalaPsiUtil.inferMethodTypesArgs(function, subst)), getImports(state)))
        }
        case _ =>
      }
      true
    }

    override def candidatesS: scala.collection.Set[ScalaResolveResult] = {
      val clazz = ScType.extractClass(tp)
      def forFilter(c: ScalaResolveResult): Option[ScalaResolveResult] = {
        def compute(): Option[ScalaResolveResult] = {
          val subst = c.substitutor
          c.element match {
            case o: ScObject if !PsiTreeUtil.isContextAncestor(o, place, false) =>
              o.getType(TypingContext.empty) match {
                case Success(objType: ScType, _) =>
                  if (!subst.subst(objType).conforms(tp)) None
                  else Some(c)
                case _ => None
              }
            case param: ScParameter if !PsiTreeUtil.isContextAncestor(param, place, false) =>
              param.getType(TypingContext.empty) match {
                case Success(paramType: ScType, _) =>
                  if (!subst.subst(paramType).conforms(tp)) None
                  else Some(c)
                case _ => None
              }
            case patt: ScBindingPattern
              if !PsiTreeUtil.isContextAncestor(ScalaPsiUtil.nameContext(patt), place, false) => {
              patt.getType(TypingContext.empty) match {
                case Success(pattType: ScType, _) =>
                  if (!subst.subst(pattType).conforms(tp)) None
                  else Some(c)
                case _ => None
              }
            }
            case fun: ScFunction if !PsiTreeUtil.isContextAncestor(fun, place, false) => {
              val oneImplicit = fun.paramClauses.clauses.length == 1 && fun.paramClauses.clauses.apply(0).isImplicit
              var doNotCheck = false
              if (!oneImplicit && fun.paramClauses.clauses.length > 0) {
                clazz match {
                  case Some(clazz) =>
                    val clause = fun.paramClauses.clauses(0)
                    val funNum = clause.parameters.length
                    val qName = "scala.Function" + funNum
                    val classQualifiedName = clazz.getQualifiedName
                    if (classQualifiedName != qName && classQualifiedName != "java.lang.Object" &&
                        classQualifiedName != "scala.ScalaObject") doNotCheck = true
                  case _ =>
                }
              }

              if (!doNotCheck) {
                fun.getType(TypingContext.empty) match {
                  case Success(funType: ScType, _) => {

                    def checkType(ret: ScType): Option[ScalaResolveResult] = {
                      var uSubst = Conformance.undefinedSubst(tp, ret)
                      uSubst.getSubstitutor match {
                        case Some(substitutor) =>
                          def hasRecursiveTypeParameters(typez: ScType): Boolean = {

                            var hasRecursiveTypeParameters = false
                            typez.recursiveUpdate {
                              case tpt: ScTypeParameterType =>
                                fun.typeParameters.find(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp)) == (tpt.name, tpt.getId)) match {
                                  case None => (true, tpt)
                                  case _ =>
                                    hasRecursiveTypeParameters = true
                                    (true, tpt)
                                }
                              case tp: ScType => (hasRecursiveTypeParameters, tp)
                            }
                            hasRecursiveTypeParameters
                          }
                          for (tParam <- fun.typeParameters) {
                            val lowerType: ScType = tParam.lowerBound.getOrNothing
                            if (lowerType != Nothing) {
                              val substedLower = substitutor.subst(subst.subst(lowerType))
                              if (!hasRecursiveTypeParameters(substedLower)) {
                                uSubst = uSubst.addLower((tParam.name, ScalaPsiUtil.getPsiElementId(tParam)), substedLower)
                              }
                            }
                            val upperType: ScType = tParam.upperBound.getOrAny
                            if (upperType != Any) {
                              val substedUpper = substitutor.subst(subst.subst(upperType))
                              if (!hasRecursiveTypeParameters(substedUpper)) {
                                uSubst = uSubst.addUpper((tParam.name, ScalaPsiUtil.getPsiElementId(tParam)), substedUpper)
                              }
                            }
                          }

                          uSubst.getSubstitutor match {
                            case Some(substitutor) =>
                              Some(c.copy(subst.followed(substitutor)))

                            case None => None
                          }

                        //failed to get implicit parameter, there is no substitution to resolve constraints
                        case None => None
                      }
                    }

                    if (subst.subst(funType) conforms tp) checkType(subst.subst(funType))
                    else {
                      subst.subst(funType) match {
                        case ScFunctionType(ret, params) if params.length == 0 || oneImplicit =>
                          if (!ret.conforms(tp)) None
                          else checkType(ret)
                        case _ => None
                      }
                    }
                  }
                  case _ => None
                }
              } else None
            }
            case _ => None
          }
        }

        import org.jetbrains.plugins.scala.caches.ScalaRecursionManager._

        doComputations(c.element, (tp: Object, searches: Seq[Object]) => searches.find{
          case t: ScType if tp.isInstanceOf[ScType] => t.equiv(tp.asInstanceOf[ScType])
          case _ => false
        } == None,
          tp, compute(), IMPLICIT_PARAM_TYPES_KEY) match {
          case Some(res) => res
          case None => None
        }
      }

      val applicable = super.candidatesS.map(forFilter).flatten
      new MostSpecificUtil(place, 1).mostSpecificForResolveResult(applicable) match {
        case Some(r) => HashSet(r)
        case _ => applicable
      }
    }
  }
}