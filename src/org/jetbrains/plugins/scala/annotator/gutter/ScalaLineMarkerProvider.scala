package org.jetbrains.plugins.scala
package annotator
package gutter

import java.util
import javax.swing.Icon

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.{DaemonCodeAnalyzerSettings, LineMarkerInfo, LineMarkerProvider}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.{CodeInsightColors, EditorColorsManager}
import com.intellij.openapi.editor.markup.{GutterIconRenderer, SeparatorPlacement}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.util.NullableFunction
import org.jetbrains.plugins.scala.annotator.gutter.GutterIcons._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.lang.psi.types.Signature

import _root_.scala.collection.mutable.ArrayBuffer
import scala.collection.{Seq, mutable}


/**
 * User: Alexander Podkhalyuzin
 * Date: 31.10.2008
 */

class ScalaLineMarkerProvider(daemonSettings: DaemonCodeAnalyzerSettings, colorsManager: EditorColorsManager)
        extends LineMarkerProvider with ScalaSeparatorProvider {

  def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[_ <: PsiElement] = {
    if (!element.isValid) return null
    val gator = getGatorInfo(element)
    if(daemonSettings.SHOW_METHOD_SEPARATORS && isSeparatorNeeded(element)) {
      if(gator == null) {
        addSeparatorInfo(createMarkerInfo(element))
      } else {
        addSeparatorInfo(gator)
      }
    } else {
      gator
    }
  }

  def createMarkerInfo(element: PsiElement) = {
    new LineMarkerInfo[PsiElement](
            element, element.getTextRange, null, Pass.UPDATE_ALL,
            NullableFunction.NULL.asInstanceOf[com.intellij.util.Function[PsiElement,String]],
            null, GutterIconRenderer.Alignment.RIGHT)
  }

  def addSeparatorInfo(info: LineMarkerInfo[_ <: PsiElement]) = {
    info.separatorColor = colorsManager.getGlobalScheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR)
    info.separatorPlacement = SeparatorPlacement.TOP
    info
  }

  def getGatorInfo(element: PsiElement): LineMarkerInfo[_ <: PsiElement] = {
    if (element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER) {
      val offset = element.getTextRange.getStartOffset

      if (element.getParent.isInstanceOf[ScReferenceElement]) return null // e.g type A = /*Int*/

      def getParent: PsiElement = {
        var e = element
        def test(x: PsiElement) = x match {
          case _: ScFunction | _: ScValue | _: ScVariable | _: ScTypeDefinition | _: ScTypeAlias => true
          case _ => false
        }
        while (e != null && !test(e)) e = e.getParent
        e
      }
      def marker(element: PsiElement, icon: Icon, typez: ScalaMarkerType): LineMarkerInfo[PsiElement] =
        new LineMarkerInfo[PsiElement](element, offset, icon, Pass.UPDATE_ALL, typez.fun, typez.handler, GutterIconRenderer.Alignment.LEFT)

      val parent = getParent
      if (parent == null) return null

      def containsNamedElement(holder: ScDeclaredElementsHolder) = holder.declaredElements.exists(_.asInstanceOf[ScNamedElement].nameId == element)

      (parent, parent.getParent) match {
        case (method: ScFunction, _: ScTemplateBody) if method.nameId == element =>
          val signatures: Seq[Signature] = mutable.HashSet[Signature](method.superSignaturesIncludingSelfType: _*).toSeq
          val icon = if (GutterUtil.isOverrides(method, signatures)) OVERRIDING_METHOD_ICON else IMPLEMENTING_METHOD_ICON
          val typez = ScalaMarkerType.OVERRIDING_MEMBER
          if (signatures.nonEmpty) {
            return marker(method.nameId, icon, typez)
          }
        case (x@(_: ScValue | _: ScVariable), _: ScTemplateBody)
          if containsNamedElement(x.asInstanceOf[ScDeclaredElementsHolder]) =>
          val signatures = new ArrayBuffer[Signature]
          val bindings = x match {case v: ScDeclaredElementsHolder => v.declaredElements case _ => return null}
          for (z <- bindings) signatures ++= ScalaPsiUtil.superValsSignatures(z, withSelfType = true)
          val icon = if (GutterUtil.isOverrides(x, signatures)) OVERRIDING_METHOD_ICON else IMPLEMENTING_METHOD_ICON
          val typez = ScalaMarkerType.OVERRIDING_MEMBER
          if (signatures.nonEmpty) {
            val token = x match {
              case v: ScValue => v.getValToken
              case v: ScVariable => v.getVarToken
            }
            return marker(token, icon, typez)
          }
        case (x: ScObject, _: ScTemplateBody) if x.nameId == element =>
          val signatures = ScalaPsiUtil.superValsSignatures(x, withSelfType = true)
          val icon = if (GutterUtil.isOverrides(x, signatures)) OVERRIDING_METHOD_ICON else IMPLEMENTING_METHOD_ICON
          val typez = ScalaMarkerType.OVERRIDING_MEMBER
          if (signatures.nonEmpty) {
            return marker(x.getObjectClassOrTraitToken, icon, typez)
          }
        case (td : ScTypeDefinition, _: ScTemplateBody) if !td.isObject =>
          val signature = ScalaPsiUtil.superTypeMembers(td, withSelfType = true)
          val icon = IMPLEMENTING_METHOD_ICON
          val typez = ScalaMarkerType.OVERRIDING_MEMBER
          if (signature.nonEmpty) {
            return marker(td.getObjectClassOrTraitToken, icon, typez)
          }
        case (ta : ScTypeAlias, _: ScTemplateBody) =>
          val signature = ScalaPsiUtil.superTypeMembers(ta, withSelfType = true)
          val icon = IMPLEMENTING_METHOD_ICON
          val typez = ScalaMarkerType.OVERRIDING_MEMBER
          if (signature.nonEmpty) {
            return marker(ta.getTypeToken, icon, typez)
          }
        case _ =>
      }

      parent match {
        case method: ScFunctionDefinition if method.nameId == element =>
          method.recursionType match {
            case RecursionType.OrdinaryRecursion =>
              return new LineMarkerInfo[PsiElement](method.nameId, offset, RECURSION_ICON, Pass.UPDATE_ALL,
                (e: PsiElement) => "Method '%s' is recursive".format(e.getText), null, GutterIconRenderer.Alignment.LEFT)
            case RecursionType.TailRecursion =>
              return new LineMarkerInfo[PsiElement](method.nameId, offset, TAIL_RECURSION_ICON, Pass.UPDATE_ALL,
                (e: PsiElement) => "Method '%s' is tail recursive".format(e.getText), null, GutterIconRenderer.Alignment.LEFT)
            case RecursionType.NoRecursion => // no markers
          }
        case _ =>
      }

    }
    null
  }

  def collectSlowLineMarkers(elements: util.List[PsiElement], result: util.Collection[LineMarkerInfo[_ <: PsiElement]]) {
    ApplicationManager.getApplication.assertReadAccessAllowed()

    val members = new ArrayBuffer[PsiElement]
    val iterator = elements.iterator()
    while (iterator.hasNext) {
      val element = iterator.next()
      ProgressManager.checkCanceled()

      element match {
        case clazz: ScTypeDefinition =>
          GutterUtil.collectInheritingClasses(clazz, result)
        case _ =>
      }

      element match {
        case x: ScTypeDefinition if !x.isObject && x.getParent.isInstanceOf[ScTemplateBody] => members += x
        case x: PsiMember with PsiNamedElement => members += x
        case _: ScValue | _: ScVariable => members += element
        case _ =>
      }
    }
    if (members.nonEmpty) {
      GutterUtil.collectOverridingMembers(members, result)
    }
  }
}

private object GutterUtil {
  def collectInheritingClasses(clazz: ScTypeDefinition, result: util.Collection[LineMarkerInfo[_ <: PsiElement]]) {
    if ("scala.ScalaObject".equals(clazz.qualifiedName)) return

    val inheritor = ClassInheritorsSearch.search(clazz, false).findFirst
    if (inheritor != null) {
      val offset = clazz.getTextOffset
      val icon = clazz match {
        case _: ScTrait => IMPLEMENTED_INTERFACE_MARKER_RENDERER
        case _ => SUBCLASSED_CLASS_MARKER_RENDERER
      }
      val typez = ScalaMarkerType.SUBCLASSED_CLASS
      val info = new LineMarkerInfo[PsiElement](clazz.nameId, offset, icon, Pass.UPDATE_OVERRIDEN_MARKERS, typez.fun, typez.handler)
      result.add(info)
    }
  }

  def collectOverridingMembers(members: ArrayBuffer[PsiElement], result: util.Collection[LineMarkerInfo[_ <: PsiElement]]) {
    for (member <- members if !member.isInstanceOf[PsiMethod] || !member.asInstanceOf[PsiMethod].isConstructor) {
      ProgressManager.checkCanceled()
      val offset = member.getTextOffset
      val members = member match {
        case d: ScDeclaredElementsHolder => d.declaredElements.toArray
        case td: ScTypeDefinition => Array[PsiNamedElement](td)
        case ta: ScTypeAlias => Array[PsiNamedElement](ta)
        case _ => Array[PsiNamedElement]()
      }
      val overrides = new ArrayBuffer[PsiNamedElement]
      for (member <- members) overrides ++= ScalaOverridingMemberSearcher.search(member, deep = false, withSelfType = true)
      if (overrides.nonEmpty) {
        val icon = if (!GutterUtil.isAbstract(member)) OVERRIDEN_METHOD_MARKER_RENDERER else IMPLEMENTED_INTERFACE_MARKER_RENDERER
        val typez = ScalaMarkerType.OVERRIDDEN_MEMBER
        val info = new LineMarkerInfo[PsiElement](member match {case memb: ScNamedElement => memb.nameId
          case _ => member}, offset, icon, Pass.UPDATE_OVERRIDEN_MARKERS, typez.fun, typez.handler)
        result.add(info)
      }
    }
  }

  def isOverrides(element: PsiElement, supers: Seq[Signature]): Boolean = {
    element match {
      case decl: ScFunctionDeclaration => true
      case v: ScValueDeclaration => true
      case v: ScVariableDeclaration => true
      case _ =>
        val iter = supers.iterator
        while (iter.hasNext) {
          val s = iter.next()
          ScalaPsiUtil.nameContext(s.namedElement) match {
            case fun: ScFunctionDefinition => return true
            case fun: ScFunction =>
            case method: PsiMethod if !method.hasAbstractModifier => return true
            case _: ScVariableDefinition | _: ScPatternDefinition => return true
            case f: PsiField if !f.hasAbstractModifier => return true
            case _: ScVariableDeclaration =>
            case _: ScValueDeclaration =>
            case _: ScParameter => return true
            case _: ScTypeAliasDefinition => return true
            case _: ScTypeAliasDeclaration =>
            case _: PsiClass => return true
            case _ =>
          }
        }
        false
    }
  }

  def isAbstract(element: PsiElement) = element match {
    case method: ScFunctionDeclaration => true
    case value: ScValueDeclaration => true
    case variable: ScVariableDeclaration => true
    case _ => false
  }
}
