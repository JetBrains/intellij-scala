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
import org.jetbrains.plugins.scala.lang.psi.types.ScalaConformance.{ScalaArray, SmartInheritanceResult, UndefinedOrWildcard}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{NonValueType, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.processor.{CompoundTypeCheckSignatureProcessor, CompoundTypeCheckTypeAliasProcessor}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil._

import scala.annotation.tailrec
import scala.collection.Seq
import scala.collection.immutable.HashSet
import scala.collection.mutable.ArrayBuffer

trait ScalaConformance extends api.Conformance {
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
                val inh = smartIsInheritor(rClass, subst, lClass)
                if (inh.isFailure) return ConstraintsResult.Left
                val tp = inh.result
                //Special case for higher kind types passed to generics.
                if (lClass.hasTypeParameters) {
                  left.removeAliasDefinitions() match {
                    case _: ScParameterizedType =>
                    case _ => return ConstraintSystem.empty
                  }
                }
                return conformsInner(left, tp, visited + rClass)
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

  private def checkParameterizedType(parametersIterator: Iterator[PsiTypeParameter], args1: scala.Seq[ScType],
                                     args2: scala.Seq[ScType], _constraints: ConstraintSystem,
                                     visited: Set[PsiClass], checkWeak: Boolean): ConstraintsResult = {
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
      val argsPair = (args1Iterator.next(), args2Iterator.next())
      tp match {
        case scp: ScTypeParam if scp.isContravariant =>
          val y = conformsInner(argsPair._2, argsPair._1, HashSet.empty, constraints)
          if (y.isLeft) return ConstraintsResult.Left
          else constraints = y.constraints
        case scp: ScTypeParam if scp.isCovariant =>
          val y = conformsInner(argsPair._1, argsPair._2, HashSet.empty, constraints)
          if (y.isLeft) return ConstraintsResult.Left
          else constraints = y.constraints
        //this case filter out such cases like undefined type
        case _ =>
          argsPair match {
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
              val t = argsPair._1.equiv(argsPair._2, constraints, falseUndef = false)
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
      override def visitDesignatorType(d: ScDesignatorType) {
        d.getValType match {
          case Some(v) =>
            result = conformsInner(l, v, visited, constraints, checkWeak)
          case _ =>
        }
      }
    }

    trait LiteralTypeWideningVisitor extends ScalaTypeVisitor {
      override def visitLiteralType(lit: ScLiteralType): Unit = {
        result = if (l eq Singleton) constraints else conformsInner(l, lit.wideType, visited, constraints, checkWeak)
      }
    }

    trait UndefinedSubstVisitor extends ScalaTypeVisitor {
      override def visitUndefinedType(u: UndefinedType): Unit = l match {
        case ParameterizedType(abs: ScAbstractType, tArgs)
          if abs.upper.equiv(Any) && tArgs.forall {
            case ScAbstractType(_, lower, upper) => lower.equiv(Nothing) && upper.equiv(Any)
            case _                               => false
          } => result = constraints
        case _ => result = constraints.withUpper(u.typeParameter.typeParamId, l)
      }
    }

    trait AbstractVisitor extends ScalaTypeVisitor {
      override def visitAbstractType(a: ScAbstractType) {
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
      override def visitParameterizedType(p: ParameterizedType) {
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

    private def checkEquiv() {
      val equiv = l.equiv(r, constraints)
      if (equiv.isRight) result = equiv
    }

    trait ExistentialSimplification extends ScalaTypeVisitor {
      override def visitExistentialType(e: ScExistentialType) {
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
      override def visitParameterizedType(p: ParameterizedType) {
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
      override def visitUndefinedType(u: UndefinedType) {
        result = ConstraintsResult.Left
      }

      override def visitMethodType(m: ScMethodType) {
        result = ConstraintsResult.Left
      }

      override def visitAbstractType(a: ScAbstractType) {
        result = ConstraintsResult.Left
      }

      override def visitTypePolymorphicType(t: ScTypePolymorphicType) {
        result = ConstraintsResult.Left
      }
    }

    trait NothingNullVisitor extends ScalaTypeVisitor {
      override def visitLiteralType(lt: ScLiteralType): Unit = {
        if (lt.wideType.eq(Null) && l.conforms(AnyRef)) result = constraints
      }

      override def visitStdType(x: StdType) {
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
      override def visitTypeParameterType(tpt: TypeParameterType) {
        result = conformsInner(l, tpt.upperType, constraints = constraints)
      }
    }

    trait ThisVisitor extends ScalaTypeVisitor {
      override def visitThisType(t: ScThisType): Unit = {
        result = t.element.getTypeWithProjections() match {
          case Right(value) => conformsInner(l, value, visited, constraints, checkWeak)
          case _ => ConstraintsResult.Left
        }
      }
    }

    trait DesignatorVisitor extends ScalaTypeVisitor {
      override def visitDesignatorType(d: ScDesignatorType): Unit = {
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
        p.isAliasType match {
          case Some(AliasType(_, _, upper)) =>
            result = upper match {
              case Right(value) => conformsInner(l, value, visited, constraints)
              case _ => ConstraintsResult.Left
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
            val res = conformsInner(l, value, visited, constraints)
            if (stopDesignatorAliasOnFailure || res.isRight) result = res
          case _ =>
        }
      }
    }

    trait CompoundTypeVisitor extends ScalaTypeVisitor {
      override def visitCompoundType(c: ScCompoundType) {
        val comps = c.components
        var results = Set[ConstraintSystem]()
        def traverse(check: (ScType, ConstraintSystem) => ConstraintsResult) = {
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

        result = l.isAliasType match {
          case Some(AliasType(_: ScTypeAliasDefinition, Right(lower), _)) =>
            conformsInner(lower, c, HashSet.empty, constraints)
          case _ => ConstraintsResult.Left
        }
      }
    }

    trait ExistentialVisitor extends ScalaTypeVisitor {
      override def visitExistentialType(ex: ScExistentialType) {
        result = conformsInner(l, ex.quantified, HashSet.empty, constraints)
      }
    }

    trait ProjectionVisitor extends ScalaTypeVisitor {
      def stopProjectionAliasOnFailure: Boolean = false

      override def visitProjectionType(proj2: ScProjectionType): Unit = {
        proj2.isAliasType match {
          case Some(AliasType(_, _, Left(_))) =>
          case Some(AliasType(_, _, Right(value))) =>
            val res = conformsInner(l, value, visited, constraints)
            if (stopProjectionAliasOnFailure || res.isRight) result = res
          case _ =>
            l match {
            case proj1: ScProjectionType if smartEquivalence(proj1.actualElement, proj2.actualElement) =>
              val projected1 = proj1.projected
              val projected2 = proj2.projected
              result = conformsInner(projected1, projected2, visited, constraints)
            case ParameterizedType(projDes: ScProjectionType, typeArguments) =>
              //TODO this looks overcomplicated. Improve the code.
              def cutProj(p: ScType, acc: List[ScProjectionType]): ScType = {
                if (acc.isEmpty) p else acc.foldLeft(p){
                  case (proj, oldProj) => ScProjectionType(proj, oldProj.element)
                }
              }
              @tailrec
              def findProjectionBase(proj: ScProjectionType, acc: List[ScProjectionType] = List()): Unit = {
                val t = proj.projected.equiv(projDes.projected, constraints)
                if (t.isRight) {
                  constraints = t.constraints
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
                    case (left, right) => conformsInner(left, right, visited, constraints)
                  }.foreach {
                    case conformance if conformance.isRight => result = conformance
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
                  result = conformsInner(l, syntheticClass.stdType, HashSet.empty, constraints)
                  return
                case v: ScBindingPattern => v.`type`()
                case v: ScParameter => v.`type`()
                case v: ScFieldId => v.`type`()
                case _ => return
              }

              result = res match {
                case Right(value) => conformsInner(l, proj2.actualSubst(value), visited, constraints)
                case _ => ConstraintsResult.Left
              }
          }
        }
      }
    }

    private var result: ConstraintsResult = null
    private var constraints: ConstraintSystem = ConstraintSystem.empty

    def getResult: ConstraintsResult = result

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

      rightVisitor = new ThisVisitor with DesignatorVisitor
        with ParameterizedAliasVisitor {}
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
        result = ConstraintsResult.Left
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
        with TypeParameterTypeVisitor with ThisVisitor {}
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
        val processor = new CompoundTypeCheckSignatureProcessor(s,retType, constraints, s.substitutor)
        processor.processType(r, s.namedElement)
        constraints = processor.getConstraints
        processor.getResult
      }

      def workWithTypeAlias(sign: TypeAliasSignature): Boolean = {
        val singletonSubst = r match {
          case ScDesignatorType(_: ScParameter | _: ScFieldId | _: ScBindingPattern) => ScSubstitutor(r)
          case _                                                                     => ScSubstitutor.empty
        }

        val subst = sign.substitutor.followed(singletonSubst)
        val processor = new CompoundTypeCheckTypeAliasProcessor(sign, constraints, subst)
        processor.processType(r, sign.typeAlias)
        constraints = processor.getConstraints
        processor.getResult
      }

      val isSuccess = c.components.forall(comp => {
        val t = conformsInner(comp, r, HashSet.empty, constraints)
        constraints = t.constraints
        t.isRight
      }) && c.signatureMap.forall {
        case (s: Signature, retType) => workWithSignature(s, retType)
      } && c.typesMap.forall {
        case (_, sign) => workWithTypeAlias(sign)
      }

      result = if (isSuccess) constraints else ConstraintsResult.Left
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

      proj.isAliasType match {
        case Some(AliasType(_, Right(lower), _)) =>
          val conforms = conformsInner(lower, r, visited, constraints)
          if (conforms.isRight) result = conforms
        case _ =>
          rightVisitor = new ExistentialVisitor {}
          r.visitType(rightVisitor)
          if (result != null) return
      }

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

    override def visitParameterizedType(p: ParameterizedType) {
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
      if (result != null) return

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

      def processEquivalentDesignators(args2: Seq[ScType]): Unit = {
        val args1 = p.typeArguments
        val des1 = p.designator
        if (args1.length != args2.length) {
          result = ConstraintsResult.Left
          return
        }
        des1.extractDesignated(expandAliases = true) match {
          case Some(ownerDesignator) =>
            val parametersIterator = ownerDesignator match {
              case td: ScTypeParametersOwner => td.typeParameters.iterator
              case ownerDesignator: PsiTypeParameterListOwner => ownerDesignator.getTypeParameters.iterator
              case _ =>
                result = ConstraintsResult.Left
                return
            }
            result = checkParameterizedType(parametersIterator, args1, args2,
              constraints, visited, checkWeak)
          case _ =>
            result = ConstraintsResult.Left
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
            case Right(value) => conformsInner(value, r, visited, constraints)
            case _ => ConstraintsResult.Left
          }
          return
        case _ =>
      }

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
              (if (args1.length != args2.length) findDiffLengthArgs(l, args2.length) else Some((args1, des1))) match {
                case Some((aArgs, aType)) =>
                  if (addBound) constraints = constraints.withUpper(typeParameter.typeParamId, aType)
                  result = checkParameterizedType(typeParameter.typeParameters.map(_.psiTypeParameter).iterator, aArgs,
                    args2, constraints, visited, checkWeak)
                case _ =>
                  result = ConstraintsResult.Left
              }
            case (UndefinedOrWildcard(typeParameter, addBound), _) =>
              (if (args1.length != args2.length) findDiffLengthArgs(r, args1.length) else Some((args2, des2))) match {
                case Some((aArgs, aType)) =>
                  if (addBound) constraints = constraints.withLower(typeParameter.typeParamId, aType)
                  result = checkParameterizedType(typeParameter.typeParameters.map(_.psiTypeParameter).iterator, args1,
                    aArgs, constraints, visited, checkWeak)
                case _ =>
                  result = ConstraintsResult.Left
              }
              if (args1.length < args2.length && (result == null || !result.isRight)) {
                //partial unification SCL-11320
                val captureLength = args2.length - args1.length

                val (captured, abstracted) = args2.splitAt(captureLength)
                val t = checkParameterizedType(typeParameter.typeParameters.map(_.psiTypeParameter).iterator, args1, abstracted,
                  constraints, visited, checkWeak)
                //TODO actually remember to what designator we are mapping and what captured parameters are
                //TODO right now, anything compiles, fix once it's fixed in the compiler
                result = if (t.isRight && addBound) {
                  val abstractedTypeParams = abstracted.zipWithIndex.map {
                    case (_, i) => TypeParameter.light("p" + i + "$$", Seq(), Nothing, Any)
                  }

                  val typeConstructor =
                    ScTypePolymorphicType(
                      ScParameterizedType(
                        des2,
                        captured ++ abstractedTypeParams.map(TypeParameterType(_))
                      ),
                      abstractedTypeParams
                    )

                  t.constraints.withLower(typeParameter.typeParamId, typeConstructor)
                } else {
                  t
                }
              }
            case (_, UndefinedOrWildcard(typeParameter, addBound)) =>
              (if (args1.length != args2.length) findDiffLengthArgs(l, args2.length) else Some((args1, des1))) match {
                case Some((aArgs, aType)) =>
                  if (addBound) constraints = constraints.withUpper(typeParameter.typeParamId, aType)
                  result = checkParameterizedType(typeParameter.typeParameters.map(_.psiTypeParameter).iterator, aArgs,
                    args2, constraints, visited, checkWeak)
                case _ =>
                  result = ConstraintsResult.Left
              }
            case _ if des1 equiv des2 =>
              result =
                if (args1.length != args2.length) ConstraintsResult.Left
                else extractParams(des1) match {
                  case Some(params) => checkParameterizedType(params, args1, args2, constraints, visited, checkWeak)
                  case _ => ConstraintsResult.Left
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
        if (!result.isRight) r.isAliasType match {
          case Some(_) =>
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

      rightVisitor = new DesignatorVisitor {}
      r.visitType(rightVisitor)
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
        result = conformsInner(simplified, r, visited, constraints, checkWeak)
        return
      }

      rightVisitor = new ExistentialSimplification with ExistentialArgumentVisitor
        with ParameterizedExistentialArgumentVisitor with OtherNonvalueTypesVisitor with NothingNullVisitor
         with TypeParameterTypeVisitor with ThisVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      rightVisitor = new ParameterizedAliasVisitor with AliasDesignatorVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      def lightTypeParam(arg: ScExistentialArgument): TypeParameter =
        TypeParameter.light(arg.name, arg.typeParameters, arg.lower, arg.upper)

      val undefines = e.wildcards.map(w => UndefinedType(lightTypeParam(w)))
      val wildcardsToUndefined = e.wildcards.zip(undefines).toMap

      val updatedWithUndefinedTypes = e.quantified.recursiveUpdate {
        case arg: ScExistentialArgument => ReplaceWith(wildcardsToUndefined.getOrElse(arg, arg))
        case _: ScExistentialType       => Stop
        case _                          => ProcessSubtypes
      }

      conformsInner(updatedWithUndefinedTypes, r, HashSet.empty, constraints) match {
        case unSubst@ConstraintSystem(solvingSubstitutor) =>
          for (un <- undefines if result == null) {
            val solvedType = solvingSubstitutor(un)

            var t = conformsInner(solvedType, un.typeParameter.lowerType.unpackedType, constraints = constraints)
            if (solvedType != un && t.isLeft) {
              result = ConstraintsResult.Left
              return
            }
            constraints = t.constraints
            t = conformsInner(un.typeParameter.upperType.unpackedType, solvedType, constraints = constraints)
            if (solvedType != un && t.isLeft) {
              result = ConstraintsResult.Left
              return
            }
            constraints = t.constraints
          }

          if (result == null) {
            //ignore undefined types from existential arguments
            val typeParamIds = undefines.map {
              _.typeParameter.typeParamId
            }.toSet

            constraints += unSubst.removeTypeParamIds(typeParamIds)
            result = constraints
          }
        case _ => result = ConstraintsResult.Left
      }
      if (result != null) return

      rightVisitor = new DesignatorVisitor {}
      r.visitType(rightVisitor)
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

    override def visitDesignatorType(des: ScDesignatorType) {
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

      des.isAliasType match {
        case Some(AliasType(_, lower, _)) =>
          result = lower match {
            case Right(value) => conformsInner(value, r, visited, constraints)
            case _ => ConstraintsResult.Left
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

    override def visitUndefinedType(u: UndefinedType) {
      val rightVisitor = new ValDesignatorSimplification {
        override def visitUndefinedType(u2: UndefinedType) {
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
            result = constraints.withLower(u.typeParameter.typeParamId, lit.blockWiden())
          case _ =>
            result = constraints.withLower(u.typeParameter.typeParamId, r)
        }
      }
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

    override def visitAbstractType(a: ScAbstractType) {
      val rightVisitor = new ValDesignatorSimplification with UndefinedSubstVisitor {}
      r.visitType(rightVisitor)
      if (result != null) return

      result = conformsInner(a.upper, r, visited, constraints, checkWeak)
      if (result.isRight) {
        val t = conformsInner(r, a.lower, visited, result.constraints, checkWeak)
        if (t.isRight) result = t
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

  private def smartIsInheritor(leftClass: PsiClass, substitutor: ScSubstitutor, rightClass: PsiClass) : SmartInheritanceResult = {
    if (areClassesEquivalent(leftClass, rightClass)) return SmartInheritanceResult.failure
    if (!isInheritorDeep(leftClass, rightClass)) return SmartInheritanceResult.failure
    smartIsInheritor(leftClass, substitutor, areClassesEquivalent(_, rightClass), new collection.immutable.HashSet[PsiClass])
  }

  private def parentWithArgNumber(leftClass: PsiClass, substitutor: ScSubstitutor, argsNumber: Int): SmartInheritanceResult = {
    smartIsInheritor(leftClass, substitutor, c => c.getTypeParameters.length == argsNumber, new collection.immutable.HashSet[PsiClass]())
  }

  private def smartIsInheritor(leftClass: PsiClass, substitutor: ScSubstitutor, condition: PsiClass => Boolean,
                               visited: collection.immutable.HashSet[PsiClass]): SmartInheritanceResult = {
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
        case tp: ScType => substitutor(tp)
        case pct: PsiClassType =>
          substitutor(pct.toScType()) match {
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
      if (!recursive.isFailure) {
        val lastResult = recursive.result

        if (res == null || lastResult.conforms(res)) {
          res = lastResult
        }
      }
    }
    SmartInheritanceResult(res)
  }

  def extractParams(des: ScType): Option[Iterator[PsiTypeParameter]] = {
    des match {
      case undef: UndefinedType =>
        Option(undef.typeParameter.psiTypeParameter).map(_.getTypeParameters.iterator)
      case tpt: TypeParameterType => Option(tpt.typeParameters.map(_.psiTypeParameter).iterator)
      case _ =>
        des.extractClass.map {
          case td: ScTypeDefinition => td.typeParameters.iterator
          case other => other.getTypeParameters.iterator
        }
    }
  }

  def addParam(typeParameter: TypeParameter, bound: ScType, constraints: ConstraintSystem): ConstraintsResult = {
    constraints
      .withUpper(typeParameter.typeParamId, bound, variance = Invariant)
      .withLower(typeParameter.typeParamId, bound, variance = Invariant)
  }

  def processHigherKindedTypeParams(undefType: ParameterizedType, defType: ParameterizedType, constraints: ConstraintSystem,
                                    falseUndef: Boolean): ConstraintsResult = {
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
                case _ if undefType.typeArguments.length < defType.typeArguments.length =>
                  val captureLength = defType.typeArguments.length - undefType.typeArguments.length
                  val (captured, abstracted) = defType.typeArguments.splitAt(captureLength)
                  var subst = constraints
                  for ((arg1, arg2) <- abstracted.zip(undefType.typeArguments)) {
                    val t = arg2.equivInner(arg1, subst, falseUndef)
                    if (t.isLeft) {
                      return ConstraintsResult.Left
                    } else {
                      subst = t.constraints
                    }
                  }
                  val abstractedTypeParams = abstracted.zipWithIndex.map {
                    case (_, i) => TypeParameter.light("p" + i + "$$", Seq(), Nothing, Any)
                  }
                  //TODO actually remember to what designator we are mapping and what captured parameters are
                  //TODO right now, anything compiles, fix once it's fixed in the compiler
                  return addParam(undef.typeParameter, ScTypePolymorphicType(ScParameterizedType(defType.designator,
                    captured ++ abstractedTypeParams.map(TypeParameterType(_))), abstractedTypeParams), subst)
                case _ => return ConstraintsResult.Left
              }
            } else {
              defArgsReplace =defType.typeArguments
              defType.designator
            }
          } else defTypeExpanded.designator
        } else {
          defTypeExpanded.designator
        }
        val y = undef.equiv(bound, constraints, falseUndef)
        if (y.isLeft) {
          ConstraintsResult.Left
        } else {
          val undefArgIterator = undefType.typeArguments.iterator
          val defIterator = defArgsReplace.iterator
          var sub = y.constraints
          while (params.hasNext && undefArgIterator.hasNext && defIterator.hasNext) {
            val arg1 = undefArgIterator.next()
            val arg2 = defIterator.next()
            val t = arg1.equiv(arg2, sub, falseUndef = false)
            if (t.isLeft) return ConstraintsResult.Left
            sub = t.constraints
          }
          sub
        }
      case _ => ConstraintsResult.Left
    }
  }

  def findDiffLengthArgs(eType: ScType, argLength: Int): Option[(Seq[ScType], ScType)] =
    eType.extractClassType match {
      case Some((clazz, classSubst)) =>
        val t = parentWithArgNumber(clazz, classSubst, argLength)
        if (t.isFailure) None
        else t.result match {
          case ParameterizedType(newDes, newArgs) =>
            Some(newArgs, newDes)
          case _ =>
            None
        }
      case _ =>
        None
    }
}

private object ScalaConformance {
  private object UndefinedOrWildcard {
    def unapply(tpe: NonValueType): Option[(TypeParameter, Boolean)] = tpe match {
      case UndefinedType(tparam, _) => Option((tparam, true))
      case WildcardType(tparam)     => Option((tparam, false))
      case _                        => None
    }
  }

  //avoid unnecessary Tuple2(Boolean, ScType), it generates a lot of garbage
  private class SmartInheritanceResult(val result: ScType) extends AnyVal {
    def isFailure: Boolean = result == null
  }

  private object SmartInheritanceResult {
    val failure: SmartInheritanceResult = new SmartInheritanceResult(null)

    def apply(res: ScType): SmartInheritanceResult = new SmartInheritanceResult(res)
  }

  private object ScalaArray {
    def unapply(p: ParameterizedType): Option[ScType] = p match {
      case ParameterizedType(ExtractClass(ClassQualifiedName("scala.Array")), Seq(arg)) => Some(arg)
      case _ => None
    }
  }

}