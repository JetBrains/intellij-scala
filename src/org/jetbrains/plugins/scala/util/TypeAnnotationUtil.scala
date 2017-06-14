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
import org.jetbrains.plugins.scala.lang.formatting.settings.{ScalaCodeStyleSettings, ScalaTabbedCodeStylePanel, TypeAnnotationPolicy, TypeAnnotationRequirement}
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

  private[this] def isRequired: Int =
    TypeAnnotationRequirement.Required.ordinal

  def isTypeAnnotationNeeded(requirement: Int,
                             overridingPolicy: Int,
                             simplePolicy: Int)
                            (implicit pair: (Boolean, Boolean)): Boolean = {
    val (isOverriding, isSimple) = pair
    val typeAnnotationPolicy = TypeAnnotationPolicy.Optional.ordinal

    requirement != TypeAnnotationRequirement.Optional.ordinal &&
      (!isSimple || simplePolicy != typeAnnotationPolicy) &&
      (!isOverriding || overridingPolicy != typeAnnotationPolicy)
  }

  def isTypeAnnotationNeededProperty(visibility: Visibility)
                                    (implicit settings: ScalaCodeStyleSettings,
                                     pair: (Boolean, Boolean)): Boolean =
    isTypeAnnotationNeeded(
      visibility.forProperty,
      settings.OVERRIDING_METHOD_TYPE_ANNOTATION,
      settings.SIMPLE_METHOD_TYPE_ANNOTATION
    )

  def isTypeAnnotationNeededProperty(property: ScMember)
                                    (implicit settings: ScalaCodeStyleSettings,
                                     pair: (Boolean, Boolean)): Boolean =
    Visibility(property) match {
      case Implicit if settings.IMPLICIT_PROPERTY_TYPE_ANNOTATION == isRequired => true
      case visibility => isTypeAnnotationNeededProperty(visibility)
    }

  def isTypeAnnotationNeededProperty(element: PsiElement, visibilityString: String)
                                    (isLocal: Boolean = this.isLocal(element),
                                     isOverriding: Boolean = this.isOverriding(element),
                                     isSimple: Boolean = this.isSimple(element))
                                    (implicit settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(element.getProject)): Boolean = {
    implicit val pair = (isOverriding, isSimple)
    isTypeAnnotationNeededProperty(Visibility(visibilityString, isLocal))
  }

  def isTypeAnnotationNeededMethod(visibility: Visibility)
                                  (implicit settings: ScalaCodeStyleSettings,
                                   pair: (Boolean, Boolean)): Boolean =
    isTypeAnnotationNeeded(
      visibility.forMethod,
      settings.OVERRIDING_METHOD_TYPE_ANNOTATION,
      settings.SIMPLE_METHOD_TYPE_ANNOTATION
    )

  def isTypeAnnotationNeededMethod(method: ScMember)
                                  (implicit settings: ScalaCodeStyleSettings,
                                   pair: (Boolean, Boolean)): Boolean =
    Visibility(method) match {
      case Implicit if settings.IMPLICIT_METHOD_TYPE_ANNOTATION == isRequired => true
      case visibility => isTypeAnnotationNeededMethod(visibility)
    }


  def isTypeAnnotationNeededMethod(element: PsiElement, visibilityString: String)
                                  (isLocal: Boolean = this.isLocal(element),
                                   isOverriding: Boolean = this.isOverriding(element),
                                   isSimple: Boolean = this.isSimple(element))
                                  (implicit settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(element.getProject)): Boolean = {
    implicit val pair = (isOverriding, isSimple)
    isTypeAnnotationNeededMethod(Visibility(visibilityString, isLocal))
  }

  def isTypeAnnotationNeeded(element: ScalaPsiElement): Boolean = {
    implicit val settings = ScalaCodeStyleSettings.getInstance(element.getProject)
    implicit val pair = (isOverriding(element), isSimple(element))

    element match {
      case value: ScPatternDefinition if value.isSimple => //not simple will contains more than one declaration
        isTypeAnnotationNeededProperty(value)
      case variable: ScVariableDefinition if variable.isSimple =>
        isTypeAnnotationNeededProperty(variable)
      case method: ScFunctionDefinition if method.hasAssign && !method.isSecondaryConstructor =>
        isTypeAnnotationNeededMethod(method)
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

    import ScFunction.Name.Apply
    reference.bind().map(resolvedElement).exists {
      case function: ScFunction => function.name == Apply && reference.refName != Apply
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

  sealed trait Visibility {

    def forProperty(implicit settings: ScalaCodeStyleSettings): Int

    def forMethod(implicit settings: ScalaCodeStyleSettings): Int
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

  private case object Implicit extends Visibility {

    override def forProperty(implicit settings: ScalaCodeStyleSettings): Int =
      settings.IMPLICIT_PROPERTY_TYPE_ANNOTATION

    override def forMethod(implicit settings: ScalaCodeStyleSettings): Int =
      settings.IMPLICIT_METHOD_TYPE_ANNOTATION
  }

  private case object Local extends Visibility {

    override def forProperty(implicit settings: ScalaCodeStyleSettings): Int =
      settings.LOCAL_PROPERTY_TYPE_ANNOTATION

    override def forMethod(implicit settings: ScalaCodeStyleSettings): Int =
      settings.LOCAL_METHOD_TYPE_ANNOTATION
  }

  case object Private extends Visibility {

    override def forProperty(implicit settings: ScalaCodeStyleSettings): Int =
      settings.PRIVATE_PROPERTY_TYPE_ANNOTATION

    override def forMethod(implicit settings: ScalaCodeStyleSettings): Int =
      settings.PRIVATE_METHOD_TYPE_ANNOTATION
  }

  case object Protected extends Visibility {

    override def forProperty(implicit settings: ScalaCodeStyleSettings): Int =
      settings.PROTECTED_PROPERTY_TYPE_ANNOTATION

    override def forMethod(implicit settings: ScalaCodeStyleSettings): Int =
      settings.PROTECTED_METHOD_TYPE_ANNOTATION
  }

  case object Public extends Visibility {

    override def forProperty(implicit settings: ScalaCodeStyleSettings): Int =
      settings.PUBLIC_PROPERTY_TYPE_ANNOTATION

    override def forMethod(implicit settings: ScalaCodeStyleSettings): Int =
      settings.PUBLIC_METHOD_TYPE_ANNOTATION
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
