package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.application.{ApplicationAdapter, ApplicationManager}
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
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, ScTypePresentation}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


object TypeAdjuster extends ApplicationAdapter {

  ApplicationManager.getApplication.addApplicationListener(this)

  private val markedElements = ArrayBuffer[SmartPsiElementPointer[PsiElement]]()

  override def writeActionFinished(action: scala.Any): Unit = {
    if (markedElements.nonEmpty) {
      val project = markedElements.head.getProject
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      adjustMarkedElements()
    }
  }

  def adjustFor(elements: Seq[PsiElement], addImports: Boolean = true, useTypeAliases: Boolean = true): Unit = {

    val typeElements = elements.toVector.flatMap(collectAdjustableTypeElements).distinct

    val rInfos = toReplacementInfos(typeElements, useTypeAliases)

    replaceAndAddImports(rInfos, addImports)
  }

  def markToAdjust(element: PsiElement) = {
    if (element != null && element.isValid) {
      val manager = SmartPointerManager.getInstance(element.getProject)
      markedElements += manager.createSmartPsiElementPointer(element)
    }
  }

  private def adjustMarkedElements() = {
    val elements = markedElements.flatMap(_.getElement.toOption).filter(_.isValid)
    markedElements.clear()

    adjustFor(elements)
  }

  private def newRef(text: String, position: PsiElement): Option[ScReferenceElement] = {
    findRef(newTypeElem(text, position))
  }

  private def newRef(sInfo: SimpleInfo): Option[ScReferenceElement] = {
    findRef(newTypeElem(sInfo.replacement, sInfo.origTypeElem))
  }

  private def newTypeElem(name: String, position: PsiElement) = ScalaPsiElementFactory.createTypeElementFromText(name, position.getContext, position)

  private def toReplacementInfos(typeElements: Seq[ScTypeElement], useTypeAliases: Boolean): Seq[ReplacementInfo] = {
    val infos = typeElements.map(ReplacementInfo.initial)
    infos.flatMap(simplify)
      .map(shortenReference(_, useTypeAliases))
  }

  private val toReplaceKey: Key[String] = Key.create[String]("type.element.to.replace")

  private def simplify(info: ReplacementInfo): Option[ReplacementInfo] = {
    def markToReplace(typeElem: ScTypeElement): Unit = typeElem.putUserData(toReplaceKey, "")
    def isMarkedToReplace(typeElem: ScTypeElement): Boolean = typeElem.getUserData(toReplaceKey) != null

    object `with .type#` {
      def unapply(info: SimpleInfo): Option[ReplacementInfo] = {
        val text = info.replacement
        if (text.contains(".type#"))
          Some(info.withNewText(text.replace(".type#", ".")))
        else None
      }
    }

    object withThisRef {
      def unapply(info: SimpleInfo): Option[ReplacementInfo] = {
        val text = info.replacement
        val thisWithDot = "this."
        val index = text.indexOf(thisWithDot)
        if (index >= 0) {
          val endIndex = index + thisWithDot.length
          val withoutThisType = text.substring(endIndex)
          val newResolve = newRef(withoutThisType, info.origTypeElem).flatMap(_.resolve().toOption)
          for {
            oldRes <- info.resolve
            newRes <- newResolve
            if ScEquivalenceUtil.smartEquivalence(oldRes, newRes)
          } yield {
            info.withNewText(withoutThisType)
          }
        } else None
      }
    }

    object withExpandableTypeAlias {
      def unapply(info: SimpleInfo): Option[ReplacementInfo] = {
        val dummyTypeElem = newTypeElem(info.replacement, info.origTypeElem)
        val replacementType = dummyTypeElem.calcType
        val withoutAliases = ScType.removeAliasDefinitions(replacementType, expandableOnly = true)
        if (replacementType.presentableText != withoutAliases.presentableText) {
          markToReplace(info.origTypeElem)
          val text = withoutAliases.canonicalText
          val newTypeEl = newTypeElem(text, info.origTypeElem)
          val subTypeElems = collectAdjustableTypeElements(newTypeEl).filter(_ != newTypeEl)
          if (subTypeElems.isEmpty) {
            Some(ReplacementInfo.initial(newTypeEl).copy(origTypeElem = info.origTypeElem))
          }
          else
            Some(CompoundInfo(info.origTypeElem, newTypeEl, subTypeElems.map(ReplacementInfo.initial)))
        }
        else None
      }
    }

    if (info.origTypeElem.parentsInFile.filterByType(classOf[ScTypeElement]).exists(isMarkedToReplace)) None
    else info match {
      case cmp: CompoundInfo =>
        Some(cmp.copy(childInfos = cmp.childInfos.flatMap(simplify)))
      case withExpandableTypeAlias(newInfo) => simplify(newInfo)
      case withThisRef(newInfo) => simplify(newInfo)
      case `with .type#`(newInfo) => simplify(newInfo)
      case _ => Some(info)
    }
  }

  private def replaceTypeElem(rInfo: ReplacementInfo): ScTypeElement = {
    rInfo match {
      case simple: SimpleInfo =>
        val typeElement = simple.origTypeElem
        val replacement = simple.replacement
        if (typeElement.getText != replacement)
          typeElement.replace(newTypeElem(replacement, typeElement)).asInstanceOf[ScTypeElement]
        else typeElement
      case cmp: CompoundInfo =>
        val tempElem = cmp.tempTypeElem
        cmp.childInfos.foreach(replaceTypeElem)
        cmp.origTypeElem.replace(tempElem).asInstanceOf[ScTypeElement]
    }
  }

  private def findRef(elem: PsiElement): Option[ScReferenceElement] = elem match {
    case r: ScReferenceElement => Some(r)
    case ScSimpleTypeElement(Some(r)) => Some(r)
    case _ => None
  }

  private def shortenReference(info: ReplacementInfo, useTypeAliases: Boolean = true): ReplacementInfo = {
    object hasStableReplacement {
      def unapply(rInfo: SimpleInfo): Option[ReplacementInfo] = {
        rInfo.resolve.flatMap { resolved =>
          val position = findRef(rInfo.origTypeElem).getOrElse(info.origTypeElem  )
          val importAlias = ScalaPsiUtil.importAliasFor(resolved, position)
          resolved match {
            case _ if importAlias.isDefined => Some(rInfo.withNewText(importAlias.get.refName))
            case named: PsiNamedElement if ScalaPsiUtil.hasStablePath(named) =>
              named match {
                case clazz: PsiClass =>
                  availableTypeAliasFor(clazz, position, useTypeAliases) match {
                    case Some(ta) if !ta.isAncestorOf(position) =>
                      if (ScalaPsiUtil.hasStablePath(ta)) Some(rInfo.updateTarget(ta))
                      else Some(rInfo.withNewText(ta.name))
                    case _ =>
                      Some(rInfo.updateTarget(named))
                  }
                case _: ScTypeAlias | _: ScBindingPattern => Some(rInfo.updateTarget(named))
                case _ => None
              }
            case _ => None
          }
        }
      }
    }

    info match {
      case cmp: CompoundInfo =>
        cmp.copy(childInfos = cmp.childInfos.map(shortenReference(_, useTypeAliases)))
      case hasStableReplacement(rInfo) => rInfo
      case _ => info
    }
  }

  private def collectImportHolders(rInfos: Set[ReplacementInfo]): Map[ReplacementInfo, ScImportsHolder] = {
    def findMaxHolders(infos: Set[ReplacementInfo]): Set[(ReplacementInfo, ScImportsHolder)] = {
      val infosToHolders = infos.map(info => info -> ScalaImportTypeFix.getImportHolder(info.origTypeElem, info.origTypeElem.getProject))
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

  private def replaceAndAddImports(rInfos: Seq[ReplacementInfo], addImports: Boolean): Unit = {
    assert(rInfos.forall(_.origTypeElem.isValid), "Psi shouldn't be modified before this stage!")

    val replacementsWithSameResolve = rInfos.filter(_.checkReplacementResolve).toSet
    val importHolders = collectImportHolders(rInfos.toSet -- replacementsWithSameResolve)
    val holderToPaths = mutable.Map[ScImportsHolder, Set[String]]()

    rInfos.foreach { info =>
      if (!addImports && !replacementsWithSameResolve.contains(info)) {}
      else {
        replaceTypeElem(info)
        val holder = importHolders.get(info)
        if (info.pathsToImport.nonEmpty && holder.isDefined) {
          val pathsToAdd = holderToPaths.getOrElseUpdate(holder.get, Set.empty) ++ info.pathsToImport
          holderToPaths += holder.get -> pathsToAdd
        }
      }
    }

    for ((holder, paths) <- holderToPaths) {
      holder.addImportsForPaths(paths.toSeq, null)
    }
  }

  private def availableTypeAliasFor(clazz: PsiClass, position: PsiElement, useTypeAliases: Boolean)
                                   (implicit typeSystem: TypeSystem = clazz.typeSystem): Option[ScTypeAliasDefinition] = {
    if (!useTypeAliases) None
    else {
      class FindTypeAliasProcessor extends BaseProcessor(ValueSet(CLASS)) {
        var collected: Option[ScTypeAliasDefinition] = None

        override def execute(element: PsiElement, state: ResolveState): Boolean = {
          element match {
            case ta: ScTypeAliasDefinition if ta.isAliasFor(clazz) && !ScTypePresentation.shouldExpand(ta) =>
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

  private def collectAdjustableTypeElements(element: PsiElement): Vector[ScTypeElement] = {
    element.depthFirst.collect {
      case s: ScSimpleTypeElement => s
      case p: ScTypeProjection => p
      case p: ScParameterizedTypeElement => p
    }.toVector
  }

  private trait ReplacementInfo {
    def origTypeElem: ScTypeElement

    def checkReplacementResolve: Boolean

    def pathsToImport: Seq[String]
  }

  private case class SimpleInfo(origTypeElem: ScTypeElement, replacement: String,
                                resolve: Option[PsiElement], pathsToImport: Seq[String]) extends ReplacementInfo {

    def withNewText(s: String): SimpleInfo = copy(replacement = s)

    def updateTarget(target: PsiNamedElement): SimpleInfo = {
      val (newText, pathToImport) = target match {
        case clazz: PsiClass =>
          val qName = clazz.qualifiedName
          val needPrefix = ScalaCodeStyleSettings.getInstance(origTypeElem.getProject).hasImportWithPrefix(qName)
          if (needPrefix) {
            val words = qName.split('.')
            val withPrefix = words.takeRight(2).mkString(".")
            val packageName = words.dropRight(1).mkString(".")
            (withPrefix, Some(packageName))
          }
          else (clazz.name, Some(qName))
        case _ => (target.name, ScalaNamesUtil.qualifiedName(target))
      }
      val replacementText = origTypeElem match {
        case s: ScSimpleTypeElement if s.singleton => s"$newText.type"
        case _ => newText
      }
      val withoutImport = copy(replacement = replacementText, resolve = Some(target), pathsToImport = Seq.empty)

      if (withoutImport.checkReplacementResolve) withoutImport
      else withoutImport.copy(pathsToImport = pathToImport.toSeq)
    }

    override def checkReplacementResolve: Boolean = {
      val newResolve = newRef(this).flatMap(_.resolve().toOption)

      (newResolve, resolve) match {
        case (Some(e1), Some(e2)) => ScEquivalenceUtil.smartEquivalence(e1, e2)
        case _ => false
      }
    }
  }

  private case class CompoundInfo(origTypeElem: ScTypeElement, tempTypeElem: ScTypeElement,
                                  childInfos: Seq[ReplacementInfo]) extends ReplacementInfo {

    if (childInfos.isEmpty || childInfos.exists(i => !tempTypeElem.isAncestorOf(i.origTypeElem))) {
      throw new IllegalArgumentException("Wrong usage of CompoundInfo")
    }

    override def checkReplacementResolve: Boolean = childInfos.forall(_.checkReplacementResolve)

    override def pathsToImport: Seq[String] = childInfos.flatMap(_.pathsToImport)
  }

  private object ReplacementInfo {
    def initial(te: ScTypeElement): SimpleInfo = {
      val text = te.getText
      val resolve = TypeAdjuster.findRef(te).flatMap(_.resolve().toOption)
      SimpleInfo(te, text, resolve, Seq.empty)
    }
  }
}

