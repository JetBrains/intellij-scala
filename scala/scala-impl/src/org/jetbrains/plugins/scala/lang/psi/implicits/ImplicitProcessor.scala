package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiFile, PsiNamedElement, ResolveState}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.ScPackageLike
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtensionBody, ScFunction, ScPatternDefinition, ScTypeAlias, ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{JavaArrayType, ParameterizedType, StdType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, Context, ScAbstractType, ScAndType, ScCompoundType, ScExistentialArgument, ScExistentialType, ScMatchType, ScOrType, ScParameterizedType, ScType, api}
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence._
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, ScalaResolveState, StdKinds}
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectPsiElementExt}

import scala.annotation.tailrec
import scala.collection.mutable

abstract class ImplicitProcessor(
  override protected val getPlace: PsiElement,
  protected val withoutPrecedence: Boolean
) extends BaseProcessor(StdKinds.refExprLastRef)(getPlace.projectContext)
  with SubstitutablePrecedenceHelper {

  private object ImplicitStrategy extends NameUniquenessStrategy

  override protected def nameUniquenessStrategy: NameUniquenessStrategy = ImplicitStrategy

  override protected val precedenceHolder: TopPrecedenceHolder = new MappedTopPrecedenceHolder(nameUniquenessStrategy)

  override protected def clearLevelQualifiedSet(): Unit = {
    //optimisation, do nothing
  }

  override protected def addResults(results: Iterable[ScalaResolveResult]): Boolean = {
    if (withoutPrecedence) {
      results.foreach(getLevelSet.add)
      true
    } else super.addResults(results)
  }

  override def changedLevel: Boolean = {
    if (!levelSet.isEmpty) {
      val iterator = levelSet.iterator()

      while (iterator.hasNext) {
        candidatesSet = candidatesSet + iterator.next()
      }

      uniqueNamesSet.addAll(levelUniqueNamesSet)
      levelSet.clear()
      levelUniqueNamesSet.clear()
    }
    true
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    var res      = candidatesSet
    val iterator = levelSet.iterator()

    while (iterator.hasNext) {
      res = res + iterator.next()
    }

    res
  }

  override protected def isCheckForEqualPrecedence = false

  override def isImplicitProcessor: Boolean = true

  protected def treeWalkUp(): Unit = {
    val isScala3 = getPlace.isInScala3File

    @tailrec
    def treeWalkUp(@Nullable element: PsiElement, @Nullable lastParent: PsiElement): Unit =
      if (element != null &&
        element.processDeclarations(this, ScalaResolveState.empty, lastParent, getPlace)) {

        val shouldStop =
          element match {
            case expr: ScExpression =>
              isScala3 &&
                !expr.contextFunctionParameters().forall(
                  _.forall(
                    this.execute(_, ScalaResolveState.empty)
                  )
                )
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
  }

  final def candidatesByPlace: Set[ScalaResolveResult] = {
    treeWalkUp()
    candidatesS
  }

  final def candidatesByType(expandedType: ScType): Set[ScalaResolveResult] = {
    val shouldExcludePackagePrefix =
      getPlace.isInScala3File || getPlace.source3Options.packagePrefixImplicits

    val includePackagePrefix =
      !shouldExcludePackagePrefix || getPlace.isSource3MigrationEnabled

    val scopeParts =
      ImplicitProcessor
        .findImplicitScopeParts(
          expandedType.removeAliasDefinitionsAndReduceMatchTypes()(Context(getPlace)),
          getPlace.resolveScope,
          includePackagePrefix
        )

    scopeParts.foreach(partTpe =>
      processType(partTpe, getPlace, ScalaResolveState.withImplicitScopeType(partTpe))
    )

    candidatesS
  }
}

object ImplicitProcessor {
  def isAccessible(namedElement: PsiNamedElement, place: PsiElement): Boolean = {
    (namedElement match {
      case f: ScFunction              => ResolveUtils.isAccessible(f, place)
      case inNameContext(m: ScMember) => ResolveUtils.isAccessible(m, place)
      case _                          => true
    }) && !lowerInFileWithoutType(namedElement, place)
  }

  private def lowerInFileWithoutType(element: PsiElement, place: PsiElement) = {
    val commonContext = PsiTreeUtil.findCommonContext(element, place)

    def contextFile(e: PsiElement) = Option(PsiTreeUtil.getContextOfType(e, classOf[PsiFile]))

    def lowerInFile =
      strictlyOrderedByContext(
        before   = place,
        after    = element,
        topLevel = Option(commonContext)
      )

    if (place == commonContext || contextFile(element) != contextFile(place)) false
    else
      element match {
        case fun: ScFunction if fun.returnTypeElement.isEmpty && !fun.isExtensionMethod => lowerInFile
        case ScalaPsiUtil.inNameContext(pd: ScPatternDefinition)
          if pd.typeElement.isEmpty =>
          lowerInFile
        case _ => false
      }
  }

  private def findImplicitScopeParts(
    `type`:               ScType,
    scope:                GlobalSearchScope,
    includePackagePrefix: Boolean
  )(implicit
    projectContext: ProjectContext,
    context: Context
  ): Seq[ScType] = {
    val implicitObjectsCache = ScalaPsiManager.instance.collectImplicitObjectsCache
    val cacheKey             = (`type`, scope, includePackagePrefix)

    implicitObjectsCache.get(cacheKey) match {
      case null =>
        val implicitObjects =
          findImplicitObjectsImpl(
            `type`, includePackagePrefix
          )(ElementScope(projectContext.project, scope), context)

        implicitObjectsCache.put(cacheKey, implicitObjects)
        implicitObjects
      case cached => cached
    }
  }

  private[this] def findImplicitObjectsImpl(
    `type`:               ScType,
    includePackagePrefix: Boolean
  )(implicit
    elementScope: ElementScope,
    context: Context
  ): Seq[ScType] = {
    val visited   = mutable.HashSet.empty[ScType]
    val parts     = mutable.Queue.empty[ScType]
    val pathTerms = mutable.HashSet.empty[ScType]

    def collectPartsIterable(iterable: IterableOnce[ScType]): Unit = {
      val iterator = iterable.iterator
      while (iterator.hasNext) {
        collectParts(iterator.next())
      }
    }

    def collectPartsTypeResult(tr: TypeResult): Unit =
      tr.foreach(collectParts(_))

    // Java Raw types are converted to F[ScExistentialArgument.Deferred("A", .....), ...]
    // In combination with F-Bounds this can lead to different instantiations that are not ==,
    // but would not reveal further parts of the type.
    //
    // Here, we convert such existential arguments to stand-in types that have a useful
    // equals/hashCode implementation, and use this as the marker in the `visitedType` set.
    def convertRawArgs(tp: ScType): ScType = {
      def rawArgToDummy(typeArgType: ScType): ScType = typeArgType match {
        case existentialArgument: ScExistentialArgument =>
          existentialArgument.typeParamOfRawArg match {
            case Some(typeParamRaw) =>
              ScAbstractType(typeParamRaw, lower = api.Nothing, upper = api.Any)
            case None =>
              typeArgType
          }
        case tp => tp
      }

      def isRawArg(tp: ScType): Boolean = tp match {
        case existentialArgument: ScExistentialArgument =>
          existentialArgument.typeParamOfRawArg.isDefined
        case _ =>
          false
      }

      tp match {
        case ParameterizedType(des, targs) =>
          if (targs.exists(isRawArg)) {
            val targsNew = targs.map(rawArgToDummy)
            val tpNew = ScParameterizedType(des, targsNew)
            tpNew
          } else
            tp
        case _ =>
          tp
      }
    }

    def collectPartsFromSuperTypes(clazz: PsiClass, subst: ScSubstitutor): Unit =
      clazz match {
        case td: ScTemplateDefinition =>
          collectPartsIterable(td.superTypes.map(subst))
          td.selfType.foreach(stpe => collectParts(subst(stpe)))
        case clazz: PsiClass =>
          collectPartsIterable(clazz.getSuperTypes.map(t => subst(t.toScType())))
      }

    /**
     * In scala 3 references to packages and package objects are anchors only under -source:3.0-migration.
     * https://dotty.epfl.ch/3.0.0/docs/reference/changed-features/implicit-resolution.html
     */
    def processPackagePrefix(pack: ScPackageLike): Unit =
      if (includePackagePrefix) {
        for {
          packageObject <- pack.findPackageObject(elementScope.scope)
          designator     = ScDesignatorType(packageObject)
        } parts += designator
        pack.parentScalaPackage.foreach(processPackagePrefix)
      }

    /**
     * See: [[https://docs.scala-lang.org/scala3/reference/changed-features/implicit-resolution.html#:~:text=of%20a%20type%3A-,Definition,-%3A%20A%20reference]]
     */
    def isAnchor(element: PsiNamedElement): Boolean =
      if (!element.isInScala3File) false
      else element match {
        case _: PsiClass                  => true
        case alias: ScTypeAliasDefinition => alias.isEffectivelyOpaque || alias.isMatchTypeAlias
        case _: ScTypeAliasDeclaration    => true
        case _                            => false
      }

    @tailrec
    def collectTermsFromPath(path: ScType): Unit = {
      def isValueAlias(pat: ScBindingPattern): Boolean = pat.`type`().exists {
        case downer: DesignatorOwner => downer.isSingleton
        case _                       => false
      }

      path match {
        case des @ ScDesignatorType(elem) => elem match {
          case pat: ScBindingPattern if isValueAlias(pat)          => ()
          case _: ScBindingPattern | _: ScFieldId | _: ScParameter => pathTerms.add(des)
          case _                                                   => ()
        }
        case proj @ ScProjectionType(prefix, elem) =>
          elem match {
            case pat: ScBindingPattern if isValueAlias(pat)          => ()
            case _: ScBindingPattern | _: ScFieldId | _: ScParameter => pathTerms.add(proj)
            case _                                                   => ()
          }
          collectTermsFromPath(prefix)
        case _ => ()
      }
    }

    def collectTypeAliasDefinitionParts(tp: ScType, tdef: ScTypeAliasDefinition): Unit =
      if (tdef.isEffectivelyOpaque) parts += tp
      else if (tdef.isMatchTypeAlias) {
        val matchType = tdef.aliasedType.map(_.asInstanceOf[ScMatchType])
        val upperBound = matchType.toOption.flatMap(_.upperBound)
        upperBound.foreach(collectParts(_))
      }

    def collectParts(tp: ScType, dealias: Boolean = true): Unit = {
      ProgressManager.checkCanceled()

      val tpWithRawTypesConverted = convertRawArgs(tp)
      if (!visited.add(tpWithRawTypesConverted))
        return

      if (dealias) {
        tp match {
          case AliasType(_, _, Right(t), _) => collectParts(t)
          case _                            => ()
        }
      }

      tp match {
        case ScDesignatorType(v: ScBindingPattern) => collectPartsTypeResult(v.`type`())
        case ScDesignatorType(v: ScFieldId)        => collectPartsTypeResult(v.`type`())
        case ScDesignatorType(p: ScParameter)      => collectPartsTypeResult(p.insideParamType)
        case ScCompoundType(comps, _, _)           => collectPartsIterable(comps)
        case ScAndType(lhs, rhs)                   => collectParts(lhs); collectParts(rhs)
        case ScOrType(lhs, rhs)                    => collectParts(lhs); collectParts(rhs)
        case ScDesignatorType(alias: ScTypeAliasDefinition) => collectTypeAliasDefinitionParts(tp, alias)
        case ScDesignatorType(alias: ScTypeAliasDeclaration) if alias.isInScala3File => parts += tp
        case ParameterizedType(a: ScAbstractType, args) =>
          collectParts(a)
          collectPartsIterable(args)
        case p @ ParameterizedType(des, args) =>
          val dealias = des match {
            //In scala 3 you can have parameterless type aliases designated to classes with
            //type parameters:
            //type Foo = ClassWithTypeParams; Foo[Int]
            //if tp = Foo[Int] we should not try to further
            //expand Foo into [T] ClassWithTypeParams[T]
            case DesignatorOwner(_: ScTypeAlias) => false
            case _                               => true
          }

          p.extractClassType match {
            case Some((clazz, subst)) =>
              parts += des
              collectParts(des, dealias = dealias)
              collectPartsIterable(args)
              collectPartsFromSuperTypes(clazz, subst)
            case _ =>
              collectParts(des, dealias = dealias)
              collectPartsIterable(args)
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
          val element = proj.actualElement

          if (isAnchor(element)) collectTermsFromPath(projected)

          element match {
            case v: ScBindingPattern         => collectPartsTypeResult(v.`type`().map(proj.actualSubst))
            case v: ScFieldId                => collectPartsTypeResult(v.`type`().map(proj.actualSubst))
            case v: ScParameter              => collectPartsTypeResult(v.insideParamType.map(proj.actualSubst))
            case tdef: ScTypeAliasDefinition => collectTypeAliasDefinitionParts(tp, tdef)
            case v: ScTypeAliasDeclaration if v.isInScala3File => parts += tp
            case _                                             => ()
          }

          tp.extractClassType match {
            case Some((clazz, subst)) =>
              parts += tp
              collectPartsFromSuperTypes(clazz, subst)
            case _ =>
          }
        case ScAbstractType(_, _, upper) => collectParts(upper)
        case ScExistentialType(quant, _) => collectParts(quant)
        case tpt: TypeParameterType      => collectParts(tpt.upperType)
        case _ =>
          tp.extractClassType match {
            case Some((clazz, subst)) =>
              parts += tp
              val packagePrefix = clazz.parentOfType(classOf[ScPackageLike], strict = false)
              packagePrefix.foreach(processPackagePrefix)
              collectPartsFromSuperTypes(clazz, subst)
            case _ =>
          }
      }
    }

    collectParts(`type`)
    val res = mutable.HashMap.empty[String, Seq[ScType]]

    def addResult(fqn: String, tp: ScType)(implicit context: Context): Unit = {
      res.get(fqn) match {
        case Some(s) =>
          if (s.forall(!_.equiv(tp))) {
            res.remove(fqn)
            res += ((fqn, s :+ tp))
          }
        case None => res += ((fqn, Seq(tp)))
      }
    }

    def workWithTypeAlias(alias: ScTypeAlias, subst: ScSubstitutor = ScSubstitutor.empty)(implicit context: Context): Unit = alias match {
      case alias: ScTypeAliasDefinition =>
        if (alias.isEffectivelyOpaque) {
          for (fqn <- alias.qualifiedNameOpt;
               companionObject <- elementScope.getCachedObject(fqn)) {
            addResult(fqn, ScDesignatorType(companionObject))
          }
        } else {
          collectObjects(subst(alias.aliasedType.getOrAny))
        }
      case declaration: ScTypeAliasDeclaration if declaration.isInScala3File =>
        for (fqn <- alias.qualifiedNameOpt;
             companionObject <- elementScope.getCachedObject(fqn)) {
          addResult(fqn, ScDesignatorType(companionObject))
        }
      case _ =>
    }

    def collectObjects(tp: ScType)(implicit context: Context): Unit =
      tp match {
        case _ if tp.isAny =>
        case tp: StdType if stdTypes.contains(tp.name) =>
          elementScope
            .getCachedObject("scala." + tp.name)
            .foreach(o => addResult(o.qualifiedName, ScDesignatorType(o)))
        case ScDesignatorType(ta: ScTypeAlias) => workWithTypeAlias(ta)
        case ScProjectionType.withActual(ta: ScTypeAlias, actualSubst) => workWithTypeAlias(ta, actualSubst)
        case ParameterizedType(ScDesignatorType(ta: ScTypeAlias), args) =>
          val genericSubst = ScSubstitutor.bind(ta.typeParameters, args)
          workWithTypeAlias(ta, genericSubst)
        case ParameterizedType(ScProjectionType.withActual(ta: ScTypeAliasDefinition, actualSubst), args) =>
          val genericSubst = ScSubstitutor.bind(ta.typeParameters, args)
          val subst        = actualSubst.followed(genericSubst)
          workWithTypeAlias(ta, subst)
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

    while (parts.nonEmpty) {
      collectObjects(parts.dequeue())
    }

    val objects = res.values.flatten.toSeq
    pathTerms.addAll(objects).toSeq
  }

  private[this] val stdTypes =
    Seq("Int", "Float", "Double", "Boolean", "Byte", "Short", "Long", "Char")

  def isDeclaredOrExportedInExtension(element: PsiNamedElement, state: ResolveState): Boolean =
    element match {
      case fn: ScFunction => fn.isExtensionMethod || state.exportedInfo.exists(_.exportedIn.is[ScExtensionBody])
      case _              => false
    }
}
