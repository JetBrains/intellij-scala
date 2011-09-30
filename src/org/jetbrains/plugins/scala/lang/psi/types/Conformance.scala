package org.jetbrains.plugins.scala
package lang
package psi
package types

import caches.CachesUtil
import com.intellij.openapi.progress.ProgressManager
import nonvalue.{ScTypePolymorphicType, NonValueType, ScMethodType}
import psi.impl.toplevel.synthetic.ScSyntheticClass
import api.statements._
import params._
import api.toplevel.typedef.ScTypeDefinition
import impl.toplevel.typedef.TypeDefinitionMembers
import _root_.scala.collection.immutable.HashSet

import com.intellij.psi._
import com.intellij.psi.util.PsiModificationTracker
import collection.Seq
import collection.mutable.HashMap
import lang.resolve.processor.CompoundTypeCheckProcessor
import result.{TypingContext, TypeResult}
import api.base.patterns.ScBindingPattern
import api.base.ScFieldId
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil
import api.toplevel.{ScTypeParametersOwner, ScNamedElement}

object Conformance {
  /**
   * Checks, whether the following assignment is correct:
   * val x: l = (y: r)
   */
  def conforms(l: ScType, r: ScType, checkWeak: Boolean = false): Boolean =
    conformsInner(l, r, HashSet.empty, new ScUndefinedSubstitutor, checkWeak)._1

  def undefinedSubst(l: ScType, r: ScType, checkWeak: Boolean = false): ScUndefinedSubstitutor =
    conformsInner(l, r, HashSet.empty, new ScUndefinedSubstitutor, checkWeak)._2

  //todo: move to TypeUtil object
  case class AliasType(ta: ScTypeAlias, lower: TypeResult[ScType], upper: TypeResult[ScType])
  def isAliasType(tp: ScType): Option[AliasType] = {
    tp match {
      case ScDesignatorType(ta: ScTypeAlias) => Some(AliasType(ta, ta.lowerBound, ta.upperBound))
      case p: ScProjectionType if p.actualElement.isInstanceOf[ScTypeAlias] =>
        val ta: ScTypeAlias = p.actualElement.asInstanceOf[ScTypeAlias]
        val subst: ScSubstitutor = p.actualSubst
        Some(AliasType(ta, ta.lowerBound.map(subst.subst(_)), ta.upperBound.map(subst.subst(_))))
      case ScParameterizedType(ScDesignatorType(ta: ScTypeAlias), args) => {
        val genericSubst = ScalaPsiUtil.
          typesCallSubstitutor(ta.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
        Some(AliasType(ta, ta.lowerBound.map(genericSubst.subst(_)), ta.upperBound.map(genericSubst.subst(_))))
      }
      case ScParameterizedType(p: ScProjectionType, args) if p.actualElement.isInstanceOf[ScTypeAlias] => {
        val ta: ScTypeAlias = p.actualElement.asInstanceOf[ScTypeAlias]
        val subst: ScSubstitutor = p.actualSubst
        val genericSubst = ScalaPsiUtil.
          typesCallSubstitutor(ta.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
        val s = subst.followed(genericSubst)
        Some(AliasType(ta, ta.lowerBound.map(s.subst(_)), ta.upperBound.map(s.subst(_))))
      }
      case _ => None
    }
  }

  private def checkParameterizedType(parametersIterator: Iterator[PsiTypeParameter], args1: scala.Seq[ScType],
                             args2: scala.Seq[ScType], _undefinedSubst: ScUndefinedSubstitutor,
                             visited: Set[PsiClass], checkWeak: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = _undefinedSubst
    val args1Iterator = args1.iterator
    val args2Iterator = args2.iterator
    while (parametersIterator.hasNext && args1Iterator.hasNext && args2Iterator.hasNext) {
      val tp = parametersIterator.next()
      val argsPair = (args1Iterator.next(), args2Iterator.next())
      tp match {
        case scp: ScTypeParam if (scp.isContravariant) => {
          //simplification rule 4 for existential types
          val r1 = argsPair._1 match {
            case ScExistentialArgument(_, List(), lower, upper) => lower
            case _ => argsPair._1
          }
          val y = Conformance.conformsInner(argsPair._2, r1, HashSet.empty, undefinedSubst)
          if (!y._1) return (false, undefinedSubst)
          else undefinedSubst = y._2
        }
        case scp: ScTypeParam if (scp.isCovariant) => {
          //simplification rule 4 for existential types
          val r1 = argsPair._1 match {
            case ScExistentialArgument(_, List(), lower, upper) => upper
            case _ => argsPair._1
          }
          val y = Conformance.conformsInner(r1, argsPair._2, HashSet.empty, undefinedSubst)
          if (!y._1) return (false, undefinedSubst)
          else undefinedSubst = y._2
        }
        //this case filter out such cases like undefined type
        case _ => {
          argsPair match {
            case (u: ScUndefinedType, rt) => {
              undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), rt)
              undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), rt)
            }
            case (lt, u: ScUndefinedType) => {
              undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), lt)
              undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), lt)
            }
            case (ScAbstractType(_, lower, upper), right) => {
              var t = conformsInner(upper, right, visited, undefinedSubst, checkWeak)
              if (!t._1) return (false, undefinedSubst)
              undefinedSubst = t._2
              t = conformsInner(right, lower, visited, undefinedSubst, checkWeak)
              if (!t._1) return (false, undefinedSubst)
              undefinedSubst = t._2
            }
            case (left, ScAbstractType(_, lower, upper)) => {
              var t = conformsInner(upper, left, visited, undefinedSubst, checkWeak)
              if (!t._1) return (false, undefinedSubst)
              undefinedSubst = t._2
              t = conformsInner(left, lower, visited, undefinedSubst, checkWeak)
              if (!t._1) return (false, undefinedSubst)
              undefinedSubst = t._2
            }
            case (_: ScExistentialArgument, _) => {
              val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
              if (!y._1) return (false, undefinedSubst)
              else undefinedSubst = y._2
            }
            case (aliasType, _) if isAliasType(aliasType) != None && isAliasType(aliasType).get.ta.isExistentialTypeAlias => {
              val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
              if (!y._1) return (false, undefinedSubst)
              else undefinedSubst = y._2
            }
            case _ => {
              val t = Equivalence.equivInner(argsPair._1, argsPair._2, undefinedSubst, false)
              if (!t._1) return (false, undefinedSubst)
              undefinedSubst = t._2
            }
          }
        }
      }
    }
    (true, undefinedSubst)
  }



  private class LeftConformanceVisitor(l: ScType, r: ScType, visited: Set[PsiClass],
                               subst: ScUndefinedSubstitutor,
                               checkWeak: Boolean = false) extends ScalaTypeVisitor {
    /*
      Different checks from right type in order of appearence.
      todo: It's seems it's possible to check order and simplify code in many places.
     */
    trait ValDesignatorSimplification extends ScalaTypeVisitor {
      override def visitDesignatorType(d: ScDesignatorType) {
        d.getValType match {
          case Some(v) =>
            result = conformsInner(l, v, visited, subst, checkWeak)
            return
          case _ =>
        }
      }
    }

    trait UndefinedSubstVisitor extends ScalaTypeVisitor {
      override def visitUndefinedType(u: ScUndefinedType) {
        result = (true, undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), l))
      }
    }
    
    trait AbstractVisitor extends ScalaTypeVisitor {
      override def visitAbstractType(a: ScAbstractType) {
        result = conformsInner(l, a.lower, visited, undefinedSubst, checkWeak)
      }
    }
    
    trait ParameterizedAbstractVisitor extends ScalaTypeVisitor {
      override def visitParameterizedType(p: ScParameterizedType) {
        p.designator match {
          case a: ScAbstractType =>
            a.lower match {
              case ScParameterizedType(lower, _) =>
                result = conformsInner(l, lower, visited, undefinedSubst, checkWeak)
                return
              case lower =>
                result = conformsInner(l, lower, visited, undefinedSubst, checkWeak)
            }
          case _ =>
        }
      }
    }

    private def checkEquiv() {
      val isEquiv = Equivalence.equivInner(l, r, undefinedSubst)
      if (isEquiv._1) result = isEquiv
    }

    trait ExistentialSimplification extends ScalaTypeVisitor {
      override def visitExistentialType(e: ScExistentialType) {
        val simplified = e.simplify()
        if (simplified != r) result = conformsInner(l, simplified, visited, undefinedSubst, checkWeak)
      }
    }

    trait SkolemizeVisitor extends ScalaTypeVisitor {
      override def visitSkolemizedType(s: ScSkolemizedType) {
        result = conformsInner(l, s.upper, HashSet.empty, undefinedSubst)
      }
    }

    trait ParameterizedSkolemizeVisitor extends ScalaTypeVisitor {
      override def visitParameterizedType(p: ScParameterizedType) {
        p.designator match {
          case s: ScSkolemizedType =>
            s.upper match {
              case ScParameterizedType(upper, _) =>
                result = conformsInner(l, upper, visited, undefinedSubst, checkWeak)
              case upper =>
                result = conformsInner(l, upper, visited, undefinedSubst, checkWeak)
            }
          case _ =>
        }
      }
    }

    trait OtherNonvalueTypesVisitor extends ScalaTypeVisitor {
      override def visitUndefinedType(u: ScUndefinedType) {
        result = (false, undefinedSubst)
      }

      override def visitMethodType(m: ScMethodType) {
        result = (false, undefinedSubst)
      }

      override def visitAbstractType(a: ScAbstractType) {
        result = (false, undefinedSubst)
      }

      override def visitTypePolymorphicType(t: ScTypePolymorphicType) {
        result = (false, undefinedSubst)
      }
    }
    
    trait NothingNullVisitor extends ScalaTypeVisitor {
      override def visitStdType(x: StdType) {
        if (x eq Nothing) result = (true, undefinedSubst)
        else if (x eq Null) {
          /*
            this case for checking: val x: T = null
            This is good if T class type: T <: AnyRef and !(T <: NotNull)
           */
          if (!conforms(AnyRef, l)) {
            result = (false, undefinedSubst)
            return
          }
          ScType.extractDesignated(l) match {
            case Some((el, _)) => {
              val notNullClass = JavaPsiFacade.getInstance(el.getProject).findClass("scala.NotNull", el.getResolveScope)
              val notNullType = ScDesignatorType(notNullClass)
              result = (!conforms(notNullType, l), undefinedSubst) //todo: think about undefinedSubst
            }
            case _ => result = (true, undefinedSubst)
          }
        }
      }
    }
    
    trait ExistentialArgumentVisitor extends ScalaTypeVisitor {
      override def visitExistentialArgument(e: ScExistentialArgument) {
        if (e.args.isEmpty) {
          result = conformsInner(l, e.upperBound, HashSet.empty, undefinedSubst) //todo: that is strange, it's also upper (!)
        }
      }
    }

    trait TypeParameterTypeVisitor extends ScalaTypeVisitor {
      override def visitTypeParameterType(tpt: ScTypeParameterType) {
        result = conformsInner(l, tpt.upper.v, HashSet.empty, undefinedSubst)
      }
    }
    
    trait TupleVisitor extends ScalaTypeVisitor {
      override def visitTupleType(t: ScTupleType) {
        t.resolveTupleTrait match {
          case Some(tp) => result = conformsInner(l, tp, visited, subst, checkWeak)
          case _ => result = (false, undefinedSubst)
        }
      }
    }
    
    trait FunctionVisitor extends ScalaTypeVisitor {
      override def visitFunctionType(f: ScFunctionType) {
        f.resolveFunctionTrait match {
          case Some(tp) => result = conformsInner(l, tp, visited, subst, checkWeak)
          case _ => result = (false, undefinedSubst)
        }
      }
    }
    
    trait ThisVisitor extends ScalaTypeVisitor {
      override def visitThisType(t: ScThisType) {
        val clazz = t.clazz
        val res = clazz.getTypeWithProjections(TypingContext.empty)
        if (res.isEmpty) result = (false, undefinedSubst)
        else result = conformsInner(l, res.get, visited, subst, checkWeak)
      }
    }

    trait DesignatorVisitor extends ScalaTypeVisitor {
      override def visitDesignatorType(d: ScDesignatorType) {
        d.element match {
          case v: ScBindingPattern => {
            val res = v.getType(TypingContext.empty)
            if (res.isEmpty) result = (false, undefinedSubst)
            else result = conformsInner(l, res.get, visited, undefinedSubst)
          }
          case v: ScParameter => {
            val res = v.getType(TypingContext.empty)
            if (res.isEmpty) result = (false, undefinedSubst)
            else result = conformsInner(l, res.get, visited, undefinedSubst)
          }
          case v: ScFieldId => {
            val res = v.getType(TypingContext.empty)
            if (res.isEmpty) result = (false, undefinedSubst)
            else result = conformsInner(l, res.get, visited, undefinedSubst)
          }
          case _ =>
        }
      }
    }
    
    trait ParameterizedAliasVisitor extends ScalaTypeVisitor {
      override def visitParameterizedType(p: ScParameterizedType) {
        p.designator match {
          case proj: ScProjectionType if proj.actualElement.isInstanceOf[ScTypeAlias] =>
            val args = p.typeArgs
            val a = proj.actualElement.asInstanceOf[ScTypeAlias]
            val subst = proj.actualSubst
            val upper: ScType = a.upperBound.toOption match {
              case Some(up) => up
              case _ =>
                result = (false, undefinedSubst)
                return
            }
            val uBound = subst.subst(upper)
            val genericSubst = ScalaPsiUtil.
              typesCallSubstitutor(a.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
            val s = subst.followed(genericSubst)
            result = conformsInner(l, s.subst(uBound), visited, undefinedSubst)
          case des: ScDesignatorType =>
            val des = p.designator.asInstanceOf[ScDesignatorType]
            if (des.element.isInstanceOf[ScTypeAlias]) {
              val a = des.element.asInstanceOf[ScTypeAlias]
              val args = p.typeArgs
              val uBound = a.upperBound.toOption match {
                case Some(tp) => tp
                case _ =>
                  result = (false, undefinedSubst)
                  return
              }
              val genericSubst = ScalaPsiUtil.
                typesCallSubstitutor(a.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
              result = conformsInner(l, genericSubst.subst(uBound), visited, undefinedSubst)
            }
          case _ =>
        }
      }
    }

    trait AliasDesignatorVisitor extends ScalaTypeVisitor {
      override def visitDesignatorType(des: ScDesignatorType) {
        des.element match {
          case a: ScTypeAlias =>
            val upper: ScType = a.upperBound.toOption match {
              case Some(up) => up
              case _ =>
                result = (false, undefinedSubst)
                return
            }
            result = conformsInner(l, upper, visited, undefinedSubst)
          case _ =>
        }
      }
    }
    
    trait CompoundTypeVisitor extends ScalaTypeVisitor {
      override def visitCompoundType(c: ScCompoundType) {
        val comps = r.asInstanceOf[ScCompoundType].components
        val iterator = comps.iterator
        while (iterator.hasNext) {
          val comp = iterator.next()
          val t = conformsInner(l, comp, HashSet.empty, undefinedSubst)
          if (t._1) {
            result = (true, t._2)
            return
          }
        }
        result = (false, undefinedSubst)
      }
    }
    
    trait ExistentialVisitor extends ScalaTypeVisitor {
      override def visitExistentialType(ex: ScExistentialType) {
        result = conformsInner(l, ex.skolem, HashSet.empty, undefinedSubst)
      }
    }

    trait ProjectionVisitor extends ScalaTypeVisitor {
      override def visitProjectionType(proj2: ScProjectionType) {
        if (proj2.actualElement.isInstanceOf[ScTypeAlias]) {
          val ta = proj2.actualElement.asInstanceOf[ScTypeAlias]
          val subst = proj2.actualSubst
          val upper: ScType = ta.upperBound.toOption match {
            case Some(up) => up
            case _ =>
              result = (false, undefinedSubst)
              return
          }
          val uBound = subst.subst(upper)
          result = conformsInner(l, uBound, visited, undefinedSubst)
        } else if (l.isInstanceOf[ScProjectionType] &&
          ScEquivalenceUtil.smartEquivalence(l.asInstanceOf[ScProjectionType].actualElement, proj2.actualElement)) {
          val proj1 = l.asInstanceOf[ScProjectionType]
          val projected1 = proj1.projected
          val projected2 = proj2.projected
          result = conformsInner(projected1, projected2, visited, undefinedSubst)
        } else {
          proj2.element match {
            case syntheticClass: ScSyntheticClass =>
              result = conformsInner(l, syntheticClass.t, HashSet.empty, undefinedSubst)
            case _ =>
          }
        }
      }
    }

    private var result: (Boolean, ScUndefinedSubstitutor) = null
    private var undefinedSubst: ScUndefinedSubstitutor = subst

    def getResult = result

    override def visitStdType(x: StdType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor
          with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with SkolemizeVisitor
        with ParameterizedSkolemizeVisitor with OtherNonvalueTypesVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      if (checkWeak && r.isInstanceOf[ValType]) {
        (r, x) match {
          case (Byte, Short | Int | Long | Float | Double) =>
            result = (true, undefinedSubst)
            return
          case (Short, Int | Long | Float | Double) =>
            result = (true, undefinedSubst)
            return
          case (Char, Byte | Short | Int | Long | Float | Double) =>
            result = (true, undefinedSubst)
            return
          case (Int, Long | Float | Double) =>
            result = (true, undefinedSubst)
            return
          case (Long, Float | Double) =>
            result = (true, undefinedSubst)
            return
          case (Float, Double) =>
            result = (true, undefinedSubst)
            return
          case _ =>
        }
      }

      if (x eq Any) {
        result = (true, undefinedSubst)
        return
      }

      rightVisitor = new NothingNullVisitor with ExistentialArgumentVisitor
        with TypeParameterTypeVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      if (x eq Null) {
        result = (r == Nothing, undefinedSubst)
        return
      }

      if (x eq AnyRef) {
        if (r eq  Any) {
          result = (false, undefinedSubst)
          return
        }
        else if (r eq  AnyVal) {
          result = (false, undefinedSubst)
          return
        }
        else if (r.isInstanceOf[ValType]) {
          result = (false, undefinedSubst)
          return
        }
        else if (!r.isInstanceOf[ScExistentialArgument] && !r.isInstanceOf[ScExistentialType]) {
          result = (true, undefinedSubst)
          return
        }
      }

      if (x eq Singleton) {
        result = (false, undefinedSubst)
      }

      if (x eq AnyVal) {
        result = (r.isInstanceOf[ValType], undefinedSubst)
        return
      }
      if (l.isInstanceOf[ValType] && r.isInstanceOf[ValType]) {
        result = (false, undefinedSubst)
        return
      }

      rightVisitor = new TupleVisitor with FunctionVisitor with ThisVisitor with DesignatorVisitor
      with ParameterizedAliasVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new AliasDesignatorVisitor with CompoundTypeVisitor with ExistentialVisitor
        with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return
    }

    override def visitFunctionType(f: ScFunctionType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with SkolemizeVisitor
        with ParameterizedSkolemizeVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor 
        with ExistentialArgumentVisitor with TypeParameterTypeVisitor with TupleVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      f.resolveFunctionTrait match {
        case Some(tp) => result = conformsInner(tp, r, visited, subst, checkWeak)
        case _ => result = (false, undefinedSubst)
      }
    }

    override def visitTupleType(t1: ScTupleType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with SkolemizeVisitor
        with ParameterizedSkolemizeVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor 
        with ExistentialArgumentVisitor with TypeParameterTypeVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      r match {
        case t2: ScTupleType =>
          val comps1 = t1.components
          val comps2 = t2.components
          if (comps1.length != comps2.length) {
            result = (false, undefinedSubst)
            return
          }
          var i = 0
          while (i < comps1.length) {
            val comp1 = comps1(i)
            val comp2 = comps2(i)
            val t = conformsInner(comp1, comp2, HashSet.empty, undefinedSubst)
            if (!t._1) {
              result = (false, undefinedSubst)
              return
            }
            else undefinedSubst = t._2
            i = i + 1
          }
          result = (true, undefinedSubst)
        case _ =>
          t1.resolveTupleTrait match {
            case Some(tp) => 
              result = conformsInner(tp, r, visited, subst, checkWeak)
            case _ => result = (false, undefinedSubst)
          }
      }
    }

    override def visitCompoundType(c: ScCompoundType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with SkolemizeVisitor
        with ParameterizedSkolemizeVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor 
        with ExistentialArgumentVisitor with TypeParameterTypeVisitor with TupleVisitor with FunctionVisitor 
        with ThisVisitor with DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ExistentialSimplification with SkolemizeVisitor
        with ParameterizedSkolemizeVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor
        with ExistentialArgumentVisitor with TypeParameterTypeVisitor with TupleVisitor with FunctionVisitor
        with ThisVisitor with DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ParameterizedAliasVisitor with AliasDesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      /*If T<:Ui for i=1,...,n and for every binding d of a type or value x in R there exists a member binding
      of x in T which subsumes d, then T conforms to the compound type	U1	with	. . .	with	Un	{R }.

      U1	with	. . .	with	Un	{R } === t1
      T                             === t2
      U1	with	. . .	with	Un       === comps1
      Un                            === compn*/
        val comps = c.components
        val decls = c.decls
        val typeMembers = c.typeDecls
        val compoundSubst = c.subst
        def workWith(t: ScNamedElement): Boolean = {
          val processor = new CompoundTypeCheckProcessor(t, undefinedSubst, compoundSubst)
          processor.processType(r, t)
          undefinedSubst = processor.getUndefinedSubstitutor
          processor.getResult
        }
        result = (comps.forall(comp => {
          val t = conformsInner(comp, r, HashSet.empty, undefinedSubst)
          undefinedSubst = t._2
          t._1
        }) && decls.forall(decl => {
          decl match {
            case fun: ScFunction => workWith(fun)
            case v: ScValue => v.declaredElements forall (decl => workWith(decl))
            case v: ScVariable => v.declaredElements forall (decl => workWith(decl))
          }
        }) && typeMembers.forall(typeMember => {
          workWith(typeMember)
        }), undefinedSubst)
    }

    override def visitProjectionType(proj: ScProjectionType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with SkolemizeVisitor
        with ParameterizedSkolemizeVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor 
        with ExistentialArgumentVisitor with TypeParameterTypeVisitor with TupleVisitor with FunctionVisitor 
        with ThisVisitor with DesignatorVisitor with ParameterizedAliasVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ParameterizedAliasVisitor with AliasDesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      if (proj.actualElement.isInstanceOf[ScTypeAlias]) {
        val ta = proj.actualElement.asInstanceOf[ScTypeAlias]
        val subst = proj.actualSubst
        if (!ta.isExistentialTypeAlias) {
          val lower = ta.lowerBound.toOption match {
            case Some(low) => low
            case _ =>
              result = (false, undefinedSubst)
              return
          }
          result = conformsInner(subst.subst(lower), r, visited, undefinedSubst)
          return
        } else {
          val lower = ta.lowerBound.toOption match {
            case Some(low) => low
            case _ =>
              result = (false, undefinedSubst)
              return
          }
          val upper = ta.upperBound.toOption match {
            case Some(up) => up
            case _ =>
              result = (false, undefinedSubst)
              return
          }
          val t = conformsInner(subst.subst(upper), r, visited, undefinedSubst)
          if (!t._1) {
            result = (false, undefinedSubst)
            return
          }
          undefinedSubst = t._2
          result = conformsInner(r, subst.subst(lower), visited, undefinedSubst)
          return
        }
      }

      rightVisitor = new CompoundTypeVisitor with ExistentialVisitor with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return
    }

    override def visitJavaArrayType(a1: JavaArrayType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with SkolemizeVisitor
        with ParameterizedSkolemizeVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor 
        with ExistentialArgumentVisitor with TypeParameterTypeVisitor with TupleVisitor with FunctionVisitor 
        with ThisVisitor with DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ParameterizedAliasVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      r match {
        case a2: JavaArrayType =>
          val arg1 = a1.arg
          val arg2 = a2.arg
          val argsPair = (arg1, arg2)
          argsPair match {
            case (ScAbstractType(_, lower, upper), right) => {
              var t = conformsInner(upper, right, visited, undefinedSubst, checkWeak)
              if (!t._1) {
                result = (false, undefinedSubst)
                return
              }
              undefinedSubst = t._2
              t = conformsInner(right, lower, visited, undefinedSubst, checkWeak)
              if (!t._1) {
                result = (false, undefinedSubst)
                return
              }
              undefinedSubst = t._2
            }
            case (left, ScAbstractType(_, lower, upper)) => {
              var t = conformsInner(upper, left, visited, undefinedSubst, checkWeak)
              if (!t._1) {
                result = (false, undefinedSubst)
                return
              }
              undefinedSubst = t._2
              t = conformsInner(left, lower, visited, undefinedSubst, checkWeak)
              if (!t._1) {
                result = (false, undefinedSubst)
                return
              }
              undefinedSubst = t._2
            }
            case (u: ScUndefinedType, rt) => {
              undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), rt)
              undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), rt)
            }
            case (lt, u: ScUndefinedType) => {
              undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), lt)
              undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), lt)
            }
            case (_: ScExistentialArgument, _) => {
              val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
              if (!y._1) {
                result = (false, undefinedSubst)
                return
              }
              else undefinedSubst = y._2
            }
            case (tp, _) if isAliasType(tp) != None && isAliasType(tp).get.ta.isExistentialTypeAlias => {
              val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
              if (!y._1) {
                result = (false, undefinedSubst)
                return
              }
              else undefinedSubst = y._2
            }
            case _ => {
              val t = Equivalence.equivInner(argsPair._1, argsPair._2, undefinedSubst, false)
              if (!t._1) {
                result = (false, undefinedSubst)
                return
              }
              undefinedSubst = t._2
            }
          }
          result = (true, undefinedSubst)
          return
        case p2: ScParameterizedType =>
          val args = p2.typeArgs
          val des = p2.designator
          if (args.length == 1 && (ScType.extractClass(des) match {
            case Some(q) => q.getQualifiedName == "scala.Array"
            case _ => false
          })) {
            val arg = a1.arg
            val argsPair = (arg, args(0))
            argsPair match {
              case (ScAbstractType(_, lower, upper), right) => {
                var t = conformsInner(upper, right, visited, undefinedSubst, checkWeak)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
                t = conformsInner(right, lower, visited, undefinedSubst, checkWeak)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
              }
              case (left, ScAbstractType(_, lower, upper)) => {
                var t = conformsInner(upper, left, visited, undefinedSubst, checkWeak)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
                t = conformsInner(left, lower, visited, undefinedSubst, checkWeak)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
              }
              case (u: ScUndefinedType, rt) => {
                undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), rt)
                undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), rt)
              }
              case (lt, u: ScUndefinedType) => {
                undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), lt)
                undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), lt)
              }
              case (_: ScExistentialArgument, _) => {
                val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
                if (!y._1) {
                  result = (false, undefinedSubst)
                  return
                }
                else undefinedSubst = y._2
              }
              case (tp, _) if isAliasType(tp) != None && isAliasType(tp).get.ta.isExistentialTypeAlias => {
                val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
                if (!y._1) {
                  result = (false, undefinedSubst)
                  return
                }
                else undefinedSubst = y._2
              }
              case _ => {
                val t = Equivalence.equivInner(argsPair._1, argsPair._2, undefinedSubst, false)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
              }
            }
            result = (true, undefinedSubst)
            return
          }
        case _ =>
      }

      rightVisitor = new AliasDesignatorVisitor with CompoundTypeVisitor with ExistentialVisitor 
        with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return
    }

    override def visitParameterizedType(p: ScParameterizedType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      p.designator match {
        case a: ScAbstractType =>
          a.upper match {
            case ScParameterizedType(upper, _) =>
              result = conformsInner(upper, r, visited, undefinedSubst, checkWeak)
              return
            case upper =>
              result = conformsInner(upper, r, visited, undefinedSubst, checkWeak)
              return
          }
        case _ =>
      }

      rightVisitor = new ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with SkolemizeVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      p.designator match {
        case s: ScSkolemizedType =>
          s.lower match {
            case ScParameterizedType(lower, _) =>
              result = conformsInner(lower, r, visited, undefinedSubst, checkWeak)
              return
            case lower =>
              result = conformsInner(lower, r, visited, undefinedSubst, checkWeak)
              return
          }
        case _ =>
      }

      rightVisitor = new ParameterizedSkolemizeVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor 
        with ExistentialArgumentVisitor with TypeParameterTypeVisitor with TupleVisitor with FunctionVisitor 
        with ThisVisitor with DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      p.designator match {
        case proj: ScProjectionType if proj.actualElement.isInstanceOf[ScTypeAlias] =>
          val args = p.typeArgs
          val a = proj.actualElement.asInstanceOf[ScTypeAlias]
          val subst = proj.actualSubst
          val lower: ScType = a.lowerBound.toOption match {
            case Some(low) => low
            case _ =>
              result = (false, undefinedSubst)
              return
          }
          val lBound = subst.subst(lower)
          val genericSubst = ScalaPsiUtil.
            typesCallSubstitutor(a.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
          val s = subst.followed(genericSubst)
          result = conformsInner(s.subst(lBound), r, visited, undefinedSubst)
          return
        case _ =>
      }

      rightVisitor = new ParameterizedAliasVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      p.designator match {
        case des: ScDesignatorType if des.element.isInstanceOf[ScTypeAlias] =>
          val a = des.element.asInstanceOf[ScTypeAlias]
          if (!a.isExistentialTypeAlias) {
            val lBound = a.lowerBound.toOption match {
              case Some(tp) => tp
              case _ =>
                result = (false, undefinedSubst)
                return
            }
            val genericSubst = ScalaPsiUtil.
              typesCallSubstitutor(a.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), p.typeArgs)
            result = conformsInner(genericSubst.subst(lBound), r, visited, undefinedSubst)
            return
          }
          else {
            val lBound = a.lowerBound.toOption match {
              case Some(tp) => tp
              case _ =>
                result = (false, undefinedSubst)
                return
            }
            val uBound = a.upperBound.toOption match {
              case Some(tp) => tp
              case _ =>
                result = (false, undefinedSubst)
                return
            }
            val genericSubst = ScalaPsiUtil.
              typesCallSubstitutor(a.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), p.typeArgs)
            val t = conformsInner(genericSubst.subst(uBound), r, visited, undefinedSubst)
            if (!t._1) {
              result = (false, undefinedSubst)
              return
            }
            undefinedSubst = t._2
            result = conformsInner(r, genericSubst.subst(lBound), visited, undefinedSubst)
            return
          }
        case _ =>
      }

      r match {
        case _: JavaArrayType =>
          val args = p.typeArgs
          val des = p.designator
          if (args.length == 1 && (ScType.extractClass(des) match {
            case Some(q) => q.getQualifiedName == "scala.Array"
            case _ => false
          })) {
            val arg = r.asInstanceOf[JavaArrayType].arg
            val argsPair = (arg, args(0))
            argsPair match {
              case (ScAbstractType(_, lower, upper), right) => {
                var t = conformsInner(upper, right, visited, undefinedSubst, checkWeak)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
                t = conformsInner(right, lower, visited, undefinedSubst, checkWeak)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
              }
              case (left, ScAbstractType(_, lower, upper)) => {
                var t = conformsInner(upper, left, visited, undefinedSubst, checkWeak)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
                t = conformsInner(left, lower, visited, undefinedSubst, checkWeak)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
              }
              case (u: ScUndefinedType, rt) => {
                undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), rt)
                undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), rt)
              }
              case (lt, u: ScUndefinedType) => {
                undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), lt)
                undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), lt)
              }
              case (_: ScExistentialArgument, _) => {
                val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
                if (!y._1) {
                  result = (false, undefinedSubst)
                  return
                }
                else undefinedSubst = y._2
              }
              case (tp, _) if isAliasType(tp) != None && isAliasType(tp).get.ta.isExistentialTypeAlias => {
                val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
                if (!y._1) {
                  result = (false, undefinedSubst)
                  return
                }
                else undefinedSubst = y._2
              }
              case _ => {
                val t = Equivalence.equivInner(argsPair._1, argsPair._2, undefinedSubst, false)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
              }
            }
            result = (true, undefinedSubst)
            return
          }
        case _ =>
      }

      if (r.isInstanceOf[ScParameterizedType]) {
        val p2 = r.asInstanceOf[ScParameterizedType]
        val des1 = p.designator
        val des2 = p2.designator
        val args1 = p.typeArgs
        val args2 = p2.typeArgs
        if (des1.isInstanceOf[ScTypeParameterType] && des2.isInstanceOf[ScTypeParameterType]) {
          val owner1 = des1.asInstanceOf[ScTypeParameterType]
          if (des1 equiv des2) {
            if (args1.length != args2.length) {
              result = (false, undefinedSubst)
              return
            }
            result = checkParameterizedType(owner1.args.map(_.param).iterator, args1, args2,
              undefinedSubst, visited, checkWeak)
            return
          } else {
            result = (false, undefinedSubst)
            return
          }
        } else if (des1.isInstanceOf[ScUndefinedType]) {
          val owner1 = des1.asInstanceOf[ScUndefinedType]
          val parameterType = owner1.tpt
          val anotherType = ScParameterizedType(des2, parameterType.args)
          undefinedSubst = undefinedSubst.addLower((owner1.tpt.name, owner1.tpt.getId), anotherType)
          if (args1.length != args2.length) {
            result = (false, undefinedSubst)
            return
          }
          result = checkParameterizedType(owner1.tpt.args.map(_.param).iterator, args1, args2,
            undefinedSubst, visited, checkWeak)
          return
        } else if (des2.isInstanceOf[ScUndefinedType]) {
          val owner2 = des2.asInstanceOf[ScUndefinedType]
          val parameterType = owner2.tpt
          val anotherType = ScParameterizedType(des1, parameterType.args)
          undefinedSubst = undefinedSubst.addUpper((owner2.tpt.name, owner2.tpt.getId), anotherType)
          if (args1.length != args2.length) {
            result = (false, undefinedSubst)
            return
          }
          result = checkParameterizedType(owner2.tpt.args.map(_.param).iterator, args1, args2,
            undefinedSubst, visited, checkWeak)
          return
        } else if (des1.equiv(des2)) {
          if (args1.length != args2.length) {
            result = (false, undefinedSubst)
            return
          }
          ScType.extractClass(des1) match {
            case Some(ownerClazz) => {
              val parametersIterator = ownerClazz match {
                case td: ScTypeDefinition => td.typeParameters.iterator
                case _ => ownerClazz.getTypeParameters.iterator
              }
              result = checkParameterizedType(parametersIterator, args1, args2,
                undefinedSubst, visited, checkWeak)
              return
            }
            case _ => {
              result = (false, undefinedSubst)
              return
            }
          }
        } else {
          if (des1.isInstanceOf[ScProjectionType]) {
            val proj1 = des1.asInstanceOf[ScProjectionType]
            if (des2.isInstanceOf[ScProjectionType]) {
              val proj2 = des2.asInstanceOf[ScProjectionType]
              if (ScEquivalenceUtil.smartEquivalence(proj1.actualElement, proj2.actualElement)) {
                val t = conformsInner(proj1, proj2, visited, undefinedSubst)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
                if (args1.length != args2.length) {
                  result = (false, undefinedSubst)
                  return
                }
                val parametersIterator = proj1.actualElement match {
                  case td: ScTypeParametersOwner => td.typeParameters.iterator
                  case td: PsiTypeParameterListOwner => td.getTypeParameters.iterator
                  case _ => {
                    result = (false, undefinedSubst)
                    return
                  }
                }
                result = checkParameterizedType(parametersIterator, args1, args2,
                  undefinedSubst, visited, checkWeak)
                return
              }
            }
          }
        }
      }

      rightVisitor = new AliasDesignatorVisitor with CompoundTypeVisitor with ExistentialVisitor
        with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return
    }

    override def visitExistentialType(e: ScExistentialType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor
          with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      val simplified = e.simplify()
      if (simplified != l) {
        result = conformsInner(simplified, r, visited, undefinedSubst, checkWeak)
        return
      }

      rightVisitor = new ExistentialSimplification with SkolemizeVisitor
        with ParameterizedSkolemizeVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor 
        with ExistentialArgumentVisitor with TypeParameterTypeVisitor with TupleVisitor with FunctionVisitor 
        with ThisVisitor with DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ParameterizedAliasVisitor with AliasDesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      val q = e.quantified
      result = conformsInner(q, r, HashSet.empty, undefinedSubst)
    }

    override def visitThisType(t: ScThisType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with SkolemizeVisitor
        with ParameterizedSkolemizeVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor 
        with ExistentialArgumentVisitor with TypeParameterTypeVisitor with TupleVisitor with FunctionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      val clazz = t.clazz
      val res = clazz.getTypeWithProjections(TypingContext.empty)
      if (res.isEmpty) result = (false, undefinedSubst)
      else result = conformsInner(res.get, r, visited, subst, checkWeak)
    }

    override def visitDesignatorType(des: ScDesignatorType) {
      des.getValType match {
        case Some(v) =>
          result = conformsInner(v, r, visited, subst, checkWeak)
          return
        case _ =>
      }

      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with SkolemizeVisitor
        with ParameterizedSkolemizeVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor 
        with ExistentialArgumentVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      des.element match {
        case a: ScTypeAlias if a.isExistentialTypeAlias =>
          val upper: ScType = a.upperBound.toOption match {
            case Some(u) => u
            case _ =>
              result = (false, undefinedSubst)
              return
          }
          val t = conformsInner(upper, r, visited, undefinedSubst)
          if (!t._1) {
            result = (false, undefinedSubst)
            return
          }
          undefinedSubst = t._2
          val lower: ScType = a.lowerBound.toOption match {
            case Some(low) => low
            case _ =>
              result = (false, undefinedSubst)
              return
          }
          result = conformsInner(r, lower, visited, undefinedSubst)
          return
        case _ =>
      }

      rightVisitor = new TypeParameterTypeVisitor with TupleVisitor with FunctionVisitor 
        with ThisVisitor with DesignatorVisitor with ParameterizedAliasVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      if (des.element.isInstanceOf[ScTypeAlias]) {
        val a = des.element.asInstanceOf[ScTypeAlias]
        if (!a.isExistentialTypeAlias) {
          val lower: ScType = a.lowerBound.toOption match {
            case Some(low) => low
            case _ =>
              result = (false, undefinedSubst)
              return
          }
          result = conformsInner(lower, r, visited, undefinedSubst)
        }
        else {
          val upper: ScType = a.upperBound.toOption match {
            case Some(low) => low
            case _ =>
              result = (false, undefinedSubst)
              return
          }
          val t = conformsInner(upper, r, visited, undefinedSubst)
          if (!t._1) {
            result = (false, undefinedSubst)
            return
          }
          undefinedSubst = t._2
          val lower: ScType = a.lowerBound.toOption match {
            case Some(low) => low
            case _ =>
              result = (false, undefinedSubst)
              return
          }
          result = conformsInner(r, lower, visited, undefinedSubst)
        }
        return
      }

      rightVisitor = new AliasDesignatorVisitor with CompoundTypeVisitor with ExistentialVisitor 
        with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return
    }

    override def visitTypeParameterType(tpt1: ScTypeParameterType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with SkolemizeVisitor
        with ParameterizedSkolemizeVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      r match {
        case tpt2: ScTypeParameterType =>
          val res = conformsInner(tpt1.lower.v, r, HashSet.empty, undefinedSubst)
          if (res._1) {
            result = res
            return
          }
          result = conformsInner(l, tpt2.upper.v, HashSet.empty, undefinedSubst)
          return
        case _ =>
      }

      rightVisitor = new ExistentialArgumentVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      result = conformsInner(tpt1.lower.v, r, HashSet.empty, undefinedSubst)
    }

    override def visitExistentialArgument(e: ScExistentialArgument) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with SkolemizeVisitor
        with ParameterizedSkolemizeVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      if (e.args.isEmpty) {
        result =  conformsInner(e.upperBound, r, HashSet.empty, undefinedSubst)
        return
      }

      rightVisitor = new ExistentialArgumentVisitor with TypeParameterTypeVisitor with TupleVisitor 
        with FunctionVisitor with ThisVisitor with DesignatorVisitor with ParameterizedAliasVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new AliasDesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      if (r.isInstanceOf[ScExistentialArgument]) {
        val ex1 = l.asInstanceOf[ScExistentialArgument]
        val ex2 = r.asInstanceOf[ScExistentialArgument]
        val params1 = ex1.args
        val params2 = ex2.args
        if (params1.isEmpty && params2.isEmpty) {
          val upper1 = ex1.upperBound
          val upper2 = ex2.upperBound
          val lower1 = ex1.lowerBound
          val lower2 = ex2.lowerBound
          var t = conformsInner(upper1, upper2, HashSet.empty, undefinedSubst)
          if (!t._1) {
            result = (false, undefinedSubst)
            return
          }
          undefinedSubst = t._2
          t = conformsInner(lower2, lower1, HashSet.empty, undefinedSubst)
          if (!t._1) {
            result = (false, undefinedSubst)
            return
          }
          undefinedSubst = t._2
          result = (true, undefinedSubst)
        }
      }

      rightVisitor = new AliasDesignatorVisitor with CompoundTypeVisitor with ExistentialVisitor 
        with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return
    }

    override def visitSkolemizedType(s: ScSkolemizedType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor
          with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification {}
      r.visitType(rightVisitor)
      if (result != null) return

      result = conformsInner(s.lower, r, HashSet.empty, undefinedSubst)
    }

    override def visitTypeVariable(t: ScTypeVariable) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with SkolemizeVisitor
        with ParameterizedSkolemizeVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor 
        with ExistentialArgumentVisitor with TypeParameterTypeVisitor with TupleVisitor with FunctionVisitor 
        with ThisVisitor with DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ParameterizedAliasVisitor with AliasDesignatorVisitor with CompoundTypeVisitor
        with ExistentialVisitor with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return
    }

    override def visitUndefinedType(u: ScUndefinedType) {
      val rightVisitor = new ValDesignatorSimplification {
        override def visitUndefinedType(u2: ScUndefinedType) {
          if (u2.level > u.level) {
            result = (true, undefinedSubst.addUpper((u2.tpt.name, u2.tpt.getId), u))
          } else if (u.level > u2.level) {
            result = (true, undefinedSubst.addUpper((u2.tpt.name, u2.tpt.getId), u))
          } else {
            result = (true, undefinedSubst)
          }
        }
      }
      r.visitType(rightVisitor)
      if (result == null)
        result = (true, undefinedSubst.addLower((u.tpt.name, u.tpt.getId), r))
    }

    override def visitMethodType(m1: ScMethodType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor
          with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification {}
      r.visitType(rightVisitor)
      if (result != null) return

      r match {
        case m2: ScMethodType =>
          val params1 = m1.params
          val params2 = m2.params
          val returnType1 = m1.returnType
          val returnType2 = m2.returnType
          if (params1.length != params2.length) {
            result = (false, undefinedSubst)
            return
          }
          var t = conformsInner(returnType1, returnType2, HashSet.empty, undefinedSubst)
          if (!t._1) {
            result = (false, undefinedSubst)
            return
          }
          undefinedSubst = t._2
          var i = 0
          while (i < params1.length) {
            if (params1(i).isRepeated != params2(i).isRepeated) {
              result = (false, undefinedSubst)
              return
            }
            t = Equivalence.equivInner(params1(i).paramType, params2(i).paramType, undefinedSubst, false)
            if (!t._1) {
              result = (false, undefinedSubst)
              return
            }
            undefinedSubst = t._2
            i = i + 1
          }
          result = (true, undefinedSubst)
        case _ =>
          result = (false, undefinedSubst)
      }
    }

    override def visitAbstractType(a: ScAbstractType) {
      val rightVisitor = new ValDesignatorSimplification with UndefinedSubstVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      result = conformsInner(a.upper, r, visited, undefinedSubst, checkWeak)
    }

    override def visitTypePolymorphicType(t1: ScTypePolymorphicType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor
          with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification {}
      r.visitType(rightVisitor)
      if (result != null) return

      r match {
        case t2: ScTypePolymorphicType =>
          val typeParameters1 = t1.typeParameters
          val typeParameters2 = t2.typeParameters
          val internalType1 = t1.internalType
          val internalType2 = t2.internalType
          if (typeParameters1.length != typeParameters2.length) {
            result = (false, undefinedSubst)
            return
          }
          var i = 0
          while (i < typeParameters1.length) {
            var t = conformsInner(typeParameters1(i).lowerType, typeParameters2(i).lowerType, HashSet.empty, undefinedSubst)
            if (!t._1) {
              result = (false, undefinedSubst)
              return
            }
            undefinedSubst = t._2
            t = conformsInner(typeParameters2(i).upperType, typeParameters1(i).lowerType, HashSet.empty, undefinedSubst)
            if (!t._1) {
              result = (false, undefinedSubst)
              return
            }
            undefinedSubst = t._2
            i = i + 1
          }
          val subst = new ScSubstitutor(new collection.immutable.HashMap[(String, String), ScType] ++ typeParameters1.zip(typeParameters2).map({
            tuple => ((tuple._1.name, ScalaPsiUtil.getPsiElementId(tuple._1.ptp)),
              new ScTypeParameterType(tuple._2.name,
                tuple._2.ptp match {
                  case p: ScTypeParam => p.typeParameters.toList.map{new ScTypeParameterType(_, ScSubstitutor.empty)}
                  case _ => Nil
                }, tuple._2.lowerType, tuple._2.upperType, tuple._2.ptp))
          }), Map.empty, None)
          val t = conformsInner(subst.subst(internalType1), internalType2, HashSet.empty, undefinedSubst)
          if (!t._1) {
            result = (false, undefinedSubst)
            return
          }
          undefinedSubst = t._2
          result = (true, undefinedSubst)
        case _ =>
          result = (false, undefinedSubst)
      }
    }
  }

  def conformsInner(l: ScType, r: ScType, visited: Set[PsiClass], uSubst: ScUndefinedSubstitutor,
                            checkWeak: Boolean = false): (Boolean, ScUndefinedSubstitutor) = {
    ProgressManager.checkCanceled()

    val leftVisitor = new LeftConformanceVisitor(l, r, visited, uSubst, checkWeak)
    l.visitType(leftVisitor)
    if (leftVisitor.getResult != null) return leftVisitor.getResult

    //tail, based on class inheritance
    ScType.extractClassType(r) match {
      case Some((clazz: PsiClass, _)) if visited.contains(clazz) => (false, uSubst)
      case Some((rClass: PsiClass, subst: ScSubstitutor)) => {
        ScType.extractClass(l) match {
          case Some(lClass) => {
            if (rClass.getQualifiedName == "java.lang.Object" ) {
              return conformsInner(l, AnyRef, visited, uSubst, checkWeak)
            } else if (lClass.getQualifiedName == "java.lang.Object") {
              return conformsInner(AnyRef, r, visited, uSubst, checkWeak)
            }
            val inh = smartIsInheritor(rClass, subst, lClass)
            if (!inh._1) return (false, uSubst)
            val tp = inh._2
            //Special case for higher kind types passed to generics.
            if (lClass.hasTypeParameters) {
              l match {
                case p: ScParameterizedType =>
                case f: ScFunctionType =>
                case t: ScTupleType =>
                case _ => return (true, uSubst)
              }
            }
            val t = conformsInner(l, tp, visited + rClass, uSubst, true)
            if (t._1) (true, t._2)
            else (false, uSubst)
          }
          case _ => (false, uSubst)
        }
      }
      case _ => {
        val bases: Seq[ScType] = BaseTypes.get(r)
        val iterator = bases.iterator
        while (iterator.hasNext) {
          ProgressManager.checkCanceled()
          val tp = iterator.next()
          val t = conformsInner(l, tp, visited, uSubst, true)
          if (t._1) return (true, t._2)
        }
        (false, uSubst)
      }
    }
  }

  private def smartIsInheritor(leftClass: PsiClass, substitutor: ScSubstitutor, rightClass: PsiClass) : (Boolean, ScType) = {
    if (!ScalaPsiUtil.cachedDeepIsInheritor(leftClass, rightClass)) return (false, null)
    smartIsInheritor(leftClass, substitutor, rightClass, new collection.mutable.HashSet[PsiClass])
  }
  private def smartIsInheritor(leftClass: PsiClass, substitutor: ScSubstitutor, rightClass: PsiClass,
                               visited: collection.mutable.HashSet[PsiClass]): (Boolean, ScType) = {
    ProgressManager.checkCanceled()
    val bases: Seq[Any] = leftClass match {
      case td: ScTypeDefinition => td.superTypes
      case _ => leftClass.getSuperTypes
    }
    val iterator = bases.iterator
    var res: ScType = null
    while (iterator.hasNext) {
      val tp: ScType = iterator.next() match {
        case tp: ScType => substitutor.subst(tp)
        case pct: PsiClassType => substitutor.subst(ScType.create(pct, leftClass.getProject))
      }
      ScType.extractClassType(tp) match {
        case Some((clazz: PsiClass, _)) if visited.contains(clazz) =>
        case Some((clazz: PsiClass, subst)) if ScEquivalenceUtil.areClassesEquivalent(clazz, rightClass) => {
          visited += clazz
          if (res == null) res = tp
          else if (tp.conforms(res)) res = tp
        }
        case Some((clazz: PsiClass, subst)) => {
          visited += clazz
          val recursive = smartIsInheritor(clazz, subst, rightClass, visited)
          if (recursive._1) {
            if (res == null) res = recursive._2
            else if (recursive._2.conforms(res)) res = recursive._2
          }
        }
        case _ =>
      }
    }
    if (res == null) (false, null)
    else (true, res)
  }
}