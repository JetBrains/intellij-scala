package org.jetbrains.plugins.scala
package lang.rearranger

import org.jetbrains.plugins.scala.lang.psi.api.{ScalaRecursiveElementVisitor, ScalaFile, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScStableCodeReferenceElement, ScReferenceElement, ScModifierList,
ScConstructor}
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken
import com.intellij.openapi.editor.Document
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil
import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import scala.Some
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType._
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier._
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.Any
import org.jetbrains.plugins.scala.lang.psi.types.Unit
import org.jetbrains.plugins.scala.lang.psi.types.Boolean
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging

/**
 * @author Roman.Shein
 *         Date: 09.07.13
 */
class ScalaArrangementVisitor(parseInfo: ScalaArrangementParseInfo, document: Document, ranges: Iterable[TextRange],
                              groupingRules: Set[ArrangementSettingsToken]) extends ScalaElementVisitor {

  private val arrangementEntries = mutable.Stack[ScalaArrangementEntry]()

  private val splitBodyByExpressions = groupingRules.contains(SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_EXPRESSIONS)

  private val unseparableRanges = mutable.HashMap[ScalaArrangementEntry /*parent*/ ,
          mutable.Queue[ScalaArrangementEntry] /*Arrangement blocks*/ ]()

  /**
   * Traverses method body to build inter-method dependencies.
   **/
  override def visitTypeAlias(alias: ScTypeAlias) {
    processEntry(createNewEntry(alias.getParent,
      expandTextRangeToComment(alias), TYPE, alias.getName, canArrange = true), alias, null)
  }

  override def visitConstructor(constr: ScConstructor) {
    createNewEntry(constr.getParent,
      expandTextRangeToComment(constr), CONSTRUCTOR, null, canArrange = true)
  }

  override def visitFunction(fun: ScFunction) {
    processEntry(createNewEntry(fun.getParent,
      expandTextRangeToComment(fun), FUNCTION, fun.getName, canArrange = true), fun, null)
  }

  override def visitFunctionDefinition(fun: ScFunctionDefinition) {
    val entry = createNewEntry(fun.getParent, expandTextRangeToComment(fun), FUNCTION, fun.getName, canArrange = true)
    parseInfo.onMethodEntryCreated(fun, entry)
    fun.body match {
      case Some(body) =>
        processEntry(entry, fun, body)
        val methodBodyProcessor = new MethodBodyProcessor(parseInfo, fun)
        body.accept(methodBodyProcessor)
      case None => processEntry(entry, fun, null)
    }
    parseProperties(fun, entry)
  }

  override def visitMacroDefinition(fun: ScMacroDefinition) {
    val entry = createNewEntry(fun.getParent, expandTextRangeToComment(fun), MACRO, fun.getName, canArrange = true)
    parseInfo.onMethodEntryCreated(fun, entry)
    processEntry(entry, fun, null)
    fun.getLastChild match {
      case ref: ScStableCodeReferenceElement =>
        val methodBodyProcessor = new MethodBodyProcessor(parseInfo, fun)
        ref.accept(methodBodyProcessor)
      case _ =>
    }
  }

  override def visitFile(file: PsiFile) = file match {
    case scFile: ScalaFile => scFile.acceptChildren(this)
    case _ =>
  }

  override def visitPatternDefinition(pat: ScPatternDefinition) {
    //TODO: insert inter-field dependency here
    processEntry(createNewEntry(pat.getParent, expandTextRangeToComment(pat), VAL,
      pat.pList.patterns.toList.head.bindings(0).getName, canArrange = true), pat, pat.expr.getOrElse(null))
  }

  override def visitElement(v: ScalaPsiElement) = v match {
    case packaging: ScPackaging => packaging.acceptChildren(this)
    case _ => super.visitElement(v)
  } 
  
  override def visitValueDeclaration(v: ScValueDeclaration) =
    processEntry(createNewEntry(v.getParent, expandTextRangeToComment(v), VAL, v.getName, canArrange = true), v, null)

  override def visitVariableDefinition(varr: ScVariableDefinition) {
    //TODO: insert inter-field dependency here
    processEntry(createNewEntry(varr.getParent, expandTextRangeToComment(varr), VAR, varr.declaredElements(0).getName,
      canArrange = true), varr, varr.expr.getOrElse(null))
  }

  override def visitVariableDeclaration(varr: ScVariableDeclaration) =
    processEntry(createNewEntry(varr.getParent, expandTextRangeToComment(varr), VAR, varr.declaredElements(0).getName, canArrange = true),
      varr, null)

  override def visitTypeDefintion(typedef: ScTypeDefinition) {
    val entry = createNewEntry(typedef.getParent, expandTextRangeToComment(typedef), typedef match {
      case _: ScClass => CLASS
      case _: ScTrait => TRAIT
      case _ => OBJECT
    }, typedef.getName, canArrange = true)
    processEntry(entry, typedef, typedef.extendsBlock.templateBody getOrElse null)
  }

  private def withinBounds(range: TextRange) = ranges.foldLeft(false)((acc: Boolean, current: TextRange) =>
    acc || current.intersects(range))

  private def getCurrentEntry = if (arrangementEntries.isEmpty) null else arrangementEntries.top

  private def createNewEntry(parent: PsiElement, range: TextRange,
                             tokenType: ArrangementSettingsToken, name: String, canArrange: Boolean) = {
    if (!withinBounds(range)) {
      null
    } else {

      val currentEntry = getCurrentEntry
      val newRange = if (canArrange && document != null) {
        ArrangementUtil.expandToLineIfPossible(range, document)
      } else {
        range
      }
      //we only arrange elements in ScTypeDefinitions and top-level elements
      val newEntry = new ScalaArrangementEntry(currentEntry, newRange, tokenType, name, canArrange &&
              (parent.isInstanceOf[ScTemplateBody] || parent.isInstanceOf[PsiFile]))

      if (currentEntry == null) {
        parseInfo.addEntry(newEntry)
      } else {
        currentEntry.addChild(newEntry)
      }
      //      psiElementsToEntries = psiElementsToEntries + (element -> newEntry)
      newEntry
    }
  }

  private def parseModifiers(modifiers: ScModifierList, entry: ScalaArrangementEntry) {
    if (modifiers != null) {
      for (modName <- modifiers.getModifiersStrings) {
        getModifierByName(modName).flatMap((mod: ArrangementSettingsToken) => {
          entry.addModifier(mod); None
        })
      }
    }
    import scala.collection.JavaConversions._
    if (scalaAccessModifiersValues.intersect(entry.getModifiers).isEmpty) {
      entry addModifier PUBLIC
    }
  }

  private def processEntry(entry: ScalaArrangementEntry, modifiers: ScModifierListOwner, nextPsiRoot: ScalaPsiElement) {
    if (entry == null) return
    if (modifiers != null) {
      parseModifiers(modifiers.getModifierList, entry)
    }
    if (nextPsiRoot != null) {
      arrangementEntries.push(entry)
      try nextPsiRoot match {
        case body: ScTemplateBody if splitBodyByExpressions => traverseTypedefBody(body, entry)
        case _ => nextPsiRoot.acceptChildren(this)
      } finally {
        arrangementEntries.pop()
      }
    }
  }

  private def traverseTypedefBody(psiRoot: ScTemplateBody, entry: ScalaArrangementEntry) {
    genUnseparableRanges(psiRoot, entry)
    val top = arrangementEntries.top
    val queue = unseparableRanges.get(entry).getOrElse(mutable.Queue[ScalaArrangementEntry]())
    //    var unseparable =
    def next() = if (queue.isEmpty) null else queue.dequeue()
    psiRoot.getChildren.foldLeft(false, if (queue.isEmpty) null else queue.dequeue())((acc, child) => {
      val (insideBlock, unseparable) = acc
      val childStart = child.getTextRange.getStartOffset
      //check if there are any more unseparable blocks at all
      val res = if (unseparable != null) {
        //process current child with regard to current block
        (insideBlock, childStart >= unseparable.getStartOffset, childStart >= unseparable.getEndOffset) match {
          case (false, true, false) => //entering arrange block
            arrangementEntries.push(unseparable)
            (true, unseparable)
          case (true, true, false) => (true, unseparable) //inside arrange block
          case (true, true, true) => //leaving arrange block
            arrangementEntries.pop()
            val nextUnseparable = next()
            //check whether new current block is immediately adjucent to the previous
            //in such case leaving the previous means entering the current
            if (childStart >= nextUnseparable.getStartOffset) {
              arrangementEntries.push(nextUnseparable)
              (true, nextUnseparable)
            } else {
              (false, nextUnseparable)
            }
          case _ => (false, unseparable) //outside arrange block
        }
      } else (false, unseparable)
      child.accept(this)
      res
    })
    if (arrangementEntries.top != top) {
      //the last block was entered, but has never been left; i.e. the last block spans body until the end
      arrangementEntries.pop()
    }
  }

  private def expandTextRangeToComment(node: ScalaPsiElement) = {
    val prev = node.getPrevSibling
    val first = node.getFirstChild
    var currentNode: PsiElement = node
    var range =
      if (first != null && first.isInstanceOf[PsiComment] && prev != null && (!prev.isInstanceOf[PsiWhiteSpace] ||
              prev.isInstanceOf[PsiWhiteSpace] && !prev.getText.contains("\n") && prev.getPrevSibling != null)) {
        new TextRange(node.getTextRange.getStartOffset + first.getTextRange.getLength + 1,
          node.getTextRange.getEndOffset)
      } else {
        node.getTextRange
      }
    range = node.nextSibling match {
      case Some(semicolon: PsiElement) if semicolon.getNode.getElementType == ScalaTokenTypes.tSEMICOLON =>
        currentNode = semicolon; range.union(semicolon.getTextRange)
      case _ => range
    }
    val res = currentNode.getNextSibling match {
      case sibling: PsiWhiteSpace =>
        if (!sibling.getText.contains("\n")) {
          sibling.getNextSibling match {
            case comment: PsiComment => range.union(sibling.getTextRange).union(comment.getTextRange)
            case nonComment: ScalaPsiElement => val next = nonComment.getFirstChild
              if (next != null && next.isInstanceOf[PsiComment]) {
                range.union(sibling.getTextRange).union(next.getTextRange)
              } else {
                range
              }
            case _ => range
          }
        } else {
          range
        }
      case comment: PsiComment => range.union(comment.getTextRange)
      case _ => range
    }
    res
  }

  private class MethodBodyProcessor(val info: ScalaArrangementParseInfo, val baseMethod: ScFunction) extends ScalaRecursiveElementVisitor {

    override def visitReference(ref: ScReferenceElement) {
      ref.resolve() match {
        case fun: ScFunction if fun.getContainingClass == baseMethod.getContainingClass =>
          assert(baseMethod != null)
          info.registerDependency(baseMethod, fun)
        case _ =>
      }
      super.visitReference(ref)
    }

    override def visitReferenceExpression(ref: ScReferenceExpression) {
      visitReference(ref)
    }
  }

  private def parseProperties(method: ScFunction, entry: ScalaArrangementEntry) {
    if (!(groupingRules.contains(JAVA_GETTERS_AND_SETTERS) || groupingRules.contains(SCALA_GETTERS_AND_SETTERS)) ||
            entry == null) {
      return
    }
    val methodName = method.getName
    val psiParent = method.getParent
    if (ScalaArrangementVisitor.isJavaGetter(method)) {
      parseInfo.registerJavaGetter((if (methodName.startsWith("get")) StringUtil.decapitalize(methodName.substring(3))
      else StringUtil.decapitalize(methodName.substring(2)), psiParent), method, entry)
    } else if (ScalaArrangementVisitor.isJavaSetter(method)) {
      parseInfo.registerJavaSetter((StringUtil.decapitalize(methodName.substring(3)), psiParent), method, entry)
    } else if (ScalaArrangementVisitor.isScalaGetter(method)) {
      parseInfo.registerScalaGetter((methodName, psiParent), method, entry)
    } else if (ScalaArrangementVisitor.isScalaSetter(method)) {
      parseInfo.registerScalaSetter((ScalaArrangementVisitor.removeScalaSetterEnding(methodName), psiParent), method,
        entry)
    }
  }

  private def genUnseparableRanges(body: ScTemplateBody, entry: ScalaArrangementEntry) = {
    body.getChildren.foldLeft(None)((startOffset, child) => {
      val newOffset = if (startOffset.isDefined) startOffset.get else child.getTextRange.getStartOffset
      if (child.isInstanceOf[ScExpression]) {
        if (!unseparableRanges.contains(entry)) {
          unseparableRanges += (entry -> mutable.Queue[ScalaArrangementEntry]())
        }
        unseparableRanges.get(entry).map(queue =>
          queue.enqueue(createNewEntry(body, new TextRange(newOffset,
            child.getTextRange.getEndOffset), UNSEPARABLE_RANGE, null, canArrange = true)))
        None
      } else startOffset
    }
    )
  }
}

object ScalaArrangementVisitor {
  private def nameStartsWith(name: String, start: String) = {
    val length = name.length
    name.startsWith(start) && length > start.length && !(Character.isLowerCase(name.charAt(start.length())) &&
            (length == start.length() + 1 || Character.isLowerCase(name.charAt(start.length() + 1))))
  }

  private def hasJavaGetterName(method: ScFunction) = {
    val name = method.getName
    if (nameStartsWith(name, "get")) {
      method.returnType.getOrAny != Unit
    } else if (nameStartsWith(name, "is")) {
      method.returnType.getOrAny == Boolean
    } else false
  }

  private def hasJavaSetterName(method: ScFunction) = {
    val name = method.name
    nameStartsWith(name, "set")
  }

  private def hasScalaSetterName(method: ScFunction) = method.name.endsWith("_=")

  private def hasSetterSignature(method: ScFunction) =
    method.getParameterList.getParametersCount == 1 && (method.returnType.getOrAny match {
      case Any => true
      case returnType: ScType => returnType == Unit
    })

  private def isJavaGetter(method: ScFunction) =
    hasJavaGetterName(method) && method.getParameterList.getParametersCount == 0

  private def isJavaSetter(method: ScFunction) = hasJavaSetterName(method) && hasSetterSignature(method)

  private def isScalaSetter(method: ScFunction) = hasScalaSetterName(method) && hasSetterSignature(method)

  private def isScalaGetter(method: ScFunction) = method.getParameterList.getParametersCount == 0

  private def removeScalaSetterEnding(name: String) = name.substring(0, name.length - 4) //removing _$eq
}
