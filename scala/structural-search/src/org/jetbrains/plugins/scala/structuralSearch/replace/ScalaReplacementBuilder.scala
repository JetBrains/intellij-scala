package org.jetbrains.plugins.scala.structuralSearch.replace

import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import com.intellij.structuralsearch.{MatchResult, StructuralSearchProfile}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.extensions.implementation.iterator.ChildrenIterator
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGuard
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchProfile.PATTERN_CONTEXT

import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala

class ScalaReplacementBuilder(val profile: StructuralSearchProfile) {
  def findMatchResult(res: MatchResult, name: String): Option[MatchResult] =
    findMatchResult(Some(res), name)
  def findMatchResult(res: Option[MatchResult], name: String): Option[MatchResult] = {
    res.flatMap(_.getChildren.asScala.find(_.getName == name))
      .orElse(res.filter(_.getName == name))
  }

  type SkipConf = (PsiElement, PsiElement, Int)
  type Skippers = mutable.Map[PsiElement, SkipConf]
  def buildChildren(psi: PsiElement,
                    scopeRes: Option[MatchResult],
                    result: StringBuilder,
                    insertBefore: Map[PsiElement, String] = Map(),
                    insertAfter: Map[PsiElement, String] = Map(),
                    skipBlock: Skippers = mutable.Map()): Unit = {
    var skipping = false
    var skipSb = StringBuilder()
    var checkSb = StringBuilder()
    var curConf: Option[SkipConf] = None
    for (cur <- ChildrenIterator(psi)) {
      insertBefore.get(cur).foreach((if skipping then skipSb else result).append)
      skipBlock.get(cur) match {
        case None =>
        case Some(conf) =>
          skipping = true
          curConf = Some(conf)
      }
      val sb = if (!skipping) result
      else if (curConf.exists(_._2 == cur)) checkSb
      else skipSb

      buildReplacement(cur, scopeRes, sb)

      if (skipping) {
        if (curConf.exists(_._2 == cur))
          skipSb.append(checkSb)
        if (curConf.exists(_._1 == cur)) {
          if (curConf.exists(_._3 < checkSb.size)) {
            result.append(skipSb)
          }
          skipping = false
          skipSb = StringBuilder()
          checkSb = StringBuilder()
          curConf = None
        }
      }
      insertAfter.get(cur).foreach((if skipping then skipSb else result).append)
    }
  }

  def ifNotMentioned(patternOpt: Option[_], replaceOpt: Option[_], refEl: PsiElement, text: Option[String]): Map[PsiElement, String] = {
    if (patternOpt.isEmpty && replaceOpt.isEmpty) {
      text.map(t => Map(refEl -> t)).getOrElse(Map())
    } else Map()
  }

  def handleMultiple(matchResult: MatchResult, result: StringBuilder, body: MatchResult => Unit, div: String = ""): Unit = {
    if (!matchResult.getChildren.isEmpty) {
      val it = matchResult.getChildren.iterator
      var last = it.next
      body(last)
      while (it.hasNext) {
        result.append(div)
        val cur = it.next
        {
          var el = last.getMatch.getNextSibling
          while (el != cur.getMatch && el != null) {
            result.append(el.getText)
            el = el.getNextSibling
          }
        }
        body(cur)
        last = cur
      }
    }
  }

  def handleScope[T <: PsiElement](replacePattern: T, ident: Option[PsiElement], scopeRes: Option[MatchResult], result: StringBuilder, body: ((PsiElement, MatchResult)) => Unit, noVarBody: Option[() => Unit] = None): Unit = {
    ident.filter(ident => profile.isReplacementTypedVariable(ident.getText))
      .map(ident =>
        findMatchResult(scopeRes, profile.stripReplacementTypedVariableDecorations(ident.getText))
          .map(subRes => {
            if (subRes.isMultipleMatch) {
              handleMultiple(subRes, result, mR => body(ident, mR))
            } else {
              body(ident, subRes)
            }
          })
          .getOrElse(Some(()))
      )
      .getOrElse(noVarBody.map(_()).getOrElse(buildChildren(replacePattern, scopeRes, result)))
  }

  def mergeInserts(map1: Map[PsiElement, String], map2: Map[PsiElement, String]): Map[PsiElement, String] = {
    map1 ++ map2.map {
      case (el, text) => el -> (map1.getOrElse(el, "") + text)
    }
  }

  def buildReplacement(element: PsiElement, scopeRes: MatchResult, result: StringBuilder): Unit =
    buildReplacement(element, Some(scopeRes), result)
  def buildReplacement(element: PsiElement, scopeRes: Option[MatchResult], result: StringBuilder): Unit = {
    element match {
      case replacePattern: ScFunction =>
        def buildFunc(subRes: Option[MatchResult], insertBefore: Map[PsiElement, String] = Map(), insertAfter: Map[PsiElement, String] = Map()): Unit = {
          val skipBlocks: Skippers = mutable.Map()
          replacePattern.annotations.headOption.map(_.getParent) match {
            case None =>
            case Some(annos) =>
              val modifierList = replacePattern.getModifierList
              skipBlocks.put(annos, (if modifierList.getText.isEmpty then modifierList.getNextSibling else modifierList.getPrevSibling, annos, 0))
          }
          replacePattern.typeParametersClause match {
            case None =>
            case Some(typpa) =>
              skipBlocks.put(typpa, (typpa, typpa, 2))
          }
          replacePattern.returnTypeElement match {
            case None =>
            case Some(ret) =>
              skipBlocks.put(replacePattern.paramClauses.getNextSibling, (ret, ret, 0))
          }
          replacePattern.asOptionOf[ScFunctionDefinition].flatMap(_.body) match {
            case None =>
            case Some(body) =>
              skipBlocks.put(replacePattern.returnTypeElement.getOrElse(replacePattern.paramClauses).getNextSibling, (body, body, 0))
          }
          buildChildren(replacePattern, subRes, result, insertBefore = insertBefore, insertAfter = insertAfter, skipBlock = skipBlocks)
        }
        handleScope(replacePattern, Option(replacePattern.nameId), scopeRes, result, (ident, subRes) => {
          val parameterMatch = subRes.getMatch.asInstanceOf[ScFunction]

          val searchPattern = findMatchResult(subRes, PATTERN_CONTEXT).getOrElse(throw new Exception("Expected pattern context")).getMatch.asInstanceOf[ScFunction]
          val modifierCopy = ifNotMentioned(searchPattern.getModifierList.modifiersOrdered.headOption, replacePattern.getModifierList.modifiersOrdered.headOption,
            {
              val modListSib = replacePattern.getModifierList.getNextSibling
              if (modListSib.is[PsiWhiteSpace]) modListSib.getNextSibling
              else modListSib
            },
            Some(parameterMatch.getModifierList.modifiersOrdered.map(_.text() + " ").mkString))
          val annotationsCopy = ifNotMentioned(searchPattern.annotations.headOption, replacePattern.annotations.headOption, replacePattern.getModifierList,
            Option.when(parameterMatch.annotations.nonEmpty)(parameterMatch.annotations.map(_.getText).mkString(" ") + "\n"))
          val typeParaCopy = ifNotMentioned(searchPattern.typeParametersClause, replacePattern.typeParametersClause, ident, parameterMatch.typeParametersClause.map(_.getText))
          val retParaCopy = ifNotMentioned(searchPattern.returnTypeElement, replacePattern.returnTypeElement, replacePattern.paramClauses, parameterMatch.returnTypeElement.map(": " + _.getText))
          val bodyCopy = ifNotMentioned(searchPattern.asOptionOf[ScFunctionDefinition].flatMap(_.body),
            replacePattern.asOptionOf[ScFunctionDefinition].flatMap(_.body),
            replacePattern.returnTypeElement.getOrElse(replacePattern.paramClauses),
            parameterMatch.asOptionOf[ScFunctionDefinition].flatMap(_.body).map(" = " + _.getText))
          val insertBefore = mergeInserts(annotationsCopy, modifierCopy)
          val insertAfter = mergeInserts(typeParaCopy, mergeInserts(retParaCopy, bodyCopy))

          buildFunc(Some(subRes), insertBefore, insertAfter)
        }, Some(() => buildFunc(scopeRes)))
      case replacePattern: ScParameter =>
        handleScope(replacePattern, Option(replacePattern.nameId), scopeRes, result, body = (ident, subRes) => {
          val parameterMatch = subRes.getMatch.asInstanceOf[ScParameter]

          val searchPattern = findMatchResult(subRes, PATTERN_CONTEXT).getOrElse(throw new Exception("Expected pattern context")).getMatch.asInstanceOf[ScParameter]

          replacePattern.annotations.headOption match {
            case Some(_) =>
              replacePattern.annotations.foreach(anno => {
                val prevSize = result.length()
                buildReplacement(anno, subRes, result)
                if (result.length() > prevSize)
                  result.append(" ")
              })
            case None =>
              if (searchPattern.annotations.isEmpty) {
                result.append(parameterMatch.annotations.map(_.getText + " ").mkString)
              }
          }
          buildReplacement(ident, subRes, result)
          replacePattern.typeElement match {
            case Some(typ) =>
              val sb = StringBuilder()
              buildReplacement(typ.getParent, subRes, sb)
              if (sb.nonEmpty) {
                result.append(": ")
                result.append(sb.result())
              }
            case None =>
              if (searchPattern.typeElement.isEmpty && parameterMatch.typeElement.nonEmpty) {
                result.append(": ")
                result.append(parameterMatch.typeElement.map(_.getText).getOrElse(""))
              }
          }
          replacePattern.getDefaultExpression match {
            case Some(default) =>
              val sb = StringBuilder()
              buildReplacement(default, subRes, sb)
              if (sb.nonEmpty) {
                result.append(" = ")
                result.append(sb.result())
              }
            case None =>
              if (searchPattern.getDefaultExpression.isEmpty && parameterMatch.getDefaultExpression.nonEmpty) {
                result.append(" = ")
                result.append(parameterMatch.getDefaultExpression.map(_.getText).getOrElse(""))
              }
          }
        })
      case annotation: ScAnnotation =>
        val text = annotation.constructorInvocation.reference.map(_.getText).getOrElse("")
        if (profile.isReplacementTypedVariable(text)) {
          findMatchResult(scopeRes, profile.stripReplacementTypedVariableDecorations(text)) match {
            case None =>
            case Some(subRes) =>
              if (subRes.isMultipleMatch)
                handleMultiple(subRes, result, mR => buildChildren(element, Some(mR), result), " ")
              else
                buildChildren(element, scopeRes, result)
          }
        } else {
          buildChildren(element, scopeRes, result)
        }
      case guard: ScGuard =>
        buildChildren(guard, scopeRes, result, skipBlock = mutable.Map(guard.getFirstChild -> (guard.getLastChild, guard.expr.orNull, 0)))
      case replacePattern: ScCaseClause =>
        handleScope(replacePattern, replacePattern.pattern, scopeRes, result, (ident, subRes) => {
          val ccMatch = subRes.getMatch match {
            case ccM: ScCaseClause => ccM
            case _ => throw Exception("Invalid element")
          }

          val searchPattern = findMatchResult(subRes, PATTERN_CONTEXT).getOrElse(throw new Exception("Expected pattern context")).getMatch.asInstanceOf[ScCaseClause]
          val insertAfter = ifNotMentioned(searchPattern.guard, replacePattern.guard, ident, ccMatch.guard.map(" " + _.getText))
          val skippers: Skippers = replacePattern.guard.map(guard => mutable.Map(guard.getPrevSibling -> (guard.asInstanceOf[PsiElement], guard.asInstanceOf[PsiElement], 0))).getOrElse(mutable.Map())
          buildChildren(replacePattern, Some(subRes), result, insertAfter = insertAfter, skipBlock = skippers)
        })
      case _ if element.getFirstChild == null =>
        val text = element.getText
        if (profile.isReplacementTypedVariable(text)) {
          findMatchResult(scopeRes, profile.stripReplacementTypedVariableDecorations(text)) match {
            case None =>
            case Some(res) =>
              if (res.isMultipleMatch)
                handleMultiple(res, result, subRes => result.append(subRes.getMatchImage))
              else
                result.append(res.getMatchImage)
          }
        } else {
          result.append(text)
        }
      case _ => buildChildren(element, scopeRes, result)
    }
  }
}
