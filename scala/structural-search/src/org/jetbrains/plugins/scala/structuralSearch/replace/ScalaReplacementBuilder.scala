package org.jetbrains.plugins.scala.structuralSearch.replace

import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import com.intellij.structuralsearch.{MatchResult, StructuralSearchProfile}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.extensions.implementation.iterator.ChildrenIterator
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationsHolder}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGuard
import org.jetbrains.plugins.scala.lang.psi.api.statements
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScValueOrVariable, ScValueOrVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScGivenDefinition, ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchProfile
import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchProfile.PATTERN_CONTEXT

import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala

class ScalaReplacementBuilder(val profile: StructuralSearchProfile) {
  def findMatchResult(res: MatchResult, name: String): Option[MatchResult] =
    findMatchResult(Some(res), name)
  def findMatchResult(res: Option[MatchResult], name: String): Option[MatchResult] = {
    res.flatMap(_.getChildren.asScala.find(_.getName == name))
      .orElse(res.flatMap(findRecMatchResult(_, None, name)))
      .orElse(res.flatMap(r => findRecMatchResult(r.getRoot, res, name)))
  }

  def findRecMatchResult(res: MatchResult, ignored: Option[MatchResult], name: String): Option[MatchResult] = {
    if (res.getChildren.asScala.exists(ignored.contains)) return None
    if (res.getName == name) return Some(res)
    for (subRes <- res.getChildren.asScala) {
      val found = findRecMatchResult(subRes, ignored, name)
      if (found.nonEmpty)
        return found
    }
    None
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

  def annotationSkipper(holder: ScAnnotationsHolder, nextEl: PsiElement, skipBlocks: Skippers): Unit = {
    holder.annotations.headOption.map(_.getParent) match {
      case None =>
      case Some(annos) =>
        skipBlocks.put(annos, (if nextEl.getText.isEmpty then nextEl.getNextSibling else nextEl.getPrevSibling, annos, 0))
    }
  }

  def typeParametersSkippers(holder: ScTypeParametersOwner, skipBlocks: Skippers): Unit = {
    holder.typeParametersClause match {
      case None =>
      case Some(typpa) => skipBlocks.put(typpa, (typpa, typpa, 2))
    }
  }

  def buildReplacement(element: PsiElement, scopeRes: MatchResult, result: StringBuilder): Unit =
    buildReplacement(element, Some(scopeRes), result)
  def buildReplacement(element: PsiElement, scopeRes: Option[MatchResult], result: StringBuilder): Unit = {
    element match {
      // Build class likes
      case replacePat: (ScClass | ScTrait | ScObject | ScGivenDefinition) =>
        val replacePattern: ScTypeDefinition = replacePat

        def buildClass(subRes: Option[MatchResult], insertBefore: Map[PsiElement, String] = Map(), insertAfter: Map[PsiElement, String] = Map()): Unit = {
          var skipBlocks: Skippers = mutable.Map()
          annotationSkipper(replacePattern, replacePattern.getModifierList, skipBlocks)
          typeParametersSkippers(replacePattern, skipBlocks)

          buildChildren(replacePattern, subRes, result, insertBefore = insertBefore, insertAfter = insertAfter, skipBlock = skipBlocks)
        }
        handleScope(replacePattern, Option(replacePattern.nameId), scopeRes, result, (ident, subRes) => {
          val parameterMatch = subRes.getMatch.asInstanceOf[ScTemplateDefinition]
          val searchPattern = findMatchResult(subRes, PATTERN_CONTEXT).getOrElse(throw new Exception("Expected pattern context")).getMatch



          buildClass(Some(subRes))
        }, Some(() => buildClass(scopeRes)))
      // Build function
      case replacePattern: ScFunction =>
        def buildFunc(subRes: Option[MatchResult], insertBefore: Map[PsiElement, String] = Map(), insertAfter: Map[PsiElement, String] = Map()): Unit = {
          val skipBlocks: Skippers = mutable.Map()
          annotationSkipper(replacePattern, replacePattern.getModifierList, skipBlocks)
          typeParametersSkippers(replacePattern, skipBlocks)
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
          val parameterMatch = subRes.getMatch
          val searchPattern = findMatchResult(subRes, PATTERN_CONTEXT).getOrElse(throw new Exception("Expected pattern context")).getMatch

          val modifierCopy = ifNotMentioned(searchPattern.asOptionOf[ScModifierListOwner].flatMap(_.getModifierList.modifiersOrdered.headOption), replacePattern.getModifierList.modifiersOrdered.headOption,
            {
              val modListSib = replacePattern.getModifierList.getNextSibling
              if (modListSib.is[PsiWhiteSpace]) modListSib.getNextSibling
              else modListSib
            },
            parameterMatch.asOptionOf[ScModifierListOwner].map(_.getModifierList.modifiersOrdered.map(_.text() + " ").mkString))
          val annotationsCopy = ifNotMentioned(searchPattern.asOptionOf[ScAnnotationsHolder].flatMap(_.annotations.headOption), replacePattern.annotations.headOption, replacePattern.getModifierList,
            parameterMatch.asOptionOf[ScAnnotationsHolder].filter(_.annotations.nonEmpty).map(_.annotations.map(_.getText).mkString(" ") + "\n"))
          val typeParaCopy = ifNotMentioned(searchPattern.asOptionOf[ScTypeParametersOwner].flatMap(_.typeParametersClause), replacePattern.typeParametersClause, ident, parameterMatch.asOptionOf[ScTypeParametersOwner].flatMap(_.typeParametersClause.map(_.getText)))
          val retParaCopy = ifNotMentioned(searchPattern.asOptionOf[ScFunction].flatMap(_.returnTypeElement), replacePattern.returnTypeElement, replacePattern.paramClauses,
            parameterMatch.asOptionOf[ScFunction].flatMap(_.returnTypeElement.map(": " + _.getText)))
          val retParaCopyValVar = ifNotMentioned(searchPattern.asOptionOf[ScValueOrVariable].flatMap(_.typeElement), replacePattern.returnTypeElement, replacePattern.paramClauses,
            parameterMatch.asOptionOf[ScValueOrVariable].flatMap(_.typeElement.map(": " + _.getText)))
          val bodyCopy = ifNotMentioned(searchPattern.asOptionOf[ScFunctionDefinition].flatMap(_.body),
            replacePattern.asOptionOf[ScFunctionDefinition].flatMap(_.body),
            replacePattern.returnTypeElement.getOrElse(replacePattern.paramClauses),
            parameterMatch.asOptionOf[ScFunctionDefinition].flatMap(_.body).map(" = " + _.getText))
          val bodyCopyValVar = ifNotMentioned(searchPattern.asOptionOf[ScValueOrVariableDefinition].flatMap(_.expr),
            replacePattern.asOptionOf[ScFunctionDefinition].flatMap(_.body),
            replacePattern.returnTypeElement.getOrElse(replacePattern.paramClauses),
            parameterMatch.asOptionOf[ScValueOrVariableDefinition].flatMap(_.expr).map(" = " + _.getText))
          val insertBefore = mergeInserts(annotationsCopy, modifierCopy)
          val insertAfter = mergeInserts(typeParaCopy, mergeInserts(mergeInserts(retParaCopy, retParaCopyValVar), mergeInserts(bodyCopy, bodyCopyValVar)))

          buildFunc(Some(subRes), insertBefore = insertBefore, insertAfter = insertAfter)
        }, Some(() => buildFunc(scopeRes)))
      // Build parameter
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
          {
            val source = if replacePattern.isVal || replacePattern.isVar then replacePattern else parameterMatch
            if (source.isVal)
              result.append("val ")
            else if (source.isVar)
              result.append("var ")
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
      // Build annotation
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
      // Build case guard
      case guard: ScGuard =>
        buildChildren(guard, scopeRes, result, skipBlock = mutable.Map(guard.getFirstChild -> (guard.getLastChild, guard.expr.orNull, 0)))
      // Build case clause
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
      // Default case for leaf elements
      case _ if element.getFirstChild == null =>
        val text = element.getText
        if (profile.isReplacementTypedVariable(text)) {
          findMatchResult(scopeRes, profile.stripReplacementTypedVariableDecorations(text)) match {
            case None =>
            case Some(res) =>
              if (res.isMultipleMatch)
                handleMultiple(res, result, subRes => insertImage(subRes, result))
              else
                insertImage(res, result)
          }
        } else {
          result.append(text)
        }
      // Default case non leaf elements
      case _ => buildChildren(element, scopeRes, result)
    }
  }

  private def insertImage(res: MatchResult, sb: StringBuilder): Unit = {
    if (res.isScopeMatch)
      findMatchResult(res, ScalaStructuralSearchProfile.SCOPE_ID) match {
        case None => sb.append(res.getMatchImage)
        case Some(mR) => sb.append(mR.getMatchImage)
      }
    else
      sb.append(res.getMatchImage)
  }
}
