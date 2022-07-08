package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil.isContextAncestor
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import gnu.trove.{THashMap, THashSet}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.ScPackageLike
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{JavaArrayType, ParameterizedType, StdType, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, ScAbstractType, ScCompoundType, ScExistentialArgument, ScExistentialType, ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence._
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, ScalaResolveState, StdKinds}
import org.jetbrains.plugins.scala.project.ProjectContext

import java.{util => ju}
import scala.annotation.tailrec
import scala.collection.mutable

/**
  * This class mark processor that only implicit object important among all PsiClasses
  */
abstract class ImplicitProcessor(override protected val getPlace: PsiElement,
                                 protected val withoutPrecedence: Boolean)
  extends BaseProcessor(StdKinds.refExprLastRef)(getPlace.projectContext)
    with SubstitutablePrecedenceHelper {

  private object ImplicitStrategy extends NameUniquenessStrategy

  override protected def nameUniquenessStrategy: NameUniquenessStrategy = ImplicitStrategy

  override protected val holder: TopPrecedenceHolder = new MappedTopPrecedenceHolder(nameUniquenessStrategy)

  private[this] val levelMap: ju.Map[ScalaResolveResult, ju.Set[ScalaResolveResult]] =
    new THashMap[ScalaResolveResult, ju.Set[ScalaResolveResult]](nameUniquenessStrategy)

  override protected def clearLevelQualifiedSet(result: ScalaResolveResult): Unit = {
    //optimisation, do nothing
  }

  override protected def getLevelSet(result: ScalaResolveResult): ju.Set[ScalaResolveResult] = {
    var levelSet = levelMap.get(result)
    if (levelSet == null) {
      levelSet = new THashSet[ScalaResolveResult]()
      levelMap.put(result, levelSet)
    }
    levelSet
  }

  override protected def addResults(results: Iterable[ScalaResolveResult]): Boolean = {
    if (withoutPrecedence) {
      candidatesSet ++= results
      true
    } else super.addResults(results)
  }

  override def changedLevel: Boolean = {
    if (levelMap.isEmpty) return true
    val iterator = levelMap.values().iterator()
    while (iterator.hasNext) {
      val setIterator = iterator.next().iterator()
      while (setIterator.hasNext) {
        candidatesSet = candidatesSet union Set(setIterator.next)
      }
    }
    uniqueNamesSet.addAll(levelUniqueNamesSet)
    levelMap.clear()
    levelUniqueNamesSet.clear()
    true
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    val res = candidatesSet
    val iterator = levelMap.values().iterator()
    while (iterator.hasNext) {
      val setIterator = iterator.next().iterator()
      while (setIterator.hasNext) {
        candidatesSet = candidatesSet union Set(setIterator.next)
      }
    }
    res
  }

  override protected def isCheckForEqualPrecedence = false

  override def isImplicitProcessor: Boolean = true

  final def candidatesByPlace: Set[ScalaResolveResult] = {
    val isScala3 = getPlace.isInScala3File

    @tailrec
    def treeWalkUp(element: PsiElement, lastParent: PsiElement): Unit =
      if (element != null &&
        element.processDeclarations(this, ScalaResolveState.empty, lastParent, getPlace)) {

        val shouldStop =
          element match {
            case expr: ScExpression =>
              isScala3 &&
                !expr.contextFunctionParameters.forall(this.execute(_, ScalaResolveState.empty))
            case _ => false
          }

        val isNewLevel = element match {
          case _: ScTemplateBody | _: ScExtendsBlock => true // template body and inherited members are at the same level
          case _                                     => changedLevel
        }

        if (isNewLevel && !shouldStop) {
          treeWalkUp(element.getContext, element)
        }
      }

    treeWalkUp(getPlace, null)
    candidatesS
  }

  final def candidatesByType(expandedType: ScType): Set[ScalaResolveResult] = {
    ImplicitProcessor
      .findImplicitObjects(expandedType.removeAliasDefinitions(), getPlace.resolveScope)
      .foreach(objectTpe =>
        processType(objectTpe, getPlace, ScalaResolveState.withImplicitScopeObject(objectTpe))
      )
    candidatesS
  }
}

object ImplicitProcessor {

  def isAccessible(namedElement: PsiNamedElement, place: PsiElement): Boolean =
    namedElement match {
      case f: ScFunction              => ResolveUtils.isAccessible(f, place) && checkFunctionIsEligible(f, place)
      case inNameContext(m: ScMember) => ResolveUtils.isAccessible(m, place)
      case _                          => true
    }

  private def checkFunctionIsEligible(function: ScFunction, place: PsiElement): Boolean = {

    if (function.hasExplicitType || !isContextAncestor(function.getContainingFile, place, false))
      return true

    val commonContext = PsiTreeUtil.findCommonContext(function, place)

    //weird case, it covers situation, when function comes from object, not treeWalkUp
    if (place == commonContext)
      return true

    if (function == commonContext)
      return false

    strictlyOrderedByContext(before = function, after = place, topLevel = Some(commonContext))
  }


  private def findImplicitObjects(`type`: ScType, scope: GlobalSearchScope)
                                 (implicit context: ProjectContext): Seq[ScType] = {
    val implicitObjectsCache = ScalaPsiManager.instance.collectImplicitObjectsCache
    val cacheKey = (`type`, scope)

    implicitObjectsCache.get(cacheKey) match {
      case null =>
        val implicitObjects = findImplicitObjectsImpl(`type`)(ElementScope(context.project, scope))
        implicitObjectsCache.put(cacheKey, implicitObjects)
        implicitObjects
      case cached => cached
    }
  }

  private[this] def findImplicitObjectsImpl(`type`: ScType)
                                           (implicit elementScope: ElementScope): Seq[ScType] = {
    val visited = mutable.HashSet.empty[ScType]
    val parts = mutable.Queue.empty[ScType]

    def collectPartsIter(iterable: IterableOnce[ScType]): Unit = {
      val iterator = iterable.iterator
      while (iterator.hasNext) {
        collectParts(iterator.next())
      }
    }

    def collectPartsTr(tr: TypeResult): Unit =
      tr.foreach(collectParts)

    // Java Raw types are converted to F[ScExistentialArgument.Deferred("A", .....), ...]
    // In combination with F-Bounds this can lead to different instantiations that are not ==,
    // but would not reveal further parts of the type.
    //
    // Here, we convert such existential arguments to stand-in types that have a useful
    // equals/hashCode implementation, and use this as the marker in the `visitedType` set.
    def convertRawArgs(tp: ScType): ScType = {
      def rawArgToDummy(tp: ScType) = tp match {
        case existentialArgument: ScExistentialArgument =>
          existentialArgument.typeParamOfRawArg match {
            case Some(typeParam) =>
              ScAbstractType(typeParam, existentialArgument.lower, existentialArgument.upper)
            case None =>
              tp
          }
        case tp => tp
      }
      def isRawArg(tp: ScType) = tp match {
        case existentialArgument: ScExistentialArgument =>
          existentialArgument.typeParamOfRawArg.isDefined
        case _ => false
      }
      tp match {
        case ParameterizedType(des, targs) =>
          if (targs.exists(isRawArg)) {
            val targs1 = targs.map(rawArgToDummy)
            ScParameterizedType(des, targs1)
          } else tp
        case _ => tp
      }
    }

    def collectParts(tp: ScType): Unit = {
      ProgressManager.checkCanceled()
      if (!visited.add(convertRawArgs(tp))) return

      tp match {
        case AliasType(_, _, Right(t)) => collectParts(t)
        case _                         =>
      }

      def collectSupers(clazz: PsiClass, subst: ScSubstitutor): Unit =
        clazz match {
          case td: ScTemplateDefinition =>
            collectPartsIter(td.superTypes.map(subst))
            td.selfType.foreach(stpe => collectParts(subst(stpe)))
          case clazz: PsiClass => collectPartsIter(clazz.getSuperTypes.map(t => subst(t.toScType())))
        }

      tp match {
        case ScDesignatorType(v: ScBindingPattern) => collectPartsTr(v.`type`())
        case ScDesignatorType(v: ScFieldId)        => collectPartsTr(v.`type`())
        case ScDesignatorType(p: ScParameter)      => collectPartsTr(p.`type`())
        case ScCompoundType(comps, _, _)           => collectPartsIter(comps)
        case ParameterizedType(a: ScAbstractType, args) =>
          collectParts(a)
          collectPartsIter(args)
        case p @ ParameterizedType(des, args) =>
          p.extractClassType match {
            case Some((clazz, subst)) =>
              parts += des
              collectParts(des)
              collectPartsIter(args)
              collectSupers(clazz, subst)
            case _ =>
              collectParts(des)
              collectPartsIter(args)
          }
        case j: JavaArrayType =>
          val parameterizedType = j.getParameterizedType
          collectParts(
            parameterizedType.getOrElse(
              return
            )
          )
        case proj @ ScProjectionType(projected, _) =>
          collectParts(projected)
          proj.actualElement match {
            case v: ScBindingPattern => collectPartsTr(v.`type`().map(proj.actualSubst))
            case v: ScFieldId        => collectPartsTr(v.`type`().map(proj.actualSubst))
            case v: ScParameter      => collectPartsTr(v.`type`().map(proj.actualSubst))
            case _                   =>
          }

          tp.extractClassType match {
            case Some((clazz, subst)) =>
              parts += tp
              collectSupers(clazz, subst)
            case _ =>
          }
        case ScAbstractType(_, _, upper) => collectParts(upper)
        case ScExistentialType(quant, _) => collectParts(quant)
        case tpt: TypeParameterType      => collectParts(tpt.upperType)
        case _ =>
          tp.extractClassType match {
            case Some((clazz, subst)) =>
              parts += tp

              @tailrec
              def packageObjectsInImplicitScope(packOpt: Option[ScPackageLike]): Unit = packOpt match {
                case Some(pack) =>
                  for {
                    packageObject <- pack.findPackageObject(elementScope.scope)
                    designator = ScDesignatorType(packageObject)
                  } parts += designator
                  packageObjectsInImplicitScope(pack.parentScalaPackage)
                case _ =>
              }

              packageObjectsInImplicitScope(clazz.parentOfType(classOf[ScPackageLike], strict = false))

              collectSupers(clazz, subst)
            case _ =>
          }
      }
    }

    collectParts(`type`)
    val res = mutable.HashMap.empty[String, Seq[ScType]]

    def addResult(fqn: String, tp: ScType): Unit = {
      res.get(fqn) match {
        case Some(s) =>
          if (s.forall(!_.equiv(tp))) {
            res.remove(fqn)
            res += ((fqn, s :+ tp))
          }
        case None => res += ((fqn, Seq(tp)))
      }
    }

    @tailrec
    def collectObjects(tp: ScType): Unit = {
      tp match {
        case _ if tp.isAny =>
        case tp: StdType if Seq("Int", "Float", "Double", "Boolean", "Byte", "Short", "Long", "Char").contains(tp.name) =>
          elementScope.getCachedObject("scala." + tp.name)
            .foreach { o =>
              addResult(o.qualifiedName, ScDesignatorType(o))
            }
        case ScDesignatorType(ta: ScTypeAliasDefinition) => collectObjects(ta.aliasedType.getOrAny)
        case ScProjectionType.withActual(actualElem: ScTypeAliasDefinition, actualSubst) =>
          collectObjects(actualSubst(actualElem.aliasedType.getOrAny))
        case ParameterizedType(ScDesignatorType(ta: ScTypeAliasDefinition), args) =>
          val genericSubst = ScSubstitutor.bind(ta.typeParameters, args)
          collectObjects(genericSubst(ta.aliasedType.getOrAny))
        case ParameterizedType(ScProjectionType.withActual(actualElem: ScTypeAliasDefinition, actualSubst), args) =>
          val genericSubst = ScSubstitutor.bind(actualElem.typeParameters, args)
          val s = actualSubst.followed(genericSubst)
          collectObjects(s(actualElem.aliasedType.getOrAny))
        case _ =>
          tp.extractClass match {
            case Some(obj: ScObject) => addResult(obj.qualifiedName, tp)
            case Some(clazz) =>
              getCompanionModule(clazz) match {
                case Some(obj: ScObject) =>
                  tp match {
                    case ScProjectionType(proj, _) =>
                      addResult(obj.qualifiedName, ScProjectionType(proj, obj))
                    case ParameterizedType(ScProjectionType(proj, _), _) =>
                      addResult(obj.qualifiedName, ScProjectionType(proj, obj))
                    case _ =>
                      addResult(obj.qualifiedName, ScDesignatorType(obj))
                  }
                case _ =>
              }
            case _ =>
          }
      }
    }

    while (parts.nonEmpty) {
      collectObjects(parts.dequeue())
    }

    res.values.flatten.toSeq
  }
}