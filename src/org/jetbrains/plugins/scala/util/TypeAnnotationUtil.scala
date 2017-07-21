package org.jetbrains.plugins.scala.util

import javax.swing.event.{HyperlinkEvent, HyperlinkListener}

import com.intellij.application.options.CodeStyleSchemesConfigurable
import com.intellij.application.options.codeStyle.CodeStyleMainPanel
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.{Configurable, ConfigurableGroup, ShowSettingsUtil}
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesAndLibrariesScope
import com.intellij.ui.HyperlinkLabel
import org.jetbrains.plugins.scala.codeInsight.intention.types.AddOrRemoveStrategy
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.formatting.settings.{ScalaCodeStyleSettings, ScalaTabbedCodeStylePanel}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{getModule, inNameContext, isImplicit, superValsSignatures}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.ReturnTypeLevel.{ADD, BY_CODE_STYLE, REMOVE}
import org.jetbrains.plugins.scala.{ScalaBundle, extensions}

import scala.annotation.tailrec

/**
  * Created by kate on 7/14/16.
  */
object TypeAnnotationUtil {
  private val TraversableClassNames =
    Seq("Seq", "Array", "Vector", "Set", "HashSet", "Map", "HashMap", "Iterator", "Option")

  def isTypeAnnotationNeeded(requirement: Boolean,
                             exludeSimple: Boolean)
                            (isSimple: Boolean): Boolean = {

    requirement && !(exludeSimple && isSimple)
  }

  def isTypeAnnotationNeededProperty(visibility: Visibility)
                                    (settings: ScalaCodeStyleSettings,
                                     isSimple: Boolean): Boolean =
    isTypeAnnotationNeeded(
      visibility.forMember(settings),
      settings.TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_OBVIOUS
    )(isSimple)

  def isTypeAnnotationNeededProperty(property: ScMember)
                                    (settings: ScalaCodeStyleSettings,
                                     isSimple: Boolean): Boolean =
    Visibility(property) match {
      case Implicit if settings.TYPE_ANNOTATION_IMPLICIT_MODIFIER => true
      case visibility => isTypeAnnotationNeededProperty(visibility)(settings, isSimple)
    }

  def isTypeAnnotationNeededProperty(element: PsiElement, visibilityString: String)
                                    (isLocal: Boolean = this.isLocal(element),
                                     isOverriding: Boolean = this.isOverriding(element),
                                     isSimple: Boolean = this.isSimple(element))
                                    (settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(element.getProject)): Boolean = {
    isTypeAnnotationNeededProperty(Visibility(visibilityString, isLocal))(settings, isSimple)
  }

  def isTypeAnnotationNeededMethod(visibility: Visibility)
                                  (settings: ScalaCodeStyleSettings,
                                   isSimple: Boolean): Boolean =
    isTypeAnnotationNeeded(
      visibility.forMember(settings),
      settings.TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_OBVIOUS
    )(isSimple)

  def isTypeAnnotationNeededMethod(method: ScMember)
                                  (settings: ScalaCodeStyleSettings,
                                   isSimple: Boolean): Boolean =
    Visibility(method) match {
      case Implicit if settings.TYPE_ANNOTATION_IMPLICIT_MODIFIER => true
      case visibility => isTypeAnnotationNeededMethod(visibility)(settings, isSimple)
    }


  def isTypeAnnotationNeededMethod(element: PsiElement, visibilityString: String)
                                  (isLocal: Boolean = this.isLocal(element),
                                   isOverriding: Boolean = this.isOverriding(element),
                                   isSimple: Boolean = this.isSimple(element))
                                  (settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(element.getProject)): Boolean = {
    isTypeAnnotationNeededMethod(Visibility(visibilityString, isLocal))(settings, isSimple)
  }

  def isTypeAnnotationNeeded(element: ScalaPsiElement): Boolean = {
    val settings = ScalaCodeStyleSettings.getInstance(element.getProject)

    element match {
      case value: ScPatternDefinition if value.isSimple => //not simple will contains more than one declaration
        isTypeAnnotationNeededProperty(value)(settings, isSimple(element))
      case variable: ScVariableDefinition if variable.isSimple =>
        isTypeAnnotationNeededProperty(variable)(settings, isSimple(element))
      case method: ScFunctionDefinition if method.hasAssign && !method.isSecondaryConstructor =>
        isTypeAnnotationNeededMethod(method)(settings, isSimple(element))
      case _ => true
    }
  }

  private def isOverriding(element: PsiElement): Boolean = {
    element match {
      case func: ScFunctionDefinition =>
        func.superSignaturesIncludingSelfType.nonEmpty
      case variable: ScVariableDefinition =>
        variable.declaredElements.headOption.map(superValsSignatures(_, withSelfType = true)).exists(_.nonEmpty)
      case pattern: ScPatternDefinition =>
        pattern.declaredElements.headOption.map(superValsSignatures(_, withSelfType = true)).exists(_.nonEmpty)
      case _ => false
    }
  }

  def isSimple(expression: ScExpression): Boolean = expression match {
    case _: ScLiteral => true
    case _: ScNewTemplateDefinition => true
    case ref: ScReferenceExpression if isObject(ref) => true
    case ScGenericCall(referenced, _) if isFactoryMethod(referenced) => true
    case ScMethodCall(invoked: ScReferenceExpression, _) if isObject(invoked) => true
    case _: ScThrowStmt => true
    case _ => false
  }

  def isSimple(element: PsiElement): Boolean = {
    val maybeExpression = element match {
      case value: ScPatternDefinition if value.isSimple => value.expr
      case variable: ScVariableDefinition if variable.isSimple => variable.expr
      case method: ScFunctionDefinition if method.hasAssign && !method.isSecondaryConstructor => method.body
      case _ => None //support isSimple for JavaPsi
    }
    maybeExpression.exists(isSimple)
  }

  def isObject(reference: ScReferenceExpression): Boolean = {
    def resolvedElement(result: ScalaResolveResult) =
      result.innerResolveResult
        .getOrElse(result).element

    reference.bind().map(resolvedElement).exists {
      case function: ScFunction => function.isApplyMethod
      case _ => false
    }
  }

  def isFactoryMethod(referenced: ScReferenceExpression): Boolean = referenced match {
    case ScReferenceExpression.withQualifier(qualifier: ScReferenceExpression) =>
      TraversableClassNames.contains(qualifier.refName) && referenced.refName == "empty"
    case _ => false
  }

  @tailrec
  final def isLocal(psiElement: PsiElement): Boolean = psiElement match {
    case null => false
    case member: ScMember => member.isLocal
    case _: ScEnumerator | _: ScForStatement | _: PsiLocalVariable => true
    case _: ScTemplateBody => false
    case _ => isLocal(psiElement.getContext)
  }

  def getTypeElement(element: ScalaPsiElement): Option[ScTypeElement] = {
    element match {
      case fun: ScFunction => fun.returnTypeElement
      case inNameContext(pd: ScPatternDefinition) => pd.typeElement
      case inNameContext(vd: ScVariableDefinition) => vd.typeElement
      case patternDefinition: ScPatternDefinition => patternDefinition.typeElement
      case variableDefinition: ScVariableDefinition => variableDefinition.typeElement
      case _ => None
    }
  }

  def removeTypeAnnotationIfNeeded(element: ScalaPsiElement,
                                   state: ScalaApplicationSettings.ReturnTypeLevel = ScalaApplicationSettings.ReturnTypeLevel.BY_CODE_STYLE): Unit = {
    val applicationSettings = ScalaApplicationSettings.getInstance()
    state match {
      case ADD => //nothing
      case REMOVE | BY_CODE_STYLE =>
        getTypeElement(element) match {
          case Some(typeElement)
            if (state == REMOVE) || ((state == BY_CODE_STYLE) && !isTypeAnnotationNeeded(element)) =>
            AddOrRemoveStrategy.removeTypeAnnotation(typeElement)
          case _ =>
        }
    }
  }

  def removeAllTypeAnnotationsIfNeeded(elements: Seq[PsiElement],
                                       state: ScalaApplicationSettings.ReturnTypeLevel = ScalaApplicationSettings.ReturnTypeLevel.BY_CODE_STYLE): Unit = {
    elements.foreach(_.depthFirst().foreach {
      case scalaPsiElement: ScalaPsiElement => removeTypeAnnotationIfNeeded(scalaPsiElement, state)
      case _ =>
    })
  }

  // TODO refactor or remove
  sealed trait Visibility {
    def forMember(settings: ScalaCodeStyleSettings): Boolean
  }

  object Visibility {

    def apply(modifierListOwner: PsiModifierListOwner): Visibility = modifierListOwner.getModifierList match {
      case list if list.hasModifierProperty("private") => Private
      case list if list.hasModifierProperty("protected") => Protected
      case _ => Public
    }

    def apply(member: ScMember): Visibility = member match {
      case _ if isImplicit(member) => Implicit
      case _ if isLocal(member) => Local
      case _ if member.isPrivate => Private
      case _ if member.isProtected => Protected
      case _ => Public
    }

    def apply(visibilityString: String, isLocal: Boolean = false): Visibility = {
      if (isLocal) Local
      else if (visibilityString == null) Public
      else {
        val lowerCased = visibilityString.toLowerCase

        if (lowerCased.contains("private")) Private
        else if (lowerCased.contains("protected")) Protected
        else Public
      }
    }

    private[this] def isLocal(member: ScMember) = {
      def isMemberOf(fqns: String*): Boolean = {
        val containingClass = member.getContainingClass match {
          case null => return false
          case c => c
        }

        val module = getModule(member) match {
          case null => return false
          case m => m
        }

        val scope = ElementScope(member.getProject, moduleWithDependenciesAndLibrariesScope(module))
        fqns.flatMap(scope.getCachedClass)
          .exists(containingClass.isInheritor(_, true))
      }

      def isAnnotatedWith(annotations: String*) = member match {
        case definition: ScFunctionDefinition => annotations.exists(definition.hasAnnotation)
        case _ => false
      }

      member.isLocal ||
        isMemberOf("scala.DelayedInit", "junit.framework.TestCase") ||
        isAnnotatedWith("org.junit.Test", "junit.framework.Test")
    }
  }

  // TODO refactor or remove
  private case object Implicit extends Visibility {

    override def forMember(settings: ScalaCodeStyleSettings): Boolean =
      settings.TYPE_ANNOTATION_IMPLICIT_MODIFIER
  }

  private case object Local extends Visibility {

    override def forMember(settings: ScalaCodeStyleSettings): Boolean =
      settings.TYPE_ANNOTATION_LOCAL_DEFINITION
  }

  case object Private extends Visibility {

    override def forMember(settings: ScalaCodeStyleSettings): Boolean =
      settings.TYPE_ANNOTATION_PRIVATE_MEMBER
  }

  case object Protected extends Visibility {

    override def forMember(settings: ScalaCodeStyleSettings): Boolean =
      settings.TYPE_ANNOTATION_PROTECTED_MEMBER
  }

  case object Public extends Visibility {

    override def forMember(settings: ScalaCodeStyleSettings): Boolean =
      settings.TYPE_ANNOTATION_PUBLIC_MEMBER
  }

  private var requestCountsToShow: Int = 0

  def showTypeAnnotationsSettings(project: Project): Unit = {
    requestCountsToShow += 1
    showWindowInvokeLater(project)
  }

  private def showWindowInvokeLater(project: Project): Unit = {
    extensions.invokeLater {
      val groups: Array[ConfigurableGroup] = ShowSettingsUtilImpl.getConfigurableGroups(project, true)
      val visitor = new ConfigurableVisitor.ByID("preferences.sourceCode.Scala")
      val configurable: Configurable = visitor.find(groups: _*)

      assert(configurable != null, "Cannot find configurable: " + classOf[CodeStyleSchemesConfigurable].getName)

      if (requestCountsToShow > 0) { // show window only for the first request
        ShowSettingsUtil.getInstance.editConfigurable(project, configurable, new Runnable() {
          requestCountsToShow += 1

          def run() {
            val codeStyleMainPanel: CodeStyleMainPanel = configurable.createComponent.asInstanceOf[CodeStyleMainPanel]
            assert(codeStyleMainPanel != null, "Cannot find Code Style main panel")

            codeStyleMainPanel.getPanels.headOption.foreach { panel =>
              val selectedPanel = panel.getSelectedPanel
              assert(selectedPanel != null)
              selectedPanel match {
                case tab: ScalaTabbedCodeStylePanel => tab.changeTab("Type Annotations")
                case _ =>
              }
            }
            requestCountsToShow = 0
          }
        })
      }
    }
  }

  def createTypeAnnotationsHLink(project: Project, msg: String): HyperlinkLabel = {
    val typeAnnotationsSettings: HyperlinkLabel = new HyperlinkLabel(msg)
    typeAnnotationsSettings.addHyperlinkListener(new HyperlinkListener() {
      def hyperlinkUpdate(e: HyperlinkEvent) {
        if (e.getEventType eq HyperlinkEvent.EventType.ACTIVATED) {
          showTypeAnnotationsSettings(project)
        }
      }
    })

    typeAnnotationsSettings.setToolTipText(ScalaBundle.message("default.ta.tooltip"))
    typeAnnotationsSettings
  }
}
