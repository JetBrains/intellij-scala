package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScTemplateBodyImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeVisitor
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedMappedWithRecursionGuard, ModCount}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.collection.immutable.HashSet
import scala.collection.mutable.ArrayBuffer

/**
 * @author ilyas
 */

object ScProjectionType {
  def apply(projected: ScType, element: PsiNamedElement,
            superReference: Boolean /* todo: find a way to remove it*/): ScType = {
    val res = new ScProjectionType(projected, element, superReference)
    projected match {
      case c: ScCompoundType =>
        res.isAliasType match {
          case Some(AliasType(td: ScTypeAliasDefinition, _, upper)) if td.typeParameters.isEmpty => upper.getOrElse(res)
          case _ => res
        }
      case _ => res
    }
  }

  def unapply(proj: ScProjectionType): Option[(ScType, PsiNamedElement, Boolean)] = {
    Some(proj.projected, proj.element, proj.superReference)
  }
}

/**
 * This type means type projection:
 * SomeType#member
 * member can be class or type alias
 */
class ScProjectionType private (val projected: ScType, val element: PsiNamedElement,
                                val superReference: Boolean /* todo: find a way to remove it*/) extends ScalaType with ValueType {
  def copy(superReference: Boolean): ScProjectionType = new ScProjectionType(projected, element, superReference)

  override protected def isAliasTypeInner: Option[AliasType] = {
    if (actualElement.isInstanceOf[ScTypeAlias]) {
      actualElement match {
        case ta: ScTypeAlias if ta.typeParameters.isEmpty =>
          val subst: ScSubstitutor = actualSubst
          Some(AliasType(ta, ta.lowerBound.map(subst.subst), ta.upperBound.map(subst.subst)))
        case ta: ScTypeAlias => //higher kind case
          ta match {
            case ta: ScTypeAliasDefinition => //hack for simple cases, it doesn't cover more complicated examples
              ta.aliasedType match {
                case Success(tp, _) =>
                  actualSubst.subst(tp) match {
                    case ScParameterizedType(des, typeArgs) =>
                      val taArgs = ta.typeParameters
                      if (taArgs.length == typeArgs.length && taArgs.zip(typeArgs).forall {
                        case (tParam: ScTypeParam, ScTypeParameterType(_, _, _, _, param)) if tParam == param => true
                        case _ => false
                      }) return Some(AliasType(ta, Success(des, Some(element)), Success(des, Some(element))))
                    case _ =>
                  }
                case _ =>
              }
            case _ =>
          }
          val args: ArrayBuffer[ScExistentialArgument] = new ArrayBuffer[ScExistentialArgument]()
          val genericSubst = ScalaPsiUtil.
            typesCallSubstitutor(ta.typeParameters.map(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp))),
            ta.typeParameters.map(tp => {
              val name = tp.name + "$$"
              args += new ScExistentialArgument(name, Nil, types.Nothing, types.Any)
              ScTypeVariable(name)
            }))
          val s = actualSubst.followed(genericSubst)
          Some(AliasType(ta, ta.lowerBound.map(scType => ScExistentialType(s.subst(scType), args.toList)),
            ta.upperBound.map(scType => ScExistentialType(s.subst(scType), args.toList))))
        case _ => None
      }
    } else None
  }

  private var hash: Int = -1

  override def hashCode: Int = {
    if (hash == -1) {
      hash = projected.hashCode() + element.hashCode() * 31 + (if (superReference) 239 else 0)
    }
    hash
  }

  override def removeAbstracts = ScProjectionType(projected.removeAbstracts, element, superReference)

  override def recursiveUpdate(update: ScType => (Boolean, ScType), visited: HashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    update(this) match {
      case (true, res) => res
      case _ =>
        ScProjectionType(projected.recursiveUpdate(update, visited + this), element, superReference)
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                                    variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        ScProjectionType(projected.recursiveVarianceUpdateModifiable(newData, update, 0), element, superReference)
    }
  }

  @CachedMappedWithRecursionGuard(element, None, ModCount.getBlockModificationCount)
  private def actualImpl(projected: ScType, superReference: Boolean): Option[(PsiNamedElement, ScSubstitutor)] = {
    val emptySubst = new ScSubstitutor(Map.empty, Map.empty, Some(projected))
    val resolvePlace = {
      def fromClazz(clazz: ScTypeDefinition): PsiElement = {
        clazz.extendsBlock.templateBody.flatMap(_.asInstanceOf[ScTemplateBodyImpl].getLastChildStub.toOption).
          getOrElse(clazz.extendsBlock)
      }
      ScType.extractClass(projected, Some(element.getProject)) match {
        case Some(clazz: ScTypeDefinition) => fromClazz(clazz)
        case _ => projected match {
          case ScThisType(clazz: ScTypeDefinition) => fromClazz(clazz)
          case _ => element
        }
      }
    }

    def resolveProcessor(kinds: Set[ResolveTargets.Value], name: String): ResolveProcessor = {
      new ResolveProcessor(kinds, resolvePlace, name) {
        override protected def addResults(results: Seq[ScalaResolveResult]): Boolean = {
          candidatesSet ++= results
          true
        }
      }
    }

    def processType(name: String): Option[(PsiNamedElement, ScSubstitutor)] = {
      import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
      val proc = resolveProcessor(ValueSet(CLASS), name)
      proc.processType(projected, resolvePlace, ResolveState.initial)
      val candidates = proc.candidates
      if (candidates.length == 1 && candidates(0).element.isInstanceOf[PsiNamedElement]) {
        val defaultSubstitutor = emptySubst followed candidates(0).substitutor
        if (superReference) {
          ScalaPsiUtil.superTypeMembersAndSubstitutors(candidates(0).element).find {
            _.info == element
          } match {
            case Some(node) =>
              Some(element, defaultSubstitutor followed node.substitutor)
            case _ => Some(element, defaultSubstitutor)
          }
        } else Some(candidates(0).element, defaultSubstitutor)
      } else None
    }
    element match {
      case a: ScTypeAlias => processType(a.name)
      case d: ScTypedDefinition if d.isStable =>
        val name = d.name
        import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._

        val proc = resolveProcessor(ValueSet(VAL, OBJECT), name)
        proc.processType(projected, resolvePlace, ResolveState.initial)
        val candidates = proc.candidates
        if (candidates.length == 1 && candidates(0).element.isInstanceOf[PsiNamedElement]) {
          //todo: superMemberSubstitutor? However I don't know working example for this case
          Some(candidates(0).element, emptySubst followed candidates(0).substitutor)
        } else None
      case d: ScTypeDefinition => processType(d.name)
      case d: PsiClass => processType(d.getName)
      case _ => None
    }
  }

  private def actual: (PsiNamedElement, ScSubstitutor) = {
    actualImpl(projected, superReference).getOrElse(element, ScSubstitutor.empty)
  }

  def actualElement: PsiNamedElement = actual._1
  def actualSubst: ScSubstitutor = actual._2

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: api.TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
    def isSingletonOk(typed: ScTypedDefinition): Boolean = {
      typed.nameContext match {
        case v: ScValue => true
        case p: ScClassParameter if !p.isVar => true
        case _ => false
      }
    }

    actualElement match {
      case a: ScTypedDefinition if isSingletonOk(a) =>
        val subst = actualSubst
        val tp = subst.subst(a.getType(TypingContext.empty).getOrAny)
        if (ScType.isSingletonType(tp)) {
          val resInner = tp.equiv(r, uSubst, falseUndef)
          if (resInner._1) return resInner
        }
      case _ =>
    }
    isAliasType match {
      case Some(AliasType(ta: ScTypeAliasDefinition, lower, _)) =>
        return (lower match {
          case Success(tp, _) => tp
          case _ => return (false, uSubst)
        }).equiv(r, uSubst, falseUndef)
      case _ =>
    }
    r match {
      case t: StdType =>
        element match {
          case synth: ScSyntheticClass => synth.t.equiv(t, uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      case param@ScParameterizedType(proj2@ScProjectionType(p1, element1, _), typeArgs) =>
        r.isAliasType match {
          case Some(AliasType(ta: ScTypeAliasDefinition, lower, _)) =>
            this.equiv(lower match {
              case Success(tp, _) => tp
              case _ => return (false, uSubst)
            }, uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      case proj2@ScProjectionType(p1, element1, _) =>
        proj2.actualElement match {
          case a: ScTypedDefinition if isSingletonOk(a) =>
            val subst = actualSubst
            val tp = subst.subst(a.getType(TypingContext.empty).getOrAny)
            if (ScType.isSingletonType(tp)) {
              val resInner = tp.equiv(this, uSubst, falseUndef)
              if (resInner._1) return resInner
            }
          case _ =>
        }
        r.isAliasType match {
          case Some(AliasType(ta: ScTypeAliasDefinition, lower, _)) =>
            this.equiv(lower match {
              case Success(tp, _) => tp
              case _ => return (false, uSubst)
            }, uSubst, falseUndef)
          case _ =>
        }
        if (actualElement != proj2.actualElement) {
          actualElement match {
            case o: ScObject =>
            case t: ScTypedDefinition if t.isStable =>
              val s: ScSubstitutor = new ScSubstitutor(Map.empty, Map.empty, Some(projected)) followed actualSubst
              t.getType(TypingContext.empty) match {
                case Success(tp, _) if ScType.isSingletonType(tp) =>
                  return s.subst(tp).equiv(r, uSubst, falseUndef)
                case _ =>
              }
            case _ =>
          }
          proj2.actualElement match {
            case o: ScObject =>
            case t: ScTypedDefinition =>
              val s: ScSubstitutor =
                new ScSubstitutor(Map.empty, Map.empty, Some(p1)) followed proj2.actualSubst
              t.getType(TypingContext.empty) match {
                case Success(tp, _) if ScType.isSingletonType(tp) =>
                  return s.subst(tp).equiv(this, uSubst, falseUndef)
                case _ =>
              }
            case _ =>
          }
          return (false, uSubst)
        }
        projected.equiv(p1, uSubst, falseUndef)
      case ScThisType(clazz) =>
        element match {
          case o: ScObject => (false, uSubst)
          case t: ScTypedDefinition if t.isStable =>
            t.getType(TypingContext.empty) match {
              case Success(singl, _) if ScType.isSingletonType(singl) =>
                val newSubst = actualSubst.followed(new ScSubstitutor(Map.empty, Map.empty, Some(projected)))
                r.equiv(newSubst.subst(singl), uSubst, falseUndef)
              case _ => (false, uSubst)
            }
          case _ => (false, uSubst)
        }
      case _ => (false, uSubst)
    }
  }

  override def isFinalType = actualElement match {
    case cl: PsiClass if cl.isEffectivelyFinal => true
    case alias: ScTypeAliasDefinition => alias.aliasedType.exists(_.isFinalType)
    case _ => false
  }

  override def visitType(visitor: TypeVisitor) = visitor match {
    case scalaVisitor: ScalaTypeVisitor => scalaVisitor.visitProjectionType(this)
    case _ =>
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[ScProjectionType]

  override def equals(other: Any): Boolean = other match {
    case that: ScProjectionType =>
      (that canEqual this) &&
        projected == that.projected &&
        element == that.element &&
        superReference == that.superReference
    case _ => false
  }

  override def typeDepth: Int = projected.typeDepth
}

/**
 * This type means type, which depends on place, where you want to get expression type.
 * For example
 *
 * class A       {
 *   def foo: this.type = this
 * }
 *
 * class B extneds A       {
 *   val z = foo // <- type in this place is B.this.type, not A.this.type
 * }
 *
 * So when expression is typed, we should replace all such types be return value.
 */
case class ScThisType(clazz: ScTemplateDefinition) extends ScalaType with ValueType {
  clazz.getClass //throw NPE if clazz is null...

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: api.TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
    (this, r) match {
      case (ScThisType(clazz1), ScThisType(clazz2)) =>
        (ScEquivalenceUtil.areClassesEquivalent(clazz1, clazz2), uSubst)
      case (ScThisType(obj1: ScObject), ScDesignatorType(obj2: ScObject)) =>
        (ScEquivalenceUtil.areClassesEquivalent(obj1, obj2), uSubst)
      case (_, ScDesignatorType(obj: ScObject)) =>
        (false, uSubst)
      case (_, ScDesignatorType(typed: ScTypedDefinition)) if typed.isStable =>
        typed.getType(TypingContext.empty) match {
          case Success(tp, _) if ScType.isSingletonType(tp) =>
            this.equiv(tp, uSubst, falseUndef)
          case _ =>
            (false, uSubst)
        }
      case (_, ScProjectionType(_, o: ScObject, _)) => (false, uSubst)
      case (_, p@ScProjectionType(tp, elem: ScTypedDefinition, _)) if elem.isStable =>
        elem.getType(TypingContext.empty) match {
          case Success(singl, _) if ScType.isSingletonType(singl) =>
            val newSubst = p.actualSubst.followed(new ScSubstitutor(Map.empty, Map.empty, Some(tp)))
            this.equiv(newSubst.subst(singl), uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      case _ => (false, uSubst)
    }
  }

  override def visitType(visitor: TypeVisitor) = visitor match {
    case scalaVisitor: ScalaTypeVisitor => scalaVisitor.visitThisType(this)
    case _ =>
  }
}

/**
 * This type means normal designator type.
 * It can be whether singleton type (v.type) or simple type (java.lang.String).
 * element can be any stable element, class, value or type alias
 */
case class ScDesignatorType(element: PsiNamedElement) extends ScalaType with ValueType {
  override protected def isAliasTypeInner: Option[AliasType] = {
    element match {
      case ta: ScTypeAlias if ta.typeParameters.isEmpty =>
        Some(AliasType(ta, ta.lowerBound, ta.upperBound))
      case ta: ScTypeAlias => //higher kind case
        ta match {
          case ta: ScTypeAliasDefinition => //hack for simple cases, it doesn't cover more complicated examples
            ta.aliasedType match {
              case Success(tp, _) =>
                tp match {
                  case ScParameterizedType(des, typeArgs) =>
                    val taArgs = ta.typeParameters
                    if (taArgs.length == typeArgs.length && taArgs.zip(typeArgs).forall {
                      case (tParam: ScTypeParam, ScTypeParameterType(_, _, _, _, param)) if tParam == param => true
                      case _ => false
                    }) return Some(AliasType(ta, Success(des, Some(element)), Success(des, Some(element))))
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
        val args: ArrayBuffer[ScExistentialArgument] = new ArrayBuffer[ScExistentialArgument]()
        val genericSubst = ScalaPsiUtil.
          typesCallSubstitutor(ta.typeParameters.map(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp))),
          ta.typeParameters.map(tp => {
            val name = tp.name + "$$"
            args += new ScExistentialArgument(name, Nil, types.Nothing, types.Any)
            ScTypeVariable(name)
          }))
        Some(AliasType(ta, ta.lowerBound.map(scType => ScExistentialType(genericSubst.subst(scType), args.toList)),
          ta.upperBound.map(scType => ScExistentialType(genericSubst.subst(scType), args.toList))))
      case _ => None
    }
  }

  def getValType: Option[StdType] = element match {
    case o: ScObject => None
    case clazz: PsiClass => StdType.QualNameToType.get(clazz.qualifiedName)
    case _ => None
  }

  private var isStaticClass = false
  /**
   * You can use this method to check if it's Java class,
   * which is used for getting static context => no implicit conversion
   */
  def isStatic = isStaticClass
  def this(elem: PsiNamedElement, isStaticClass: Boolean) {
    this (elem)
    this.isStaticClass = isStaticClass
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: api.TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
    (this, r) match {
      case (ScDesignatorType(a: ScTypeAliasDefinition), _) =>
        (a.aliasedType match {
          case Success(tp, _) => tp
          case _ => return (false, uSubst)
        }).equiv(r, uSubst, falseUndef)
      case (_, ScDesignatorType(element1)) =>
        if (ScEquivalenceUtil.smartEquivalence(element, element1)) return (true, uSubst)
        if (ScType.isSingletonType(this) && ScType.isSingletonType(r)) {
          element match {
            case o: ScObject =>
            case bind: ScTypedDefinition if bind.isStable =>
              bind.getType(TypingContext.empty) match {
                case Success(tp, _) if ScType.isSingletonType(tp) =>
                  return tp.equiv(r, uSubst, falseUndef)
                case _ =>
              }
            case _ =>
          }
          element1 match {
            case o: ScObject =>
            case bind: ScTypedDefinition if bind.isStable =>
              bind.getType(TypingContext.empty) match {
                case Success(tp, _) if ScType.isSingletonType(tp) =>
                  return tp.equiv(this, uSubst, falseUndef)
                case _ =>
              }
          }
        }
        (false, uSubst)
      case _ => (false, uSubst)
    }
  }

  override def visitType(visitor: TypeVisitor) = visitor match {
    case scalaVisitor: ScalaTypeVisitor => scalaVisitor.visitDesignatorType(this)
    case _ =>
  }

  override def isFinalType = element match {
    case cl: PsiClass if cl.isEffectivelyFinal => true
    case _ => false
  }
}

object ScDesignatorType {
  def fromClassFqn(fqn: String, project: Project, scope: GlobalSearchScope): ScType = {
    ScalaPsiManager.instance(project).getCachedClass(scope, fqn) match {
      case Some(c) => ScType.designator(c)
      case _ => types.Nothing
    }
  }
}
