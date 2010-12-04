package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.util.Key
import collection.Set
import psi.api.base.patterns.ScBindingPattern
import psi.ScalaPsiUtil
import psi.api.toplevel.typedef.ScObject
import psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportWildcardSelectorUsed, ImportUsed}
import psi.impl.toplevel.synthetic.ScSyntheticClass
import psi.impl.ScPackageImpl
import collection.mutable.HashSet
import caches.CachesUtil
import psi.api.statements.params.ScTypeParam
import psi.api.expr.{ScSuperReference, ScThisReference}

class ResolveProcessor(override val kinds: Set[ResolveTargets.Value],
                       val ref: PsiElement,
                       val name: String) extends BaseProcessor(kinds) {
  val isThisOrSuperResolve = ref.getParent match {
    case _: ScThisReference | _: ScSuperReference => true
    case _ => false
  }

  def emptyResultSet: Boolean = candidatesSet.isEmpty || levelSet.isEmpty

  protected val qualifiedNamesSet: HashSet[String] = new HashSet[String]
  protected val levelQualifiedNamesSet: HashSet[String] = new HashSet[String]
  protected lazy val placePackageName: String = ResolveUtils.getPlacePackage(ref)
  /**
   * Contains highest precedence of all resolve results.
   * 1 - import a._
   * 2 - import a.x
   * 3 - definition or declaration
   */
  protected var precedence: Int = 0

  /**
   * This method useful for resetting precednce if we dropped
   * all found candidates to seek implicit conversion candidates.
   */
  def resetPrecedence {
    precedence = 0
  }

  protected val levelSet: collection.mutable.HashSet[ScalaResolveResult] = new collection.mutable.HashSet

  /**
   * Do not add ResolveResults through candidatesSet. It may break precedence. Use this method instead.
   */
  protected def addResult(result: ScalaResolveResult): Boolean = {
    lazy val qualifiedName: String = {
      result.element match {
        case c: ScTypeParam => null
        case c: ScObject => "Object:" + c.getQualifiedName
        case c: PsiClass => "Class:" + c.getQualifiedName
        case _ => null
      }
    }
    def addResult {
      if (qualifiedName != null) levelQualifiedNamesSet += qualifiedName
      levelSet += result
    }
    val currentPrecedence = getPrecendence(result)
    if (currentPrecedence < precedence) return false
    else if (currentPrecedence == precedence && levelSet.isEmpty) return false
    else if (currentPrecedence == precedence && !levelSet.isEmpty) {
      if (qualifiedName != null && (levelQualifiedNamesSet.contains(qualifiedName) ||
              qualifiedNamesSet.contains(qualifiedName))) {
        return false
      }
      addResult
    } else {
      if (qualifiedName != null && (levelQualifiedNamesSet.contains(qualifiedName) ||
              qualifiedNamesSet.contains(qualifiedName))) {
        return false
      } else {
        precedence = currentPrecedence
        val newSet = levelSet.filterNot(p => getPrecendence(p) < precedence)
        levelSet.clear
        levelSet ++= newSet
        levelQualifiedNamesSet.clear
        addResult
      }
    }
    true
  }

  protected def getPrecendence(result: ScalaResolveResult): Int = {
    def getPackagePrecedence(qualifier: String): Int = {
      if (qualifier == null) return 6
      val index: Int = qualifier.lastIndexOf('.')
      if (index == -1) return 3
      val q = qualifier.substring(0, index)
      if (q == "java.lang") return 1
      else if (q == "scala") return 2
      else if (q == placePackageName) return 6
      else return 3
    }
    def getClazzPrecedence(clazz: PsiClass): Int = {
      val qualifier = clazz.getQualifiedName
      if (qualifier == null) return 6
      val index: Int = qualifier.lastIndexOf('.')
      if (index == -1) return 6
      val q = qualifier.substring(0, index)
      if (q == "java.lang") return 1
      else if (q == "scala") return 2
      else if (clazz.getContainingFile == ref.getContainingFile) return 6
      else return 3
    }
    if (result.importsUsed.size == 0) {
      ScalaPsiUtil.nameContext(result.getActualElement) match {
        case synthetic: ScSyntheticClass => return 2 //like scala.Int
        case obj: ScObject if obj.isPackageObject => {
          val qualifier = obj.getQualifiedName
          return getPackagePrecedence(qualifier)
        }
        case pack: PsiPackage => {
          val qualifier = pack.getQualifiedName
          return getPackagePrecedence(qualifier)
        }
        case clazz: PsiClass => {
          return getClazzPrecedence(clazz)
        }
        case _: ScBindingPattern | _: PsiMember => {
          val clazzStub = ScalaPsiUtil.getContextOfType(result.getActualElement, false, classOf[PsiClass])
          val clazz: PsiClass = clazzStub match {
            case clazz: PsiClass => clazz
            case _ => null
          }
          //val clazz = PsiTreeUtil.getParentOfType(result.getActualElement, classOf[PsiClass])
          if (clazz == null) return 6
          else {
            clazz.getQualifiedName match {
              case "scala.Predef" => return 2
              case "scala.LowPriorityImplicits" => return 2
              case "scala" => return 2
              case _ => return 6
            }
          }
        }
        case _ =>
      }
      return 6
    }
    val importUsed: ImportUsed = result.importsUsed.toSeq.apply(0)
    // TODO this conflates imported functions and imported implicit views. ScalaResolveResult should really store
    //      these separately.
    importUsed match {
      case _: ImportWildcardSelectorUsed => return 4
      case _: ImportSelectorUsed => return 5
      case ImportExprUsed(expr) => {
        if (expr.singleWildcard) return 4
        else return 5
      }
    }
  }

  override def changedLevel = {
    def update: Boolean = {
      candidatesSet ++= levelSet
      qualifiedNamesSet ++= levelQualifiedNamesSet
      levelSet.clear
      levelQualifiedNamesSet.clear
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
    return ResolveUtils.isAccessible(memb, place)
  }

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return true
      named match {
        case o: ScObject if o.isPackageObject && JavaPsiFacade.getInstance(element.getProject).
                findPackage(o.getQualifiedName) != null =>
        case pack: PsiPackage =>
          addResult(new ScalaResolveResult(ScPackageImpl(pack), getSubst(state), getImports(state)))
        case clazz: PsiClass if !isThisOrSuperResolve || PsiTreeUtil.isContextAncestor(clazz, ref, true) =>
          addResult(new ScalaResolveResult(named, getSubst(state),
            getImports(state), boundClass = getBoundClass(state), fromType = getFromType(state)))
        case clazz: PsiClass => //do nothing, it's wrong class or object
        case _ =>
          addResult(new ScalaResolveResult(named, getSubst(state),
            getImports(state), boundClass = getBoundClass(state), fromType = getFromType(state)))
      }
    }
    return true
  }

  protected def nameAndKindMatch(named: PsiNamedElement, state: ResolveState): Boolean = {
    val nameSet = state.get(ResolverEnv.nameKey)
    val elName = if (nameSet == null) {
      val name = named.getName
      if (name == null) return false
      if (name.charAt(0) == '`') name.substring(1, name.length - 1) else name
    } else if (nameSet.charAt(0) == '`') nameSet.substring(1, nameSet.length - 1) else nameSet
    elName == name && kindMatches(named)
  }

  override def getHint[T](hintKey: Key[T]): T = {
    if (hintKey == NameHint.KEY && name != "") ScalaNameHint.asInstanceOf[T]
    else super.getHint(hintKey)
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    candidatesSet ++ levelSet
  }

  object ScalaNameHint extends NameHint {
    def getName(state: ResolveState) = {
      val stateName = state.get(ResolverEnv.nameKey)
      if (stateName == null) name else stateName
    }
  }
}
