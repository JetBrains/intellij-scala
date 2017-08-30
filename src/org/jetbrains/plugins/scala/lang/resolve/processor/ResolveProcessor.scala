package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.scope._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScSuperReference, ScThisReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence._
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.collection.mutable.ArrayBuffer
import scala.collection.{Set, mutable}

class ResolveProcessor(override val kinds: Set[ResolveTargets.Value],
                       val ref: PsiElement,
                       val name: String) extends BaseProcessor(kinds)(ref) with PrecedenceHelper[String] {

  import ResolveProcessor._

  @volatile
  private var resolveScope: GlobalSearchScope = null

  private val history = new ArrayBuffer[HistoryEvent]
  private var fromHistory: Boolean = false

  def getResolveScope: GlobalSearchScope = {
    if (resolveScope == null) {
      resolveScope = ref.resolveScope
    }
    resolveScope
  }

  private val ignoredSet = new mutable.HashSet[ScalaResolveResult]()

  def getPlace: PsiElement = ref

  val isThisOrSuperResolve = ref.getParent match {
    case _: ScThisReference | _: ScSuperReference => true
    case _ => false
  }

  def emptyResultSet: Boolean = candidatesSet.isEmpty || levelSet.isEmpty

  protected var precedence: Int = 0

  /**
    * This method useful for resetting precednce if we dropped
    * all found candidates to seek implicit conversion candidates.
    */
  def resetPrecedence() {
    precedence = 0
  }

  import PrecedenceTypes._

  def checkImports(): Boolean = precedence <= IMPORT

  def checkWildcardImports(): Boolean = precedence <= WILDCARD_IMPORT

  def checkPredefinedClassesAndPackages(): Boolean = precedence <= SCALA_PREDEF

  override protected def getQualifiedName(result: ScalaResolveResult): String = {
    def defaultForTypeAlias(t: ScTypeAlias): String = {
      if (t.getParent.isInstanceOf[ScTemplateBody] && t.containingClass != null) {
        "TypeAlias:" + t.containingClass.qualifiedName + "#" + t.name
      } else null
    }

    result.getActualElement match {
      case _: ScTypeParam => null
      case c: ScObject => "Object:" + c.qualifiedName
      case c: PsiClass => "Class:" + c.qualifiedName
      case t: ScTypeAliasDefinition if t.typeParameters.isEmpty =>
        t.aliasedType match {
          case Success(tp, _) =>
            tp.extractClass match {
              case Some(_: ScObject) => defaultForTypeAlias(t)
              case Some(td: ScTypeDefinition) if td.typeParameters.isEmpty && ScalaPsiUtil.hasStablePath(td) =>
                "Class:" + td.qualifiedName
              case Some(c: PsiClass) if c.getTypeParameters.isEmpty => "Class:" + c.qualifiedName
              case _ => defaultForTypeAlias(t)
            }
          case _ => defaultForTypeAlias(t)
        }
      case t: ScTypeAlias => defaultForTypeAlias(t)
      case p: PsiPackage => "Package:" + p.getQualifiedName
      case _ => null
    }
  }

  protected def getTopPrecedence(result: ScalaResolveResult): Int = precedence

  protected def setTopPrecedence(result: ScalaResolveResult, i: Int) {
    precedence = i
  }

  override def changedLevel: Boolean = {
    if (!fromHistory && !history.lastOption.contains(ChangedLevel)) {
      history += ChangedLevel
    }

    def update: Boolean = {
      val iterator = levelSet.iterator()
      while (iterator.hasNext) {
        candidatesSet += iterator.next()
      }
      qualifiedNamesSet.addAll(levelQualifiedNamesSet)
      levelSet.clear()
      levelQualifiedNamesSet.clear()
      false
    }

    if (levelSet.isEmpty) true
    else if (precedence == OTHER_MEMBERS) update
    else !update
  }

  def isAccessible(named: PsiNamedElement, place: PsiElement): Boolean = {
    val memb: PsiMember = {
      named match {
        case memb: PsiMember => memb
        case _ => ScalaPsiUtil.nameContext(named) match {
          case memb: PsiMember => memb
          case _ => return true //something strange
        }
      }
    }
    ResolveUtils.isAccessible(memb, place)
  }

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]

    def nameShadow: Option[String] = Option(state.get(ResolverEnv.nameKey))

    if (nameAndKindMatch(named, state)) {
      val accessible = isAccessible(named, ref)
      if (accessibility && !accessible) return true
      named match {
        case o: ScObject if o.isPackageObject && JavaPsiFacade.getInstance(element.getProject).
          findPackage(o.qualifiedName) != null =>
        case pack: PsiPackage =>
          val resolveResult: ScalaResolveResult =
            new ScalaResolveResult(ScPackageImpl(pack), getSubst(state), getImports(state), nameShadow, isAccessible = accessible)
          addResult(resolveResult)
        case clazz: PsiClass if !isThisOrSuperResolve || PsiTreeUtil.isContextAncestor(clazz, ref, true) =>
          addResult(new ScalaResolveResult(named, getSubst(state),
            getImports(state), nameShadow, boundClass = getBoundClass(state), fromType = getFromType(state), isAccessible = accessible))
        case _: PsiClass => //do nothing, it's wrong class or object
        case _ if isThisOrSuperResolve => //do nothing for type alias
        case _ =>
          addResult(new ScalaResolveResult(named, getSubst(state),
            getImports(state), nameShadow, boundClass = getBoundClass(state), fromType = getFromType(state), isAccessible = accessible))
      }
    }
    true
  }

  protected def nameAndKindMatch(named: PsiNamedElement, state: ResolveState): Boolean = {
    val nameSet = state.get(ResolverEnv.nameKey)
    val elName = if (nameSet == null) {
      val name = named.name
      if (name == null) return false
      if (name == "") return false
      name
    } else nameSet
    val nameMatches = ScalaNamesUtil.equivalent(elName, name)
    nameMatches && kindMatches(named)
  }

  override def getHint[T](hintKey: Key[T]): T = {
    hintKey match {
      case NameHint.KEY if name != "" => ScalaNameHint.asInstanceOf[T]
      case _ => super.getHint(hintKey)
    }
  }

  override protected def addResults(results: Seq[ScalaResolveResult]): Boolean = {
    if (!fromHistory) history += AddResult(results)
    super.addResults(results)
  }

  override protected def ignored(results: Seq[ScalaResolveResult]): Boolean = {
    val result = !fromHistory && super.ignored(results)

    if (result) {
      ignoredSet ++= results
    }

    result
  }

  override protected def clear(): Unit = {
    ignoredSet.clear()
    super.clear()

    fromHistory = true
    try {
      history.foreach {
        case ChangedLevel => changedLevel
        case AddResult(results) => addResults(results)
      }
    }
    finally {
      fromHistory = false
    }
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    var res = candidatesSet
    val iterator = levelSet.iterator()
    while (iterator.hasNext) {
      res += iterator.next()
    }
    if (!ResolveProcessor.compare(ignoredSet, res)) {
      res.clear()
      clear()
      //now let's add everything again
      res = candidatesSet
      val iterator = levelSet.iterator()
      while (iterator.hasNext) {
        res += iterator.next()
      }
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
    def getName(state: ResolveState): String = {
      val stateName = state.get(ResolverEnv.nameKey)
      if (stateName == null) name else stateName
    }
  }

  override def toString = s"ResolveProcessor($name)"
}

object ResolveProcessor {

  private sealed trait HistoryEvent

  private case object ChangedLevel extends HistoryEvent

  private case class AddResult(results: Seq[ScalaResolveResult]) extends HistoryEvent

  private def compare(ignoredSet: mutable.HashSet[ScalaResolveResult],
                      set: mutable.HashSet[ScalaResolveResult]): Boolean = {
    if (ignoredSet.nonEmpty && set.isEmpty) return false

    val ignoredElements = ignoredSet.map(_.getActualElement)
    val elements = set.map(_.getActualElement)

    ignoredElements.forall { result =>
      elements.forall(areEquivalent(result, _))
    }
  }

  private[this] def areEquivalent(left: PsiNamedElement, right: PsiNamedElement): Boolean =
    ScEquivalenceUtil.smartEquivalence(left, right) ||
      isExactAliasFor(left, right) || isExactAliasFor(right, left)

  private[this] def isExactAliasFor(left: PsiNamedElement, right: PsiNamedElement): Boolean =
    left.isInstanceOf[ScTypeAliasDefinition] &&
      right.isInstanceOf[PsiClass] &&
      left.asInstanceOf[ScTypeAliasDefinition].isExactAliasFor(right.asInstanceOf[PsiClass])
}
