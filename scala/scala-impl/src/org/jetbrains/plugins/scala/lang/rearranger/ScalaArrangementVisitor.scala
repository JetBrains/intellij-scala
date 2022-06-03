package org.jetbrains.plugins.scala
package lang.rearranger

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType._
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier._
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.{OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScModifierList, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScPackaging}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

private class ScalaArrangementVisitor(parseInfo: ScalaArrangementParseInfo,
                                      document: Document,
                                      ranges: Iterable[TextRange],
                                      groupingRules: Set[ArrangementSettingsToken])
  extends ScalaElementVisitor {

  import ScalaArrangementVisitor.getTokenType

  private val arrangementEntries = mutable.Stack[ScalaArrangementEntry]()

  private val splitBodyByExpressions = groupingRules.contains(RearrangerUtils.SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_EXPRESSIONS)

  private val splitBodyByImplicits = groupingRules.contains(RearrangerUtils.SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_IMPLICITS)

  private val unseparableRanges = mutable.HashMap[ScalaArrangementEntry /*parent*/ ,
          mutable.Queue[ScalaArrangementEntry] /*Arrangement blocks*/ ]()

  /**
   * Traverses method body to build inter-method dependencies.
   **/
  override def visitTypeAlias(alias: ScTypeAlias): Unit = {
    processEntry(getEntryForRange(alias.getParent,
      expandTextRangeToComment(alias), getTokenType(alias), alias.getName, canArrange = true), alias, null)
  }

  override def visitConstructorInvocation(constrInvocation: ScConstructorInvocation): Unit = {
    getEntryForRange(constrInvocation.getParent,
      expandTextRangeToComment(constrInvocation), getTokenType(constrInvocation), null, canArrange = true)
  }

  override def visitFunction(fun: ScFunction): Unit = {
    processEntry(getEntryForRange(fun.getParent,
      expandTextRangeToComment(fun), getTokenType(fun), fun.getName, canArrange = true), fun, null)
  }

  override def visitFunctionDefinition(fun: ScFunctionDefinition): Unit = {
    val entry = getEntryForRange(fun.getParent, expandTextRangeToComment(fun), getTokenType(fun), fun.getName, canArrange = true)
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

  override def visitMacroDefinition(fun: ScMacroDefinition): Unit = {
    val entry = getEntryForRange(fun.getParent, expandTextRangeToComment(fun), getTokenType(fun), fun.getName, canArrange = true)
    parseInfo.onMethodEntryCreated(fun, entry)
    processEntry(entry, fun, null)
    fun.getLastChild match {
      case ref: ScStableCodeReference =>
        val methodBodyProcessor = new MethodBodyProcessor(parseInfo, fun)
        ref.accept(methodBodyProcessor)
      case _ =>
    }
  }

  override def visitFile(file: PsiFile): Unit = file.acceptChildren(this)

  override def visitPatternDefinition(pat: ScPatternDefinition): Unit = {
    //TODO: insert inter-field dependency here
    val name  = pat.pList.patterns.headOption.flatMap(_.bindings.headOption).safeMap(_.getName) match {
      case Some(value) => value
      case None        => return
    }
    val range = expandTextRangeToComment(pat)
    val entry = getEntryForRange(pat.getParent, range, getTokenType(pat), name, canArrange = true)
    processEntry(entry, pat, pat.expr.orNull)
  }

  override def visitScalaElement(v: ScalaPsiElement): Unit = v match {
    case packaging: ScPackaging => packaging.acceptChildren(this)
    case _ => super.visitScalaElement(v)
  }

  override def visitValueDeclaration(v: ScValueDeclaration): Unit =
    processEntry(getEntryForRange(v.getParent, expandTextRangeToComment(v), getTokenType(v), v.getName, canArrange = true), v, null)

  override def visitVariableDefinition(varr: ScVariableDefinition): Unit = {
    //TODO: insert inter-field dependency here
    processEntry(getEntryForRange(varr.getParent, expandTextRangeToComment(varr), getTokenType(varr), varr.declaredElements.head.getName,
      canArrange = true), varr, varr.expr.orNull)
  }

  override def visitVariableDeclaration(varr: ScVariableDeclaration): Unit =
    processEntry(getEntryForRange(varr.getParent, expandTextRangeToComment(varr), getTokenType(varr), varr.declaredElements.head.getName, canArrange = true),
      varr, null)

  override def visitTypeDefinition(typedef: ScTypeDefinition): Unit = {
    processEntry(
      getEntryForRange(typedef.getParent, expandTextRangeToComment(typedef), getTokenType(typedef), typedef.getName, canArrange = true),
      typedef,
      typedef.extendsBlock.templateBody.orNull
    )
  }

  private def withinBounds(range: TextRange) = ranges.foldLeft(false)((acc: Boolean, current: TextRange) =>
    acc || current.intersects(range))

  private def getCurrentEntry = if (arrangementEntries.isEmpty) null else arrangementEntries.top

  private def getEntryForRange(parent: PsiElement, range: TextRange,
                               tokenType: ArrangementSettingsToken, name: String, canArrange: Boolean,
                               innerTokenType: Option[ArrangementSettingsToken] = None) = {
    if (!withinBounds(range)) {
      null
    } else {

      val currentEntry = getCurrentEntry
      val newRange = if (canArrange && document != null) {
        ArrangementUtil.expandToLineIfPossible(range, document)
      } else {
        range
      }
      if (currentEntry != null && currentEntry.spansTextRange(newRange)) {
        currentEntry
      } else {
        //we only arrange elements in ScTypeDefinitions and top-level elements
        val newEntry = new ScalaArrangementEntry(currentEntry, newRange, tokenType, name, canArrange &&
          (parent.isInstanceOf[ScTemplateBody] || parent.isInstanceOf[PsiFile]), innerTokenType)

        if (currentEntry == null) {
          parseInfo.addEntry(newEntry)
        } else {
          currentEntry.addChild(newEntry)
        }
        newEntry
      }
    }
  }

  private def parseModifiers(modifiers: ScModifierList, entry: ScalaArrangementEntry): Unit = {
    import org.jetbrains.plugins.scala.util.EnumSet._

    if (modifiers != null) {
      for (modName <- modifiers.modifiers) {
        RearrangerUtils.getModifierByName(modName.text()).flatMap((mod: ArrangementSettingsToken) => {
          entry.addModifier(mod); None
        })
      }
    }
    if (RearrangerUtils.scalaAccessModifiers.intersect(entry.getModifiers.asScala).isEmpty) {
      entry addModifier PUBLIC
    }
  }

  private def processEntry(entry: ScalaArrangementEntry, modifiers: ScModifierListOwner, nextPsiRoot: ScalaPsiElement): Unit = {
    if (entry == null) return
    if (modifiers != null) {
      parseModifiers(modifiers.getModifierList, entry)
    }
    if (nextPsiRoot != null) {
      //current entry may have been processed as unseparable range in upper block
      val newEntry = arrangementEntries.isEmpty || arrangementEntries.head.getType != RearrangerUtils.UNSEPARABLE_RANGE ||
        arrangementEntries.head.getStartOffset != entry.getStartOffset ||
        arrangementEntries.head.getEndOffset != entry.getEndOffset
      if (newEntry) arrangementEntries.push(entry)
      try nextPsiRoot match {
        case body: ScTemplateBody if splitBodyByExpressions || splitBodyByImplicits =>
          traverseTypedefBody(body, if (newEntry) entry else arrangementEntries.head)
        case _ => nextPsiRoot.acceptChildren(this)
      } finally {
        if (newEntry) arrangementEntries.pop()
      }
    }
  }

  private def traverseTypedefBody(psiRoot: ScTemplateBody, entry: ScalaArrangementEntry): Unit = {
    genUnseparableRanges(psiRoot, entry)
    val top = arrangementEntries.top
    val queue = unseparableRanges.getOrElse(entry, mutable.Queue[ScalaArrangementEntry]())
    //    var unseparable =
    def next() = Option(if (queue.isEmpty) null else queue.dequeue())
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
            //check whether new current block is immediately adjucent to the previous
            //in such case leaving the previous means entering the current
            next() match {
              case Some(nextUnseparable) if childStart >= nextUnseparable.getStartOffset =>
                arrangementEntries.push(nextUnseparable)
                (true, nextUnseparable)
              case Some(nextUnseparable) => (false, nextUnseparable)
              case _ => (false, null)
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

    override def visitReference(ref: ScReference): Unit = {
      ref.resolve() match {
        case fun: ScFunction if fun.getContainingClass == baseMethod.getContainingClass =>
          assert(baseMethod != null)
          info.registerDependency(baseMethod, fun)
        case _ =>
      }
      super.visitReference(ref)
    }

    override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
      visitReference(ref)
    }
  }

  private def parseProperties(method: ScFunction, entry: ScalaArrangementEntry): Unit = {
    if (!(groupingRules.contains(RearrangerUtils.JAVA_GETTERS_AND_SETTERS) || groupingRules.contains(RearrangerUtils.SCALA_GETTERS_AND_SETTERS)) ||
            entry == null) {
      return
    }
    val methodName = method.getName
    val psiParent = method.getParent
    if (ScalaArrangementVisitor.isJavaGetter(method)) {
      parseInfo.registerJavaGetter((if (methodName.startsWith("get")) StringUtil.decapitalize(methodName.substring(3))
      else StringUtil.decapitalize(methodName.substring(2)), psiParent), entry)
    } else if (ScalaArrangementVisitor.isJavaSetter(method)) {
      parseInfo.registerJavaSetter((StringUtil.decapitalize(methodName.substring(3)), psiParent), entry)
    } else if (ScalaArrangementVisitor.isScalaGetter(method)) {
      parseInfo.registerScalaGetter((methodName, psiParent), entry)
    } else if (ScalaArrangementVisitor.isScalaSetter(method)) {
      parseInfo.registerScalaSetter((ScalaArrangementVisitor.removeScalaSetterEnding(methodName), psiParent),
        entry)
    }
  }

  private def genUnseparableRanges(body: ScTemplateBody, entry: ScalaArrangementEntry) = {
    body.getChildren.foldLeft(None)((startOffset, child) => {
      val newOffset = if (startOffset.isDefined) startOffset.get else child.getTextRange.getStartOffset
      if (isExpressionSplit(child) || isImplicitSplit(child)) {
        if (!unseparableRanges.contains(entry)) {
          unseparableRanges += (entry -> mutable.Queue[ScalaArrangementEntry]())
        }
        unseparableRanges.get(entry).foreach(queue =>
          queue.enqueue(getEntryForRange(body, new TextRange(newOffset,
            child.getTextRange.getEndOffset), RearrangerUtils.UNSEPARABLE_RANGE, null, canArrange = false, Some(getTokenType(child)))))
        None
      } else startOffset
    })
  }

  private def isExpressionSplit(child: PsiElement): Boolean = splitBodyByExpressions && child.isInstanceOf[ScExpression]

  private def isImplicitSplit(child: PsiElement): Boolean = splitBodyByImplicits &&
    (child match {
      case modListOwner: ScModifierListOwner => modListOwner.hasModifierProperty("implicit")
      case _ => false
    })
}

object ScalaArrangementVisitor {

  private def nameStartsWith(name: String, @NonNls start: String) = {
    val length = name.length
    name.startsWith(start) && length > start.length && !(Character.isLowerCase(name.charAt(start.length())) &&
            (length == start.length() + 1 || Character.isLowerCase(name.charAt(start.length() + 1))))
  }

  private def hasJavaGetterName(method: ScFunction) = {
    import method.projectContext

    val name = method.getName
    val getAnd = "getAnd"
    if (nameStartsWith(name, "get") && !(nameStartsWith(name, getAnd) && name.charAt(getAnd.length).isUpper)) {
      method.returnType.getOrAny != api.Unit
    } else if (nameStartsWith(name, "is")) {
      method.returnType.getOrAny == api.Boolean
    } else false
  }

  private def hasJavaSetterName(method: ScFunction) = {
    val name = method.name
    nameStartsWith(name, "set")
  }

  private def hasScalaSetterName(method: ScFunction) = method.name.endsWith("_=")

  private def hasSetterSignature(method: ScFunction) =
    method.getParameterList.getParametersCount == 1 && (method.returnType.getOrAny match {
      case t if t.isAny => true
      case returnType: ScType => returnType.isUnit
    })

  private def isJavaGetter(method: ScFunction) =
    hasJavaGetterName(method) && method.getParameterList.getParametersCount == 0

  private def isJavaSetter(method: ScFunction) = hasJavaSetterName(method) && hasSetterSignature(method)

  private def isScalaSetter(method: ScFunction) = hasScalaSetterName(method) && hasSetterSignature(method)

  private def isScalaGetter(method: ScFunction) = method.getParameterList.getParametersCount == 0

  private def removeScalaSetterEnding(name: String) = name.substring(0, name.length - 4) //removing _$eq

  def getTokenType(psiElement: PsiElement): ArrangementSettingsToken = {
    import RearrangerUtils._
    psiElement match {
      case _: ScTypeAlias                                     => TYPE
      case _: ScMacroDefinition                               => MACRO
      case _: ScConstructorInvocation                         => CONSTRUCTOR
      case _: ScFunction | _: ScFunctionDefinition            => FUNCTION
      case _: ScPatternDefinition | _: ScValueDeclaration     => VAL
      case _: ScClass                                         => CLASS
      case _: ScVariableDefinition | _: ScVariableDeclaration => VAR
      case _: ScTrait                                         => TRAIT
      case _: ScObject                                        => OBJECT
      case _                                                  => UNSEPARABLE_RANGE
    }
  }
}
