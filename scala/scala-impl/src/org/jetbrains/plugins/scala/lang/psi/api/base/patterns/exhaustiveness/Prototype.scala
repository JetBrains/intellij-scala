package org.jetbrains.plugins.scala.lang.psi.api.base.patterns.exhaustiveness

import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.{PsiClass, PsiEnumConstant}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{BooleanExt, JavaEnum, ObjectExt, PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.exhaustiveness.Space.{Empty, SingletonTypeText, typePresentationRegex}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMatch, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.ScBooleanLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, TupleType}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScAndType, ScLiteralType, ScOrType, ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.matching.Regex


sealed trait Space {
  private val isSubSpaceCache = mutable.HashMap.empty[Space, Boolean]
  def isSubSpaceOf(other: Space): Boolean =
    isSubSpaceCache.getOrElseUpdate(other, Space.computeIsSubSpace(this, other))

  def flatten: Seq[Space] = this match {
    case Space.Prod(typ, fun, spaces) =>
      val ss = LazyList(spaces: _*).map(_.flatten)

      ss.foldLeft(LazyList(Nil : List[Space])) { (acc, flat) =>
        for { sps <- acc; s <- flat }
          yield sps :+ s
      }.map { sps =>
        Space.Prod(typ, fun, sps)
      }

    case Space.Or(spaces) =>
      LazyList(spaces: _*).flatMap(_.flatten)

    case _ =>
      List(this)
  }
  lazy val simplified: Space = Space.simplify(this)
  def -(other: Space): Space = Space.minus(this, other)

  final def toReadableString(implicit ctx: TypePresentationContext): String = {

    def doShow(s: Space): String = s match {
      case Space.Empty => "empty"
      case Space.Typ(typ, _) =>
        typ match {
          case _ if typ.isAny || typ.isAnyVal => "_"
          case SingletonTypeText(txt) => txt
          case _ => s"_: ${typ.presentableText}"
        }
      case Space.Prod(typ, fun, spaces) =>
        val funName = fun match {
          case Space.FunOrigin.Tuple(_) => ""
          case Space.FunOrigin.Extractor(_, _) =>
            // TODO: do it better
            typePresentationRegex.replaceAllIn(typ.presentableText, "")
        }
        funName + spaces.map(doShow).mkString("(", ", ", ")")
      case Space.Or(spaces) =>
        spaces.map(doShow).sorted.mkString(" | ")
    }
    doShow(this)
  }
}

object Space {
  case object Empty extends Space

  case class Typ(typ: ScType, isDecomposed: Boolean = true) extends Space {
    def canDecompose: Boolean = _decomposed.isDefined
    def decomposesToNil: Boolean = _decomposed.contains(Nil)

    private val _decomposed =
      Space.decomposeType(typ).map(_.map(Typ(_)))
    lazy val decomposed: List[Typ] = _decomposed.get
  }

  sealed abstract class FunOrigin {
    def isUnapply: Boolean
  }
  object FunOrigin {
    final case class Tuple(types: List[ScType]) extends FunOrigin {
      override def isUnapply: Boolean = true
    }
    final case class Extractor(fun: ScFunction, pattern: ScPattern) extends FunOrigin {
      override def isUnapply: Boolean = fun.isUnapplyMethod
    }
  }
  case class Prod(typ: ScType, fun: FunOrigin, params: List[Space]) extends Space

  case class Or(spaces: List[Space]) extends Space

  object Or {
    def apply(spaces: List[Space]): Space =
      spaces.filter(_ != Empty) match {
        case Nil => Empty
        case single :: Nil => single
        case spaces => new Or(spaces)
      }
  }


  def from(typ: ScType): Space = Typ(typ)

  def from(result: TypeResult): Space = result.map(from).getOrElse(Empty)

  def from(pattern: ScPattern): Space = pattern match {
    case g: ScGivenPattern => Space.from(g.typeElement.`type`())
    case typed: Sc3TypedPattern => ??? // more difficult because it can have complicated sub patterns
    case ScTypedPattern(typ) => Space.from(typ.`type`())
    case comp: ScCompositePattern => Space.Or(comp.subpatterns.map(from).toList)
    case interp: ScInterpolationPattern => ???
    case constr: ScConstructorPattern => fromExtractor(constr, constr.subpatterns)
    case infix: ScInfixPattern => fromExtractor(infix, infix.left :: infix.rightOption.toList)
    case ScLiteralPattern(lit) => Space.from(lit.`type`())
    case ScNamingPattern(subPattern) => Space.from(subPattern)
    case ScParenthesisedPattern(pattern) => Space.from(pattern)
    case ref: ScReferencePattern => Space.all(ref)
    case stable: ScStableReferencePattern => Space.from(stable.`type`())
    case wildcardPattern: ScWildcardPattern => Space.all(wildcardPattern)
    case tuple: ScTuplePattern =>
      val types = tuple.patternList.toList.flatMap(_.patterns.map(_.`type`().getOrAny))
      Space.Prod(
        TupleType(types)(pattern.elementScope),
        FunOrigin.Tuple(types),
        tuple.patternList.toList.flatMap(_.patterns.map(from))
      )
    case _: ScQuotedPattern => throw new Exception("Quoted patterns are not supported")
    case _: ScSeqWildcardPattern => throw new Exception("Seq wildcard should have been handled by extractor pattern")
    case _ => Space.Empty
  }

  def all(ctx: ProjectContext): Space = Typ(ctx.stdTypes.Any, isDecomposed = false)

  def dedup(spaces: Seq[Space]): Seq[Space] =
    if (spaces.lengthIs <= 1 || spaces.lengthIs >= 10) spaces
    else {
      val res = spaces.map(sp => (sp, spaces.filter(_ ne sp))).find {
        case (sp, sps) => sp.isSubSpaceOf(Or(List(sps: _*)))
      }
      if (res.isEmpty) spaces
      else res.get._2
    }

  private def fromExtractor(pattern: ScExtractorPattern, subPatterns: Seq[ScPattern]): Space = {
    val ref = pattern.ref
    resolveUnapplyMethodFromReference(ref) match {
      case Some(fun) =>
        Space.Prod(
          pattern.`type`().getOrAny,
          FunOrigin.Extractor(fun, pattern),
          subPatterns.map(Space.from).toList
        )
      case None =>
        Space.Empty
    }
  }


  private def resolveUnapplyMethodFromReference(ref: ScStableCodeReference): Option[ScFunction] = for {
    resolveResult <- ref.bind()
    maybeUnapplyMethod = Option(resolveResult.getElement)
    unapplyMethod <- maybeUnapplyMethod.collect { case method: ScFunction if method.isUnapplyMethod => method }
  } yield unapplyMethod


  private def isSubType(a: ScType, b: ScType): Boolean = a.conforms(b)

  private def covers(fun: FunOrigin, typ: ScType, len: Int): Boolean = {
    // TODO: more cases
    isIrrifutableWhenTypeMatch(fun)
  }

  private def isIrrifutableWhenTypeMatch(fun: FunOrigin): Boolean = {
    fun match {
      case FunOrigin.Tuple(_) => true
      case FunOrigin.Extractor(fun, _) =>
        fun.returnType.exists { tp =>
          fun.isSynthetic || // <- unapply is from a synthetic companion object
            tp.extractClass.exists { cls =>
              cls.qualifiedName == "scala.Some"
            } || (
              tp match {
                case ScLiteralType(v, _) if v.value.asInstanceOf[AnyRef] == java.lang.Boolean.TRUE =>
                  // case for when the unapply method returns true
                  true
                case _ => false
              }
            )
          // TODO: more cases
        }
    }
  }

  private def signature(fun: FunOrigin, typ: ScType, len: Int): List[ScType] = {
    fun match {
      case FunOrigin.Tuple(types) => types
      case FunOrigin.Extractor(fun, pattern) =>
        val matches = ScPattern.extractorMatches(fun.returnType.getOrAny, pattern, fun)
        matches.bestMatch(len) match {
          case Some(matched) =>
            val prodTypes = matched.productTypes.toList
            matched.sequenceTypeOption match {
              case Some(seqType) if prodTypes.length != len => prodTypes ::: List.fill(len - prodTypes.length)(seqType)
              case _ => prodTypes
            }
          case None =>
            Nil
        }
    }
  }

  private def isSameUnapply(a: FunOrigin, b: FunOrigin): Boolean = {
    (a, b) match {
      case (FunOrigin.Tuple(_), FunOrigin.Tuple(_)) => true
      case (FunOrigin.Extractor(aFun, _), FunOrigin.Extractor(bFun, _)) =>
        // TODO: handle term prefixes (this element of the unapply method)
        aFun == bFun
      case _ => false
    }
  }

  def simplify(space: Space): Space = space match {
    case Prod(typ, fun, subs) =>
      val simplified = subs.mapConserve(simplify)
      if (simplified.contains(Empty)) Empty
      else if (decomposeType(typ).contains(Nil)) Empty
      else if (simplified eq subs) space
      else Prod(typ, fun, simplified)
    case Or(spaces) =>
      val newSpaces = spaces.mapConserve(simplify).filterConserve(_ != Empty)
      if (newSpaces eq spaces) space
      else Or(spaces.map(_.simplified))
    case typ: Typ if typ.decomposesToNil => Empty
    case _ => space
  }

  /** Is `a` a subspace of `b`?
   *
   * Equivalent to `simplify(simplify(a) - simplify(b)) == Empty`, but faster
   */
  def computeIsSubSpace(a: Space, b: Space): Boolean = {
    val aa = a.simplified
    val bb = b.simplified
    if ((aa ne a) || (bb ne b)) aa.isSubSpaceOf(bb)
    else (a, b) match {
      case (Empty, _) => true
      case (_, Empty) => false
      case (Or(ss), _) => ss.forall(_.isSubSpaceOf(b))
      case (a: Typ, Or(bSpaces)) =>
        bSpaces.exists(b => a.isSubSpaceOf(b)) ||
          a.canDecompose && Or(a.decomposed).isSubSpaceOf(b)
      case (_, Or(_)) => (a - b).simplified == Empty
      case (a@Typ(aT, _), b@Typ(bT, _)) =>
        isSubType(aT, bT) ||
          (a.canDecompose && Or(a.decomposed).isSubSpaceOf(b)) ||
          (b.canDecompose && a.isSubSpaceOf(Or(b.decomposed)))
      case (Prod(aT, _, _), Typ(bT, _)) =>
        isSubType(aT, bT)
      case (a@Typ(aT, _), Prod(bT, bFun, bSubs)) =>
        isSubType(aT, bT) &&
          covers(bFun, aT, bSubs.length) &&
          Prod(bT, bFun, signature(bFun, aT, bSubs.length).map(Typ(_, isDecomposed = false)))
            .isSubSpaceOf(b) ||
          a.canDecompose && Or(a.decomposed).isSubSpaceOf(b)
      case (Prod(_, aFun, aSubs), Prod(_, bFun, bSubs)) =>
        isSameUnapply(aFun, bFun) && aSubs.lazyZip(bSubs).forall(_ isSubSpaceOf _)
    }
  }

  def minus(a: Space, b: Space): Space =
    (a, b) match {
      case (Empty, _) => Empty
      case (_, Empty) => a
      case (Or(ss), _) => Or(ss.map(_ - b))
      case (_, Or(ss)) => ss.foldLeft(a)(_ - _)
      case (a@Typ(aT, _), b@Typ(bT, _)) =>
        if (isSubType(aT, bT)) Empty
        else if (!isSubType(bT, aT)) a // if they have no relationship, don't decompose
        else if (a.canDecompose) Or(a.decomposed.map(_ - b))
        else if (b.canDecompose) Or(b.decomposed.map(a - _))
        else a
      case (a@Typ(aT, _), Prod(bT, bFun, bSubs)) =>
        if (isSubType(aT, bT) && covers(bFun, aT, bSubs.length))
          Prod(aT, bFun, signature(bFun, aT, bSubs.length).map(Typ(_, isDecomposed = false))) - b
        else if (!isSubType(bT, aT)) a
        else if (a.canDecompose) Or(a.decomposed) - b
        else a
      case (Prod(aT, aFun, aSubs), b@Typ(bT, _)) =>
        if (isSubType(aT, bT)) Empty
        else if (a.simplified == Empty) Empty
        else if (b.canDecompose) a - Or(b.decomposed)
        else a
      case (Prod(_, aFun, _), Prod(_, bFun, _)) if !isSameUnapply(aFun, bFun) =>
        a
      case (Prod(_, aFun, aSubs), Prod(_, _, bSubs)) if aFun.isUnapply && aSubs.length != bSubs.length =>
        a
      case (Prod(aT, aFun, aSubs), Prod(_, _, bSubs)) =>
        val range = aSubs.indices
        val cache = Array.fill[Space](bSubs.length)(null)

        def sub(i: Int): Space = {
          if (cache(i) == null) {
            cache(i) = aSubs(i) - bSubs(i)
          }
          assert(cache(i) != null)
          cache(i)
        }

        if (range.exists(i => aSubs(i).isSubSpaceOf(sub(i)))) a
        else if (cache.forall(sub => sub.isSubSpaceOf(Empty))) Empty
        else {
          val spaces = Iterator(range: _*).flatMap { i =>
            sub(i).flatten.map(s => Prod(aT, aFun, aSubs.updated(i, s)))
          }.toList
          Or(spaces)
        }
    }

  private def isSingletonType(typ: ScType): Boolean = {
    typ match {
      case _: ScLiteralType => true
      case _ =>
        typ.extractClass.exists {
          case _: ScObject => true
          case _: PsiEnumConstant => true
          case _ => false
        }
    }
  }

  object SingletonTypeText {
    def unapply(typ: ScType): Option[String] = {
      typ match {
        case ScLiteralType(lit, _) => Some(lit.value.toString)
        case ExtractClass(obj@(_: ScObject | _: PsiEnumConstant)) => Some(obj.name)
        case _ => None
      }
    }
  }

  private def decomposeType(typ: ScType): Option[List[ScType]] = {
    def rec(typ: ScType, mixins: List[ScType]): Option[List[ScType]] = {
      typ.removeAliasDefinitions() match {
        case ScAndType(aT, bT) =>
          val parts1 = rec(aT, bT :: mixins)
          val (bT2, parts2) = parts1 match {
            case Some(parts) => (bT, Some(parts))
            case None => (aT, rec(bT, aT :: mixins))
          }
          parts2.map { parts =>
            parts.collect {
              case typ if typ.conforms(bT2) => typ
              case typ if bT2.conforms(typ) => bT2
              case typ if ??? /* !TypeComparer.provablyDisjoint(tp, tpB) */ => ScAndType(typ, bT2)
            }
          }

        case ScOrType(aT, bT) => Some(List(aT, bT))
        case typ if typ.isBoolean =>
          Some(List(
            ScLiteralType(ScBooleanLiteralImpl.Value(true))(typ.projectContext),
            ScLiteralType(ScBooleanLiteralImpl.Value(false))(typ.projectContext),
          ))
        case typ if typ.isUnit => Some(List(typ))
        case typ if isSingletonType(typ) => None
        case JavaEnumType(cases) => Some(cases)
        // case Childless(tp @ AppliedType(Parts(parts), targs)) => ???
        case DecomposableToChildren(children) =>
          Some(children.map { child =>
            // TODO: here a lot of stuff is missing, like inheriting type arguments from the parent
            ScDesignatorType(child)
          })

        case _ => None
      }
    }

    rec(typ, Nil)
  }

  object JavaEnumType {
    def unapply(typ: ScType): Option[List[ScType]] =
      typ.extractClass.flatMap {
        case JavaEnum(cases) => Some(cases.map(ScDesignatorType(_)).toList)
        case _ => None
      }
  }

  object DecomposableToChildren {
    def unapply(typ: ScType): Option[List[PsiClass]] = {
      typ.extractClass.flatMap { cls =>
        val inheritors =
          ClassInheritorsSearch.search(cls, new LocalSearchScope(cls.getContainingFile), false)
            .findAll()
            .asScala
            .toList

        (cls.isSealed &&
          cls.isTraitOrAbstract &&
          inheritors.forall(c => !c.is[ScNewTemplateDefinition]) &&
          inheritors.nonEmpty
          // && typ.isOpaque
        ).option(inheritors)
      }
    }
  }

  val typePresentationRegex: Regex = raw"""\[.+\]""".r

  implicit class ListExt[T](private val list: List[T]) extends AnyVal {
    /**
     * A version of `map` that returns the same list instance if all mappings returned the same instance.
     */
    final def mapConserve(f: T => T): List[T] = {
      @tailrec
      def loop(@Nullable mapped: mutable.ListBuffer[T], unchanged: List[T], pending: List[T]): List[T] =
        if (pending.isEmpty)
          if (mapped == null) unchanged
          else mapped.prependToList(unchanged)
        else {
          val head0 = pending.head
          val head1 = f(head0)

          if (head1.asInstanceOf[AnyRef] eq head0.asInstanceOf[AnyRef])
            loop(mapped, unchanged, pending.tail)
          else {
            val b = if (mapped == null) new mutable.ListBuffer[T] else mapped
            var xc = unchanged
            while (xc ne pending) {
              b += xc.head
              xc = xc.tail
            }
            b += head1
            val tail0 = pending.tail
            loop(b, tail0, tail0)
          }
        }

      loop(null, list, list)
    }

    final def filterConserve(f: T => Boolean): List[T] = {
      @tailrec
      def loop(i: Int, rest: List[T]): List[T] = rest match {
        case Nil => list
        case head :: tail =>
          if (f(head)) loop(i + 1, tail)
          else {
            val builder = List.newBuilder[T]
            builder.addAll(list.iterator.take(i))
            builder.addAll(tail.iterator.filter(f))
            builder.result()
          }
      }
      loop(0, list)
    }
  }
}

object SpaceEngine {

  private def shouldCheckExhaustiveness(m: ScMatch): Boolean = {
    // TODO: implement
    true
  }

  private def shouldCheckExamples(selTyp: ScType): Boolean = {
    // TODO: implement
    true
  }

  private def isSatisfiable(selTyp: ScType, s: Space): Boolean = {
    // TODO: implement
    true
  }

  private def checkExhaustiveness(m: ScMatch): Unit = {
    if (!shouldCheckExhaustiveness(m)) return

    val selTyp = m.expression.flatMap(_.`type`().toOption) match {
      case Some(typ) => typ
      case None => return
    }

    val targetSpace = Space.from(selTyp)

    val patternSpace = Space.Or(m.clauses.map { clause =>
      clause.pattern match {
        case Some(p) if clause.guard.isEmpty => Space.from(p)
        case _ => Empty
      }
    }.toList)

    val checkGADTSAT = shouldCheckExamples(selTyp)

    val uncovered = (targetSpace - patternSpace)
      .simplified
      .flatten
      .filter { s =>
        s != Space.Empty && (!checkGADTSAT || !isSatisfiable(selTyp, s))
      }

    println(uncovered)
  }

  def checkMatch(m: ScMatch): Unit = {
    checkExhaustiveness(m)
  }
}