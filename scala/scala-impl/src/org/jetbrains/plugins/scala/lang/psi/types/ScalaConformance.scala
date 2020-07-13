package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types.ScalaConformance._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{NonValueType, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.processor.{CompoundTypeCheckSignatureProcessor, CompoundTypeCheckTypeAliasProcessor}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil._

import scala.collection.Seq
import scala.collection.immutable.HashSet

trait ScalaConformance extends api.Conformance with TypeVariableUnification {
  typeSystem: api.TypeSystem =>

  override protected def conformsComputable(key: Key,
                                            visited: Set[PsiClass]): Computable[ConstraintsResult] =
    new Computable[ConstraintsResult] {
      override def compute(): ConstraintsResult = {
        val Key(left, right, checkWeak) = key

        val leftVisitor = new LeftConformanceVisitor(key, visited)
        left.visitType(leftVisitor)
        if (leftVisitor.getResult != null) return leftVisitor.getResult

        //tail, based on class inheritance
        right.extractClassType match {
          case Some((clazz: PsiClass, _)) if visited.contains(clazz) => return ConstraintsResult.Left
          case Some((rClass: PsiClass, subst: ScSubstitutor)) =>
            left.extractClass match {
              case Some(lClass) =>
                if (rClass.qualifiedName == "java.lang.Object") {
                  return conformsInner(left, AnyRef, visited, checkWeak = checkWeak)
                } else if (lClass.qualifiedName == "java.lang.Object") {
                  return conformsInner(AnyRef, right, visited, checkWeak = checkWeak)
                }
                val inh = SmartSuperTypeUtil.smartIsInheritor(rClass, subst, lClass)
                if (inh.isEmpty) return ConstraintsResult.Left
                //Special case for higher kind types passed to generics.
                if (lClass.hasTypeParameters) {
                  left.removeAliasDefinitions() match {
                    case _: ScParameterizedType =>
                    case _ => return ConstraintSystem.empty
                  }
                }
                return conformsInner(left, inh.orNull, visited + rClass)
              case _ =>
            }
          case _ =>
        }
        val iterator = BaseTypes.iterator(right)
        while (iterator.hasNext) {
          ProgressManager.checkCanceled()
          val tp = iterator.next()
          val t = conformsInner(left, tp, visited, checkWeak = true)
          if (t.isRight) return t.constraints
        }
        ConstraintsResult.Left
      }
    }

  private def retryTypeParamsConformance(
    lhs:         TypeParameterType,
    rhs:         TypeParameterType,
    l:           ScType,
    r:           ScType,
    constraints: ConstraintSystem
  ): ConstraintsResult = {
    val lo = lhs.lowerType

    if (!lhs.lowerType.equiv(lhs)) {
      val updated = l.updateRecursively { case `lhs` => lo }
      r.conforms(updated, constraints)
    }
    else ConstraintsResult.Left
  }

  protected def checkParameterizedType(
    parametersIterator: Iterator[PsiTypeParameter],
    args1:              scala.Seq[ScType],
    args2:              scala.Seq[ScType],
    _constraints:       ConstraintSystem,
    visited:            Set[PsiClass],
    checkWeak:          Boolean,
    checkEquivalence:   Boolean = false
  ): ConstraintsResult = {
    var constraints = _constraints

    def addAbstract(upper: ScType, lower: ScType, tp: ScType): Boolean = {
      if (!upper.equiv(Any)) {
        val t = conformsInner(upper, tp, visited, constraints, checkWeak)
        if (t.isLeft) return false
        constraints = t.constraints
      }
      if (!lower.equiv(Nothing)) {
        val t = conformsInner(tp, lower, visited, constraints, checkWeak)
        if (t.isLeft) return false
        constraints = t.constraints
      }
      true
    }

    val args1Iterator = args1.iterator
    val args2Iterator = args2.iterator

    while (parametersIterator.hasNext && args1Iterator.hasNext && args2Iterator.hasNext) {
      val tp = parametersIterator.next()
      val (lhs, rhs) = (args1Iterator.next(), args2Iterator.next())
      tp match {
        case scp: ScTypeParam if scp.isContravariant && !checkEquivalence =>
          val y = conformsInner(rhs, lhs, HashSet.empty, constraints)
          if (y.isLeft) return ConstraintsResult.Left
          else constraints = y.constraints
        case scp: ScTypeParam if scp.isCovariant && !checkEquivalence =>
          val y = conformsInner(lhs, rhs, HashSet.empty, constraints)
          if (y.isLeft) return ConstraintsResult.Left
          else constraints = y.constraints
        //this case filter out such cases like undefined type
        case _ =>
          (lhs, rhs) match {
            case (UndefinedType(typeParameter, _), rt) =>
              val y = addParam(typeParameter, rt, constraints)
              if (y.isLeft) return ConstraintsResult.Left
              constraints = y.constraints
            case (lt, UndefinedType(typeParameter, _)) =>
              val y = addParam(typeParameter, lt, constraints)
              if (y.isLeft) return ConstraintsResult.Left
              constraints = y.constraints
            case (ScAbstractType(_, lower, upper), right) =>
              if (!addAbstract(upper, lower, right))
                return ConstraintsResult.Left
            case (left, ScAbstractType(_, lower, upper)) =>
              if (!addAbstract(upper, lower, left))
                return ConstraintsResult.Left
            case _ =>
              val t = lhs.equiv(rhs, constraints, falseUndef = false)
              if (t.isLeft) return ConstraintsResult.Left
              constraints = t.constraints
          }
      }
    }
    constraints
  }

  private class LeftConformanceVisitor(key: Key, visited: Set[PsiClass]) extends ScalaTypeVisitor {

    private val Key(l, r, checkWeak) = key

    private implicit val projectContext: ProjectContext = l.projectContext

    private def addBounds(typeParameter: TypeParameter, `type`: ScType): Unit = {
      val name = typeParameter.typeParamId
      constraints = constraints
        .withLower(name, `type`, variance = Invariant)
        .withUpper(name, `type`, variance = Invariant)
    }

    def checkArrayArgs(leftArg: ScType, rightArg: ScType): ConstraintsResult = {

      (leftArg, rightArg) match {
        case (ScAbstractType(_, lower, upper), right) =>
          if (!upper.equiv(Any)) {
            val t = conformsInner(upper, right, visited, constraints, checkWeak)
            if (t.isLeft) {
              return ConstraintsResult.Left
            }
            constraints = t.constraints
          }
          if (!lower.equiv(Nothing)) {
            val t = conformsInner(right, lower, visited, constraints, checkWeak)
            if (t.isLeft) {
              return ConstraintsResult.Left
            }
            constraints = t.constraints
          }
        case (left, ScAbstractType(_, lower, upper)) =>
          if (!upper.equiv(Any)) {
            val t = conformsInner(upper, left, visited, constraints, checkWeak)
            if (t.isLeft) {
              return ConstraintsResult.Left
            }
            constraints = t.constraints
          }
          if (!lower.equiv(Nothing)) {
            val t = conformsInner(left, lower, visited, constraints, checkWeak)
            if (t.isLeft) {
              return ConstraintsResult.Left
            }
            constraints = t.constraints
          }
        case (UndefinedType(typeParameter, _), rt) => addBounds(typeParameter, rt)
        case (lt, UndefinedType(typeParameter, _)) => addBounds(typeParameter, lt)
        case _ =>
          val t = leftArg.equiv(rightArg, constraints, falseUndef = false)
          if (t.isLeft) {
            return ConstraintsResult.Left

          }
          constraints = t.constraints
      }
      constraints
    }


    /*
      Different checks from right type in order of appearence.
      todo: It's seems it's possible to check order and simplify code in many places.
     */
    trait ValDesignatorSimplification extends ScalaTypeVisitor {
      override def visitDesignatorType(d: ScDesignatorType): Unit = {
        d.getValType match {
          case Some(v) =>
            result = conformsInner(l, v, visited, constraints, checkWeak)
          case _ =>
        }
      }
    }

    trait LiteralTypeWideningVisitor extends ScalaTypeVisitor {
      override def visitLiteralType(lit: ScLiteralType): Unit =
        result =
          if (l eq Singleton) constraints
          else                conformsInner(l, lit.wideType, visited, constraints, checkWeak)
    }

    trait UndefinedSubstVisitor extends ScalaTypeVisitor {
      override def visitUndefinedType(u: UndefinedType): Unit = l match {
        case HKAbstract() => result = constraints
        case _            => result = constraints.withUpper(u.typeParameter.typeParamId, l)
      }
    }

    trait AbstractVisitor extends ScalaTypeVisitor {
      override def visitAbstractType(a: ScAbstractType): Unit = {
        if (!a.lower.equiv(Nothing)) {
          result = conformsInner(l, a.lower, visited, constraints, checkWeak)
        } else {
          result = constraints
        }
        if (result.isRight && !a.upper.equiv(Any)) {
          val t = conformsInner(a.upper, l, visited, result.constraints, checkWeak)
          if (t.isRight) result = t //this is optionally
        }
      }
    }

    trait ParameterizedAbstractVisitor extends ScalaTypeVisitor {
      override def visitParameterizedType(p: ParameterizedType): Unit = {
        p.designator match {
          case ScAbstractType(typeParameter, lowerBound, _) =>
            val subst = ScSubstitutor.bind(typeParameter.typeParameters, p.typeArguments)
            val lower: ScType =
              subst(lowerBound) match {
                case ParameterizedType(lower, _) => ScParameterizedType(lower, p.typeArguments)
                case lower => ScParameterizedType(lower, p.typeArguments)
              }
            if (!lower.equiv(Nothing)) {
              result = conformsInner(l, lower, visited, constraints, checkWeak)
            }
          case _ =>
        }
      }
    }

    private def checkEquiv(): Unit = {
      val equiv = l.equiv(r, constraints)
      if (equiv.isRight) result = equiv
    }

    trait ExistentialSimplification extends ScalaTypeVisitor {
      override def visitExistentialType(e: ScExistentialType): Unit = {
        val simplified = e.simplify()
        if (simplified != r) result = conformsInner(l, simplified, visited, constraints, checkWeak)
      }
    }

    trait ExistentialArgumentVisitor extends ScalaTypeVisitor {
      override def visitExistentialArgument(s: ScExistentialArgument): Unit = {
        result = conformsInner(l, s.upper, HashSet.empty, constraints)
      }
    }

    trait ParameterizedExistentialArgumentVisitor extends ScalaTypeVisitor {
      override def visitParameterizedType(p: ParameterizedType): Unit = {
        p.designator match {
          case s: ScExistentialArgument =>
            s.upper match {
              case ParameterizedType(upper, _) =>
                result = conformsInner(l, upper, visited, constraints, checkWeak)
              case upper =>
                result = conformsInner(l, upper, visited, constraints, checkWeak)
            }
          case _ =>
        }
      }
    }

    trait OtherNonvalueTypesVisitor extends ScalaTypeVisitor {
      override def visitUndefinedType(u: UndefinedType): Unit = {
        result = ConstraintsResult.Left
      }

      override def visitMethodType(m: ScMethodType): Unit = {
        result = ConstraintsResult.Left
      }

      override def visitAbstractType(a: ScAbstractType): Unit = {
        result = ConstraintsResult.Left
      }

      override def visitTypePolymorphicType(t: ScTypePolymorphicType): Unit = {
        result = ConstraintsResult.Left
      }
    }

    trait NothingNullVisitor extends ScalaTypeVisitor {
      override def visitLiteralType(lt: ScLiteralType): Unit = {
        if (lt.wideType.eq(Null) && l.conforms(AnyRef)) result = constraints
      }

      override def visitStdType(x: StdType): Unit = {
        if (x eq Nothing) result = constraints
        else if (x eq Null) {
          /*
            this case for checking: val x: T = null
            This is good if T class type: T <: AnyRef and !(T <: NotNull)
           */
          if (!l.conforms(AnyRef)) {
            result = ConstraintsResult.Left
            return
          }
          l.extractDesignated(expandAliases = false) match {
            case Some(el) =>
              val flag =
                el.elementScope.getCachedClass("scala.NotNull")
                  .map(ScDesignatorType(_))
                  .exists(l.conforms(_))

              result = // todo: think about constraints
                if (!flag) constraints
                else ConstraintsResult.Left
            case _ => result = constraints
          }
        }
      }
    }

    trait TypeParameterTypeVisitor extends ScalaTypeVisitor {
      override def visitTypeParameterType(tpt: TypeParameterType): Unit = {
        result = conformsInner(l, tpt.upperType, constraints = constraints)
      }
    }

    trait ThisVisitor extends ScalaTypeVisitor {
      override def visitThisType(t: ScThisType): Unit = {
        if (l eq Singleton) {
          result = constraints
          return
        }

        result = t.element.getTypeWithProjections() match {
          case Right(value) => conformsInner(l, value, visited, constraints, checkWeak)
          case _            => ConstraintsResult.Left
        }

        if (result.isLeft) {
          result = t.element.selfType match {
            case Some(selfTp) => conformsInner(l, selfTp, visited, constraints, checkWeak)
            case _            => result
          }
        }
      }
    }

    trait DesignatorVisitor extends ScalaTypeVisitor {
      override def visitDesignatorType(d: ScDesignatorType): Unit = {
        if ((l eq Singleton) && d.isSingleton) {
          result = constraints
          return
        }

        val maybeType = d.element match {
          case v: ScBindingPattern => v.`type`()
          case v: ScParameter      => v.`type`()
          case v: ScFieldId        => v.`type`()
          case _                   => return
        }

        result = maybeType match {
          case Right(value) => conformsInner(l, value, visited, constraints)
          case _            => ConstraintsResult.Left
        }
      }
    }

    trait ParameterizedAliasVisitor extends ScalaTypeVisitor {
      override def visitParameterizedType(p: ParameterizedType): Unit = {
        p match {
          case AliasType(_, _, upper) =>
            result = upper match {
              case Right(value) => conformsInner(l, value, visited, constraints)
              case _            => ConstraintsResult.Left
            }
          case _ =>
        }
      }
    }

    trait AliasDesignatorVisitor extends ScalaTypeVisitor {
      def stopDesignatorAliasOnFailure: Boolean = false

      override def visitDesignatorType(des: ScDesignatorType): Unit = {
        des match {
          case AliasType(_, _, Right(value)) =>
            val res = conformsInner(l, value, visited, constraints)
            if (stopDesignatorAliasOnFailure || res.isRight) result = res
          case _ =>
        }
      }
    }

    trait CompoundTypeVisitor extends ScalaTypeVisitor {
      override def visitCompoundType(c: ScCompoundType): Unit = {
        val comps = c.components
        var results = Set[ConstraintSystem]()
        def traverse(check: (ScType, ConstraintSystem) => ConstraintsResult): Unit = {
          val iterator = comps.iterator
          while (iterator.hasNext) {
            val comp = iterator.next()
            val t = check(comp, constraints)
            if (t.isRight) {
              results = results + t.constraints
            }
          }
        }
        traverse(typeSystem.equivInner(l, _, _))
        if (results.isEmpty) {
          traverse(conformsInner(l, _, HashSet.empty, _))
        }

        if (results.size == 1) {
          result = results.head
          return
        } else if (results.size > 1) {
          result = ConstraintSystem(results)
          return
        }

        result = l match {
          case AliasType(_: ScTypeAliasDefinition, Right(lower), _) =>
            conformsInner(lower, c, HashSet.empty, constraints)
          case _ => ConstraintsResult.Left
        }
      }
    }

    trait ExistentialVisitor extends ScalaTypeVisitor {
      override def visitExistentialType(ex: ScExistentialType): Unit = {
        result = conformsInner(l, ex.quantified, HashSet.empty, constraints)
      }
    }

    trait ProjectionVisitor extends ScalaTypeVisitor {
      def stopProjectionAliasOnFailure: Boolean = false

      override def visitProjectionType(proj2: ScProjectionType): Unit = {
        if ((l eq Singleton) && proj2.isSingleton) {
          result = constraints
          return
        }

        proj2 match {
          case AliasType(_, _, Left(_)) =>
          case AliasType(_, _, Right(value)) =>
            val res = conformsInner(l, value, visited, constraints)
            if (stopProjectionAliasOnFailure || res.isRight) result = res
          case _ =>
            l match {
              case proj1: ScProjectionType if smartEquivalence(proj1.actualElement, proj2.actualElement) =>
                val projected1 = proj1.projected
                val projected2 = proj2.projected
                result = conformsInner(projected1, projected2, visited, constraints)
              case _ =>
                val res = proj2.actualElement match {
                  case syntheticClass: ScSyntheticClass =>
                    result = conformsInner(l, syntheticClass.stdType, HashSet.empty, constraints)
                    return
                  case v: ScBindingPattern => v.`type`()
                  case v: ScParameter      => v.`type`()
                  case v: ScFieldId        => v.`type`()
                  case _                   => return
                }

                result = res match {
                  case Right(value) => conformsInner(l, proj2.actualSubst(value), visited, constraints)
                  case _            => ConstraintsResult.Left
                }
            }
        }
      }
    }

    private var result: ConstraintsResult = _
    private var constraints: ConstraintSystem = ConstraintSystem.empty

    def getResult: ConstraintsResult = result

    override def visitStdType(x: StdType): Unit = {
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
            result = constraints
            return
          case (Short, Int | Long | Float | Double) =>
            result = constraints
            return
          case (Char, Byte | Short | Int | Long | Float | Double) =>
            result = constraints
            return
          case (Int, Long | Float | Double) =>
            result = constraints
            return
          case (Long, Float | Double) =>
            result = constraints
            return
          case (Float, Double) =>
            result = constraints
            return
          case _ =>
        }
      }

      if (x eq Any) {
        result = constraints
        return
      }

      if (x == Nothing && r == Null) {
        result = ConstraintsResult.Left
        return
      }

      rightVisitor = new NothingNullVisitor with TypeParameterTypeVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ThisVisitor with DesignatorVisitor with ParameterizedAliasVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new AliasDesignatorVisitor with CompoundTypeVisitor with ExistentialVisitor
        with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new LiteralTypeWideningVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      if (x eq Null) {
        result = if (r.isNothing) constraints else ConstraintsResult.Left
        return
      }

      if (x eq AnyRef) {
        if (r eq Any) {
          result = ConstraintsResult.Left
          return
        }
        else if (r eq AnyVal) {
          result = ConstraintsResult.Left
          return
        }
        else if (r.isInstanceOf[ScLiteralType]) {
          result = ConstraintsResult.Left
          return
        }
        else if (r.isInstanceOf[ValType]) {
          result = ConstraintsResult.Left
          return
        }
        else if (!r.isInstanceOf[ScExistentialType]) {
          rightVisitor = new AliasDesignatorVisitor with ProjectionVisitor {
            override def stopProjectionAliasOnFailure: Boolean = true

            override def stopDesignatorAliasOnFailure: Boolean = true
          }
          r.visitType(rightVisitor)
          if (result != null) return
          result = constraints
          return
        }
      }

      if (x eq Singleton) {
        /** Conformance is checked in corresponding rightVisitors
         * [[ThisVisitor]], [[LiteralTypeWideningVisitor]],
         * [[ProjectionVisitor]] and [[DesignatorVisitor]] */
        result = ConstraintsResult.Left
        return
      }

      if (x eq AnyVal) {
        result =
          if (r.isInstanceOf[ValType] || ValueClassType.isValueType(r)) constraints
          else ConstraintsResult.Left
        return
      }
      if (l.isInstanceOf[ValType] && r.isInstanceOf[ValType]) {
        result = ConstraintsResult.Left
        return
      }
    }

    override def visitCompoundType(c: ScCompoundType): Unit = {
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

      rightVisitor = new ParameterizedAliasVisitor with AliasDesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      /*If T<:Ui for i=1,...,n and for every binding d of a type or value x in R there exists a member binding
      of x in T which subsumes d, then T conforms to the compound type	U1	with	. . .	with	Un	{R }.

      U1	with	. . .	with	Un	{R } === t1
      T                             === t2
      U1	with	. . .	with	Un       === comps1
      Un                            === compn*/
      def workWithSignature(s: TermSignature, retType: ScType): Boolean = {
        val processor = new CompoundTypeCheckSignatureProcessor(s,retType, constraints)
        processor.processType(r, s.namedElement)
        constraints = processor.getConstraints
        processor.getResult
      }

      def workWithTypeAlias(sign: TypeAliasSignature): Boolean = {
        val singletonSubst = r match {
          case ScDesignatorType(_: ScParameter | _: ScFieldId | _: ScBindingPattern) => ScSubstitutor(r)
          case _                                                                     => ScSubstitutor.empty
        }

        val processor = new CompoundTypeCheckTypeAliasProcessor(sign, constraints, singletonSubst)
        processor.processType(r, sign.typeAlias)
        constraints = processor.getConstraints
        processor.getResult
      }

      val isSuccess = c.components.forall(comp => {
        val t = conformsInner(comp, r, HashSet.empty, constraints)
        constraints = t.constraints
        t.isRight
      }) && c.signatureMap.forall {
        case (s: TermSignature, retType) => workWithSignature(s, retType)
      } && c.typesMap.forall {
        case (_, sign) => workWithTypeAlias(sign)
      }

      result = if (isSuccess) constraints else ConstraintsResult.Left
    }

    override def visitProjectionType(proj: ScProjectionType): Unit = {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with ExistentialArgumentVisitor
        with ParameterizedExistentialArgumentVisitor with NothingNullVisitor
        with TypeParameterTypeVisitor with ThisVisitor with ParameterizedAliasVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ParameterizedAliasVisitor with AliasDesignatorVisitor with CompoundTypeVisitor with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      r match {
        case proj1: ScProjectionType if smartEquivalence(proj1.actualElement, proj.actualElement) =>
          val projected1 = proj.projected
          val projected2 = proj1.projected
          result = conformsInner(projected1, projected2, visited, constraints)
          if (result != null) return
        case proj1: ScProjectionType if proj1.actualElement.name == proj.actualElement.name =>
          val projected1 = proj.projected
          val projected2 = proj1.projected
          val t = conformsInner(projected1, projected2, visited, constraints)
          if (t.isRight) {
            result = t
            return
          }
        case _ =>
      }

      rightVisitor = new LiteralTypeWideningVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      proj match {
        case AliasType(_, Right(lower), _) =>
          val conforms = conformsInner(lower, r, visited, constraints)
          if (conforms.isRight) result = conforms
        case _ =>
          rightVisitor = new ExistentialVisitor {}
          r.visitType(rightVisitor)
          if (result != null) return
      }

      if (result ne null) return
      rightVisitor = new OtherNonvalueTypesVisitor {}
      r.visitType(rightVisitor)

      if (result != null) return
      rightVisitor = new DesignatorVisitor {}
      r.visitType(rightVisitor)
    }

    override def visitLiteralType(l: ScLiteralType): Unit = {
      val rightVisitor: ScalaTypeVisitor = new UndefinedSubstVisitor(){}

      r.visitType(rightVisitor)

      if (result != null) return

      checkEquiv()
    }

    override def visitJavaArrayType(a1: JavaArrayType): Unit = {
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
        with TypeParameterTypeVisitor with ThisVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ParameterizedAliasVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      r match {
        case JavaArrayType(arg2) =>
          result = checkArrayArgs(arg1, arg2)
          return
        case ScalaArray(arg2) =>
          result = checkArrayArgs(arg1, arg2)
          return
        case _ =>
      }

      rightVisitor = new AliasDesignatorVisitor with CompoundTypeVisitor with ExistentialVisitor
        with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new DesignatorVisitor {}
      r.visitType(rightVisitor)
    }

    override def visitParameterizedType(p: ParameterizedType): Unit = {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      p.designator match {
        case a: ScAbstractType =>
          val subst = ScSubstitutor.bind(a.typeParameter.typeParameters, p.typeArguments)
          val upper: ScType =
            subst(a.upper) match {
              case up if up.equiv(Any)      => ScParameterizedType(WildcardType(a.typeParameter), p.typeArguments)
              case ParameterizedType(up, _) => ScParameterizedType(up, p.typeArguments)
              case up                       => ScParameterizedType(up, p.typeArguments)
            }
          if (!upper.equiv(Any)) {
            result = conformsInner(upper, r, visited, constraints, checkWeak)
          } else {
            result = constraints
          }
          if (result.isRight) {
            val lower: ScType =
              subst(a.lower) match {
                case low if low.equiv(Nothing) => ScParameterizedType(WildcardType(a.typeParameter), p.typeArguments)
                case ParameterizedType(low, _) => ScParameterizedType(low, p.typeArguments)
                case low                       => ScParameterizedType(low, p.typeArguments)
              }
            if (!lower.equiv(Nothing)) {
              val t = conformsInner(r, lower, visited, result.constraints, checkWeak)
              if (t.isRight) result = t
            }
          }
          return
        case _ =>
      }

      rightVisitor = new ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result ne null) return

      rightVisitor = new ExistentialSimplification with ExistentialArgumentVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      p.designator match {
        case s: ScExistentialArgument =>
          s.lower match {
            case ParameterizedType(lower, _) =>
              result = conformsInner(lower, r, visited, constraints, checkWeak)
              return
            case lower =>
              result = conformsInner(lower, r, visited, constraints, checkWeak)
              return
          }
        case _ =>
      }

      rightVisitor = new ParameterizedExistentialArgumentVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor
         with ThisVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      r match {
        case ScalaArray(rightArg) =>
          p match {
            case ScalaArray(leftArg) =>
              result = checkArrayArgs(leftArg, rightArg)
            case _ =>
          }
        case p2: ScParameterizedType =>
          val des1 = p.designator
          val des2 = p2.designator
          val args1 = p.typeArguments
          val args2 = p2.typeArguments
          (des1, des2) match {
            case (lhs: TypeParameterType, rhs: TypeParameterType) =>
              if (des1.equiv(des2)) {
                if (args1.length != args2.length) {
                  result = ConstraintsResult.Left
                } else {
                  result = checkParameterizedType(lhs.typeParameters.map(_.psiTypeParameter).iterator, args1, args2,
                    constraints, visited, checkWeak)
                }
              } else result = retryTypeParamsConformance(lhs, rhs, l, r, constraints)
            case (UndefinedOrWildcard(_, _), UndefinedOrWildcard(typeParameter, addBound)) =>
              if (TypeVariableUnification.unifiableKinds(p, p2)) {
                if (addBound) constraints = constraints.withUpper(typeParameter.typeParamId, des1)

                result = checkParameterizedType(
                  typeParameter.typeParameters.map(_.psiTypeParameter).iterator,
                  args1, args2, constraints,
                  visited, checkWeak
                )
              } else result = ConstraintsResult.Left
            case (UndefinedOrWildcard(_, _), _) => result = unifyHK(p, p2, constraints, Bound.Lower, visited, checkWeak)
            case (_, UndefinedOrWildcard(_, _)) => result = unifyHK(p2, p, constraints, Bound.Upper, visited, checkWeak)
            case _ if des1 equiv des2 =>
              result =
                if (args1.length != args2.length) ConstraintsResult.Left
                else extractParams(des1) match {
                  case Some(params) => checkParameterizedType(params, args1, args2, constraints, visited, checkWeak)
                  case _            => ConstraintsResult.Left
                }
            case (_, t: TypeParameterType) if t.typeParameters.length == p2.typeArguments.length =>
              val subst = ScSubstitutor.bind(t.typeParameters, p.typeArguments)
              result = conformsInner(des1, subst(t.upperType), visited, constraints, checkWeak)
            case (proj1: ScProjectionType, proj2: ScProjectionType)
              if smartEquivalence(proj1.actualElement, proj2.actualElement) =>
              val t = conformsInner(proj1, proj2, visited, constraints)
              if (t.isLeft) {
                result = ConstraintsResult.Left
              } else {
                constraints = t.constraints
                if (args1.length != args2.length) {
                  result = ConstraintsResult.Left
                } else {
                  proj1.actualElement match {
                    case td: ScTypeParametersOwner =>
                      result = checkParameterizedType(td.typeParameters.iterator, args1, args2, constraints, visited, checkWeak)
                    case td: PsiTypeParameterListOwner =>
                      result = checkParameterizedType(td.getTypeParameters.iterator, args1, args2, constraints, visited, checkWeak)
                    case _ =>
                      result = ConstraintsResult.Left
                  }
                }
              }
            case _ =>
          }
        case _ =>
      }

      if (result != null) {
        //sometimes when the above part has failed, we still have to check for alias
        if (!result.isRight && r.isAliasType) {
          rightVisitor = new ParameterizedAliasVisitor with TypeParameterTypeVisitor {}
          r.visitType(rightVisitor)
        }

        return
      }

      rightVisitor = new ParameterizedAliasVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      r match {
        case JavaArrayType(rightArg) =>
          p match {
            case ScalaArray(leftArg) =>
              result = checkArrayArgs(leftArg, rightArg)
            case _ =>
          }
        case _ =>
      }

      if (result != null) return

      rightVisitor = new AliasDesignatorVisitor with CompoundTypeVisitor with ExistentialVisitor
        with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      p match {
        case AliasType(_, lower, _) =>
          result = lower match {
            case Right(value) => conformsInner(value, r, visited, constraints)
            case _            => ConstraintsResult.Left
          }
          return
        case _ =>
      }

      rightVisitor = new DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new TypeParameterTypeVisitor {}
      r.visitType(rightVisitor)
    }

    override def visitExistentialType(e: ScExistentialType): Unit = {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with LiteralTypeWideningVisitor with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      rightVisitor = new ExistentialSimplification with ExistentialArgumentVisitor
        with ParameterizedExistentialArgumentVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor
         with TypeParameterTypeVisitor with ThisVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ParameterizedAliasVisitor with AliasDesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      val (updatedWithUndefinedTypes, undefines) = {
        val remapper = new ExistentialArgumentsToTypeParameters(e.wildcards, UndefinedType(_))
        (remapper.remapExistentials(e.quantified), remapper.remapped)
      }

      val skolemizeExistentialsOnTheRight = r match {
        case etpe: ScExistentialType =>
          new ExistentialArgumentsToTypeParameters(etpe.wildcards, TypeParameterType(_))
            .remapExistentials(etpe.quantified)
        case t => t
      }

      conformsInner(updatedWithUndefinedTypes, skolemizeExistentialsOnTheRight, HashSet.empty, constraints) match {
        case unSubst @ ConstraintSystem(solvingSubstitutor) =>
          for (un <- undefines if result == null) {
            val solvedType = solvingSubstitutor(un)

            val lowerBoundSubsted = solvingSubstitutor(un.typeParameter.lowerType.unpackedType)
            var t = conformsInner(solvedType, lowerBoundSubsted, constraints = constraints)
            if (solvedType != un && t.isLeft) {
              result = ConstraintsResult.Left
              return
            }

            constraints = t.constraints
            val upperBoundSubsted = solvingSubstitutor(un.typeParameter.upperType.unpackedType)
            t = conformsInner(upperBoundSubsted, solvedType, constraints = constraints)

            if (solvedType != un && t.isLeft) {
              result = ConstraintsResult.Left
              return
            }
            constraints = t.constraints
          }

          if (result == null) {
            //ignore undefined types from existential arguments
            val typeParamIds = undefines
              .map(_.typeParameter.typeParamId)
              .toSet

            constraints += unSubst.removeTypeParamIds(typeParamIds)
            result = constraints
          }
        case _ => result = ConstraintsResult.Left
      }
      if (result != null) return

      rightVisitor = new DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      val simplified = e.simplify()
      if (simplified != l) {
        result = conformsInner(simplified, r, visited, constraints, checkWeak)
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
        case Right(value) => conformsInner(value, r, visited, constraints, checkWeak)
        case _ => ConstraintsResult.Left
      }
    }

    override def visitDesignatorType(des: ScDesignatorType): Unit = {
      des.getValType match {
        case Some(v) =>
          result = conformsInner(v, r, visited, constraints, checkWeak)
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
        with ThisVisitor with ParameterizedAliasVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new AliasDesignatorVisitor with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      des match {
        case AliasType(_, lower, _) =>
          result = lower match {
            case Right(value) => conformsInner(value, r, visited, constraints)
            case _            => ConstraintsResult.Left
          }
        case _ =>
          rightVisitor = new CompoundTypeVisitor with ExistentialVisitor {}
          r.visitType(rightVisitor)
          if (result != null) return
      }
      if (result != null) return

      rightVisitor = new DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new LiteralTypeWideningVisitor {}
      r.visitType(rightVisitor)
    }

    override def visitTypeParameterType(tpt1: TypeParameterType): Unit = {
      var rightVisitor: ScalaTypeVisitor =
        new ValDesignatorSimplification with UndefinedSubstVisitor with AbstractVisitor
          with ParameterizedAbstractVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      checkEquiv()
      if (result != null) return

      trait TypeParameterTypeNothingNullVisitor extends NothingNullVisitor {
        override def visitStdType(x: StdType): Unit = {
          if (x eq Nothing) result = constraints
          else if (x eq Null) {
            result = conformsInner(tpt1.lowerType, r, HashSet.empty, constraints)
          }
        }
      }

      rightVisitor = new ExistentialSimplification with ExistentialArgumentVisitor
        with ParameterizedExistentialArgumentVisitor with OtherNonvalueTypesVisitor with TypeParameterTypeNothingNullVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      r match {
        case tpt2: TypeParameterType =>
          val res = conformsInner(tpt1.lowerType, r, HashSet.empty, constraints)
          if (res.isRight) {
            result = res
            return
          }
          result = conformsInner(l, tpt2.upperType, HashSet.empty, constraints)
          return
        case _ =>
      }

      val t = conformsInner(tpt1.lowerType, r, HashSet.empty, constraints)
      if (t.isRight) {
        result = t
        return
      }

      rightVisitor = new ParameterizedAliasVisitor with AliasDesignatorVisitor with CompoundTypeVisitor
        with ExistentialVisitor with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      result = ConstraintsResult.Left
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

      r match {
        case tpt2: ScExistentialArgument =>
          val res = conformsInner(s.lower, r, HashSet.empty, constraints)
          if (res.isRight) {
            result = res
            return
          }
          result = conformsInner(l, tpt2.upper, HashSet.empty, constraints)
          return
        case _ =>
      }

      val t = conformsInner(s.lower, r, HashSet.empty, constraints)

      if (t.isRight) {
        result = t.constraints
        return
      }

      rightVisitor = new OtherNonvalueTypesVisitor with NothingNullVisitor
        with TypeParameterTypeVisitor with ThisVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ParameterizedAliasVisitor with AliasDesignatorVisitor with CompoundTypeVisitor
        with ExistentialVisitor with ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new DesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return
    }

    override def visitUndefinedType(u: UndefinedType): Unit = {
      val rightVisitor = new ValDesignatorSimplification {
        override def visitUndefinedType(u2: UndefinedType): Unit = {
          val name = u2.typeParameter.typeParamId
          result = if (u2.level > u.level) {
            constraints.withUpper(name, u)
          } else if (u.level > u2.level) {
            constraints.withUpper(name, u)
          } else {
            constraints
          }
        }
      }
      r.visitType(rightVisitor)
      if (result == null) {
        r match {
          case lit: ScLiteralType if lit.allowWiden && !u.typeParameter.upperType.conforms(Singleton) =>
            result = conformsInner(l, lit.wideType, visited, constraints, checkWeak)
          case lit: ScLiteralType =>
            result = constraints.withLower(u.typeParameter.typeParamId, lit.blockWiden)
          case _ =>
            result = constraints.withLower(u.typeParameter.typeParamId, r)
        }
      }
    }

    override def visitMethodType(m1: ScMethodType): Unit = {
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
          val returnType1 = m1.result
          val returnType2 = m2.result
          if (params1.length != params2.length) {
            result = ConstraintsResult.Left
            return
          }
          var t = conformsInner(returnType1, returnType2, HashSet.empty, constraints)
          if (t.isLeft) {
            result = ConstraintsResult.Left
            return
          }
          constraints = t.constraints
          var i = 0
          while (i < params1.length) {
            if (params1(i).isRepeated != params2(i).isRepeated) {
              result = ConstraintsResult.Left
              return
            }
            t = params1(i).paramType.equiv(params2(i).paramType, constraints, falseUndef = false)
            if (t.isLeft) {
              result = ConstraintsResult.Left
              return
            }
            constraints = t.constraints
            i = i + 1
          }
          result = constraints
        case _ =>
          result = ConstraintsResult.Left
      }
    }

    override def visitAbstractType(a: ScAbstractType): Unit = {
      val rightVisitor = new ValDesignatorSimplification with UndefinedSubstVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      result = conformsInner(a.upper, r, visited, constraints, checkWeak)
      if (result.isRight) {
        val t = conformsInner(r, a.lower, visited, result.constraints, checkWeak)
        if (t.isRight) result = t
      }
    }

    override def visitTypePolymorphicType(t1: ScTypePolymorphicType): Unit = {
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

      rightVisitor = new ProjectionVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      r match {
        case t2: ScTypePolymorphicType =>
          val typeParameters1 = t1.typeParameters
          val typeParameters2 = t2.typeParameters
          val internalType1 = t1.internalType
          val internalType2 = t2.internalType
          if (typeParameters1.length != typeParameters2.length) {
            result = ConstraintsResult.Left
            return
          }
          var i = 0
          while (i < typeParameters1.length) {
            var t = conformsInner(typeParameters1(i).lowerType, typeParameters2(i).lowerType, HashSet.empty, constraints)
            if (t.isLeft) {
              result = ConstraintsResult.Left
              return
            }
            constraints = t.constraints
            t = conformsInner(typeParameters2(i).upperType, typeParameters1(i).upperType, HashSet.empty, constraints)
            if (t.isLeft) {
              result = ConstraintsResult.Left
              return
            }
            constraints = t.constraints
            i = i + 1
          }
          val subst = ScSubstitutor.bind(typeParameters1, typeParameters2)(TypeParameterType(_))
          val t = conformsInner(subst(internalType1), internalType2, HashSet.empty, constraints)
          if (t.isLeft) {
            result = ConstraintsResult.Left
            return
          }
          constraints = t.constraints
          result = constraints
        case _ =>
          result = ConstraintsResult.Left
      }
    }
  }
}

private object ScalaConformance {
  private[psi] object HKAbstract {
    def unapply(tpe: ParameterizedType): Boolean = tpe match {
      case ParameterizedType(abs: ScAbstractType, tArgs) =>
        import abs.projectContext
        abs.upper.equiv(Any) && tArgs.forall {
          case ScAbstractType(_, lower, upper) => lower.equiv(Nothing) && upper.equiv(Any)
          case _                               => false
        }
      case _ => false
    }
  }

  private[psi] def addParam(
    typeParameter: TypeParameter,
    bound:         ScType,
    constraints:   ConstraintSystem
  ): ConstraintSystem =
    bound match {
      case HKAbstract() => constraints
      case _ =>
        constraints
          .withUpper(typeParameter.typeParamId, bound, variance = Invariant)
          .withLower(typeParameter.typeParamId, bound, variance = Invariant)
    }

  private[psi] def extractParams(des: ScType): Option[Iterator[PsiTypeParameter]] =
    des match {
      case undef: UndefinedType =>
        Option(undef.typeParameter.psiTypeParameter).map(_.getTypeParameters.iterator)
      case tpt: TypeParameterType => Option(tpt.typeParameters.map(_.psiTypeParameter).iterator)
      case _ =>
        des.extractDesignated(false).map {
          case ta: ScTypeAlias      => ta.typeParameters.iterator
          case td: ScTypeDefinition => td.typeParameters.iterator
          case cls: PsiClass        => cls.getTypeParameters.iterator
          case _                    => Iterator.empty
        }
    }


  private[types] object UndefinedOrWildcard {
    def unapply(tpe: NonValueType): Option[(TypeParameter, Boolean)] = tpe match {
      case UndefinedType(tparam, _) => Option((tparam, true))
      case WildcardType(tparam)     => Option((tparam, false))
      case _                        => None
    }
  }

  private object ScalaArray {
    def unapply(p: ParameterizedType): Option[ScType] = p match {
      case ParameterizedType(ExtractClass(ClassQualifiedName("scala.Array")), Seq(arg)) => Some(arg)
      case _ => None
    }
  }

  private[psi] sealed trait Bound
  private[psi] object Bound {
    case object Lower       extends Bound
    case object Upper       extends Bound
    case object Equivalence extends Bound
  }

  private class ExistentialArgumentsToTypeParameters[T <: ScType](
    exs:             Seq[ScExistentialArgument],
    typeParamToType: TypeParameter => T
  ) {
    private[this] lazy val remapExistentials: Map[ScExistentialArgument, T] =
      exs.map(
        ex =>
          ex -> typeParamToType(
            TypeParameter.deferred(
              ex.name,
              ex.typeParameters,
              () => remapExistentials(ex.lower),
              () => remapExistentials(ex.upper)
            )
          )
      )(collection.breakOut)

    def remapExistentials(tpe: ScType): ScType =
      tpe.recursiveUpdate {
        case arg: ScExistentialArgument => ReplaceWith(remapExistentials.getOrElse(arg, arg))
        case _: ScExistentialType       => Stop
        case _                          => ProcessSubtypes
      }


    def remapped: Seq[T] = remapExistentials.values.toSeq
  }
}