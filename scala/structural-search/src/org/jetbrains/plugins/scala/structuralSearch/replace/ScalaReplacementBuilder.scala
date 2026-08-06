package org.jetbrains.plugins.scala.structuralSearch.replace

import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import com.intellij.structuralsearch.{MatchResult, StructuralSearchProfile}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.extensions.implementation.iterator.ChildrenIterator
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationsHolder, ScConstructorInvocation}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGuard
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScValue, ScValueOrVariable, ScValueOrVariableDefinition, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScConstructorOwner, ScGiven, ScGivenDefinition, ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchProfile
import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchProfile.PATTERN_CONTEXT

import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala

class ScalaReplacementBuilder(val profile: StructuralSearchProfile) {
  private def findMatchResult(res: MatchResult, name: String): Option[MatchResult] =
    findMatchResult(Some(res), name)
  private def findMatchResult(res: Option[MatchResult], name: String): Option[MatchResult] = {
    res.flatMap(_.getChildren.asScala.find(_.getName == name))
      .orElse(res.flatMap(findRecMatchResult(_, None, name)))
      .orElse(res.flatMap(r => findRecMatchResult(r.getRoot, res, name)))
  }

  private def findRecMatchResult(res: MatchResult, ignored: Option[MatchResult], name: String): Option[MatchResult] = {
    if (res.getChildren.asScala.exists(ignored.contains)) None
    else if (res.getName == name) Some(res)
    else res.getChildren.asScala.map(findRecMatchResult(_, ignored, name))
      .find(_.nonEmpty).flatten
  }

  private type SkipConf = (PsiElement, PsiElement, Int)
  private type Skippers = mutable.Map[PsiElement, SkipConf]
  private type InsertBeAf = Map[PsiElement, String]
  def buildChildren(psi: PsiElement,
                    scopeRes: Option[MatchResult],
                    result: StringBuilder,
                    insertBefore: InsertBeAf = Map(),
                    insertAfter: InsertBeAf = Map(),
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

      buildReplacement(cur, scopeRes, sb, insertBefore, insertAfter)

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

  private def ifNotMentioned(patternOpt: Option[?], replaceOpt: Option[?], refEl: PsiElement, text: Option[String]): Map[PsiElement, String] = {
    if (patternOpt.isEmpty && replaceOpt.isEmpty) {
      text.map(t => Map(refEl -> t)).getOrElse(Map())
    } else Map()
  }

  private def handleMultiple(matchResult: MatchResult, result: StringBuilder, body: MatchResult => Unit, div: Option[String] = None): Unit = {
    if (!matchResult.getChildren.isEmpty) {
      val it = matchResult.getChildren.iterator
      var last = it.next
      body(last)
      while (it.hasNext) {
        val cur = it.next
        if (div.nonEmpty) result.append(div.get)
        else {
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

  private def handleScope[T <: PsiElement](replacePattern: T, ident: Option[PsiElement], scopeRes: Option[MatchResult], result: StringBuilder, div: Option[String], body: ((PsiElement, MatchResult)) => Unit, noVarBody: Option[() => Unit] = None): Unit = {
    ident.filter(ident => profile.isReplacementTypedVariable(ident.getText))
      .map(ident =>
        findMatchResult(scopeRes, profile.stripReplacementTypedVariableDecorations(ident.getText))
          .map(subRes => {
            if (subRes.isMultipleMatch) {
              handleMultiple(subRes, result, mR => body(ident, mR), div)
            } else {
              body(ident, subRes)
            }
          })
          .getOrElse(Some(()))
      )
      .getOrElse(noVarBody.map(_()).getOrElse(buildChildren(replacePattern, scopeRes, result)))
  }

  private def mergeInserts(maps: InsertBeAf*): InsertBeAf = {
    maps.foldLeft(Map.empty[PsiElement, String])((map1: InsertBeAf, map2: InsertBeAf) =>
      map1 ++ map2.map {
        case (el, text) => el -> (map1.getOrElse(el, "") + text)
      })
  }

  private def createAnnotationSkipper(holder: ScAnnotationsHolder, nextEl: PsiElement, skipBlocks: Skippers): Unit = {
    holder.annotations.headOption.map(_.getParent) match {
      case None =>
      case Some(annos) =>
        skipBlocks.put(annos, (if nextEl.getText.isEmpty then nextEl.getNextSibling else nextEl.getPrevSibling, annos, 0))
    }
  }

  private def createTypeParametersSkippers(holder: ScTypeParametersOwner, skipBlocks: Skippers): Unit = {
    holder.typeParametersClause match {
      case None =>
      case Some(typpa) => skipBlocks.put(typpa, (typpa, typpa, 2))
    }
  }

  private def createAnnotationCopy(searchPattern: PsiElement, replacePattern: ScAnnotationsHolder, anchor: PsiElement,
                                   parameterMatch: PsiElement, nl: Boolean = true): InsertBeAf =
    ifNotMentioned(searchPattern.asOptionOf[ScAnnotationsHolder].flatMap(_.annotations.headOption), replacePattern.annotations.headOption, anchor,
      parameterMatch.asOptionOf[ScAnnotationsHolder].filter(_.annotations.nonEmpty).map(
        if (nl) _.annotations.map(_.getText).mkString(" ") + "\n"
        else _.annotations.map(_.getText + " ").mkString))

  private def createModifierCopy(searchPattern: PsiElement, replacePattern: ScModifierListOwner, parameterMatch: PsiElement): InsertBeAf =
    ifNotMentioned(searchPattern.asOptionOf[ScModifierListOwner].flatMap(_.getModifierList.modifiersOrdered.headOption), replacePattern.getModifierList.modifiersOrdered.headOption,
      {
        val modListSib = replacePattern.getModifierList.getNextSibling
        if (modListSib.is[PsiWhiteSpace]) modListSib.getNextSibling
        else modListSib
      },
      parameterMatch.asOptionOf[ScModifierListOwner].map(_.getModifierList.modifiersOrdered.map(_.text() + " ").mkString))

  private def createTypeParaCopy(searchPattern: PsiElement, replacePattern: ScTypeParametersOwner, anchor: PsiElement, parameterMatch: PsiElement): InsertBeAf =
    ifNotMentioned(searchPattern.asOptionOf[ScTypeParametersOwner].flatMap(_.typeParametersClause), replacePattern.typeParametersClause,
      anchor, parameterMatch.asOptionOf[ScTypeParametersOwner].flatMap(_.typeParametersClause.map(_.getText)))

  private def createRetTypeEquivCopy(searchPattern: PsiElement, replacePattern: PsiElement, anchor: PsiElement, parameterMatch: PsiElement): InsertBeAf = {
    def extractType(pattern: PsiElement): Option[PsiElement] =
      pattern match {
        case valvar: ScValueOrVariable => valvar.typeElement
        case func: ScFunction => func.returnTypeElement
        case para: ScParameter => para.typeElement
        case _ => None
      }
    ifNotMentioned(extractType(searchPattern), extractType(replacePattern), anchor, extractType(parameterMatch).map(": " + _.getText))
  }

  private def createFuncBodyEquivCopy(searchPattern: PsiElement, replacePattern: PsiElement, anchor: PsiElement, parameterMatch: PsiElement): InsertBeAf = {
    def extractType(pattern: PsiElement): Option[PsiElement] =
      pattern match {
        case valvar: ScValueOrVariableDefinition => valvar.expr
        case func: ScFunctionDefinition => func.body
        case para: ScParameter => para.getDefaultExpression
        case _ => None
      }
    ifNotMentioned(extractType(searchPattern), extractType(replacePattern), anchor, extractType(parameterMatch).map(" = " + _.getText))
  }

  def buildReplacement(element: PsiElement, scopeRes: MatchResult, result: StringBuilder): Unit =
    buildReplacement(element, Some(scopeRes), result)
  def buildReplacement(element: PsiElement, scopeRes: Option[MatchResult], result: StringBuilder, insertBefore: InsertBeAf = Map(), insertAfter: InsertBeAf = Map()): Unit = {
    element match {
      case replacePattern: ScExtendsBlock if replacePattern.getParent.is[ScClass | ScTrait | ScObject | ScGivenDefinition] =>
        val skipBlocks: Skippers = mutable.Map()
        replacePattern.templateParents match {
          case None =>
          case Some(par) => skipBlocks.put(replacePattern.getFirstChild, (if (par.getNextSibling.is[PsiWhiteSpace]) par.getNextSibling else par, par, 0))
        }
        buildChildren(element, scopeRes, result, insertAfter = insertAfter, skipBlock = skipBlocks)
      case replacePattern: ScTemplateBody if replacePattern.getParent.getParent.is[ScClass | ScTrait | ScObject | ScGivenDefinition] =>
        buildChildren(element, scopeRes, result, insertAfter = insertAfter)
      // Build class likes
      case replacePattern: ScTypeDefinition if replacePattern.is[ScClass | ScTrait | ScObject | ScGivenDefinition] =>
        def buildClass(subRes: Option[MatchResult], insertBefore: InsertBeAf = Map(), insertAfter: InsertBeAf = Map()): Unit = {
          val skipBlocks: Skippers = mutable.Map()
          createAnnotationSkipper(replacePattern, replacePattern.getModifierList, skipBlocks)
          createTypeParametersSkippers(replacePattern, skipBlocks)
          replacePattern.asOptionOf[ScConstructorOwner].flatMap(_.constructor) match {
            case None =>
            case Some(constr) => skipBlocks.put(constr, (constr, constr, 2))
          }

          buildChildren(replacePattern, subRes, result, insertBefore = insertBefore, insertAfter = insertAfter, skipBlock = skipBlocks)
        }
        handleScope(replacePattern, Option(replacePattern.nameId), scopeRes, result, Some("\n\n"), (ident, subRes) => {
          val parameterMatch = subRes.getMatch
          val searchPattern = findMatchResult(subRes, PATTERN_CONTEXT).getOrElse(throw new Exception("Expected pattern context")).getMatch

          val annotationCopy = createAnnotationCopy(searchPattern, replacePattern, replacePattern.getModifierList, parameterMatch)
          val modifierCopy = createModifierCopy(searchPattern, replacePattern, parameterMatch)
          val typeParaCopy = createTypeParaCopy(searchPattern, replacePattern, ident, parameterMatch)
          val primConstrCopy = ifNotMentioned(searchPattern.asOptionOf[ScConstructorOwner].flatMap(_.constructor.filter(_.getTextLength > 0)),
            replacePattern.asOptionOf[ScConstructorOwner].flatMap(_.constructor.filter(_.getTextLength > 0)),
            replacePattern.typeParametersClause.getOrElse(ident), parameterMatch.asOptionOf[ScConstructorOwner].flatMap(_.constructor.map(_.getText))
          )
          val parentsCopy = ifNotMentioned(searchPattern.asOptionOf[ScTemplateDefinition].flatMap(_.extendsBlock.templateParents),
            replacePattern.extendsBlock.templateParents,
            replacePattern.asOptionOf[ScConstructorOwner].flatMap(_.constructor)
              .orElse(replacePattern.typeParametersClause)
              .getOrElse(ident),
            parameterMatch.asOptionOf[ScTemplateDefinition].flatMap(_.extendsBlock.templateParents.map(par => if (par.getTextLength > 0) " extends " + par.getText else "")))

          val noBody = replacePattern.extendsBlock.templateBody.isEmpty
          val (bodyStart, enclStartAdd: InsertBeAf) = {
            if (noBody)
              (replacePattern.extendsBlock, Map(replacePattern.extendsBlock.asInstanceOf[PsiElement] -> " {"))
            else
              (replacePattern.extendsBlock.templateBody.get.getEnclosingStartElement.getOrElse(throw Exception("Body needs to have start element")), Map[PsiElement, String]())
          }

          val propCopy = ifNotMentioned(searchPattern.asOptionOf[ScTemplateDefinition].flatMap(_.properties.headOption),
            replacePattern.properties.headOption, bodyStart,
            parameterMatch.asOptionOf[ScTemplateDefinition].map(_.properties.map("\n  " + _.getText).mkString))
          val functionCopy = ifNotMentioned(searchPattern.asOptionOf[ScTemplateDefinition].flatMap(_.functions.headOption),
            replacePattern.functions.headOption, bodyStart,
            parameterMatch.asOptionOf[ScTemplateDefinition].map(_.functions.map("\n  " + _.getText).mkString))
          val subClassCopy = ifNotMentioned(searchPattern.asOptionOf[ScTemplateDefinition].flatMap(_.typeDefinitions.headOption),
            replacePattern.typeDefinitions.headOption, bodyStart,
            parameterMatch.asOptionOf[ScTemplateDefinition].map(_.typeDefinitions.map("\n  " + _.getText).mkString))
          val insertBefore = mergeInserts(annotationCopy, modifierCopy)
          val insertAfter = mergeInserts(typeParaCopy, primConstrCopy, parentsCopy, enclStartAdd, propCopy, functionCopy, subClassCopy)
          buildClass(Some(subRes), insertBefore = insertBefore, insertAfter = insertAfter)
          if (noBody) result.append("\n}")
        }, Some(() => buildClass(scopeRes)))
      case g: ScGiven =>
        buildChildren(element, scopeRes, result)
      // Build function
      case replacePattern: ScFunction =>
        def buildFunc(subRes: Option[MatchResult], insertBefore: Map[PsiElement, String] = Map(), insertAfter: Map[PsiElement, String] = Map()): Unit = {
          val skipBlocks: Skippers = mutable.Map()
          createAnnotationSkipper(replacePattern, replacePattern.getModifierList, skipBlocks)
          createTypeParametersSkippers(replacePattern, skipBlocks)
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
        handleScope(replacePattern, Option(replacePattern.nameId), scopeRes, result, Some("\n\n"), (ident, subRes) => {
          val parameterMatch = subRes.getMatch
          val searchPattern = findMatchResult(subRes, PATTERN_CONTEXT).getOrElse(throw new Exception("Expected pattern context")).getMatch

          val annotationsCopy = createAnnotationCopy(searchPattern, replacePattern, replacePattern.getModifierList, parameterMatch)
          val modifierCopy = createModifierCopy(searchPattern, replacePattern, parameterMatch)
          val typeParaCopy = createTypeParaCopy(searchPattern, replacePattern, ident, parameterMatch)
          val retParaCopy = createRetTypeEquivCopy(searchPattern, replacePattern, replacePattern.paramClauses, parameterMatch)
          val bodyCopy = createFuncBodyEquivCopy(searchPattern, replacePattern,
            replacePattern.returnTypeElement.getOrElse(replacePattern.paramClauses), parameterMatch)
          val insertBefore = mergeInserts(annotationsCopy, modifierCopy)
          val insertAfter = mergeInserts(typeParaCopy, retParaCopy, bodyCopy)

          buildFunc(Some(subRes), insertBefore = insertBefore, insertAfter = insertAfter)
        }, Some(() => buildFunc(scopeRes)))
      // Build parameter
      case replacePattern: ScParameter =>
        handleScope(replacePattern, Option(replacePattern.nameId), scopeRes, result, Some(", "), (ident, subRes) => {
          val parameterMatch = subRes.getMatch

          val searchPattern = findMatchResult(subRes, PATTERN_CONTEXT).getOrElse(throw new Exception("Expected pattern context")).getMatch

          {
            createAnnotationCopy(searchPattern, replacePattern, ident, parameterMatch, false).get(ident) match {
              case None =>
                replacePattern.annotations.foreach(anno => {
                  val prevSize = result.length()
                  buildReplacement(anno, subRes, result)
                  if (result.length() > prevSize)
                    result.append(" ")
                })
              case Some(cop) => result.append(cop)
            }
          }
          {
            val source = if replacePattern.isVal || replacePattern.isVar then replacePattern else parameterMatch
            if (source.asOptionOf[ScTypedDefinition].exists(_.isVal) || source.is[ScValue])
              result.append("val ")
            else if (source.asOptionOf[ScTypedDefinition].exists(_.isVar) || source.is[ScVariable])
              result.append("var ")
          }
          buildReplacement(ident, subRes, result)
          {
            replacePattern.typeElement match {
              case Some(typ) =>
                val sb = StringBuilder()
                buildReplacement(typ.getParent, subRes, sb)
                if (sb.nonEmpty) {
                  result.append(": ")
                  result.append(sb.result())
                }
              case None =>
                createRetTypeEquivCopy(searchPattern, replacePattern, ident, parameterMatch).get(ident) match {
                  case Some(cop) => result.append(cop)
                  case None =>
                }
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
              createFuncBodyEquivCopy(searchPattern, replacePattern, ident, parameterMatch).get(ident) match {
                case Some(cop) => result.append(cop)
                case None =>
              }
          }
        })
      case replacePattern: ScParameterClause =>
        result.append(replacePattern.getFirstChild.getText)
        var lastEl: Option[PsiElement] = None
        var foundFirst = false
        for (param <- replacePattern.parameters) {
          val sb = StringBuilder()
          buildReplacement(param, scopeRes, sb)
          if (sb.nonEmpty) {
            if (lastEl.nonEmpty && foundFirst) {
              var cur = lastEl.get.getNextSibling
              while (cur != param) {
                result.append(cur.getText)
                cur = cur.getNextSibling
              }
            }
            foundFirst = true
            result.append(sb.result)
          }
          lastEl = Some(param)
        }
        result.append(replacePattern.getLastChild.getText)
      // Value and Variable
      case replacePattern: ScValueOrVariable =>
        handleScope(replacePattern, Option.when(replacePattern.declaredNames.size == 1)(replacePattern.declaredElements.head.getParent), scopeRes, result, Some("\n"), (ident, subRes) => {
          val parameterMatch = subRes.getMatch
          val searchPattern = findMatchResult(subRes, PATTERN_CONTEXT).getOrElse(throw new Exception("Expected pattern context")).getMatch

          val annotationsCopy = createAnnotationCopy(searchPattern, replacePattern, replacePattern.getModifierList, parameterMatch)
          val modifierCopy = createModifierCopy(searchPattern, replacePattern, parameterMatch)
          val typeCopy = createRetTypeEquivCopy(searchPattern, replacePattern, ident, parameterMatch)
          val bodyCopy = createFuncBodyEquivCopy(searchPattern, replacePattern, replacePattern.typeElement.getOrElse(ident), parameterMatch)
          val insertBefore = mergeInserts(annotationsCopy, modifierCopy)
          val insertAfter = mergeInserts(typeCopy, bodyCopy)

          buildChildren(replacePattern, Some(subRes), result, insertBefore = insertBefore, insertAfter = insertAfter)
        })
      // Build annotation
      case annotation: ScAnnotation =>
        val text = annotation.constructorInvocation.reference.map(_.getText).getOrElse("")
        if (profile.isReplacementTypedVariable(text)) {
          findMatchResult(scopeRes, profile.stripReplacementTypedVariableDecorations(text)) match {
            case None =>
            case Some(subRes) =>
              if (subRes.isMultipleMatch)
                handleMultiple(subRes, result, mR => buildChildren(element, Some(mR), result), Some(" "))
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
        handleScope(replacePattern, replacePattern.pattern, scopeRes, result, Some("\n"), (ident, subRes) => {
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
    if (res.isScopeMatch && !res.getMatch.is[ScConstructorInvocation, ScTypeParam])
      findMatchResult(res, ScalaStructuralSearchProfile.SCOPE_ID) match {
        case None => sb.append(res.getMatchImage)
        case Some(mR) => sb.append(mR.getMatchImage)
      }
    else
      sb.append(res.getMatchImage)
  }
}
