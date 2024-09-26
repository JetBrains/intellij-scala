package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.application.{ApplicationListener, ApplicationManager}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement, ScTypeProjection}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScExpressionExt, ScFunctionDefinition, ScTypeAlias, ScTypeAliasDefinition, ScValueOrVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.{ScalaTypePresentation, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.{isOperatorName, qualifiedName, splitName}
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveState}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.smartEquivalence

import scala.annotation.{nowarn, tailrec}
import scala.collection.immutable.ArraySeq
import scala.collection.mutable

/**
 * @note Not thread safe, must be used in a single thread. Currently marked as usable only on the UI thread.
 */
private[scala] final class TypeAdjuster {
  private val markedElements: mutable.ArrayBuffer[SmartPsiElementPointer[PsiElement]] =
    mutable.ArrayBuffer.empty[SmartPsiElementPointer[PsiElement]]

  @RequiresEdt
  def markToAdjust(element: PsiElement): Unit = {
    if (element != null && element.isValid) {
      markedElements += element.createSmartPointer
    }
  }

  @RequiresEdt
  def adjustTypes(): Unit = {
    if (markedElements.nonEmpty) {
      val head = markedElements.head
      PsiDocumentManager.getInstance(head.getProject).commitAllDocuments()

      val elements = markedElements.iterator.collect {
        case ValidSmartPointer(element) => element
      }.to(ArraySeq)
      markedElements.clear()

      TypeAdjuster.adjustFor(elements)
    }
  }
}

/**
 * ATTENTION: Description copied from commit message of commit 28489efa
 *
 * Type adjuster works in three steps:<br>
 * 1. collects all information about ScTypeElement's in the given PsiElements<br>
 * 2. replaces all type elements with simplified ones<br>
 * 3. adds imports for all replaced types<br>
 *
 * Caches are invalidated only twice, after 2) and 3)
 * Also it is possible to postpone adjusting types to the end of a write action.
 */
object TypeAdjuster extends ApplicationListener {

  private val LOG = Logger.getInstance(classOf[TypeAdjuster])

  {
    ApplicationManager.getApplication.addApplicationListener(this): @nowarn("cat=deprecation")
  }

  private val globalAdjuster: TypeAdjuster = new TypeAdjuster()

  override def writeActionFinished(action: Any): Unit = {
    globalAdjuster.adjustTypes()
  }

  def markToAdjust(element: PsiElement): Unit = {
    globalAdjuster.markToAdjust(element)
  }

  def isMarkedForAdjustment(element: PsiElement): Boolean =
    if (element != null) globalAdjuster.markedElements.contains(element.createSmartPointer)
    else                 false

  def adjustFor(elements: Seq[PsiElement], addImports: Boolean = true, useTypeAliases: Boolean = true): Unit = {
    val progressManager = ProgressManager.getInstance()
    @Nullable val indicator = progressManager.getProgressIndicator
    if (indicator ne null) {
      indicator.checkCanceled()
      indicator.setIndeterminate(false)
      indicator.setFraction(0)
    }

    val indexedElements = elements.toIndexedSeq

    val infos = {
      val total = indexedElements.length
      indexedElements.zipWithIndex.flatMap { case (element, index) =>
        updateProgress(indicator, index, total)
        collectAdjustableTypeElements(element).distinct.flatMap { typeElement =>
          simplify(SimpleInfo(typeElement)).map { simplified =>
            shortenReference(simplified, useTypeAliases)
          }
        }
      }
    }

    val rewrittenInfos = rewriteInfosAsInfix(infos, indicator)

    // Do not check for cancelation past this point, as PSI is being modified. Otherwise, it is possible to generate
    // code that doesn't compile.
    progressManager.executeNonCancelableSection { () =>
      val replacedAndComputedImports = replaceAndAddImports(rewrittenInfos, addImports, indicator)
      val total = replacedAndComputedImports.size
      replacedAndComputedImports.zipWithIndex.foreach { case ((holder, pathsToAdd), index) =>
        updateProgress(indicator, index, total)
        holder.addImportsForPaths(pathsToAdd.toSeq, null)
      }
    }
  }

  private def updateProgress(@Nullable indicator: ProgressIndicator, index: Int, total: Int): Unit = {
    if (indicator ne null) {
      indicator.checkCanceled()
      val fraction = (index + 1).toDouble / total
      indicator.setFraction(fraction)
    }
  }

  private def newRef(text: String, position: PsiElement): Option[ScReference] =
    findRef(newTypeElem(text, position))

  private def newTypeElem(name: String, position: PsiElement) =
    ScalaPsiElementFactory.createTypeElementFromText(name, position.getContext, position) match {
      case null =>
        LOG.error(s"Cannot create type from text:\n$name", new Throwable)
        null
      case result => result
    }

  private def findRef(element: PsiElement) = element match {
    case reference: ScReference => Some(reference)
    case simple: ScSimpleTypeElement => simple.reference
    case _ => None
  }

  private object ToReplace {

    private[this] val toReplaceKey = Key.create[String]("type.element.to.replace")

    def apply(element: PsiElement): Unit = element.putUserData(toReplaceKey, "")

    def unapply(element: PsiElement): Boolean = element match {
      case typeElement: ScTypeElement => typeElement.getUserData(toReplaceKey) != null
      case _ => false
    }
  }

  private def simplify(info: ReplacementInfo): Option[ReplacementInfo] = {

    object `with .type#` {

      private[this] val Target = ".type#"

      def unapply(info: SimpleInfo): Option[ReplacementInfo] = info.replacement match {
        case text if text.contains(Target) =>
          val replacement = text.replace(Target, ".")
          Some(info.copy(replacement = replacement))
        case _ => None
      }
    }

    object withThisRef {

      private[this] val Target = "this."

      def unapply(info: SimpleInfo): Option[ReplacementInfo] = {
        val SimpleInfo(place, replacement, resolve, _) = info

        replacement.indexOf(Target) match {
          case -1 => None
          case index =>
            replacement.substring(index + Target.length) match {
              case text if text.startsWith("type") && !isIdentifier(text.take(Target.length)) => None
              case text =>
                for {
                  oldRes <- resolve
                  newRef <- newRef(text, place)
                  newRes <- elementToImport(newRef)
                  if smartEquivalence(oldRes, newRes)
                } yield info.copy(replacement = text)
            }
        }
      }

    }

    object withExpandableTypeAlias {

      def unapply(info: SimpleInfo): Option[ReplacementInfo] = {
        val SimpleInfo(place, replacement, _, _) = info

        val replacementType = newTypeElem(replacement, place).calcType
        val withoutAliases = replacementType.removeAliasDefinitions(expandableOnly = true)

        if (replacementType.presentableText(TypePresentationContext.emptyContext) != withoutAliases.presentableText(TypePresentationContext.emptyContext)) {
          ToReplace(place)

          val newTypeElement = newTypeElem(withoutAliases.canonicalText, place)

          val result = collectAdjustableTypeElements(newTypeElement).toList.collect {
            case typeElement if typeElement != newTypeElement => SimpleInfo(typeElement)
          } match {
            case Nil => SimpleInfo(newTypeElement).copy(place = place)
            case childInfos => CompoundInfo(childInfos)(place, newTypeElement)
          }

          Some(result)
        } else None
      }
    }

    if (info.place.parentsInFile.exists(ToReplace.unapply)) None
    else info match {
      case compound: CompoundInfo => Some(compound.flatMap(simplify))
      case withExpandableTypeAlias(newInfo) => simplify(newInfo)
      case withThisRef(newInfo) => simplify(newInfo)
      case `with .type#`(newInfo) => simplify(newInfo)
      case _ => Some(info)
    }
  }

  private def replaceElem: ReplacementInfo => Unit = {
    case SimpleInfo(place, replacement, _, _) if !place.textMatches(replacement) =>
      val maybeNewElement = place match {
        case _: ScTypeElement => Some(newTypeElem(replacement, place))
        case _: ScReference => newRef(replacement, place)
        case _ => None
      }

      maybeNewElement.foreach(place.replace)
    case compound: CompoundInfo =>
      compound.foreach(replaceElem)
      compound.replace()
    case _ =>
  }

  private def shortenReference(info: ReplacementInfo, useTypeAliases: Boolean = true): ReplacementInfo = {
    object hasStableReplacement {

      import ScalaPsiUtil._

      def unapply(info: SimpleInfo): Option[SimpleInfo] = {
        val SimpleInfo(place, _, resolve, _) = info

        resolve.flatMap { resolved =>
          val position = findRef(place).getOrElse(place)

          importAliasFor(resolved, position) match {
            case Some(importAlias) => Some(info.copy(replacement = importAlias.refName))
            case _ =>
              resolved match {
                case named: PsiNamedElement if hasStablePath(named) =>
                  named match {
                    case clazz: PsiClass =>
                      availableTypeAliasFor(clazz, position, useTypeAliases) match {
                        case Some(ta) if !ta.isAncestorOf(position) =>
                          if (hasStablePath(ta)) Some(info.updateTarget(ta))
                          else Some(info.copy(replacement = ta.name))
                        case _ => Some(info.updateTarget(named))
                      }
                    case _: ScTypeAlias |
                         _: ScBindingPattern => Some(info.updateTarget(named))
                    case _ => None
                  }
                case _ => checkQualifier(place)
              }
          }
        }
      }

      private def checkQualifier(element: PsiElement) =
        qualifierInfo(element)
          .flatMap(unapply)
          .filter { info =>
            val SimpleInfo(place, replacement, _, _) = info
            !place.textMatches(replacement)
          }

      @tailrec
      private def qualifierInfo(element: PsiElement): Option[SimpleInfo] = element match {
        case ScSimpleTypeElement(reference: ScReference) => qualifierInfo(reference)
        case ScReference.qualifier(reference: ScReference) => Some(SimpleInfo(reference))
        case _ => None
      }
    }

    info match {
      case compound: CompoundInfo => compound.map(shortenReference(_, useTypeAliases))
      case hasStableReplacement(rInfo) => rInfo
      case _ => info
    }
  }

  private object ToRewrite {

    private[this] val toRewriteKey = Key.create[Int]("type.element.to.rewrite.as.infix")

    def apply(element: PsiElement): Unit = element.putUserData(toRewriteKey, 1)

    def unapply(element: PsiElement): Boolean = element match {
      case typeElement: ScParameterizedTypeElement => typeElement.getUserData(toRewriteKey) == 1
      case _ => false
    }
  }

  private def rewriteInfosAsInfix(infos: IndexedSeq[ReplacementInfo], @Nullable indicator: ProgressIndicator): IndexedSeq[ReplacementInfo] = {
    def infoToMappings(info: ReplacementInfo): List[(String, PsiElement)] = info match {
      case SimpleInfo(place, replacement, _, _) =>
        val maybePair = for {
          ref    <- findRef(place)
          target <- elementToImport(ref)
        } yield replacement -> target

        maybePair.toList
      case CompoundInfo(children) => children.flatMap(infoToMappings)
    }

    object CanBeInfixType {

      def unapply(typeElement: ScTypeElement): Boolean = typeElement match {
        case ScParameterizedTypeElement(ScSimpleTypeElement(ref), Seq(_, _)) =>
          isOperatorName(ref.refName) ||
            ref.bind().collect {
              case ScalaResolveResult(clazz: PsiClass, _) => clazz
            }.toSeq.flatMap {
              _.getAnnotations
            }.exists {
              _.getQualifiedName == "scala.annotation.showAsInfix"
            }
        case _ => false
      }
    }

    def rewriteAsInfix(info: ReplacementInfo): ReplacementInfo = info match {
      case simple: SimpleInfo if simple.place.parentsInFile.exists(ToRewrite.unapply) => simple
      case simple: SimpleInfo =>
        simple.place match {
          case e@CanBeInfixType() =>
            ToRewrite(e)

            val mappings = infos
              .filter(_.place.parentsInFile.contains(e))
              .flatMap(infoToMappings)
              .toMap

            val presentationContext: TypePresentationContext = new TypePresentationContext {
              override def nameResolvesTo(name: String, target: PsiElement): Boolean =
                mappings.get(name).exists(smartEquivalence(_, target))

              override lazy val compoundTypeWithAndToken: Boolean =
                simple.place.containingFile.exists(_.isScala3OrSource3Enabled)
            }

            val newTypeText = e.calcType.presentableText(presentationContext)
            simple.copy(replacement = newTypeText)
          case _ => simple
        }
      case compound: CompoundInfo => compound.map(rewriteAsInfix)
    }

    val total = infos.length
    infos.zipWithIndex.map { case (info, index) =>
      updateProgress(indicator, index, total)
      rewriteAsInfix(info)
    }
  }

  private def collectImportHolders(infos: Set[ReplacementInfo]): Map[ReplacementInfo, ScImportsHolder] = {
    val pathToInfo = mutable.Map.empty[String, Set[ReplacementInfo]]
      .withDefaultValue(Set.empty)

    for {
      info <- infos
      path <- info.pathsToImport
    } pathToInfo(path) += info

    pathToInfo.values
      .flatMap { infos =>
        val infoToHolders = for {
          info <- infos
          place = info.place
        } yield info -> ScImportsHolder(place)

        val holders = infoToHolders.map(_._2)
        val maxHolders = holders.filterNot { holder =>
          holders.exists(_.isAncestorOf(holder))
        }

        infoToHolders.map {
          case (info, holder) => info -> maxHolders.find(PsiTreeUtil.isAncestor(_, holder, false)).get
        }
      }.toMap
  }

  private def replaceAndAddImports(
    infos: IndexedSeq[ReplacementInfo],
    addImports: Boolean,
    @Nullable indicator: ProgressIndicator
  ) = {
    assert(infos.forall(_.place.isValid), "Psi shouldn't be modified before this stage!")

    val (
      sameResolve: Set[ReplacementInfo],
      otherResolve: Set[ReplacementInfo]
    ) = infos.toSet.partition(_.checkReplacementResolve)

    val importHolders = collectImportHolders(otherResolve)

    val result = mutable.Map.empty[ScImportsHolder, Set[String]]
      .withDefaultValue(Set.empty)

    val infosFinal = if (addImports) infos else infos.filter(sameResolve.contains)
    val total = infosFinal.length
    infosFinal.zipWithIndex.foreach { case (info, index) =>
      updateProgress(indicator, index, total)
      replaceElem(info)

      val pathsToImport = info.pathsToImport
      importHolders.get(info) match {
        case Some(holder) if pathsToImport.nonEmpty => result(holder) ++= pathsToImport
        case _ =>
      }
    }

    result.toMap
  }

  private def findIncomingExpressions(position: PsiElement): Seq[ScExpression] = {
    @tailrec
    def findIncomingExpressions(element: PsiElement): Set[ScExpression] =
      element.getContext match {
        case fun: ScFunctionDefinition if fun.returnTypeElement.contains(element) =>
          fun.returnUsages
        case variable: ScValueOrVariableDefinition if variable.typeElement.contains(element) =>
          variable.expr.fold(Set.empty[ScExpression])(_.calculateTailReturns)
        case typeElement: ScTypeElement =>
          findIncomingExpressions(typeElement)
        case _ =>
          Set.empty
      }

    val incomingExpressions = findIncomingExpressions(position)
    incomingExpressions.toSeq
  }

  private def availableTypeAliasFor(clazz: PsiClass, position: PsiElement, useTypeAliases: Boolean): Option[ScTypeAliasDefinition] = {
    if (!useTypeAliases) None
    else {
      val clazzTy = ScDesignatorType(clazz)
      val incomingTypes =
        findIncomingExpressions(position)
          .filter(!_.isInstanceOf[PsiLiteral])
          .flatMap(_.`type`().toOption)
          .filter(!_.isAliasType)

      // Let's not search for type aliases if one of the incoming types is obviously something else
      if (incomingTypes.exists(_ equiv clazzTy)) {
        return None
      }

      class FindTypeAliasProcessor extends BaseProcessor(ValueSet(CLASS))(clazz) {
        var collected: Option[ScTypeAliasDefinition] = None

        override protected def execute(namedElement: PsiNamedElement)
                                      (implicit state: ResolveState): Boolean = namedElement match {
          case ta: ScTypeAliasDefinition if ta.isAliasFor(clazz) &&
            !TypePresentation.shouldExpand(ta) && !ta.isDeprecated =>

            collected = Some(ta)
            false
          case _ => true
        }
      }
      val processor = new FindTypeAliasProcessor
      PsiTreeUtil.treeWalkUp(processor, position, null, ScalaResolveState.empty)
      processor.collected
    }
  }

  private def collectAdjustableTypeElements(element: PsiElement) =
    element.depthFirst().collect {
      case simple: ScSimpleTypeElement => simple
      case projection: ScTypeProjection => projection
      case parameterized: ScParameterizedTypeElement => parameterized
    }.toIndexedSeq

  private sealed trait ReplacementInfo {

    val place: PsiElement

    def checkReplacementResolve: Boolean

    def pathsToImport: List[String]
  }

  private final case class SimpleInfo(override val place: PsiElement,
                                      replacement: String,
                                      resolve: Option[PsiElement],
                                      override val pathsToImport: List[String] = Nil) extends ReplacementInfo {

    def updateTarget(target: PsiNamedElement): SimpleInfo = {
      def prefixAndPath(qualifiedName: String, prefixLength: Int): Option[(String, Some[String])] =
        splitName(qualifiedName) match {
          case words if words.size > prefixLength =>
            val packageName = words.dropRight(prefixLength).mkString(".")
            val prefixed = words.takeRight(prefixLength + 1).mkString(".")

            val suffix = place match {
              case s: ScSimpleTypeElement if s.isSingleton => ScalaTypePresentation.ObjectTypeSuffix
              case _ => ""
            }
            Some((prefixed + suffix, Some(packageName)))
          case _ => None
        }

      def prefixAndPathGenerator(qualifiedName: String) = Iterator.from(0)
        .map(prefixAndPath(qualifiedName, _))
        .takeWhile(_.isDefined)
        .flatten
        .collectFirst {
          case (prefixed, _) if resolvesRight(prefixed) => (prefixed, None)
          case pair@(prefixed, _) if !resolvesWrong(prefixed) => pair
        }

      val maybePrefixAndPath = target match {
        case clazz: PsiClass if needPrefix(clazz) => prefixAndPath(clazz.qualifiedName, 1)
        case _ => qualifiedName(target).flatMap(prefixAndPathGenerator)
      }

      maybePrefixAndPath.fold(this) {
        case (newReplacement, maybePath) => SimpleInfo(place, newReplacement, Some(target), maybePath.toList)
      }
    }

    override def checkReplacementResolve: Boolean = resolvesRight(replacement)

    private def resolvesRight(refText: String): Boolean = alreadyResolves(refText).getOrElse(false)

    private def resolvesWrong(refText: String): Boolean = alreadyResolves(refText).contains(false)

    private def alreadyResolves(refText: String): Option[Boolean] = {
      def equivalent(left: PsiNamedElement, right: PsiNamedElement): Boolean =
        smartEquivalence(left, right) ||
          ScDesignatorType(left).equiv(ScDesignatorType(right))

      val ref = newRef(refText, place)
        .flatMap(reference => elementToImport(reference))

      (ref, resolve) match {
        case (Some(e1: PsiNamedElement), Some(e2: PsiNamedElement)) =>
          Some(equivalent(e1, e2))
        case (Some(_), _) => Some(false)
        case _ => None
      }
    }

    private def needPrefix(c: PsiClass) = {
      val fromSettings = ScalaCodeStyleSettings.getInstance(place.getProject).hasImportWithPrefix(c.qualifiedName)

      def forInnerClass = {
        val isExternalRefToInnerClass = Option(c.containingClass).exists(!_.isAncestorOf(place))
        isExternalRefToInnerClass && !resolvesRight(c.name)
      }

      fromSettings || c.is[ScEnumCase] || forInnerClass
    }
  }

  private object SimpleInfo {

    def apply(typeElement: ScTypeElement): SimpleInfo = applyImpl(typeElement)

    def apply(reference: ScReference): SimpleInfo = applyImpl(reference)

    private def applyImpl(element: PsiElement): SimpleInfo = {
      val resolve = for {
        ref <- findRef(element)
        target <- elementToImport(ref)
      } yield target
      SimpleInfo(element, element.getText, resolve)
    }
  }

  private final case class CompoundInfo(children: List[ReplacementInfo])
                                       (override val place: PsiElement,
                                        private val typeElement: ScTypeElement) extends ReplacementInfo {

    if (!children.map(_.place).forall(typeElement.isAncestorOf)) {
      throw new IllegalArgumentException("Wrong usage of CompoundInfo")
    }

    override def checkReplacementResolve: Boolean = children.forall(_.checkReplacementResolve)

    override def pathsToImport: List[String] = children.flatMap(_.pathsToImport)

    def replace(): Unit =
      place.replace(typeElement)

    def foreach(function: ReplacementInfo => Unit): Unit =
      children.foreach(function)

    def map(function: ReplacementInfo => ReplacementInfo): CompoundInfo =
      CompoundInfo(children.map(function))(place, typeElement)

    def flatMap(function: ReplacementInfo => Iterable[ReplacementInfo]): CompoundInfo =
      CompoundInfo(children.flatMap(function))(place, typeElement)
  }

  private def elementToImport(ref: ScReference): Option[PsiNamedElement] =
    ref.bind().map(srr => srr.parentElement.getOrElse(srr.element))

}

