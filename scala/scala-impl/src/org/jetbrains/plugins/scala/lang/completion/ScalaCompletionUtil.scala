package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.codeInsight.completion.{CompletionParameters, CompletionUtil, PrefixMatcher}
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, ModTracker}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

import java.util.regex.{Matcher, Pattern}

object ScalaCompletionUtil {

  import ScalaTokenTypes._

  val PREFIX_COMPLETION_KEY: Key[Boolean] = Key.create("prefix.completion.key")

  def hasNoQualifier(ref: ScReferenceExpression): Boolean =
    ref.qualifier.isEmpty && (ref.getParent match {
      case e: ScSugarCallExpr => e.operation != ref
      case _ => true
    })

  def hasQualifier(ref: ScReferenceExpression): Boolean = !hasNoQualifier(ref)

  def shouldRunClassNameCompletion(dummyPosition: PsiElement,
                                   prefixMatcher: PrefixMatcher,
                                   checkInvocationCount: Boolean = true)
                                  (implicit parameters: CompletionParameters): Boolean = {
    val invocationCount = parameters.getInvocationCount
    if (checkInvocationCount && !regardlessAccessibility(invocationCount)) return false

    if (dummyPosition.getNode.getElementType == tIDENTIFIER) {
      dummyPosition.getParent match {
        case ref: ScReference if ref.qualifier.isDefined => return false
        case _ =>
      }
    }

    if (checkInvocationCount && regardlessAccessibility(invocationCount)) return true

    val prefix = prefixMatcher.getPrefix
    prefix.nonEmpty && prefix.charAt(0).isUpper
  }

  def getLeafByOffset(offset: Int, element: PsiElement): PsiElement = {
    if (offset < 0) {
      return null
    }
    var candidate: PsiElement = element.getContainingFile
    if (candidate == null || candidate.getNode == null) return null
    while (candidate.getNode.getChildren(null).nonEmpty) {
      candidate = candidate.findElementAt(offset)
      if (candidate == null || candidate.getNode == null) return null
    }
    candidate
  }

  /**
   * first return value mean to stop here.
   * Second return value in case if first is true return second value
   */
  def getForAll(parent: PsiElement, leaf: PsiElement): (Boolean, Boolean) = {
    parent match {
      case _: ScalaFile =>
        if (leaf.getNextSibling != null && leaf.getNextSibling.getNextSibling.is[ScPackaging] &&
                leaf.getNextSibling.getNextSibling.getText.indexOf('{') == -1)
          return (true, false)
      case _ =>
    }
    parent match {
      case _: ScalaFile | _: ScPackaging =>
        var node = leaf.getPrevSibling
        if (node.is[PsiWhiteSpace]) node = node.getPrevSibling
        node match {
          case x: PsiErrorElement =>
            val s = ErrMsg("wrong.top.statement.declaration")
            x.getErrorDescription match {
              case `s` => return (true, true)
              case _ => return (true, false)
            }
          case _ => return (true, true)
        }
      case _: ScReferenceExpression =>
        parent.getParent match {
          case _: ScDeclarationSequenceHolder |
               _: ScTemplateBody |
               _: ScCaseClause =>
            if (awful(parent, leaf))
              return (true, true)
          case _ =>
        }
      case _ =>
    }

    (false, true)
  }

  def awful(parent: PsiElement, leaf: PsiElement): Boolean = {
    (leaf.getPrevSibling == null || leaf.getPrevSibling.getPrevSibling == null ||
      leaf.getPrevSibling.getPrevSibling.getNode.getElementType != kDEF) &&
      (parent.getPrevSibling == null || parent.getPrevSibling.getPrevSibling == null ||
        (parent.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaElementType.MATCH_STMT ||
          !parent.getPrevSibling.getPrevSibling.getLastChild.is[PsiErrorElement]))
  }

  def checkAfterSoftModifier(parent: PsiElement, leaf: PsiElement): Boolean = {
    if (!parent.is[ScReferenceExpression]) return false

    def check(ref: ScReferenceExpression): Boolean =
      ref.isInScala3File && ScalaKeyword.SOFT_MODIFIERS.contains(ref.refName) && awful(parent, leaf)

    parent.getParent match {
      // soft_modifier parent<caret>
      case ScPostfixExpr(ref: ScReferenceExpression, `parent`) => check(ref)
      // ... soft_modifier parent<caret>
      case ScInfixExpr(_, ref: ScReferenceExpression, `parent`) => check(ref)
      case _ => false
    }
  }

  def checkClassWith(clazz: ScTypeDefinition, additionText: String, manager: PsiManager): Boolean = {
    val classText: String = clazz.getText
    val text = replaceDummy(classText + " " + additionText)

    checkWith(manager.getProject, text)
  }

  def checkGivenWith(definition: ScGivenDefinition, additionText: String): Boolean = {
    val language = if (definition.isInScala3File) Scala3Language.INSTANCE else ScalaLanguage.INSTANCE
    val fileText = replaceDummy(definition.getText + " " + additionText)

    checkWith(definition.getProject, fileText, language)
  }

  def checkElseWith(text: String, context: PsiElement): Boolean = {
    val language = if (context.isInScala3File) Scala3Language.INSTANCE else ScalaLanguage.INSTANCE
    val fileText = "class a {\n" + text + "\n}"

    checkWith(context.getProject, fileText, language)
  }

  def checkThenWith(text: String, context: PsiElement): Boolean = context.isInScala3File &&
    checkWith(context.getProject, text, Scala3Language.INSTANCE)

  def checkDoWith(text: String, manager: PsiManager): Boolean = {
    val fileText = "class a {\n" + text + "\n}"

    checkWith(manager.getProject, fileText)
  }

  def checkTypeWith(typez: ScTypeElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = typez.getText
    val text = replaceDummy("class a { x:" + typeText + " " + additionText + "}")

    checkWith(manager.getProject, text)
  }

  def checkAnyWith(typez: PsiElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = typez.getText
    val text = replaceDummy("class a { " + typeText + " " + additionText + "}")

    checkWith(manager.getProject, text)
  }

  import CompletionUtil.DUMMY_IDENTIFIER_TRIMMED

  private val LiteralPattern: Pattern = Pattern.compile(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED, Pattern.LITERAL)

  def replaceLiteral(text: String, replacement: String): String =
    LiteralPattern.matcher(text)
      .replaceAll(Matcher.quoteReplacement(replacement))

  private def replaceDummy(text: String): String =
    if (text.contains(DUMMY_IDENTIFIER_TRIMMED)) text.replaceAll("\\w*" + DUMMY_IDENTIFIER_TRIMMED, "")
    else text

  def checkNewWith(news: ScNewTemplateDefinition, additionText: String, manager: PsiManager): Boolean = {
    val newsText = news.getText
    val text = replaceDummy("class a { " + newsText + " " + additionText + "}")

    checkWith(manager.getProject, text)
  }

  def checkReplace(elem: PsiElement, additionText: String): Boolean = {
    val project = elem.getProject
    val language = if (elem.isInScala3File) Scala3Language.INSTANCE else ScalaLanguage.INSTANCE
    val typeText = elem.getText
    var text = "class a { " + typeText + "}"
    if (!text.contains(DUMMY_IDENTIFIER_TRIMMED)) return false
    text = text.replaceAll("\\w*" + DUMMY_IDENTIFIER_TRIMMED, " " + additionText + " ")

    checkWith(project, text, language)
  }

  private def checkErrors(elem: PsiElement): Boolean = {
    elem match {
      case _: PsiErrorElement => return true
      case _ =>
    }
    val iterator = elem.getChildren.iterator
    while (iterator.hasNext) {
      val child = iterator.next()
      if (checkErrors(child)) return true
    }
    false
  }

  /**
   * @param leaf Start PsiElement
   * @return (End PsiElement, ContainingFile.isScriptFile)
   */
  def processPsiLeafForFilter(leaf: PsiElement): (PsiElement, Boolean) = Option(leaf) map {
    l => l.getContainingFile match {
      case scriptFile: ScalaFile if scriptFile.isScriptFile => (leaf.getParent, true)
      case _: ScalaFile => (leaf, false)
      case _ => (null, false)
    }
  } getOrElse (null, false)


  //find objects which may be used to import this member
  //select a single one if possible
  def findInheritorObjectsForOwner(member: ScMember): Set[ScObject] = {
    val allObjects = findAllInheritorObjectsForOwner(member)
    if (allObjects.isEmpty || member.containingClass.hasTypeParameters) {
      allObjects
    } else {
      //if `clazz` is not generic, members in all objects are the same, so we return one with the shortest qualified name
      Set(allObjects.minBy(o => (o.isDeprecated, o.qualifiedName.length, o.qualifiedName)))
    }
  }

  //find objects which may be used to import this member
  def findAllInheritorObjectsForOwner(member: ScMember): Set[ScObject] =
    member.containingClass match {
      case null => Set.empty
      case clazz =>
        (inheritorObjectsInLibraries(clazz) ++ inheritorObjectsInProject(clazz))
          .filterNot(o => isInExcludedPackage(o.qualifiedName, member.getProject))
    }

  def findPackageForTopLevelMember(member: ScMember): Option[ScPackaging] =
    if (member.containingClass == null) {
      member match {
        case fn: ScFunction if fn.isExtensionMethod =>
          fn.extensionMethodOwner.flatMap(_.getContext.asOptionOf[ScPackaging])
        case _ =>
          member.getContext.asOptionOf[ScPackaging]
      }
    } else None

  def isInExcludedPackage(qualifiedName: String, project: Project): Boolean =
    JavaProjectCodeInsightSettings.getSettings(project).isExcluded(qualifiedName)

  private def checkWith(project: Project, fileText: String, language: Language = ScalaFileType.INSTANCE.getLanguage): Boolean = {
    val fileName = "dummy." + ScalaFileType.INSTANCE.getDefaultExtension
    val dummyFile = PsiFileFactory.getInstance(project).
      createFileFromText(fileName, language, fileText)
      .asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  @CachedInUserData(clazz, BlockModificationTracker(clazz))
  private def inheritorObjectsInProject(clazz: ScTemplateDefinition): Set[ScObject] = {
    ScalaInheritors.allInheritorObjects(clazz)
  }

  @CachedInUserData(clazz, ModTracker.libraryAware(clazz))
  private def inheritorObjectsInLibraries(clazz: ScTemplateDefinition): Set[ScObject] = {
    ScalaInheritors.allInheritorObjects(clazz)
  }
}
