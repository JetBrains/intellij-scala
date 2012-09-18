package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.util.Key
import collection.Set
import psi.ScalaPsiUtil
import psi.impl.ScPackageImpl
import psi.api.statements.params.ScTypeParam
import psi.api.expr.{ScSuperReference, ScThisReference}
import psi.api.statements.ScTypeAlias
import psi.api.toplevel.templates.ScTemplateBody
import reflect.NameTransformer
import psi.api.toplevel.typedef.{ScClass, ScTrait, ScObject}
import extensions.{toPsiNamedElementExt, toPsiClassExt}
import com.intellij.psi.search.GlobalSearchScope

class ResolveProcessor(override val kinds: Set[ResolveTargets.Value],
                       val ref: PsiElement,
                       val name: String) extends BaseProcessor(kinds) with PrecedenceHelper[String] {
  @volatile
  private var resolveScope: GlobalSearchScope = null
  def getResolveScope: GlobalSearchScope = {
    if (resolveScope == null) {
      resolveScope = ref.getResolveScope
    }
    resolveScope
  }

  protected def getPlace: PsiElement = ref

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
  
  def checkImports(): Boolean = precedence < 6
  
  def checkWildcardImports(): Boolean = precedence < 5

  protected def getQualifiedName(result: ScalaResolveResult): String = {
    result.getActualElement match {
      case c: ScTypeParam => null
      case c: ScObject => "Object:" + c.qualifiedName
      case c: PsiClass => "Class:" + c.qualifiedName
      case t: ScTypeAlias if t.getParent.isInstanceOf[ScTemplateBody] &&
        t.containingClass != null => "TypeAlias:" + t.containingClass.qualifiedName + "#" + t.name
      case p: PsiPackage => "Package:" + p.getQualifiedName
      case _ => null
    }
  }

  protected def getTopPrecedence(result: ScalaResolveResult): Int = precedence

  protected def setTopPrecedence(result: ScalaResolveResult, i: Int) {
    precedence = i
  }

  override def changedLevel = {
    def update: Boolean = {
      val iterator = levelSet.iterator()
      while (iterator.hasNext) {
        candidatesSet += iterator.next()
      }
      qualifiedNamesSet ++= levelQualifiedNamesSet
      levelSet.clear()
      levelQualifiedNamesSet.clear()
      false
    }
    if (levelSet.isEmpty) true
    else if (precedence == 6) update
    else !update
  }

  def isAccessible(named: PsiNamedElement, place: PsiElement): Boolean = {
    val memb: PsiMember = {
      named match {
        case memb: PsiMember => memb
        case pl => ScalaPsiUtil.nameContext(named) match {
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
          addResult(new ScalaResolveResult(ScPackageImpl(pack), getSubst(state), getImports(state), nameShadow, isAccessible = accessible))
        case clazz: PsiClass if !isThisOrSuperResolve || PsiTreeUtil.isContextAncestor(clazz, ref, true) =>
          addResult(new ScalaResolveResult(named, getSubst(state),
            getImports(state), nameShadow, boundClass = getBoundClass(state), fromType = getFromType(state), isAccessible = accessible))
        case clazz: PsiClass => //do nothing, it's wrong class or object
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
      if (name.charAt(0) == '`') name.substring(1, name.length - 1) else name
    } else if (nameSet.charAt(0) == '`') nameSet.substring(1, nameSet.length - 1) else nameSet
    val nameMatches = NameTransformer.decode(elName) == NameTransformer.decode(name)
    nameMatches && kindMatches(named)
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
      res += iterator.next()
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
            ScalaPsiUtil.superTypeMembers(rr.element).find(_ == r.element) == None
          case (true, _) => true
        }
      case _ => true
    }
  }

  object ScalaNameHint extends NameHint {
    def getName(state: ResolveState) = {
      val stateName = state.get(ResolverEnv.nameKey)
      if (stateName == null) name else stateName
    }
  }
}
