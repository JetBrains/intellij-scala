package org.jetbrains.plugins.scala.conversion

import com.intellij.codeInspection.{InspectionManager, LocalQuickFixOnPsiElement, ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.codeInsight.intention.RemoveBracesIntention
import org.jetbrains.plugins.scala.codeInspection.parentheses.ScalaUnnecessaryParenthesesInspection
import org.jetbrains.plugins.scala.codeInspection.redundantReturnInspection.RemoveRedundantReturnInspection
import org.jetbrains.plugins.scala.codeInspection.semicolon.ScalaUnnecessarySemicolonInspection
import org.jetbrains.plugins.scala.conversion.ast.{CommentsCollector, IntermediateNode, TypedElement}
import org.jetbrains.plugins.scala.conversion.copy.{Association, AssociationHelper, Associations}
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
import scala.collection.mutable.ArrayBuffer

/**
  * Created by Kate Ustyuzhanina
  * on 12/8/15
  */
object ConverterUtil {
  def getTopElements(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int]): (Seq[Part], mutable.HashSet[PsiElement]) = {

    def buildTextPart(offset1: Int, offset2: Int, dropElements: mutable.HashSet[PsiElement]): TextPart = {
      val possibleComment = file.findElementAt(offset1)
      if (possibleComment != null && CommentsCollector.isComment(possibleComment)) dropElements += possibleComment
      TextPart(new TextRange(offset1, offset2).substring(file.getText))
    }

    val dropElements = new mutable.HashSet[PsiElement]()
    val buffer = new ArrayBuffer[Part]
    for ((startOffset, endOffset) <- startOffsets.zip(endOffsets)) {
      @tailrec
      def findElem(offset: Int): PsiElement = {
        if (offset > endOffset) return null
        val elem = file.findElementAt(offset)
        if (elem == null) return null

        if (elem.getParent.getTextRange.getEndOffset > endOffset ||
          elem.getParent.getTextRange.getStartOffset < startOffset) {
          if (CommentsCollector.isComment(elem) && !dropElements.contains(elem)) {
            buffer += TextPart(elem.getText + "\n")
            dropElements += elem
          }
          findElem(elem.getTextRange.getEndOffset + 1)
        }
        else
          elem
      }
      var elem: PsiElement = findElem(startOffset)
      if (elem != null) {
        while (elem.getParent != null && !elem.getParent.isInstanceOf[PsiFile] &&
          elem.getParent.getTextRange.getEndOffset <= endOffset &&
          elem.getParent.getTextRange.getStartOffset >= startOffset) {
          elem = elem.getParent
        }
        //get wrong result when copy element that has PsiComment without comment
        val shifted = shiftedElement(elem, dropElements, endOffset)
        if (shifted != elem) {
          elem = shifted
        } else if (startOffset < elem.getTextRange.getStartOffset) {
          buffer += buildTextPart(startOffset, elem.getTextRange.getStartOffset, dropElements)
        }

        buffer += ElementPart(elem)
        while (elem.getNextSibling != null && elem.getNextSibling.getTextRange.getEndOffset <= endOffset) {
          elem = elem.getNextSibling
          buffer += ElementPart(elem)
        }

        if (elem.getTextRange.getEndOffset < endOffset) {
          buffer += buildTextPart(elem.getTextRange.getEndOffset, endOffset, dropElements)
        }
      }
    }
    (buffer.toSeq, dropElements)
  }


  def getTopElements2(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int]): (Seq[Part], mutable.HashSet[PsiElement]) = {
    val ranges = startOffsets.zip(endOffsets).sortWith(_._1 < _._1)
      .collect { case pair: (Int, Int) if pair._1 != pair._2 => new TextRange(pair._1, pair._2) }

    def canDropRange(range: TextRange) = !ranges.contains(range)
    val rangesToDrop = new ArrayBuffer[TextRange]()
    for (range <- ranges) {
      val start = range.getStartOffset
      val end = range.getEndOffset
      val startElement = file.findElementAt(start)
      val elementToClipLeft = getMaximalParent(startElement, new TextRange(start, end))

      if (elementToClipLeft != null) {
        val elementStart = elementToClipLeft.getTextRange.getStartOffset
        if (elementStart < start) {
          val clipBound = tryToClipSide(elementToClipLeft, start)
          if (clipBound >= 0) {
            val rangeToDrop = new TextRange(elementStart, clipBound)
            if (canDropRange(rangeToDrop)) {
              rangesToDrop += rangeToDrop
            }
          }
        }
      }

      val endElement = file.findElementAt(end - 1)
    }

    def tryToClipSide(element: PsiElement, rangeBound: Int): Int = {
      if (element.getFirstChild == null) return -100

      val range = element.getTextRange
      var clipTo = range.getStartOffset

      val children = element.getChildren
      for (child <- children) {
        val cRange = child.getTextRange
        if (cRange.getStartOffset >= rangeBound) return clipTo
        if (cRange.getEndOffset <= rangeBound) {
          if (!canDropElement(child)) return -100
          clipTo = cRange.getEndOffset
        } else {
          // rangeBound is inside child's range
          if (child.isInstanceOf[PsiWhiteSpace]) return clipTo
          return tryToClipSide(child, rangeBound)
          //        }
        }

      }
      clipTo
    }


    //TODO create ParentsWithSelfIterator
    def getParentsWithSelf(element: PsiElement): Iterable[PsiElement] = {
      Iterable[PsiElement](element) ++ element.parents
    }

    def getMaximalParent(inElement: PsiElement, range: TextRange): PsiElement = {
      val parentsWithSelf = getParentsWithSelf(inElement).takeWhile {
        !_.isInstanceOf[PsiDirectory]
      }.collectFirst {
        case el if range.contains(el.getTextRange) => el
      }

      if (parentsWithSelf.isDefined) {
        getParentsWithSelf(parentsWithSelf.get).takeWhile {
          !_.isInstanceOf[PsiDirectory]
        }.takeWhile(el => range.contains(getMinimizeTextRange(el))).last
      } else null
    }


    //TODO lots of NULL may occure
    def getMinimizeTextRange(element: PsiElement): TextRange = {
      if (element.children.isEmpty) return element.getTextRange
      val fChild = element.getFirstChild
      val fRange = if (fChild != null) {
        if (!canDropElement(fChild)) Some(fChild)
        else fChild.nextSiblings.collectFirst { case el if !canDropElement(el) => el }
      } else None

      if (fChild == null || fRange.isEmpty) return element.getTextRange

      //        return element.getTextRange
      //      }

      val lChild =
        if (!canDropElement(element.getLastChild)) Some(element.getLastChild)
        else element.getLastChild.prevSiblings.collectFirst { case el if !canDropElement(el) => el }
      new TextRange(getMinimizeTextRange(fRange.get).getStartOffset,
        getMinimizeTextRange(lChild.get).getEndOffset)
    }


    //    val firstNotInRange = parentsWithSelf.takeWhile { it !is PsiDirectory }.firstOrNull { it.range !in range } ?: return null
    //    return firstNotInRange.parentsWithSelf.lastOrNull { it.minimizedTextRange() in range }

    //    fun canDropRange(range: TextRange) = ranges.all { range !in it }
    //
    //    val rangesToDrop = ArrayList<TextRange>()
    //    for (range in ranges) {
    //      val start = range.start
    //      val end = range.end
    //      if (start == end) continue
    //
    //      val startToken = file.findElementAt(start)!!
    //      val elementToClipLeft = startToken.maximalParentToClip(range)
    //      if (elementToClipLeft != null) {
    //        val elementStart = elementToClipLeft.range.start
    //        if (elementStart < start) {
    //          val clipBound = tryClipLeftSide(elementToClipLeft, start)
    //          if (clipBound != null) {
    //            val rangeToDrop = TextRange(elementStart, clipBound)
    //            if (canDropRange(rangeToDrop)) {
    //              rangesToDrop.add(rangeToDrop)
    //            }
    //          }
    //        }
    //      }
    //
    //      val endToken = file.findElementAt(end - 1)!!
    //      val elementToClipRight = endToken.maximalParentToClip(range)
    //      if (elementToClipRight != null) {
    //        val elementEnd = elementToClipRight.range.end
    //        if (elementEnd > end) {
    //          val clipBound = tryClipRightSide(elementToClipRight, end)
    //          if (clipBound != null) {
    //            val rangeToDrop = TextRange(clipBound, elementEnd)
    //            if (canDropRange(rangeToDrop)) {
    //              rangesToDrop.add(rangeToDrop)
    //            }
    //          }
    //        }
    //      }
    //    }
    //
    //    if (rangesToDrop.isEmpty()) return null
    //
    //    val newFileText = StringBuilder {
    //      var offset = 0
    //      for (range in rangesToDrop) {
    //        assert(range.start >= offset)
    //        append(fileText.substring(offset, range.start))
    //        offset = range.end
    //      }
    //      append(fileText.substring(offset, fileText.length()))
    //    }.toString()
    //
    //    fun IntArray.update() {
    //      for (range in rangesToDrop.asReversed()) {
    //        for (i in indices) {
    //          val offset = this[i]
    //          if (offset >= range.end) {
    //            this[i] = offset - range.length
    //          }
    //          else {
    //            assert(offset <= range.start)
    //          }
    //        }
    //      }
    //    }
    //
    //    startOffsets.update()
    //    endOffsets.update()

    null
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

  def shiftedElement(inElem: PsiElement, dropElements: mutable.HashSet[PsiElement], endOffset: Int): PsiElement = {
    var elem = inElem
    while (elem.getPrevSibling != null &&
      !elem.getPrevSibling.isInstanceOf[PsiFile] &&
      canDropElement(elem.getPrevSibling)) {

      elem = elem.getPrevSibling
      dropElements += elem
    }

    if (elem.getParent != null && !elem.getParent.isInstanceOf[PsiFile] && elem.getParent.getFirstChild == elem
      && elem.getParent.getTextRange.getEndOffset <= endOffset) {
      elem = elem.getParent
    } else if (elem.getParent != null && !elem.getParent.isInstanceOf[PsiFile]
      && elem.getParent.getTextRange.getEndOffset <= endOffset) {
      val it = elem.getParent.children.takeWhile(el => canDropElement(el)).toSeq
      dropElements ++= it
      elem = elem.getParent
    }

    elem
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

  def addImportsForPrefixedElements(elements: Seq[Binding], project: Project) = {
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

}
