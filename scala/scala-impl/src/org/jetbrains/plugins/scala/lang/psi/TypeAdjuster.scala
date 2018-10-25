package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.openapi.application.{ApplicationAdapter, ApplicationManager}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement, ScTypeProjection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.{ScalaTypePresentation, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.annotation.tailrec
import scala.collection.mutable

object TypeAdjuster extends ApplicationAdapter {

  import ScEquivalenceUtil.smartEquivalence

  private val LOG = Logger.getInstance(getClass)

  ApplicationManager.getApplication.addApplicationListener(this)

  private val markedElements = mutable.ArrayBuffer.empty[SmartPsiElementPointer[PsiElement]]

  override def writeActionFinished(action: Any): Unit = {
    if (markedElements.nonEmpty) {
      val head = markedElements.head
      PsiDocumentManager.getInstance(head.getProject).commitAllDocuments()

      val elements = markedElements.collect {
        case ValidSmartPointer(element) => element
      }
      markedElements.clear()

      adjustFor(elements)
    }
  }

  def markToAdjust(element: PsiElement): Unit =
    if (element != null && element.isValid) {
      markedElements += element.createSmartPointer
    }

  def adjustFor(elements: Seq[PsiElement],
                addImports: Boolean = true,
                useTypeAliases: Boolean = true): Unit = {
    val infos = for {
      element <- elements
      typeElement <- collectAdjustableTypeElements(element).distinct

      info = SimpleInfo(typeElement)
      simplified <- simplify(info)

      shortened = shortenReference(simplified, useTypeAliases)
    } yield shortened

    val rewrittenInfos = rewriteInfosAsInfix(infos)
    for {
      (holder, paths) <- replaceAndAddImports(rewrittenInfos, addImports)
    } holder.addImportsForPaths(paths.toSeq, null)
  }

  private def newRef(text: String, position: PsiElement): Option[ScReferenceElement] =
    findRef(newTypeElem(text, position))

  private def newTypeElem(name: String, position: PsiElement) =
    ScalaPsiElementFactory.createTypeElementFromText(name, position.getContext, position) match {
      case null =>
        LOG.error(s"Cannot create type from text:\n$name", new Throwable)
        null
      case result => result
    }

  private def findRef(element: PsiElement) = element match {
    case reference: ScReferenceElement => Some(reference)
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
                  ResolvesTo(newRes) <- newRef(text, place)
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

        if (replacementType.presentableText != withoutAliases.presentableText) {
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

  private def replaceElem(info: ReplacementInfo): Unit = info match {
    case SimpleInfo(place, replacement, _, _) if place.getText != replacement =>
      val maybeNewElement = place match {
        case _: ScTypeElement => Some(newTypeElem(replacement, place))
        case _: ScReferenceElement => newRef(replacement, place)
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
            replacement != place.getText
          }

      @tailrec
      private def qualifierInfo(element: PsiElement): Option[SimpleInfo] = element match {
        case ScSimpleTypeElement(Some(reference: ScReferenceElement)) => qualifierInfo(reference)
        case ScReferenceElement.withQualifier(reference: ScReferenceElement) => Some(SimpleInfo(reference))
        case _ => None
      }
    }

    info match {
      case compound: CompoundInfo => compound.map(shortenReference(_, useTypeAliases))
      case hasStableReplacement(rInfo) => rInfo
      case _ => info
    }
  }

  private val infixRewriteKey = Key.create[Int]("type.element.to.rewrite.as.infix")

  private def rewriteInfosAsInfix(infos: Seq[ReplacementInfo]): Seq[ReplacementInfo] = {
    def infoToMappings(info: ReplacementInfo): Seq[(String, PsiElement)] = info match {
      case SimpleInfo(place, replacement, _, _) => findRef(place).flatMap(_.resolve.toOption).map(replacement -> _).toSeq
      case CompoundInfo(children) => children.flatMap(infoToMappings)
      case _ => Seq.empty
    }

    def markToRewrite(e: PsiElement): Unit = e.putUserData(infixRewriteKey, 1)

    def annotatedAsInfix(e: PsiClass): Boolean = e.getAnnotations.exists(_.getQualifiedName == "scala.annotation.showAsInfix")

    def canBeInfix(te: ScTypeElement): Boolean = te match {
      case ScSimpleTypeElement(Some(ref)) =>
        ScalaNamesUtil.isOperatorName(ref.refName) ||
          ref.bind().map(_.element).exists {
            case aClass: PsiClass => annotatedAsInfix(aClass)
            case _ => false
          }
      case _ => false
    }

    def isChildOfInfixType(e: PsiElement): Boolean =
      e.parentsInFile.filterByType[ScParameterizedTypeElement].exists(_.getUserData(infixRewriteKey) == 1)

    def rewriteAsInfix(info: ReplacementInfo): ReplacementInfo = info match {
      case simple: SimpleInfo if isChildOfInfixType(simple.place) => simple
      case simple: SimpleInfo =>
        simple.place match {
          case e@ScParameterizedTypeElement(des, Seq(_, _)) if canBeInfix(des) =>
            markToRewrite(e)

            val mappings: Map[String, PsiElement] = infos
              .filter(_.place.parentsInFile.contains(e))
              .flatMap(infoToMappings)(collection.breakOut)

            val presentationContext: TypePresentationContext = (name, target) =>
              mappings.get(name).exists(smartEquivalence(_, target))

            val newTypeText = e.calcType.presentableText(presentationContext)
            simple.copy(replacement = newTypeText)
          case _ => info
        }
      case compound: CompoundInfo => compound.map(rewriteAsInfix)
      case _ => info
    }

    infos.map(rewriteAsInfix)
  }

  private def collectImportHolders(rInfos: Set[ReplacementInfo]): Map[ReplacementInfo, ScImportsHolder] = {
    def findMaxHolders(infos: Set[ReplacementInfo]): Set[(ReplacementInfo, ScImportsHolder)] = {
      val infosToHolders = infos.map(info => info -> ScalaImportTypeFix.getImportHolder(info.place, info.place.getProject))
      val holders = infosToHolders.map(_._2)
      val maxHolders = holders.filter(h => !holders.exists(_.isAncestorOf(h)))
      infosToHolders.map {
        case (i, h) => (i, maxHolders.find(PsiTreeUtil.isAncestor(_, h, false)).get)
      }
    }

    val byPath = mutable.Map[String, Set[ReplacementInfo]]()
    for {
      info <- rInfos
      path <- info.pathsToImport
    } {
      byPath.update(path, byPath.getOrElseUpdate(path, Set.empty) + info)
    }

    val withMaxHolders = byPath.values.map(infos => findMaxHolders(infos))
    withMaxHolders.flatten.toMap
  }

  private def replaceAndAddImports(rInfos: Seq[ReplacementInfo],
                                   addImports: Boolean) = {
    assert(rInfos.forall(_.place.isValid), "Psi shouldn't be modified before this stage!")

    val replacementsWithSameResolve = rInfos.filter(_.checkReplacementResolve).toSet
    val importHolders = collectImportHolders(rInfos.toSet -- replacementsWithSameResolve)
    val holderToPaths = mutable.Map[ScImportsHolder, Set[String]]()

    rInfos.foreach { info =>
      if (!addImports && !replacementsWithSameResolve.contains(info)) {}
      else {
        replaceElem(info)
        val holder = importHolders.get(info)
        if (info.pathsToImport.nonEmpty && holder.isDefined) {
          val pathsToAdd = holderToPaths.getOrElseUpdate(holder.get, Set.empty) ++ info.pathsToImport
          holderToPaths += holder.get -> pathsToAdd
        }
      }
    }

    holderToPaths.toMap
  }

  private def availableTypeAliasFor(clazz: PsiClass, position: PsiElement, useTypeAliases: Boolean): Option[ScTypeAliasDefinition] = {
    if (!useTypeAliases) None
    else {
      class FindTypeAliasProcessor extends BaseProcessor(ValueSet(CLASS))(clazz) {
        var collected: Option[ScTypeAliasDefinition] = None

        override def execute(element: PsiElement, state: ResolveState): Boolean = {
          element match {
            case ta: ScTypeAliasDefinition if ta.isAliasFor(clazz) &&
              !ScTypePresentation.shouldExpand(ta) && !ta.isDeprecated =>

              collected = Some(ta)
              false
            case _ => true
          }
        }
      }
      val processor = new FindTypeAliasProcessor
      PsiTreeUtil.treeWalkUp(processor, position, null, ResolveState.initial())
      processor.collected
    }
  }

  private def collectAdjustableTypeElements(element: PsiElement) =
    element.depthFirst().collect {
      case simple: ScSimpleTypeElement => simple
      case projection: ScTypeProjection => projection
      case parameterized: ScParameterizedTypeElement => parameterized
    }.toSeq

  private sealed trait ReplacementInfo {

    val place: PsiElement

    def checkReplacementResolve: Boolean

    def pathsToImport: List[String]
  }

  private final case class SimpleInfo(place: PsiElement,
                                      replacement: String,
                                      resolve: Option[PsiElement],
                                      pathsToImport: List[String] = Nil) extends ReplacementInfo {

    def updateTarget(target: PsiNamedElement): SimpleInfo = {
      import ScalaNamesUtil.{qualifiedName, splitName}

      def prefixAndPath(qualifiedName: String, prefixLength: Int): Option[(String, Some[String])] =
        splitName(qualifiedName) match {
          case words if words.size > prefixLength =>
            val packageName = words.dropRight(prefixLength).mkString(".")
            val prefixed = words.takeRight(prefixLength + 1).mkString(".")

            val suffix = place match {
              case s: ScSimpleTypeElement if s.singleton => ScalaTypePresentation.ObjectTypeSuffix
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
        .flatMap(reference => Option(reference.resolve()))

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

      fromSettings || forInnerClass
    }
  }

  private object SimpleInfo {

    def apply(typeElement: ScTypeElement): SimpleInfo = applyImpl(typeElement)

    def apply(reference: ScReferenceElement): SimpleInfo = applyImpl(reference)

    private def applyImpl(element: PsiElement): SimpleInfo = {
      val resolve = for {
        ResolvesTo(target) <- findRef(element)
      } yield target
      SimpleInfo(element, element.getText, resolve)
    }
  }

  private final case class CompoundInfo(children: List[ReplacementInfo])
                                       (val place: PsiElement,
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

    def flatMap(function: ReplacementInfo => Traversable[ReplacementInfo]): CompoundInfo =
      CompoundInfo(children.flatMap(function))(place, typeElement)
  }

}

