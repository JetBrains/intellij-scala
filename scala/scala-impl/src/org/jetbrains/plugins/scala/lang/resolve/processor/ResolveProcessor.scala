package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.scope._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScSuperReference, ScThisReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.types.{ApplicabilityProblem, TypeIsNotStable}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence._
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}

class ResolveProcessor(override val kinds: Set[ResolveTargets.Value],
                       val ref: PsiElement,
                       val name: String) extends BaseProcessor(kinds)(ref) with PrecedenceHelper {

  private object ResolveStrategy extends NameUniquenessStrategy {

    override def isValid(result: ScalaResolveResult): Boolean = result.qualifiedNameId != null

    override def hashCode(result: ScalaResolveResult): Int = result.qualifiedNameId match {
      case null => 0
      case id   => id.hashCode
    }

    override def equals(left: ScalaResolveResult, right: ScalaResolveResult): Boolean =
      if (left == null && right == null) true
      else if (left == null || right == null) false
      else left.qualifiedNameId == right.qualifiedNameId
  }

  @volatile
  private var resolveScope: GlobalSearchScope = _

  def getResolveScope: GlobalSearchScope = {
    if (resolveScope == null) {
      resolveScope = ref.resolveScope
    }
    resolveScope
  }

  override protected def getPlace: PsiElement = ref

  private[this] val isThisOrSuperResolve = ref.getParent match {
    case _: ScThisReference | _: ScSuperReference => true
    case _                                        => false
  }

  override protected def nameUniquenessStrategy: NameUniquenessStrategy = ResolveStrategy

  override protected val holder: SimpleTopPrecedenceHolder = new SimpleTopPrecedenceHolder

  /**
    * This method useful for resetting precednce if we dropped
    * all found candidates to seek implicit conversion candidates.
    */
  def resetPrecedence(): Unit = holder.reset()

  import precedenceTypes._

  def checkImports(): Boolean = checkPrecedence(IMPORT)

  def checkWildcardImports(): Boolean = checkPrecedence(WILDCARD_IMPORT)

  def checkPredefinedClassesAndPackages(): Boolean =
    checkPrecedence(precedenceTypes.defaultImportMaxPrecedence)

  private def checkPrecedence(i: Int) =
    holder.currentPrecedence <= i

  override def changedLevel: Boolean = {
    def update: Boolean = {
      val iterator = levelSet.iterator()
      while (iterator.hasNext) {
        candidatesSet = candidatesSet + iterator.next()
      }
      uniqueNamesSet.addAll(levelUniqueNamesSet)
      levelSet.clear()
      levelUniqueNamesSet.clear()
      false
    }

    if (levelSet.isEmpty)                               true
    else if (holder.currentPrecedence == OTHER_MEMBERS) update
    else                                                !update
  }

  override protected def execute(namedElement: PsiNamedElement)
                                (implicit state: ResolveState): Boolean = {
    def renamed: Option[String] = state.renamed

    if (nameMatches(namedElement)) {
      val accessible = isAccessible(namedElement, ref)
      if (accessibility && !accessible)
        return true

      //NOTE: for now this is the only resolver where `state.stableTypeExpected` is handled
      //It was enough to properly highlight resolved references with non-stable types
      //But I am not entirely sure where else it might be important
      val problems: Seq[ApplicabilityProblem] =
        if (state.stableTypeExpected && !hasStableTypeOrNotApplicable(namedElement))
          TypeIsNotStable :: Nil
        else Nil

      namedElement match {
        case o: ScObject if o.isPackageObject && JavaPsiFacade.getInstance(namedElement.getProject).
          findPackage(o.qualifiedName) != null =>
        case pack: PsiPackage =>
          val result = new ScalaResolveResult(
            ScPackageImpl(pack),
            state.substitutor,
            state.importsUsed,
            renamed,
            problems = problems,
            isAccessible = accessible
          )
          addResult(result)
        case clazz: PsiClass if !isThisOrSuperResolve || PsiTreeUtil.isContextAncestor(clazz, ref, true) =>
          val result = new ScalaResolveResult(
            namedElement,
            state.substitutor,
            state.importsUsed,
            renamed,
            problems = problems,
            fromType = state.fromType,
            isAccessible = accessible,
            matchClauseSubstitutor = state.matchClauseSubstitutor
          )
          addResult(result)
        case _: PsiClass => //do nothing, it's wrong class or object
        case _ if isThisOrSuperResolve => //do nothing for type alias
        case _ =>
          val result = new ScalaResolveResult(
            namedElement,
            state.substitutor,
            state.importsUsed,
            renamed,
            problems               = problems,
            fromType               = state.fromType,
            isAccessible           = accessible,
            matchClauseSubstitutor = state.matchClauseSubstitutor
          )
          addResult(result)
      }
    }

    true
  }

  private def hasStableTypeOrNotApplicable(namedElement: PsiNamedElement): Boolean =
    namedElement match {
      case typed: ScTypedDefinition => typed.isStable
      case _ => true
    }

  protected final def nameMatches(namedElement: PsiNamedElement)
                                 (implicit state: ResolveState): Boolean = {
    val elementName = state.renamed.getOrElse(namedElement.name)

    (name == "_root_" && elementName == null) ||
      !StringUtil.isEmpty(elementName) && ScalaNamesUtil.equivalent(elementName, name)
  }

  override def getHint[T](hintKey: Key[T]): T = {
    hintKey match {
      case NameHint.KEY if name != "" => ScalaNameHint.asInstanceOf[T]
      case _ => super.getHint(hintKey)
    }
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    var res = candidatesSet
    val iterator = levelSet.iterator()
    while (iterator.hasNext) {
      res = res + iterator.next()
    }

    /*
    This is also hack for self type elements to filter duplicates.
    For example:
    trait IJTest {
      self : MySub =>
      type FooType
      protected implicit def d: FooType
    }
    trait MySub extends IJTest {
      type FooType = Long
    }
     */
    res.filter {
      case r@ScalaResolveResult(_: ScTypeAlias | _: ScClass | _: ScTrait, _) =>
        res.foldLeft(true) {
          case (false, _) => false
          case (true, rr@ScalaResolveResult(_: ScTypeAlias | _: ScClass | _: ScTrait, _)) =>
            rr.element.name != r.element.name ||
              !ScalaPsiUtil.superTypeMembers(rr.element).contains(r.element)
          case (true, _) => true
        }
      case _ => true
    }
  }

  object ScalaNameHint extends NameHint {
    override def getName(state: ResolveState): String = state.renamed.getOrElse(name)
  }

  override def toString = s"ResolveProcessor($name)"
}

