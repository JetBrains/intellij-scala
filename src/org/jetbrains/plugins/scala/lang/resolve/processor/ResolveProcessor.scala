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
import psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportWildcardSelectorUsed, ImportUsed}
import psi.impl.toplevel.synthetic.ScSyntheticClass
import psi.impl.ScPackageImpl
import psi.api.statements.params.ScTypeParam
import psi.api.expr.{ScSuperReference, ScThisReference}
import psi.api.statements.ScTypeAlias
import psi.api.toplevel.templates.ScTemplateBody
import reflect.NameTransformer
import collection.mutable.HashSet
import psi.api.toplevel.typedef.{ScClass, ScTrait, ScMember, ScObject}
import psi.api.toplevel.ScNamedElement

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
  def resetPrecedence() {
    precedence = 0
  }

  protected val levelSet: collection.mutable.HashSet[ScalaResolveResult] = new collection.mutable.HashSet

  /**
   * Do not add ResolveResults through candidatesSet. It may break precedence. Use this method instead.
   */
  protected def addResult(result: ScalaResolveResult): Boolean = addResults(Seq(result))
  protected def addResults(results: Seq[ScalaResolveResult]): Boolean = {
    if (results.length == 0) return true
    lazy val qualifiedName: String = {
      results(0).getActualElement match {
        case c: ScTypeParam => null
        case c: ScObject => "Object:" + c.getQualifiedName
        case c: PsiClass => "Class:" + c.getQualifiedName
        case t: ScTypeAlias if t.getParent.isInstanceOf[ScTemplateBody] &&
          t.getContainingClass != null => "TypeAlias:" + t.getContainingClass.getQualifiedName + "#" + t.getName
        case p: PsiPackage => "Package:" + p.getQualifiedName
        case _ => null
      }
    }
    def addResults() {
      if (qualifiedName != null) levelQualifiedNamesSet += qualifiedName
      levelSet ++= results
    }
    val currentPrecedence = getPrecendence(results(0))
    if (currentPrecedence < precedence) return false
    else if (currentPrecedence == precedence && levelSet.isEmpty) return false
    else if (currentPrecedence == precedence && !levelSet.isEmpty) {
      if (qualifiedName != null && (levelQualifiedNamesSet.contains(qualifiedName) ||
              qualifiedNamesSet.contains(qualifiedName))) {
        return false
      }
      addResults()
    } else {
      if (qualifiedName != null && (levelQualifiedNamesSet.contains(qualifiedName) ||
              qualifiedNamesSet.contains(qualifiedName))) {
        return false
      } else {
        precedence = currentPrecedence
        val newSet = levelSet.filterNot(p => getPrecendence(p) < precedence)
        levelSet.clear()
        levelSet ++= newSet
        levelQualifiedNamesSet.clear()
        addResults()
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
      else if (PsiTreeUtil.isContextAncestor(clazz.getContainingFile, ref, true)) return 6
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
    val importsUsedSeq = result.importsUsed.toSeq
    val importUsed: ImportUsed = importsUsedSeq.apply(importsUsedSeq.length - 1)
    // TODO this conflates imported functions and imported implicit views. ScalaResolveResult should really store
    //      these separately.
    importUsed match {
      case _: ImportWildcardSelectorUsed => 4
      case _: ImportSelectorUsed => 5
      case ImportExprUsed(expr) => {
        if (expr.singleWildcard) 4
        else 5
      }
    }
  }

  override def changedLevel = {
    def update: Boolean = {
      candidatesSet ++= levelSet
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
    true
  }

  protected def nameAndKindMatch(named: PsiNamedElement, state: ResolveState): Boolean = {
    val nameSet = state.get(ResolverEnv.nameKey)
    val elName = if (nameSet == null) {
      val name = named match {
        case named: ScNamedElement => named.name //todo: it's not the same. It can't be class name in scala.
          //so class name in ScFunctionImpl.getName for constructors is only for Java
        case _ => named.getName
      }
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
    val res = candidatesSet ++ levelSet
    /*
    This code works in following way:
    if we found to Types (from types namespace), which import is the same,
    then type from package object have bigger priority.
    The same for Values (terms namespace).
    Actually such behaviour is undefined (spec 9.3), but this code is closer to compiler behaviour.
     */
    if (res.size > 1) {
      val problems = res.filter(r => ScalaPsiUtil.nameContext(r.getActualElement) match {
        case memb: ScMember =>
          memb.getContext.isInstanceOf[ScTemplateBody] && memb.getContainingClass.isInstanceOf[ScObject] &&
            memb.getContainingClass.asInstanceOf[ScObject].isPackageObject
        case _ => false
      }).map(r => {
        val elem = r.getActualElement
        val context = ScalaPsiUtil.nameContext(elem)
        val pref = context.asInstanceOf[ScMember].getContainingClass.getQualifiedName
        elem match {
          case _: ScClass | _: ScTrait | _: ScTypeAlias => "Type:" + pref + "." + elem.getName
          case _ => "Value:" + pref + "." + elem.getName
        }
      })

      res.filter(r => r.getActualElement match {
        case o: ScObject => !problems.contains("Value:" + o.getQualifiedName)
        case c: PsiClass => !problems.contains("Type:" + c.getQualifiedName)
        case _ => true
      })
    } else res
  }

  object ScalaNameHint extends NameHint {
    def getName(state: ResolveState) = {
      val stateName = state.get(ResolverEnv.nameKey)
      if (stateName == null) name else stateName
    }
  }
}
