package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.light.scala.ScExistentialLightTypeParam
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.lang.resolve.processor.{CompoundTypeCheckSignatureProcessor, CompoundTypeCheckTypeAliasProcessor}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil._

import _root_.scala.collection.immutable.HashSet
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.collection.{Seq, immutable, mutable}

trait ScalaConformance extends api.Conformance {
  typeSystem: api.TypeSystem =>

  override protected def conformsComputable(left: ScType, right: ScType, visited: Set[PsiClass], checkWeak: Boolean) =
    new Computable[(Boolean, ScUndefinedSubstitutor)] {
      override def compute(): (Boolean, ScUndefinedSubstitutor) = {
        val substitutor = ScUndefinedSubstitutor()
        val leftVisitor = new LeftConformanceVisitor(left, right, visited, substitutor, checkWeak)
        left.visitType(leftVisitor)
        if (leftVisitor.getResult != null) return leftVisitor.getResult

        //tail, based on class inheritance
        right.extractClassType match {
          case Some((clazz: PsiClass, _)) if visited.contains(clazz) => return (false, substitutor)
          case Some((rClass: PsiClass, subst: ScSubstitutor)) =>
            left.extractClass match {
              case Some(lClass) =>
                if (rClass.qualifiedName == "java.lang.Object") {
                  return conformsInner(left, AnyRef, visited, substitutor, checkWeak)
                } else if (lClass.qualifiedName == "java.lang.Object") {
                  return conformsInner(AnyRef, right, visited, substitutor, checkWeak)
                }
                val inh = smartIsInheritor(rClass, subst, lClass)
                if (!inh._1) return (false, substitutor)
                val tp = inh._2
                //Special case for higher kind types passed to generics.
                if (lClass.hasTypeParameters) {
                  left match {
                    case _: ScParameterizedType =>
                    case _ => return (true, substitutor)
                  }
                }
                val t = conformsInner(left, tp, visited + rClass, substitutor, checkWeak = false)
                if (t._1) return (true, t._2)
                else return (false, substitutor)
              case _ =>
            }
          case _ =>
        }
        val iterator = BaseTypes.iterator(right)
        while (iterator.hasNext) {
          ProgressManager.checkCanceled()
          val tp = iterator.next()
          val t = conformsInner(left, tp, visited, substitutor, checkWeak = true)
          if (t._1) return (true, t._2)
        }
        (false, substitutor)
      }
    }

  private def checkParameterizedType(parametersIterator: Iterator[PsiTypeParameter], args1: scala.Seq[ScType],
                             args2: scala.Seq[ScType], _undefinedSubst: ScUndefinedSubstitutor,
                             visited: Set[PsiClass], checkWeak: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = _undefinedSubst

    def addAbstract(upper: ScType, lower: ScType, tp: ScType, alternateTp: ScType): Boolean = {
      if (!upper.equiv(Any)) {
        val t = conformsInner(upper, tp, visited, undefinedSubst, checkWeak)
        if (!t._1) {
          val t = conformsInner(upper, alternateTp, visited, undefinedSubst, checkWeak)
          if (!t._1) return false
          else undefinedSubst = t._2
        } else undefinedSubst = t._2
      }
      if (!lower.equiv(Nothing)) {
        val t = conformsInner(tp, lower, visited, undefinedSubst, checkWeak)
        if (!t._1) {
          val t = conformsInner(alternateTp, lower, visited, undefinedSubst, checkWeak)
          if (!t._1) return false
          else undefinedSubst = t._2
        } else undefinedSubst = t._2
      }
      true
    }

    val args1Iterator = args1.iterator
    val args2Iterator = args2.iterator

    while (parametersIterator.hasNext && args1Iterator.hasNext && args2Iterator.hasNext) {
      val tp = parametersIterator.next()
      val argsPair = (args1Iterator.next(), args2Iterator.next())
      tp match {
        case scp: ScTypeParam if scp.isContravariant =>
          val y = conformsInner(argsPair._2, argsPair._1, HashSet.empty, undefinedSubst)
          if (!y._1) return (false, undefinedSubst)
          else undefinedSubst = y._2
        case scp: ScTypeParam if scp.isCovariant =>
          val y = conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
          if (!y._1) return (false, undefinedSubst)
          else undefinedSubst = y._2
        //this case filter out such cases like undefined type
        case _ =>
          argsPair match {
            case (UndefinedType(parameterType, _), rt) =>
              val y = addParam(parameterType, rt, undefinedSubst)
              if (!y._1) return (false, undefinedSubst)
              undefinedSubst = y._2
            case (lt, UndefinedType(parameterType, _)) =>
              val y = addParam(parameterType, lt, undefinedSubst)
              if (!y._1) return (false, undefinedSubst)
              undefinedSubst = y._2
            case (ScAbstractType(tpt, lower, upper), r) =>
              val (right, alternateRight) =
                if (tpt.arguments.nonEmpty && !r.isInstanceOf[ScParameterizedType])
                  (ScParameterizedType(r, tpt.arguments), r)
                else (r, r)
                if (!addAbstract(upper, lower, right, alternateRight)) return (false, undefinedSubst)
            case (l, ScAbstractType(tpt, lower, upper)) =>
              val (left, alternateLeft) =
                if (tpt.arguments.nonEmpty && !l.isInstanceOf[ScParameterizedType])
                  (ScParameterizedType(l, tpt.arguments), l)
                else (l, l)
              if (!addAbstract(upper, lower, left, alternateLeft)) return (false, undefinedSubst)
            case _ =>
              val t = argsPair._1.equiv(argsPair._2, undefinedSubst, falseUndef = false)
              if (!t._1) return (false, undefinedSubst)
              undefinedSubst = t._2
          }
      }
    }
    (true, undefinedSubst)
  }

  private class LeftConformanceVisitor(l: ScType, r: ScType, visited: Set[PsiClass],
                                       subst: ScUndefinedSubstitutor,
                                       checkWeak: Boolean = false) extends ScalaTypeVisitor {

    private implicit val projectContext = l.projectContext

    private def addBounds(parameterType: TypeParameterType, `type`: ScType) = {
      val name = parameterType.nameAndId
      undefinedSubst = undefinedSubst
        .addLower(name, `type`, variance = Invariant)
        .addUpper(name, `type`, variance = Invariant)
    }

    /*
      Different checks from right type in order of appearence.
      todo: It's seems it's possible to check order and simplify code in many places.
     */
    trait ValDesignatorSimplification extends ScalaTypeVisitor {
      override def visitDesignatorType(d: ScDesignatorType) {
        d.getValType match {
          case Some(v) =>
            result = conformsInner(l, v, visited, subst, checkWeak)
          case _ =>
        }
      }
    }

    trait UndefinedSubstVisitor extends ScalaTypeVisitor {
      override def visitUndefinedType(u: UndefinedType) {
        result = (true, undefinedSubst.addUpper(u.parameterType.nameAndId, l))
      }
    }

    trait AbstractVisitor extends ScalaTypeVisitor {
      override def visitAbstractType(a: ScAbstractType) {
        val left =
          if (a.parameterType.arguments.nonEmpty && !l.isInstanceOf[ScParameterizedType])
            ScParameterizedType(l, a.parameterType.arguments)
          else l
        if (!a.lower.equiv(Nothing)) {
          result = conformsInner(left, a.lower, visited, undefinedSubst, checkWeak)
        } else {
          result = (true, undefinedSubst)
        }
        if (result._1 && !a.upper.equiv(Any)) {
          val t = conformsInner(a.upper, left, visited, result._2, checkWeak)
          if (t._1) result = t //this is optionally
        }
      }
    }

    trait ParameterizedAbstractVisitor extends ScalaTypeVisitor {
      override def visitParameterizedType(p: ParameterizedType) {
        p.designator match {
          case ScAbstractType(parameterType, lowerBound, _) =>
            val subst = ScSubstitutor.bind(parameterType.arguments, p.typeArguments)
            val lower: ScType =
              subst.subst(lowerBound) match {
                case ParameterizedType(lower, _) => ScParameterizedType(lower, p.typeArguments)
                case lower => ScParameterizedType(lower, p.typeArguments)
              }
            if (!lower.equiv(Nothing)) {
              result = conformsInner(l, lower, visited, undefinedSubst, checkWeak)
            }
          case _ =>
        }
      }
    }

    private def checkEquiv() {
      val isEquiv = l.equiv(r, undefinedSubst)
      if (isEquiv._1) result = isEquiv
    }

    trait ExistentialSimplification extends ScalaTypeVisitor {
      override def visitExistentialType(e: ScExistentialType) {
        val simplified = e.simplify()
        if (simplified != r) result = conformsInner(l, simplified, visited, undefinedSubst, checkWeak)
      }
    }

    trait ExistentialArgumentVisitor extends ScalaTypeVisitor {
      override def visitExistentialArgument(s: ScExistentialArgument): Unit = {
        result = conformsInner(l, s.upper, HashSet.empty, undefinedSubst)
      }
    }

    trait ParameterizedExistentialArgumentVisitor extends ScalaTypeVisitor {
      override def visitParameterizedType(p: ParameterizedType) {
        p.designator match {
          case s: ScExistentialArgument =>
            s.upper match {
              case ParameterizedType(upper, _) =>
                result = conformsInner(l, upper, visited, undefinedSubst, checkWeak)
              case upper =>
                result = conformsInner(l, upper, visited, undefinedSubst, checkWeak)
            }
          case _ =>
        }
      }
    }

    trait OtherNonvalueTypesVisitor extends ScalaTypeVisitor {
      override def visitUndefinedType(u: UndefinedType) {
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
          if (!l.conforms(AnyRef)) {
            result = (false, undefinedSubst)
            return
          }
          l.extractDesignated(expandAliases = false) match {
            case Some(el) =>
              val flag = el.elementScope.getCachedClass("scala.NotNull")
                .map {
                  ScDesignatorType(_)
                }.exists {
                l.conforms(_)
              }
              result = (!flag, undefinedSubst) // todo: think about undefinedSubst
            case _ => result = (true, undefinedSubst)
          }
        }
      }
    }

    trait TypeParameterTypeVisitor extends ScalaTypeVisitor {
      override def visitTypeParameterType(tpt: TypeParameterType) {
        result = conformsInner(l, tpt.upperType, substitutor = undefinedSubst)
      }
    }

    trait ThisVisitor extends ScalaTypeVisitor {
      override def visitThisType(t: ScThisType): Unit = {
        result = t.element.getTypeWithProjections() match {
          case Right(value) => conformsInner(l, value, visited, subst, checkWeak)
          case _ => (false, undefinedSubst)
        }
      }
    }

    trait DesignatorVisitor extends ScalaTypeVisitor {
      override def visitDesignatorType(d: ScDesignatorType): Unit = {
        val maybeType = d.element match {
          case v: ScBindingPattern => v.`type`()
          case v: ScParameter => v.`type`()
          case v: ScFieldId => v.`type`()
          case _ => return
        }

        result = maybeType match {
          case Right(value) => conformsInner(l, value, visited, undefinedSubst)
          case _ => (false, undefinedSubst)
        }
      }
    }

    trait ParameterizedAliasVisitor extends ScalaTypeVisitor {
      override def visitParameterizedType(p: ParameterizedType): Unit = {
        p.isAliasType match {
          case Some(AliasType(_, _, upper)) =>
            result = upper match {
              case Right(value) => conformsInner(l, value, visited, undefinedSubst)
              case _ => (false, undefinedSubst)
            }
          case _ =>
        }
      }
    }

    trait AliasDesignatorVisitor extends ScalaTypeVisitor {
      def stopDesignatorAliasOnFailure: Boolean = false

      override def visitDesignatorType(des: ScDesignatorType) {
        des.isAliasType match {
          case Some(AliasType(_, _, Right(value))) =>
            val res = conformsInner(l, value, visited, undefinedSubst)
            if (stopDesignatorAliasOnFailure || res._1) result = res
          case _ =>
        }
      }
    }

    trait CompoundTypeVisitor extends ScalaTypeVisitor {
      override def visitCompoundType(c: ScCompoundType) {
        val comps = c.components
        var results = Set[ScUndefinedSubstitutor]()
        def traverse(check: (ScType, ScUndefinedSubstitutor) => (Boolean, ScUndefinedSubstitutor)) = {
          val iterator = comps.iterator
          while (iterator.hasNext) {
            val comp = iterator.next()
            val t = check(comp, undefinedSubst)
            if (t._1) {
              results = results + t._2
            }
          }
        }
        traverse(typeSystem.equivInner(l, _, _))
        if (results.isEmpty) {
          traverse(conformsInner(l, _, HashSet.empty, _))
        }

        if (results.size == 1) {
          result = (true, results.head)
          return
        } else if (results.size > 1) {
          result = (true, ScUndefinedSubstitutor.multi(results))
          return
        }

        result = l.isAliasType match {
          case Some(AliasType(_: ScTypeAliasDefinition, Right(comp: ScCompoundType), _)) =>
            conformsInner(comp, c, HashSet.empty, undefinedSubst)
          case _ => (false, undefinedSubst)
        }
      }
    }

    trait ExistentialVisitor extends ScalaTypeVisitor {
      override def visitExistentialType(ex: ScExistentialType) {
        result = conformsInner(l, ex.quantified, HashSet.empty, undefinedSubst)
      }
    }

    trait ProjectionVisitor extends ScalaTypeVisitor {
      def stopProjectionAliasOnFailure: Boolean = false

      override def visitProjectionType(proj2: ScProjectionType): Unit = {
        proj2.isAliasType match {
          case Some(AliasType(_, _, Left(_))) =>
          case Some(AliasType(_, _, Right(value))) =>
            val res = conformsInner(l, value, visited, undefinedSubst)
            if (stopProjectionAliasOnFailure || res._1) result = res
          case _ =>
            l match {
            case proj1: ScProjectionType if smartEquivalence(proj1.actualElement, proj2.actualElement) =>
              val projected1 = proj1.projected
              val projected2 = proj2.projected
              result = conformsInner(projected1, projected2, visited, undefinedSubst)
            case ParameterizedType(projDes: ScProjectionType, typeArguments) =>
              //TODO this looks overcomplicated. Improve the code.
              def cutProj(p: ScType, acc: List[ScProjectionType]): ScType = {
                if (acc.isEmpty) p else acc.foldLeft(p){
                  case (proj, oldProj) => ScProjectionType(proj, oldProj.element, oldProj.superReference)
                }
              }
              @tailrec
              def findProjectionBase(proj: ScProjectionType, acc: List[ScProjectionType] = List()): Unit = {
                val t = proj.projected.equiv(projDes.projected, undefinedSubst)
                if (t._1) {
                  undefinedSubst = t._2
                  val (maybeLeft, maybeRight) = (projDes.actualElement, proj.actualElement) match {
                    case (desT: Typeable, projT: Typeable) =>
                      val left = desT.`type`().toOption
                        .collect {
                          case ParameterizedType(designator, _) => designator
                        }.map(ScParameterizedType(_, typeArguments))


                      val right = projT.`type`().toOption
                        .map(cutProj(_, acc))

                      (left, right)
                    case _ => (None, None)
                  }

                  maybeLeft.zip(maybeRight).map {
                    case (left, right) => conformsInner(left, right, visited, undefinedSubst)
                  }.foreach {
                    case r@(true, _) => result = r
                    case _ =>
                  }
                } else {
                  proj.projected match {
                    case p: ScProjectionType => findProjectionBase(p, proj :: acc)
                    case _ =>
                  }
                }
              }
              findProjectionBase(proj2)
            case _ =>
              val res = proj2.actualElement match {
                case syntheticClass: ScSyntheticClass =>
                  result = conformsInner(l, syntheticClass.stdType, HashSet.empty, undefinedSubst)
                  return
                case v: ScBindingPattern => v.`type`()
                case v: ScParameter => v.`type`()
                case v: ScFieldId => v.`type`()
                case _ => return
              }

              result = res match {
                case Right(value) => conformsInner(l, proj2.actualSubst.subst(value), visited, undefinedSubst)
                case _ => (false, undefinedSubst)
              }
          }
        }
      }
    }

    private var result: (Boolean, ScUndefinedSubstitutor) = null
    private var undefinedSubst: ScUndefinedSubstitutor = subst

    def getResult: (Boolean, ScUndefinedSubstitutor) = result

    override def visitStdType(x: StdType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor
          with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with ExistentialArgumentVisitor
        with ParameterizedExistentialArgumentVisitor with OtherNonvalueTypesVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      if (checkWeak && r.isInstanceOf[ValType]) {
        val stdTypes = StdTypes.instance
        import stdTypes._

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

      if (x == Nothing && r == Null) {
        result = (false, undefinedSubst)
        return
      }

      rightVisitor = new NothingNullVisitor with TypeParameterTypeVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ThisVisitor with DesignatorVisitor
        with ParameterizedAliasVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new AliasDesignatorVisitor with CompoundTypeVisitor with ExistentialVisitor
        with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      if (x eq Null) {
        result = (r == Nothing, undefinedSubst)
        return
      }

      if (x eq AnyRef) {
        if (r eq Any) {
          result = (false, undefinedSubst)
          return
        }
        else if (r eq AnyVal) {
          result = (false, undefinedSubst)
          return
        }
        else if (r.isInstanceOf[ValType]) {
          result = (false, undefinedSubst)
          return
        }
        else if (!r.isInstanceOf[ScExistentialType]) {
          rightVisitor = new AliasDesignatorVisitor with ProjectionVisitor {
            override def stopProjectionAliasOnFailure: Boolean = true

            override def stopDesignatorAliasOnFailure: Boolean = true
          }
          r.visitType(rightVisitor)
          if (result != null) return
          result = (true, undefinedSubst)
          return
        }
      }

      if (x eq Singleton) {
        result = (false, undefinedSubst)
      }

      if (x eq AnyVal) {
        result = (r.isInstanceOf[ValType] || ValueClassType.isValueType(r), undefinedSubst)
        return
      }
      if (l.isInstanceOf[ValType] && r.isInstanceOf[ValType]) {
        result = (false, undefinedSubst)
        return
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

      rightVisitor = new ExistentialSimplification with ExistentialArgumentVisitor
        with ParameterizedExistentialArgumentVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor
        with TypeParameterTypeVisitor with ThisVisitor with DesignatorVisitor {}
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
      def workWithSignature(s: Signature, retType: ScType): Boolean = {
        val processor = new CompoundTypeCheckSignatureProcessor(s,retType, undefinedSubst, s.substitutor)
        processor.processType(r, s.namedElement)
        undefinedSubst = processor.getUndefinedSubstitutor
        processor.getResult
      }

      def workWithTypeAlias(sign: TypeAliasSignature): Boolean = {
        val processor = new CompoundTypeCheckTypeAliasProcessor(sign, undefinedSubst, ScSubstitutor.empty)
        processor.processType(r, sign.ta)
        undefinedSubst = processor.getUndefinedSubstitutor
        processor.getResult
      }

      result = (c.components.forall(comp => {
        val t = conformsInner(comp, r, HashSet.empty, undefinedSubst)
        undefinedSubst = t._2
        t._1
      }) && c.signatureMap.forall {
        case (s: Signature, retType) => workWithSignature(s, retType)
      } && c.typesMap.forall {
        case (_, sign) => workWithTypeAlias(sign)
      }, undefinedSubst)
    }

    override def visitProjectionType(proj: ScProjectionType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with ExistentialArgumentVisitor
        with ParameterizedExistentialArgumentVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor
        with TypeParameterTypeVisitor with ThisVisitor with DesignatorVisitor with ParameterizedAliasVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ParameterizedAliasVisitor with AliasDesignatorVisitor with CompoundTypeVisitor with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      r match {
        case proj1: ScProjectionType if smartEquivalence(proj1.actualElement, proj.actualElement) =>
          val projected1 = proj.projected
          val projected2 = proj1.projected
          result = conformsInner(projected1, projected2, visited, undefinedSubst)
          if (result != null) return
        case proj1: ScProjectionType if proj1.actualElement.name == proj.actualElement.name =>
          val projected1 = proj.projected
          val projected2 = proj1.projected
          val t = conformsInner(projected1, projected2, visited, undefinedSubst)
          if (t._1) {
            result = t
            return
          }
        case _ =>
      }

      proj.isAliasType match {
        case Some(AliasType(_, lower, _)) =>
          result = lower match {
            case Right(value) => conformsInner(value, r, visited, undefinedSubst)
            case _ => (false, undefinedSubst)
          }
        case _ =>
          rightVisitor = new ExistentialVisitor {}
          r.visitType(rightVisitor)
          if (result != null) return
      }
    }

    override def visitJavaArrayType(a1: JavaArrayType) {
      val JavaArrayType(arg1) = a1
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with ExistentialArgumentVisitor
        with ParameterizedExistentialArgumentVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor
        with TypeParameterTypeVisitor with ThisVisitor with DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ParameterizedAliasVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      r match {
        case JavaArrayType(arg2) =>
          val argsPair = (arg1, arg2)
          argsPair match {
            case (ScAbstractType(tpt, lower, upper), r) =>
              val right =
                if (tpt.arguments.nonEmpty && !r.isInstanceOf[ScParameterizedType])
                  ScParameterizedType(r, tpt.arguments)
                else r
              if (!upper.equiv(Any)) {
                val t = conformsInner(upper, right, visited, undefinedSubst, checkWeak)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
              }
              if (!lower.equiv(Nothing)) {
                val t = conformsInner(right, lower, visited, undefinedSubst, checkWeak)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
              }
            case (l, ScAbstractType(tpt, lower, upper)) =>
              val left =
                if (tpt.arguments.nonEmpty && !l.isInstanceOf[ScParameterizedType])
                  ScParameterizedType(l, tpt.arguments)
                else l
              if (!upper.equiv(Any)) {
                val t = conformsInner(upper, left, visited, undefinedSubst, checkWeak)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
              }
              if (!lower.equiv(Nothing)) {
                val t = conformsInner(left, lower, visited, undefinedSubst, checkWeak)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
              }
            case (UndefinedType(parameterType, _), rt) => addBounds(parameterType, rt)
            case (lt, UndefinedType(parameterType, _)) => addBounds(parameterType, lt)
            case _ =>
              val t = argsPair._1.equiv(argsPair._2, undefinedSubst, falseUndef = false)
              if (!t._1) {
                result = (false, undefinedSubst)
                return
              }
              undefinedSubst = t._2
          }
          result = (true, undefinedSubst)
          return
        case p2: ScParameterizedType =>
          val args = p2.typeArguments
          val des = p2.designator
          if (args.length == 1 && (des.extractClass match {
            case Some(q) => q.qualifiedName == "scala.Array"
            case _ => false
          })) {
            val arg = a1.argument
            val argsPair = (arg, args.head)
            argsPair match {
              case (ScAbstractType(tpt, lower, upper), r) =>
                val right =
                  if (tpt.arguments.nonEmpty && !r.isInstanceOf[ScParameterizedType])
                    ScParameterizedType(r, tpt.arguments)
                  else r
                if (!upper.equiv(Any)) {
                  val t = conformsInner(upper, right, visited, undefinedSubst, checkWeak)
                  if (!t._1) {
                    result = (false, undefinedSubst)
                    return
                  }
                  undefinedSubst = t._2
                }
                if (!lower.equiv(Nothing)) {
                  val t = conformsInner(right, lower, visited, undefinedSubst, checkWeak)
                  if (!t._1) {
                    result = (false, undefinedSubst)
                    return
                  }
                  undefinedSubst = t._2
                }
              case (l, ScAbstractType(tpt, lower, upper)) =>
                val left =
                  if (tpt.arguments.nonEmpty && !l.isInstanceOf[ScParameterizedType])
                    ScParameterizedType(l, tpt.arguments)
                  else l
                if (!upper.equiv(Any)) {
                  val t = conformsInner(upper, left, visited, undefinedSubst, checkWeak)
                  if (!t._1) {
                    result = (false, undefinedSubst)
                    return
                  }
                  undefinedSubst = t._2
                }
                if (!lower.equiv(Nothing)) {
                  val t = conformsInner(left, lower, visited, undefinedSubst, checkWeak)
                  if (!t._1) {
                    result = (false, undefinedSubst)
                    return
                  }
                  undefinedSubst = t._2
                }
              case (UndefinedType(parameterType, _), rt) => addBounds(parameterType, rt)
              case (lt, UndefinedType(parameterType, _)) => addBounds(parameterType, lt)
              case _ =>
                val t = argsPair._1.equiv(argsPair._2, undefinedSubst, falseUndef = false)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
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

    override def visitParameterizedType(p: ParameterizedType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      p.designator match {
        case a: ScAbstractType =>
          val subst = ScSubstitutor.bind(a.parameterType.arguments, p.typeArguments)
          val upper: ScType =
            subst.subst(a.upper) match {
              case ParameterizedType(up, _) => ScParameterizedType(up, p.typeArguments)
              case up => ScParameterizedType(up, p.typeArguments)
            }
          if (!upper.equiv(Any)) {
            result = conformsInner(upper, r, visited, undefinedSubst, checkWeak)
          } else {
            result = (true, undefinedSubst)
          }
          if (result._1) {
            val lower: ScType =
              subst.subst(a.lower) match {
                case ParameterizedType(low, _) => ScParameterizedType(low, p.typeArguments)
                case low => ScParameterizedType(low, p.typeArguments)
              }
            if (!lower.equiv(Nothing)) {
              val t = conformsInner(r, lower, visited, result._2, checkWeak)
              if (t._1) result = t
            }
          }
          return
        case _ =>
      }

      rightVisitor = new ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with ExistentialArgumentVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      p.designator match {
        case s: ScExistentialArgument =>
          s.lower match {
            case ParameterizedType(lower, _) =>
              result = conformsInner(lower, r, visited, undefinedSubst, checkWeak)
              return
            case lower =>
              result = conformsInner(lower, r, visited, undefinedSubst, checkWeak)
              return
          }
        case _ =>
      }

      rightVisitor = new ParameterizedExistentialArgumentVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor
         with ThisVisitor with DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      def processEquivalentDesignators(args2: Seq[ScType]): Unit = {
        val args1 = p.typeArguments
        val des1 = p.designator
        if (args1.length != args2.length) {
          result = (false, undefinedSubst)
          return
        }
        des1.extractDesignated(expandAliases = true) match {
          case Some(ownerDesignator) =>
            val parametersIterator = ownerDesignator match {
              case td: ScTypeParametersOwner => td.typeParameters.iterator
              case ownerDesignator: PsiTypeParameterListOwner => ownerDesignator.getTypeParameters.iterator
              case _ =>
                result = (false, undefinedSubst)
                return
            }
            result = checkParameterizedType(parametersIterator, args1, args2,
              undefinedSubst, visited, checkWeak)
          case _ =>
            result = (false, undefinedSubst)
        }
      }

      //todo: looks like this code can be simplified and unified.
      //todo: what if left is type alias declaration, right is type alias definition, which is alias to that declaration?
      p.isAliasType match {
        case Some(AliasType(ta, lower, _)) =>
          if (ta.isInstanceOf[ScTypeAliasDeclaration])
            r match {
              case ParameterizedType(proj, args2) if r.isAliasType.isDefined && (proj equiv p.designator) =>
                processEquivalentDesignators(args2)
                return
              case _ =>
            }

          result = lower match {
            case Right(value) => conformsInner(value, r, visited, undefinedSubst)
            case _ => (false, undefinedSubst)
          }
          return
        case _ =>
      }

      r match {
        case p2: ScParameterizedType =>
          val des1 = p.designator
          val des2 = p2.designator
          val args1 = p.typeArguments
          val args2 = p2.typeArguments
          (des1, des2) match {
            case (owner1: TypeParameterType, _: TypeParameterType) =>
              if (des1 equiv des2) {
                if (args1.length != args2.length) {
                  result = (false, undefinedSubst)
                } else {
                  result = checkParameterizedType(owner1.arguments.map(_.psiTypeParameter).iterator, args1, args2,
                    undefinedSubst, visited, checkWeak)
                }
              } else {
                result = (false, undefinedSubst)
              }
            case (_: UndefinedType, UndefinedType(parameterType, _)) =>
              (if (args1.length != args2.length) findDiffLengthArgs(l, args2.length) else Some((args1, des1))) match {
                case Some((aArgs, aType)) =>
                  undefinedSubst = undefinedSubst.addUpper(parameterType.nameAndId, aType)
                  result = checkParameterizedType(parameterType.arguments.map(_.psiTypeParameter).iterator, aArgs,
                    args2, undefinedSubst, visited, checkWeak)
                case _ =>
                  result = (false, undefinedSubst)
              }
            case (UndefinedType(parameterType, _), _) =>
              (if (args1.length != args2.length) findDiffLengthArgs(r, args1.length) else Some((args2, des2))) match {
                case Some((aArgs, aType)) =>
                  undefinedSubst = undefinedSubst.addLower(parameterType.nameAndId, aType)
                  result = checkParameterizedType(parameterType.arguments.map(_.psiTypeParameter).iterator, args1,
                    aArgs, undefinedSubst, visited, checkWeak)
                case _ =>
                  result = (false, undefinedSubst)
              }
              if (args1.length < args2.length && (result == null || !result._1)) {
                //partial unification SCL-11320
                val captureLength = args2.length - args1.length

                val (captured, abstracted) = args2.splitAt(captureLength)
                val t = checkParameterizedType(parameterType.arguments.map(_.psiTypeParameter).iterator, args1, abstracted,
                  undefinedSubst, visited, checkWeak)
                result = if (t._1) {
                  val abstractedTypeParams = abstracted.zipWithIndex.map { case (aType, i) => TypeParameter(Seq(), Nothing, Any, new ScExistentialLightTypeParam("p" + i + "$$")) }
                  addParam(parameterType, ScTypePolymorphicType(ScParameterizedType(des2,
                    captured ++ abstractedTypeParams.map(TypeParameterType(_))), abstractedTypeParams), t._2)
                } else {
                  t
                }
              }
            case (_, UndefinedType(parameterType, _)) =>
              (if (args1.length != args2.length) findDiffLengthArgs(l, args2.length) else Some((args1, des1))) match {
                case Some((aArgs, aType)) =>
                  undefinedSubst = undefinedSubst.addUpper(parameterType.nameAndId, aType)
                  result = checkParameterizedType(parameterType.arguments.map(_.psiTypeParameter).iterator, aArgs,
                    args2, undefinedSubst, visited, checkWeak)
                case _ =>
                  result = (false, undefinedSubst)
              }
            case _ if des1 equiv des2 =>
              result =
                if (args1.length != args2.length) (false, undefinedSubst)
                else extractParams(des1) match {
                  case Some(params) => checkParameterizedType(params, args1, args2, undefinedSubst, visited, checkWeak)
                  case _ => (false, undefinedSubst)
                }
            case (_, t: TypeParameterType) if t.arguments.length == p2.typeArguments.length =>
              val subst = ScSubstitutor.bind(t.arguments, p.typeArguments)
              result = conformsInner(des1, subst.subst(t.upperType), visited, undefinedSubst, checkWeak)
            case (proj1: ScProjectionType, proj2: ScProjectionType)
              if smartEquivalence(proj1.actualElement, proj2.actualElement) =>
              val t = conformsInner(proj1, proj2, visited, undefinedSubst)
              if (!t._1) {
                result = (false, undefinedSubst)
              } else {
                undefinedSubst = t._2
                if (args1.length != args2.length) {
                  result = (false, undefinedSubst)
                } else {
                  proj1.actualElement match {
                    case td: ScTypeParametersOwner =>
                      result = checkParameterizedType(td.typeParameters.iterator, args1, args2, undefinedSubst, visited, checkWeak)
                    case td: PsiTypeParameterListOwner =>
                      result = checkParameterizedType(td.getTypeParameters.iterator, args1, args2, undefinedSubst, visited, checkWeak)
                    case _ =>
                      result = (false, undefinedSubst)
                  }
                }
              }
            case _ =>
          }
        case _ =>
      }

      if (result != null) {
        //sometimes when the above part has failed, we still have to check for alias
        if (!result._1) r.isAliasType match {
          case Some(aliasType) =>
            rightVisitor = new ParameterizedAliasVisitor with TypeParameterTypeVisitor {}
            r.visitType(rightVisitor)
          case _ =>
        }
        return
      }

      rightVisitor = new ParameterizedAliasVisitor with TypeParameterTypeVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      r match {
        case _: JavaArrayType =>
          val args = p.typeArguments
          val des = p.designator
          if (args.length == 1 && (des.extractClass match {
            case Some(q) => q.qualifiedName == "scala.Array"
            case _ => false
          })) {
            val arg = r.asInstanceOf[JavaArrayType].argument
            val argsPair = (arg, args.head)
            argsPair match {
              case (ScAbstractType(tpt, lower, upper), r) =>
                val right =
                  if (tpt.arguments.nonEmpty && !r.isInstanceOf[ScParameterizedType])
                    ScParameterizedType(r, tpt.arguments)
                  else r
                if (!upper.equiv(Any)) {
                  val t = conformsInner(upper, right, visited, undefinedSubst, checkWeak)
                  if (!t._1) {
                    result = (false, undefinedSubst)
                    return
                  }
                  undefinedSubst = t._2
                }
                if (!lower.equiv(Nothing)) {
                  val t = conformsInner(right, lower, visited, undefinedSubst, checkWeak)
                  if (!t._1) {
                    result = (false, undefinedSubst)
                    return
                  }
                  undefinedSubst = t._2
                }
              case (l, ScAbstractType(tpt, lower, upper)) =>
                val left =
                  if (tpt.arguments.nonEmpty && !l.isInstanceOf[ScParameterizedType])
                    ScParameterizedType(l, tpt.arguments)
                  else l
                if (!upper.equiv(Any)) {
                  val t = conformsInner(upper, left, visited, undefinedSubst, checkWeak)
                  if (!t._1) {
                    result = (false, undefinedSubst)
                    return
                  }
                  undefinedSubst = t._2
                }
                if (!lower.equiv(Nothing)) {
                  val t = conformsInner(left, lower, visited, undefinedSubst, checkWeak)
                  if (!t._1) {
                    result = (false, undefinedSubst)
                    return
                  }
                  undefinedSubst = t._2
                }
              case (UndefinedType(parameterType, _), rt) => addBounds(parameterType, rt)
              case (lt, UndefinedType(parameterType, _)) => addBounds(parameterType, lt)
              case _ =>
                val t = argsPair._1.equiv(argsPair._2, undefinedSubst, falseUndef = false)
                if (!t._1) {
                  result = (false, undefinedSubst)
                  return
                }
                undefinedSubst = t._2
            }
            result = (true, undefinedSubst)
            return
          }
        case _ =>
      }

      p.designator match {
        case t: TypeParameterType if t.arguments.length == p.typeArguments.length =>
          val subst = ScSubstitutor.bind(t.arguments, p.typeArguments)
          result = conformsInner(subst.subst(t.lowerType), r, visited, undefinedSubst, checkWeak)
          return
        case _ =>
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

      rightVisitor = new ExistentialSimplification with ExistentialArgumentVisitor
        with ParameterizedExistentialArgumentVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor
         with TypeParameterTypeVisitor with ThisVisitor with DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ParameterizedAliasVisitor with AliasDesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      val tptsMap = new mutable.HashMap[String, TypeParameterType]()
      def updateType(t: ScType, rejected: HashSet[String] = HashSet.empty): ScType = {
        t.recursiveUpdate {
          case ScExistentialArgument(name, _, _, _) =>
            e.wildcards.find(_.name == name) match {
              case Some(ScExistentialArgument(thatName, args, lower, upper)) if !rejected.contains(thatName) =>
                val tpt = tptsMap.getOrElseUpdate(thatName,
                  TypeParameterType(args, lower, upper, new ScExistentialLightTypeParam(name))
                )
                ReplaceWith(tpt)
              case _ => ProcessSubtypes
            }
          case ScExistentialType(innerQ, wilds) =>
            ReplaceWith(ScExistentialType(updateType(innerQ, rejected ++ wilds.map(_.name)), wilds))
          case _ => ProcessSubtypes
        }
      }
      val q = updateType(e.quantified)
      val tpts = tptsMap.values.toSeq
      val undefinedTpts = tpts.map(UndefinedType(_))
      val subst = ScSubstitutor.bind(tpts, undefinedTpts)

      val res = conformsInner(subst.subst(q), r, HashSet.empty, undefinedSubst)
      if (!res._1) {
        result = (false, undefinedSubst)
      } else {
        val unSubst: ScUndefinedSubstitutor = res._2
        unSubst.getSubstitutor match {
          case Some(uSubst) =>
            for (tpt <- tptsMap.values if result == null) {
              val substedTpt = uSubst.subst(tpt)
              var t = conformsInner(substedTpt, uSubst.subst(updateType(tpt.lowerType)), immutable.Set.empty, undefinedSubst)
              if (substedTpt != tpt && !t._1) {
                result = (false, undefinedSubst)
                return
              }
              undefinedSubst = t._2
              t = conformsInner(uSubst.subst(updateType(tpt.upperType)), substedTpt, immutable.Set.empty, undefinedSubst)
              if (substedTpt != tpt && !t._1) {
                result = (false, undefinedSubst)
                return
              }
              undefinedSubst = t._2
            }
            if (result == null) {
              val filterFunction: (((String, Long), Set[ScType])) => Boolean = {
                case (id: (String, Long), _: Set[ScType]) =>
                  !tptsMap.values.exists(_.nameAndId == id)
              }
              val newUndefSubst = unSubst.filter(filterFunction)
              undefinedSubst += newUndefSubst
              result = (true, undefinedSubst)
            }
          case None => result = (false, undefinedSubst)
        }
      }
    }

    override def visitThisType(t: ScThisType): Unit = {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with ExistentialArgumentVisitor
        with ParameterizedExistentialArgumentVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor
         with TypeParameterTypeVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      result = t.element.getTypeWithProjections() match {
        case Right(value) => conformsInner(value, r, visited, subst, checkWeak)
        case _ => (false, undefinedSubst)
      }
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

      rightVisitor = new ExistentialSimplification with ExistentialArgumentVisitor
        with ParameterizedExistentialArgumentVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor
         {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new TypeParameterTypeVisitor
        with ThisVisitor with DesignatorVisitor with ParameterizedAliasVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new AliasDesignatorVisitor with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      des.isAliasType match {
        case Some(AliasType(_, lower, _)) =>
          result = lower match {
            case Right(value) => conformsInner(value, r, visited, undefinedSubst)
            case _ => (false, undefinedSubst)
          }
        case _ =>
          rightVisitor = new CompoundTypeVisitor with ExistentialVisitor {}
          r.visitType(rightVisitor)
          if (result != null) return
      }
    }

    override def visitTypeParameterType(tpt1: TypeParameterType) {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      trait TypeParameterTypeNothingNullVisitor extends NothingNullVisitor {
        override def visitStdType(x: StdType) {
          if (x eq Nothing) result = (true, undefinedSubst)
          else if (x eq Null) {
            result = conformsInner(tpt1.lowerType, r, HashSet.empty, undefinedSubst)
          }
        }
      }

      rightVisitor = new ExistentialSimplification with ExistentialArgumentVisitor
        with ParameterizedExistentialArgumentVisitor with OtherNonvalueTypesVisitor with TypeParameterTypeNothingNullVisitor
        with DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      r match {
        case tpt2: TypeParameterType =>
          val res = conformsInner(tpt1.lowerType, r, HashSet.empty, undefinedSubst)
          if (res._1) {
            result = res
            return
          }
          result = conformsInner(l, tpt2.upperType, HashSet.empty, undefinedSubst)
          return
        case _ =>
      }

      val t = conformsInner(tpt1.lowerType, r, HashSet.empty, undefinedSubst)
      if (t._1) {
        result = t
        return
      }

      rightVisitor = new ParameterizedAliasVisitor with AliasDesignatorVisitor with CompoundTypeVisitor
        with ExistentialVisitor with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      result = (false, undefinedSubst)
    }

    override def visitExistentialArgument(s: ScExistentialArgument): Unit = {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor
          with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      val t = conformsInner(s.lower, r, HashSet.empty, undefinedSubst)

      if (t._1) {
        result = t
        return
      }

      rightVisitor = new OtherNonvalueTypesVisitor with NothingNullVisitor
        with TypeParameterTypeVisitor with ThisVisitor with DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ParameterizedAliasVisitor with AliasDesignatorVisitor with CompoundTypeVisitor
        with ExistentialVisitor with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return
    }

    override def visitUndefinedType(u: UndefinedType) {
      val rightVisitor = new ValDesignatorSimplification {
        override def visitUndefinedType(u2: UndefinedType) {
          val name = u2.parameterType.nameAndId
          result = (true, if (u2.level > u.level) {
            undefinedSubst.addUpper(name, u)
          } else if (u.level > u2.level) {
            undefinedSubst.addUpper(name, u)
          } else {
            undefinedSubst
          })
        }
      }
      r.visitType(rightVisitor)
      if (result == null)
        result = (true, undefinedSubst.addLower(u.parameterType.nameAndId, r))
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
            t = params1(i).paramType.equiv(params2(i).paramType, undefinedSubst, falseUndef = false)
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
      val right =
        if (a.parameterType.arguments.nonEmpty && !r.isInstanceOf[ScParameterizedType])
          ScParameterizedType(r, a.parameterType.arguments)
        else r

        result = conformsInner(a.upper, right, visited, undefinedSubst, checkWeak)
      if (result._1) {
        val t = conformsInner(right, a.lower, visited, result._2, checkWeak)
        if (t._1) result = t
      }
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
          val subst = ScSubstitutor.bind(typeParameters1, typeParameters2)(TypeParameterType(_))
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

  private def smartIsInheritor(leftClass: PsiClass, substitutor: ScSubstitutor, rightClass: PsiClass) : (Boolean, ScType) = {
    if (areClassesEquivalent(leftClass, rightClass)) return (false, null)
    if (!isInheritorDeep(leftClass, rightClass)) return (false, null)
    smartIsInheritor(leftClass, substitutor, areClassesEquivalent(_, rightClass), new collection.immutable.HashSet[PsiClass])
  }

  private def parentWithArgNumber(leftClass: PsiClass, substitutor: ScSubstitutor, argsNumber: Int): (Boolean, ScType) = {
    smartIsInheritor(leftClass, substitutor, c => c.getTypeParameters.length == argsNumber, new collection.immutable.HashSet[PsiClass]())
  }

  private def smartIsInheritor(leftClass: PsiClass, substitutor: ScSubstitutor, condition: PsiClass => Boolean,
                               visited: collection.immutable.HashSet[PsiClass]): (Boolean, ScType) = {
    ProgressManager.checkCanceled()
    val bases: Seq[Any] = leftClass match {
      case td: ScTypeDefinition => td.superTypes
      case _ => leftClass.getSuperTypes
    }
    val iterator = bases.iterator
    val later: ArrayBuffer[(PsiClass, ScSubstitutor)] = new ArrayBuffer[(PsiClass, ScSubstitutor)]()
    var res: ScType = null
    while (iterator.hasNext) {
      val tp: ScType = iterator.next() match {
        case tp: ScType => substitutor.subst(tp)
        case pct: PsiClassType =>
          substitutor.subst(pct.toScType()) match {
            case ex: ScExistentialType => ex.quantified //it's required for the raw types
            case r => r
          }
      }
      tp.extractClassType match {
        case Some((clazz: PsiClass, _)) if visited.contains(clazz) =>
        case Some((clazz: PsiClass, _)) if condition(clazz) =>
          if (res == null) res = tp
          else if (tp.conforms(res)) res = tp
        case Some((clazz: PsiClass, subst)) =>
          later += ((clazz, subst))
        case _ =>
      }
    }
    val laterIterator = later.iterator
    while (laterIterator.hasNext) {
      val (clazz, subst) = laterIterator.next()
      val recursive = smartIsInheritor(clazz, subst, condition, visited + clazz)
      if (recursive._1) {
        if (res == null) res = recursive._2
        else if (recursive._2.conforms(res)) res = recursive._2
      }
    }
    if (res == null) (false, null)
    else (true, res)
  }

  def extractParams(des: ScType): Option[Iterator[PsiTypeParameter]] = {
    des match {
      case undef: UndefinedType =>
        Option(undef.parameterType.psiTypeParameter).map(_.getTypeParameters.iterator)
      case _ =>
        des.extractClass.map {
          case td: ScTypeDefinition => td.typeParameters.iterator
          case other => other.getTypeParameters.iterator
        }
    }
  }

  def addParam(parameterType: TypeParameterType, bound: ScType, undefinedSubst: ScUndefinedSubstitutor): (Boolean, ScUndefinedSubstitutor) =
    addArgedBound(parameterType, bound, undefinedSubst, variance = Invariant, addUpper = true, addLower = true)

  def addArgedBound(parameterType: TypeParameterType, bound: ScType, undefinedSubst: ScUndefinedSubstitutor,
                    variance: Variance = Covariant, addUpper: Boolean = false, addLower: Boolean = false): (Boolean, ScUndefinedSubstitutor) = {
    if (!addUpper && !addLower) return (false, undefinedSubst)
    var res = undefinedSubst
    if (addUpper) res = res.addUpper(parameterType.nameAndId, bound, variance = variance)
    if (addLower) res = res.addLower(parameterType.nameAndId, bound, variance = variance)
    (true, res)
  }

  def processHigherKindedTypeParams(undefType: ParameterizedType, defType: ParameterizedType, undefinedSubst: ScUndefinedSubstitutor,
                                    falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    val defTypeExpanded = defType.isAliasType.map(_.lower).map {
      case Right(p: ScParameterizedType) => p
      case _ => defType
    }.getOrElse(defType)
    extractParams(defTypeExpanded.designator) match {
      case Some(params) =>
        val undef = undefType.designator.asInstanceOf[UndefinedType]
        var defArgsReplace = defTypeExpanded.typeArguments
        val bound = if (params.nonEmpty) {
          if (defTypeExpanded.typeArguments.length != undefType.typeArguments.length)
          {
            if (defType.typeArguments.length != undefType.typeArguments.length) {
              findDiffLengthArgs(defType, undefType.typeArguments.length) match {
                case Some((newArgs, newDes)) =>
                  defArgsReplace = newArgs
                  newDes
                case _ => return (false, undefinedSubst)
              }
            } else {
              defArgsReplace =defType.typeArguments
              defType.designator
            }
          } else defTypeExpanded.designator
        } else {
          defTypeExpanded.designator
        }
        val y = undef.equiv(bound, undefinedSubst, falseUndef)
        if (!y._1) {
          (false, undefinedSubst)
        } else {
          val undefArgIterator = undefType.typeArguments.iterator
          val defIterator = defArgsReplace.iterator
          var sub = y._2
          while (params.hasNext && undefArgIterator.hasNext && defIterator.hasNext) {
            val arg1 = undefArgIterator.next()
            val arg2 = defIterator.next()
            val t = arg1.equiv(arg2, sub, falseUndef = false)
            if (!t._1) return (false, undefinedSubst)
            sub = t._2
          }
          (true, sub)
        }
      case _ => (false, undefinedSubst)
    }
  }

  def findDiffLengthArgs(eType: ScType, argLength: Int): Option[(Seq[ScType], ScType)] =
    eType.extractClassType match {
      case Some((clazz, classSubst)) =>
        val t: (Boolean, ScType) = parentWithArgNumber(clazz, classSubst, argLength)
        if (!t._1) {
          None
        } else t._2 match {
          case ParameterizedType(newDes, newArgs) =>
            Some(newArgs, newDes)
          case _ =>
            None
        }
      case _ =>
        None
    }
}
