package org.jetbrains.plugins.scala
package lang
package completion

import java.util.regex.{Matcher, Pattern}

import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.codeInsight.completion.{CompletionParameters, CompletionUtil, PrefixMatcher}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope.notScope
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, ModTracker}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

/**
* User: Alexander Podkhalyuzin
* Date: 21.05.2008.
*/

object ScalaCompletionUtil {

  import ScalaTokenTypes._

  val PREFIX_COMPLETION_KEY: Key[Boolean] = Key.create("prefix.completion.key")

  def hasNoQualifier(ref: ScReferenceExpression): Boolean =
    ref.qualifier.isEmpty && (ref.getParent match {
      case e: ScSugarCallExpr => e.operation != ref
      case _ => true
    })

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
        if (leaf.getNextSibling != null && leaf.getNextSibling.getNextSibling.isInstanceOf[ScPackaging] &&
                leaf.getNextSibling.getNextSibling.getText.indexOf('{') == -1)
          return (true, false)
      case _ =>
    }
    parent match {
      case _: ScalaFile | _: ScPackaging =>
        var node = leaf.getPrevSibling
        if (node.isInstanceOf[PsiWhiteSpace]) node = node.getPrevSibling
        node match {
          case x: PsiErrorElement =>
            val s = ErrMsg("wrong.top.statment.declaration")
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
          !parent.getPrevSibling.getPrevSibling.getLastChild.isInstanceOf[PsiErrorElement]))
  }

  def checkClassWith(clazz: ScTypeDefinition, additionText: String, manager: PsiManager): Boolean = {
    val classText: String = clazz.getText
    val text = replaceDummy(classText + " " + additionText)
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, text).asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  def checkElseWith(text: String, manager: PsiManager): Boolean = {
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, "class a {\n" + text + "\n}").asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  def checkDoWith(text: String, manager: PsiManager): Boolean = {
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, "class a {\n" + text + "\n}").asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  def checkTypeWith(typez: ScTypeElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = typez.getText
    val text = replaceDummy("class a { x:" + typeText + " " + additionText + "}")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, text).asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  def checkAnyTypeWith(typez: ScTypeElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = typez.getText
    val text = replaceDummy("class a { val x:" + typeText + " " + additionText + "}")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, text).asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  def checkAnyWith(typez: PsiElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = typez.getText
    val text = replaceDummy("class a { " + typeText + " " + additionText + "}")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, text).asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
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
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, text).asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  def checkReplace(elem: PsiElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = elem.getText
    var text = "class a { " + typeText + "}"
    if (!text.contains(DUMMY_IDENTIFIER_TRIMMED)) return false
    text = text.replaceAll("\\w*" + DUMMY_IDENTIFIER_TRIMMED, " " + additionText + " ")

    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, text).asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
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


  def isInExcludedPackage(qualifiedName: String, project: Project): Boolean =
    JavaProjectCodeInsightSettings.getSettings(project).isExcluded(qualifiedName)

  @CachedInUserData(clazz, BlockModificationTracker(clazz))
  private def inheritorObjectsInProject(clazz: ScTemplateDefinition): Set[ScObject] = {
    val scope = projectScope(clazz).intersectWith(clazz.resolveScope)
    ScalaInheritors.allInheritorObjects(clazz, scope)
  }

  @CachedInUserData(clazz, ModTracker.libraryAware(clazz))
  private def inheritorObjectsInLibraries(clazz: ScTemplateDefinition): Set[ScObject] = {
    val notProjectScope = notScope(projectScope(clazz)).intersectWith(clazz.resolveScope)
    ScalaInheritors.allInheritorObjects(clazz, notProjectScope)
  }

  private def projectScope(clazz: PsiClass) = GlobalSearchScope.projectScope(clazz.getProject)
}
