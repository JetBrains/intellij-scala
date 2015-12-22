package org.jetbrains.plugins.scala.conversion

import com.intellij.codeInsight.editorActions.ReferenceData
import com.intellij.codeInspection.{InspectionManager, LocalQuickFixOnPsiElement, ProblemDescriptor, ProblemsHolder}
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.{EmptyProgressIndicator, ProgressIndicator, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.codeInsight.intention.RemoveBracesIntention
import org.jetbrains.plugins.scala.codeInspection.parentheses.ScalaUnnecessaryParenthesesInspection
import org.jetbrains.plugins.scala.codeInspection.redundantReturnInspection.RemoveRedundantReturnInspection
import org.jetbrains.plugins.scala.codeInspection.semicolon.ScalaUnnecessarySemicolonInspection
import org.jetbrains.plugins.scala.conversion.ast._
import org.jetbrains.plugins.scala.conversion.copy.{Association, AssociationHelper, Associations}
import org.jetbrains.plugins.scala.conversion.visitors.PrintWithComments
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScParenthesisedExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager.ClassCategory

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

/**
  * Created by Kate Ustyuzhanina
  * on 12/8/15
  */
object ConverterUtil {
  //old method spilt current text on psi & text parts
  def getElementsBetweenOffsets(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int]): Seq[Part] = {
    val buffer = new ArrayBuffer[Part]
    for ((startOffset, endOffset) <- startOffsets.zip(endOffsets)) {
      @tailrec
      def findElem(offset: Int): PsiElement = {
        if (offset > endOffset) return null
        val elem = file.findElementAt(offset)
        if (elem == null) return null
        if (elem.getParent.getTextRange.getEndOffset > endOffset ||
          elem.getParent.getTextRange.getStartOffset < startOffset) findElem(elem.getTextRange.getEndOffset + 1)
        else elem
      }

      var elem: PsiElement = findElem(startOffset)
      if (elem != null) {
        while (elem.getParent != null && !elem.getParent.isInstanceOf[PsiFile] &&
          elem.getParent.getTextRange.getEndOffset <= endOffset &&
          elem.getParent.getTextRange.getStartOffset >= startOffset) {
          elem = elem.getParent
        }
        if (startOffset < elem.getTextRange.getStartOffset) {
          buffer += TextPart(new TextRange(startOffset, elem.getTextRange.getStartOffset).substring(file.getText))
        }
        buffer += ElementPart(elem)
        while (elem.getNextSibling != null && elem.getNextSibling.getTextRange.getEndOffset <= endOffset) {
          elem = elem.getNextSibling
          buffer += ElementPart(elem)
        }
        if (elem.getTextRange.getEndOffset < endOffset) {
          buffer += TextPart(new TextRange(elem.getTextRange.getEndOffset, endOffset).substring(file.getText))
        }
      }
    }
    buffer.toSeq
  }

  def newFileTextWithOffsets(file: PsiFile, startOffsets: Array[Int],
                             endOffsets: Array[Int]): (String, Array[Int], Array[Int]) = {
    def transformInt(value: Int) = Int.MaxValue - value

    def tryClipRight(element: PsiElement, rangeBound: Int): Option[Int] =
      tryToClipSide(element, transformInt(rangeBound), isRight = true).map(el => transformInt(el))

    def tryToClipSide(element: PsiElement, rangeBound: Int, isRight: Boolean = false): Option[Int] = {
      def getChildren(element: PsiElement) = if (isRight) element.getChildren.reverse else element.getChildren

      def transform(textRange: TextRange) = {
        if (isRight)
          new TextRange(transformInt(textRange.getEndOffset), transformInt(textRange.getStartOffset))
        else textRange
      }

      if (element.getFirstChild == null) return None

      var clipTo = transform(element.getTextRange).getStartOffset
      val children = getChildren(element)
      for (child <- children) {
        val (start, end) = (transform(child.getTextRange).getStartOffset, transform(child.getTextRange).getEndOffset)
        if (start >= rangeBound) return Some(clipTo)
        if (end <= rangeBound) clipTo = end
        else {
          if (child.isInstanceOf[PsiWhiteSpace]) return Some(clipTo)
          return tryToClipSide(child, rangeBound, isRight)
        }
      }
      Some(clipTo)
    }

    def getParentsWithSelf(element: PsiElement): Iterator[PsiElement] = element.parentsWithSelfInFile

    def getMaximalParent(inElement: PsiElement, range: TextRange): PsiElement = {
      def getMinimizeTextRange(element: PsiElement): TextRange = {
        if (element.children.isEmpty) return element.getTextRange

        val fRange = element.getFirstChild.nextElements.collectFirst { case el if !canDropElement(el) => el }
          .getOrElse(return element.getTextRange)

        val lChild = element.getLastChild.prevElements.collectFirst { case el if !canDropElement(el) => el }
          .getOrElse(return element.getTextRange)

        new TextRange(getMinimizeTextRange(fRange).getStartOffset, getMinimizeTextRange(lChild).getEndOffset)
      }

      val parentsWithSelf = getParentsWithSelf(inElement).collectFirst {
        case el if !range.contains(el.getTextRange) => el
      }

      if (parentsWithSelf.isDefined) {
        val temp = getParentsWithSelf(parentsWithSelf.get).takeWhile { el => range.contains(getMinimizeTextRange(el)) }
        if (temp.isEmpty) null
        else temp.next()
      } else null
    }

    def newFileText(rangesToDrop: ArrayBuffer[TextRange]) = {
      val oldFileText = file.getText
      val newFile = new StringBuilder()
      var offset = 0
      for (range <- rangesToDrop) {
        newFile.append(oldFileText.substring(offset, range.getStartOffset))
        offset = range.getEndOffset
      }
      newFile.append(oldFileText.substring(offset, oldFileText.length()))
      newFile.toString()
    }

    def update(offsets: Array[Int], rangesToDrop: ArrayBuffer[TextRange]) = {
      for (range <- rangesToDrop.reverse) {
        for (offset <- offsets) {
          if (offset >= range.getEndOffset)
            offsets(offsets.indexOf(offset)) -= range.getLength
        }
      }
      offsets
    }

    def canDropRange(range: TextRange, allRanges: Seq[TextRange]) = !allRanges.contains(range)

    val ranges = startOffsets.zip(endOffsets).sortWith(_._1 < _._1)
      .collect { case pair: (Int, Int) if pair._1 != pair._2 => new TextRange(pair._1, pair._2) }

    val rangesToDrop = new ArrayBuffer[TextRange]()
    for (range <- ranges) {
      val start = range.getStartOffset
      val end = range.getEndOffset
      val startElement = file.findElementAt(start)
      val elementToClipLeft = getMaximalParent(startElement, range)

      if (elementToClipLeft != null) {
        val elementStart = elementToClipLeft.getTextRange.getStartOffset
        if (elementStart < start) {
          val clipBound = tryToClipSide(elementToClipLeft, start)
          if (clipBound.isDefined) {
            val rangeToDrop = new TextRange(elementStart, clipBound.get)
            if (canDropRange(rangeToDrop, ranges)) {
              rangesToDrop += rangeToDrop
            }
          }
        }
      }

      val endElement = file.findElementAt(end - 1)
      val elementToClipRight = getMaximalParent(endElement, range)
      if (elementToClipRight != null) {
        val elementEnd = elementToClipRight.getTextRange.getEndOffset
        if (elementEnd > end) {
          val clipRight = tryClipRight(elementToClipRight, end)
          if (clipRight.isDefined) {
            val rangeToDrop = new TextRange(clipRight.get, elementEnd)
            if (canDropRange(rangeToDrop, ranges)) {
              rangesToDrop += rangeToDrop
            }
          }
        }
      }

      if (rangesToDrop.isEmpty) return null
    }

    (newFileText(rangesToDrop), update(startOffsets, rangesToDrop), update(endOffsets, rangesToDrop))
  }

  def canDropElement(element: PsiElement): Boolean = {
    element match {
      case s: PsiWhiteSpace => true
      case c: PsiComment => true
      case a: PsiModifierList => true
      case r: PsiAnnotation => true
      case t: PsiJavaToken =>
        val drop = Seq(JavaTokenType.PUBLIC_KEYWORD, JavaTokenType.PROTECTED_KEYWORD, JavaTokenType.PRIVATE_KEYWORD,
          JavaTokenType.STATIC_KEYWORD, JavaTokenType.ABSTRACT_KEYWORD, JavaTokenType.FINAL_KEYWORD,
          JavaTokenType.NATIVE_KEYWORD, JavaTokenType.SYNCHRONIZED_KEYWORD, JavaTokenType.STRICTFP_KEYWORD,
          JavaTokenType.TRANSIENT_KEYWORD, JavaTokenType.VOLATILE_KEYWORD, JavaTokenType.DEFAULT_KEYWORD)

        drop.contains(t.getTokenType) && t.getParent.isInstanceOf[PsiModifierList] ||
          t.getTokenType == JavaTokenType.SEMICOLON
      case c: PsiCodeBlock if c.getParent.isInstanceOf[PsiMethod] => true
      case o => o.getFirstChild == null
    }
  }

  //collect top elements in range
  def collectTopElements(startOffset: Int, endOffset: Int, javaFile: PsiFile): Array[PsiElement] = {
    val buf = new ArrayBuffer[PsiElement]
    var elem: PsiElement = javaFile.findElementAt(startOffset)
    assert(elem.getTextRange.getStartOffset == startOffset)
    while (elem.getParent != null && !elem.getParent.isInstanceOf[PsiFile] &&
      elem.getParent.getTextRange.getStartOffset == startOffset) {
      elem = elem.getParent
    }

    buf += elem
    while (elem != null && elem.getTextRange.getEndOffset < endOffset) {
      elem = elem.getNextSibling
      buf += elem
    }
    buf.toArray
  }

  def handleOneProblem(problem: ProblemDescriptor) = {
    val fixes = problem.getFixes.collect { case f: LocalQuickFixOnPsiElement => f }
    fixes.foreach(_.applyFix)
  }

  def runInspections(file: PsiFile, project: Project, offset: Int, endOffset: Int, editor: Editor = null): Unit = {
    val intention = new RemoveBracesIntention
    val holder = new ProblemsHolder(InspectionManager.getInstance(project), file, false)

    val removeReturnVisitor = (new RemoveRedundantReturnInspection).buildVisitor(holder, isOnTheFly = false)
    val parenthesisedExpr = (new ScalaUnnecessaryParenthesesInspection).buildVisitor(holder, isOnTheFly = false)
    val removeSemicolon = (new ScalaUnnecessarySemicolonInspection).buildVisitor(holder, isOnTheFly = false)

    collectTopElements(offset, endOffset, file).foreach(_.depthFirst.foreach {
      case el: ScFunctionDefinition =>
        removeReturnVisitor.visitElement(el)
      case parentized: ScParenthesisedExpr =>
        parenthesisedExpr.visitElement(parentized)
      case semicolon: PsiElement if semicolon.getNode.getElementType == ScalaTokenTypes.tSEMICOLON =>
        removeSemicolon.visitElement(semicolon)
      case el =>
        intention.invoke(project, editor, el)
    })

    import scala.collection.JavaConversions._
    holder.getResults.foreach(handleOneProblem)
  }

  sealed trait Part

  case class ElementPart(elem: PsiElement) extends Part

  case class TextPart(text: String) extends Part

  def getTextBetweenOffsets(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int]): String = {
    val builder = new StringBuilder()
    val textGaps = startOffsets.zip(endOffsets).sortWith(_._1 < _._1)
    for ((start, end) <- textGaps) {
      if (start != end && start < end)
        builder.append(file.getText.substring(start, end))
    }
    builder.toString()
  }

  def compareTextNEq(text1: String, text2: String): Boolean = {
    def textWithoutLastSemicolon(text: String) = {
      if ((text.length > 0) && (text.last == ';')) text.substring(0, text.length - 1)
      else text
    }
    textWithoutLastSemicolon(text1) != textWithoutLastSemicolon(text2)
  }

  def getBindings(value: Associations, file: PsiFile, offset: Int, project: Project, needAll: Boolean = false) = {
    (for {
      association <- value.associations
      element <- elementFor(association, file, offset)
      if needAll || !association.isSatisfiedIn(element)
    } yield Binding(element, association.path.asString(ScalaCodeStyleSettings.getInstance(project).
      isImportMembersUsingUnderScore))).filter {
      case Binding(_, path) =>
        val index = path.lastIndexOf('.')
        index != -1 && !Set("scala", "java.lang", "scala.Predef").contains(path.substring(0, index))
    }
  }

  def getOrCreateIndicator: ProgressIndicator = {
    class MyEmptyProgressIndicator extends EmptyProgressIndicator {
      private var myText: String = null
      private var myText2: String = null
      private var myFraction: Double = .0

      override def setText(text: String) {
        myText = text
      }

      override def getText: String = {
        myText
      }

      override def setText2(text: String) {
        myText2 = text
      }

      override def getText2: String = {
        myText2
      }

      override def setFraction(fraction: Double) {
        myFraction = fraction
      }

      override def getFraction: Double = {
        myFraction
      }
    }
    var progress: ProgressIndicator = ProgressManager.getInstance.getProgressIndicator
    if (progress == null) progress = new MyEmptyProgressIndicator
    progress
  }

  def addImportsForPrefixedElements(elements: Seq[Binding], project: Project) = {
    //    val indicator: ProgressIndicator = getOrCreateIndicator
    //    if (indicator != null) indicator.setText2(": analyzing imports usage")
    //
    //
    //    val list: util.ArrayList[Binding] = new util.ArrayList[Binding]()
    //
    //    def addChildren(elements: Seq[Binding]): Unit = {
    //      for (el <- elements)
    //        list.add(el)
    //    }
    //    addChildren(elements)
    //
    //    val i = new AtomicInteger(0)
    //    val size = list.size()
    //    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, indicator, true, true, new Processor[Binding] {
    //      override def process(element: Binding): Boolean = {
    //        val manager = ScalaPsiManager.instance(project)
    //        val searchScope = GlobalSearchScope.allScope(project)
    //        val count: Int = i.getAndIncrement
    //        if (count <= size && indicator != null) indicator.setFraction(count.toDouble / size)
    //        element match {
    //          case el =>
    //            val cashed = Option(manager.getCachedClass(el.path, searchScope, ClassCategory.TYPE))
    //            val reference = Option(el.element.getReference)
    //            if (cashed.isDefined && reference.isDefined) {
    //              if (reference.get.bindToElement(cashed.get) == reference.get)
    //                inWriteAction(ScalaPsiUtil.adjustTypes(el.element))
    //              true
    //            } else {
    //              inWriteAction(ScalaPsiUtil.adjustTypes(el.element))
    //              true
    //            }
    //        }
    //      }
    //    })
    val manager = ScalaPsiManager.instance(project)
    val searchScope = GlobalSearchScope.allScope(project)
    elements.foreach {
      case el =>
        val cashed = Option(manager.getCachedClass(el.path, searchScope, ClassCategory.TYPE))
        val reference = Option(el.element.getReference)
        if (cashed.isDefined && reference.isDefined) {
          if (reference.get.bindToElement(cashed.get) == reference.get)
            inWriteAction(ScalaPsiUtil.adjustTypes(el.element))
        } else
          inWriteAction(ScalaPsiUtil.adjustTypes(el.element))
    }
  }

  def addImportsForAssociations(associations: Seq[Association], file: PsiFile, offset: Int, project: Project): Unit = {
    val needPrefix = associations.filter(el => needPrefixToElement(el.path.entity, project))
    val bindings = getBindings(Associations(needPrefix), file, offset, project, needAll = true)
    addImportsForPrefixedElements(bindings, project)
  }

  def needPrefixToElement(qn: String, project: Project): Boolean =
    ScalaCodeStyleSettings.getInstance(project).hasImportWithPrefix(qn)


  def updateAssociations(old: Seq[AssociationHelper], rangeMap: mutable.HashMap[IntermediateNode, TextRange]): Seq[Association] = {
    old.filter(_.itype.isInstanceOf[TypedElement]).
      map { a =>
        val typedElement = a.itype.asInstanceOf[TypedElement].getType
        val range = rangeMap.getOrElse(typedElement, new TextRange(0, 0))
        new Association(a.kind, range, a.path)
      }
  }

  def elementFor(dependency: Association, file: PsiFile, offset: Int): Option[PsiElement] = {
    val range = dependency.range.shiftRight(offset)

    for (ref <- Option(file.findElementAt(range.getStartOffset));
         parent <- ref.parent if parent.getTextRange == range) yield parent
  }

  case class Binding(element: PsiElement, path: String)


  def prepareDataForConversion(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int]): (Seq[Part], PsiFile) = {
    val updatedFileTextAndOffsets = ConverterUtil.newFileTextWithOffsets(file, startOffsets, endOffsets)
    val (newFile, newStartOffsets, newEndOffsets) = updatedFileTextAndOffsets match {
      case (n, s, e) =>
        (PsiFileFactory.getInstance(file.getProject).createFileFromText(JavaLanguage.INSTANCE, n), s, e)
      case _ => (file, startOffsets, endOffsets)
    }

    (ConverterUtil.getElementsBetweenOffsets(newFile, newStartOffsets, newEndOffsets), newFile)
  }

  def convertData(parts:Seq[Part], refs: Seq[ReferenceData] = Seq.empty): (String, Array[Association]) = {
    val associationsHelper = new ListBuffer[AssociationHelper]()
    val resultNode = new MainConstruction

    Comments.topElements ++= parts.collect { case el: ElementPart => el }.map((el: ElementPart) => el.elem)
    val used = new mutable.HashSet[PsiElement]()
    for (part <- parts) {
      part match {
        case TextPart(s) =>
          resultNode.addChild(LiteralExpression(s))
        case ElementPart(element) =>
          val result = JavaToScala.convertPsiToIntermdeiate(element, null)(associationsHelper, refs, used)
          resultNode.addChild(result)
      }
    }

    val visitor = new PrintWithComments
    visitor.visit(resultNode)
    val text = visitor.stringResult

    visitor.rangedElementsMap.foreach(el => println(el.hashCode()))
    val updatedAssociations = ConverterUtil.updateAssociations(associationsHelper, visitor.rangedElementsMap)
    (text, updatedAssociations.toArray)
  }
}
