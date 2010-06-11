package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.scope._
import com.intellij.psi._
import _root_.scala.collection.Set
import org.jetbrains.plugins.scala.lang.psi.api._
import expr.ScReferenceExpression
import statements.{ScTypeAlias}
import psi.types._
import psi.ScalaPsiElement
import psi.impl.toplevel.typedef.TypeDefinitionMembers
import toplevel.imports.usages.ImportUsed
import ResolveTargets._
import _root_.scala.collection.mutable.HashSet
import com.intellij.psi.util.PsiTreeUtil
import toplevel.typedef.ScTemplateDefinition
import psi.impl.toplevel.synthetic.{ScSyntheticFunction, SyntheticClasses}


object BaseProcessor {
  def unapply(p: BaseProcessor) = Some(p.kinds)

  val boundClassKey: Key[PsiClass] = Key.create("bound.class.key")
}

abstract class BaseProcessor(val kinds: Set[ResolveTargets.Value]) extends PsiScopeProcessor {
  protected val candidatesSet: HashSet[ScalaResolveResult] = new HashSet[ScalaResolveResult]

  def changedLevel = true

  def candidates[T >: ScalaResolveResult : ClassManifest]: Array[T] = candidatesSet.toArray[T]

  //java compatibility
  object MyElementClassHint extends ElementClassHint {
    import ElementClassHint.DeclaractionKind
    def shouldProcess(kind: DeclaractionKind): Boolean = {
      kind match {
        case null => true
        case DeclaractionKind.PACKAGE => kinds contains ResolveTargets.PACKAGE
        case DeclaractionKind.CLASS => (kinds contains ResolveTargets.CLASS) || (kinds contains ResolveTargets.OBJECT) ||
                (kinds contains ResolveTargets.METHOD) //case classes get 'apply' generated
        case DeclaractionKind.VARIABLE => (kinds contains ResolveTargets.VAR) || (kinds contains ResolveTargets.VAL)
        case DeclaractionKind.FIELD => (kinds contains ResolveTargets.VAR) || (kinds contains ResolveTargets.VAL)
        case DeclaractionKind.METHOD => kinds contains (ResolveTargets.METHOD)
        case _ => false
      }
    }
  }

  def getHint[T](hintKey: Key[T]): T = {
    if (hintKey == ElementClassHint.KEY) {
      return MyElementClassHint.asInstanceOf[T]
    } else {
      return null.asInstanceOf[T]
    }
  }

  def handleEvent(event: PsiScopeProcessor.Event, associated: Object) = {}

  protected def kindMatches(element: PsiElement): Boolean = ResolveUtils.kindMatches(element, kinds)

  def processType(t: ScType, place: ScalaPsiElement): Boolean = processType(t, place, ResolveState.initial)

  def processType(t: ScType, place: ScalaPsiElement, state: ResolveState): Boolean = {
    ProgressManager.checkCanceled

    def isInApplyCall = place.getContext match {
      case expr: ScReferenceExpression => expr.nameId.getText == "apply"
      case _ => false
    }

    if (ScType.extractClass(t).map(_.getQualifiedName) == Some("java.lang.String")) {
      val plusMethod: ScSyntheticFunction = SyntheticClasses.get(place.getProject).stringPlusMethod
      if (plusMethod != null) execute(plusMethod, state) //add + method
    }

    t match {
      case ScDesignatorType(e) => processElement(e, ScSubstitutor.empty, place, state)
      case ScPolymorphicType(_, Nil, _, upper) => processType(upper.v, place)
      case j: JavaArrayType => processType(j.getParameterizedType(place.getProject, place.getResolveScope).getOrElse(return true), place, state)
      case p@ScParameterizedType(des, typeArgs) => {
        p.designator match {
          case ScPolymorphicType(_, _, _, upper) => processType(p.substitutor.subst(upper.v), place)
          case _ => p.designated match {
            case Some(des) => processElement(des, p.substitutor, place, state)
            case None => true
          }
        }
      }
      //resolve dependent types case
      case proj@ScProjectionType(des, ref) => proj.resolveResult match {
        case Some(res) => {
          val clazz = PsiTreeUtil.getContextOfType(res.element, classOf[PsiClass], true)
          val subst = if (clazz != null) {
            des match {
              case ScDesignatorType(c: PsiClass) if c != clazz =>
                {
                  Bounds.superSubstitutor(clazz, c, res.substitutor.bindD(clazz, c)) match {
                    case Some(s) => {
                      if (c.isInstanceOf[ScTemplateDefinition]) {
                        Bounds.putAliases(c.asInstanceOf[ScTemplateDefinition], s)
                      } else s
                    }
                    case _ => res.substitutor.bindD(clazz, c)
                  }
                }
              case ScDesignatorType(elem) if elem != clazz => res.substitutor.bindD(clazz, elem)
              case s@ScSingletonType(path) => {
                s.pathType match {
                  case ScDesignatorType(c: PsiClass) => {
                    Bounds.superSubstitutor(clazz, c, res.substitutor.bindD(clazz, c)) match {
                      case Some(s) => {
                        if (c.isInstanceOf[ScTemplateDefinition]) {
                          Bounds.putAliases(c.asInstanceOf[ScTemplateDefinition], s)
                        } else s
                      }
                      case _ => res.substitutor.bindD(clazz, c)
                    }
                  }
                  case ScDesignatorType(elem) if elem != clazz => res.substitutor.bindD(clazz, elem)
                  case _ => res.substitutor
                }
              }
              case _ => res.substitutor
            }
          } else res.substitutor
          processElement(res.element, subst, place, state)
        }
        case None => true
      }

      case StdType(name, tSuper) => (SyntheticClasses.get(place.getProject).byName(name): @unchecked) match {
        case Some(c) => {
          if (!c.processDeclarations(this, state, null, place) ||
                  !(tSuper match {
                    case Some(ts) => processType(ts, place)
                    case _ => true
                  })) return false

          if (name == "Any") {
            for (m <- c.methods) {
              m._1 match {
                case "toString" | "hashCode" | "equals" => for (meth <- m._2) this.execute(meth, state)
                case _ => //do nothing
              }
            }
          }
          true
        }
        case None => true//nothing to do
      }

      case ft@ScFunctionType(rt, params) if isInApplyCall => {
        ft.resolveFunctionTrait(place.getProject).map(processType((_: ScType), place)).getOrElse(true)
      }

      case ft@ScFunctionType(rt, params) if params.isEmpty => {
        processType(rt, place)
      }

      case tp@ScTupleType(comps) => {
        tp.resolveTupleTrait(place.getProject).map(processType((_: ScType), place)).getOrElse(true)
      }

      case comp@ScCompoundType(components, declarations, types, substitutor) => {
        val oldSubst = state.get(ScSubstitutor.key)
        val newState = state.put(ScSubstitutor.key, substitutor.followed(oldSubst))
        if (kinds.contains(VAR) || kinds.contains(VAL) || kinds.contains(METHOD)) {
          for (declaration <- declarations) {
            for (declared <- declaration.declaredElements) {
              if (!execute(declared, newState)) return false
            }
          }
        }

        if (kinds.contains(CLASS)) {
          for (t <- types) {
            if (!execute(t, newState)) return false
          }
        }

        //todo: comps already substituted
        if (!TypeDefinitionMembers.processDeclarations(comp, this, newState, null, place)) return false
        true
      }
      case singl: ScSingletonType => {
        // See test ThisTypeCompound.
        val qual = place match {
          case ref: ScReferenceExpression => ref.qualifier
          case _ => None
        }
        processType(singl.pathTypeInContext(qual), place)
      }
      case ex: ScExistentialType => processType(ex.skolem, place)
      case z: ScExistentialArgument => processType(z.upperBound, place); processType(z.lowerBound, place)
      case _ => true
    }
  }

  private def processElement (e : PsiNamedElement, s : ScSubstitutor, place: ScalaPsiElement, state: ResolveState) = {
    e match {
      case ta: ScTypeAlias => processType(s.subst(ta.upperBound.getOrElse(Any)), place)

      //need to process scala way
      case clazz: PsiClass =>
        TypeDefinitionMembers.processDeclarations(clazz, this, state.put(ScSubstitutor.key, s),
          null, place)

      case des => des.processDeclarations(this, state.put(ScSubstitutor.key, s), null, place)
    }
  }

  protected def getSubst(state: ResolveState) = {
    val subst: ScSubstitutor = state.get(ScSubstitutor.key)
    if (subst == null) ScSubstitutor.empty else subst
  }

  protected def getImports(state: ResolveState): Set[ImportUsed] = {
    val used = state.get(ImportUsed.key)
    if (used == null) Set[ImportUsed]() else used
  }

  protected def getBoundClass(state: ResolveState): PsiClass = {
    state.get(BaseProcessor.boundClassKey)
  }
}
