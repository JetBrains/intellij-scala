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
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}

import scala.collection.Set

object ResolveProcessor {
  def getQualifiedName(result: ScalaResolveResult, place: PsiElement): String = {
    def defaultForTypeAlias(t: ScTypeAlias): String = {
      if (t.getParent.isInstanceOf[ScTemplateBody] && t.containingClass != null) {
        "TypeAlias:" + t.containingClass.qualifiedName + "#" + t.name
      } else null
    }

    result.getActualElement match {
      case c: ScTypeParam => null
      case c: ScObject => "Object:" + c.qualifiedName
      case c: PsiClass => "Class:" + c.qualifiedName
      case t: ScTypeAliasDefinition if t.typeParameters.isEmpty =>
        t.aliasedType(TypingContext.empty) match {
          case Success(tp, elem) =>
            ScType.extractClass(tp, Option(place).map(_.getProject)) match {
              case Some(c: ScObject) => defaultForTypeAlias(t)
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
}

class ResolveProcessor(override val kinds: Set[ResolveTargets.Value],
                       val ref: PsiElement,
                       val name: String)
                      (implicit override val typeSystem: TypeSystem) extends BaseProcessor(kinds) with PrecedenceHelper[String] {
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

  import org.jetbrains.plugins.scala.lang.resolve.processor.PrecedenceHelper.PrecedenceTypes._
  def checkImports(): Boolean = precedence <= IMPORT
  
  def checkWildcardImports(): Boolean = precedence <= WILDCARD_IMPORT

  def checkPredefinedClassesAndPackages(): Boolean = precedence <= SCALA_PREDEF

  override protected def getQualifiedName(result: ScalaResolveResult): String = {
    ResolveProcessor.getQualifiedName(result, getPlace)
  }

  protected def getTopPrecedence(result: ScalaResolveResult): Int = precedence

  protected def setTopPrecedence(result: ScalaResolveResult, i: Int) {
    precedence = i
  }

  override def isUpdateHistory: Boolean = true

  override def changedLevel = {
    addChangedLevelToHistory()

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
          val resolveResult: ScalaResolveResult =
            new ScalaResolveResult(ScPackageImpl(pack), getSubst(state), getImports(state), nameShadow, isAccessible = accessible)
          addResult(resolveResult)
        case clazz: PsiClass if !isThisOrSuperResolve || PsiTreeUtil.isContextAncestor(clazz, ref, true) =>
          addResult(new ScalaResolveResult(named, getSubst(state),
            getImports(state), nameShadow, boundClass = getBoundClass(state), fromType = getFromType(state), isAccessible = accessible))
        case clazz: PsiClass => //do nothing, it's wrong class or object
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
    } else  nameSet
    val nameMatches = ScalaPsiUtil.memberNamesEquals(elName, name)
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
    if (!compareWithIgnoredSet(res)) {
      res.clear()
      restartFromHistory()
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
    def getName(state: ResolveState) = {
      val stateName = state.get(ResolverEnv.nameKey)
      val result = if (stateName == null) name else stateName
      if (result != null && result.startsWith("`") && result.endsWith("`") && result.length > 1) result.substring(1, result.length - 1)
      else result
    }
  }
}
