package org.jetbrains.plugins.scala.util

import javax.swing.event.{HyperlinkEvent, HyperlinkListener}

import com.intellij.application.options.CodeStyleSchemesConfigurable
import com.intellij.application.options.codeStyle.CodeStyleMainPanel
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.{Configurable, ConfigurableGroup, ShowSettingsUtil}
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.HyperlinkLabel
import org.jetbrains.plugins.scala.codeInsight.intention.types.AddOnlyStrategy
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.formatting.settings.{ScalaCodeStyleSettings, ScalaTabbedCodeStylePanel, TypeAnnotationPolicy, TypeAnnotationRequirement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.ReturnTypeLevel

/**
  * Created by kate on 7/14/16.
  */
object TypeAnnotationUtil {
  private val CollectionClassNames =
    Seq("Seq", "Array", "Vector", "Set", "HashSet", "Map", "HashMap", "Iterator", "Option")

  def isTypeAnnotationNeeded(requiment: Int, ovPolicy: Int, simplePolicy: Int, isOverride: Boolean, isSimple: Boolean): Boolean = {

    requiment != TypeAnnotationRequirement.Optional.ordinal() &&
      (!isSimple || simplePolicy != TypeAnnotationPolicy.Optional.ordinal()) &&
      (!isOverride || ovPolicy != TypeAnnotationPolicy.Optional.ordinal())
  }

  def isTypeAnnotationNeeded(element: ScalaPsiElement): Boolean = {
    val settings = ScalaCodeStyleSettings.getInstance(element.getProject)

    element match {
      case value: ScPatternDefinition if value.isSimple => //not simple will contains more than one declaration

        isTypeAnnotationNeeded(requirementForProperty(value, settings),
          settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION,
          settings.SIMPLE_PROPERTY_TYPE_ANNOTATION,
          isOverriding(value),
          isSimple(value))

      case variable: ScVariableDefinition if variable.isSimple =>

        isTypeAnnotationNeeded(requirementForProperty(variable, settings),
          settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION,
          settings.SIMPLE_PROPERTY_TYPE_ANNOTATION,
          isOverriding(variable),
          isSimple(variable))

      case method: ScFunctionDefinition if method.hasAssign && !method.isSecondaryConstructor =>

        isTypeAnnotationNeeded(requirementForMethod(method, settings),
          settings.OVERRIDING_METHOD_TYPE_ANNOTATION,
          settings.SIMPLE_METHOD_TYPE_ANNOTATION,
          isOverriding(method),
          isSimple(method))

      case _ => true
    }
  }
  def isOverriding(element: PsiElement): Boolean = {
    element match {
      case func: ScFunctionDefinition =>
        func.superSignaturesIncludingSelfType.nonEmpty
      case variable: ScVariableDefinition =>
        variable.declaredElements.headOption.map(ScalaPsiUtil.superValsSignatures(_, withSelfType = true)).exists(_.nonEmpty)
      case pattern: ScPatternDefinition =>
        pattern.declaredElements.headOption.map(ScalaPsiUtil.superValsSignatures(_, withSelfType = true)).exists(_.nonEmpty)
      case _ => false
    }
  }

  def requirementForProperty(isLocal: Boolean, visibility: Visibility, settings: ScalaCodeStyleSettings): Int = {
    if (isLocal)
      settings.LOCAL_PROPERTY_TYPE_ANNOTATION
    else visibility match {
      case Private => settings.PRIVATE_PROPERTY_TYPE_ANNOTATION
      case Protected => settings.PROTECTED_PROPERTY_TYPE_ANNOTATION
      case Public => settings.PUBLIC_PROPERTY_TYPE_ANNOTATION
    }
  }

  def requirementForMethod(isLocal: Boolean, visibility: Visibility, settings: ScalaCodeStyleSettings): Int = {
    if (isLocal)
      settings.LOCAL_METHOD_TYPE_ANNOTATION
    else visibility match {
      case Private => settings.PRIVATE_METHOD_TYPE_ANNOTATION
      case Protected => settings.PROTECTED_METHOD_TYPE_ANNOTATION
      case Public => settings.PUBLIC_METHOD_TYPE_ANNOTATION
    }
  }

  def requirementForProperty(property: ScMember, settings: ScalaCodeStyleSettings): Int = {
    if (property.isLocal || isMemberOf(property, "scala.DelayedInit")) {
      settings.LOCAL_PROPERTY_TYPE_ANNOTATION
    } else {
      if (property.isPrivate) settings.PRIVATE_PROPERTY_TYPE_ANNOTATION
      else if (property.isProtected) settings.PROTECTED_PROPERTY_TYPE_ANNOTATION
      else settings.PUBLIC_PROPERTY_TYPE_ANNOTATION
    }
  }

  def isMemberOf(member: ScMember, fqn: String): Boolean = {
    val result =
      for (containtingClass <- Option(member.getContainingClass);
           module <- Option(ScalaPsiUtil.getModule(member));
           scope <- Option(GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
           aClass <- ScalaPsiManager.instance(member.getProject).getCachedClass(scope, fqn))
        yield containtingClass.isInheritor(aClass, true)

    result.getOrElse(false)
  }

  def requirementForMethod(method: ScMember, settings: ScalaCodeStyleSettings): Int = {
    if (method.isLocal) {
      settings.LOCAL_METHOD_TYPE_ANNOTATION
    } else {
      if (method.isPrivate) settings.PRIVATE_METHOD_TYPE_ANNOTATION
      else if (method.isProtected) settings.PROTECTED_METHOD_TYPE_ANNOTATION
      else settings.PUBLIC_METHOD_TYPE_ANNOTATION
    }
  }

  def isSimple(element: PsiElement): Boolean = {
    def isSimpleInner(exp: ScExpression) = {
      exp match {
        case _: ScLiteral => true
        case _: ScNewTemplateDefinition => true
        case ref: ScReferenceExpression if ref.refName(0).isUpper => true //heuristic for objects
        case call: ScGenericCall if isEmptyCollectionFactory(call) => true
        case call: ScMethodCall => call.getInvokedExpr match {
          case ref: ScReferenceExpression if ref.refName(0).isUpper => true //heuristic for case classes
          case _ => false
        }
        case _: ScThrowStmt => true
        case _ => false
      }
    }

    element match {
      case value: ScPatternDefinition if value.isSimple => value.expr.exists(isSimpleInner)
      case variable: ScVariableDefinition if variable.isSimple => variable.expr.exists(isSimpleInner)
      case method: ScFunctionDefinition if method.hasAssign && !method.isSecondaryConstructor => method.body.exists(isSimpleInner)
      case expr: ScExpression => isSimpleInner(expr)
      case _ => false //support isSimple for JavaPsi
    }
  }

  def isEmptyCollectionFactory(e: ScExpression): Boolean = e match {
    case ScGenericCall(ref @ ScReferenceExpression.withQualifier(qual: ScReferenceExpression), _) =>
      CollectionClassNames.contains(qual.refName) && ref.refName == "empty"
    case _ => false
  }

  def isLocal(psiElement: PsiElement): Boolean = psiElement match {
    case member: ScMember => member.isLocal
    case _: PsiLocalVariable => true
    case _ if psiElement.getContext != null => !psiElement.getContext.isInstanceOf[ScTemplateBody]
    case _ => false
  }

  def getTypeElement(element: ScalaPsiElement): Option[ScTypeElement] = {
    element match {
      case fun: ScFunction => fun.returnTypeElement
      case ScalaPsiUtil.inNameContext(pd: ScPatternDefinition) => pd.typeElement
      case ScalaPsiUtil.inNameContext(vd: ScVariableDefinition) => vd.typeElement
      case patternDefinition: ScPatternDefinition => patternDefinition.typeElement
      case variableDefinition: ScVariableDefinition => variableDefinition.typeElement
      case _ => None
    }
  }

  def removeTypeAnnotationIfNeeded(element: ScalaPsiElement): Unit = {
    val state = ScalaApplicationSettings.getInstance().SPECIFY_RETURN_TYPE_EXPLICITLY
  
    state match {
      case ReturnTypeLevel.ADD => //nothing
      case ReturnTypeLevel.REMOVE | ReturnTypeLevel.BY_CODE_STYLE =>
        getTypeElement(element) match {
          case Some(typeElement) if (state == ReturnTypeLevel.REMOVE) || ((state == ReturnTypeLevel.BY_CODE_STYLE) && !isTypeAnnotationNeeded(element)) =>
            AddOnlyStrategy.withoutEditor.removeTypeAnnotation(typeElement)
          case _ =>
        }
    }
  }
  
  def removeAllTypeAnnotationsIfNeeded(elements: Seq[PsiElement]): Unit = {
    elements.foreach(_.depthFirst().foreach {
      case scalaPsiElement: ScalaPsiElement => removeTypeAnnotationIfNeeded(scalaPsiElement)
      case _ =>
    })
  }

  sealed abstract class Visibility

  case object Private extends Visibility

  case object Protected extends Visibility

  case object Public extends Visibility

  def visibilityFromString(visibilityString: String): Visibility = {
    if (visibilityString.contains("private"))
      TypeAnnotationUtil.Private
    else if (visibilityString.contains("protected"))
      TypeAnnotationUtil.Protected
    else TypeAnnotationUtil.Public
  }
  
  def showTypeAnnotationsSettings(project: Project): Unit = {
    val groups: Array[ConfigurableGroup] = ShowSettingsUtilImpl.getConfigurableGroups(project, true)
    
    val visitor = new ConfigurableVisitor.ByID("preferences.sourceCode.Scala")
    val configurable: Configurable = visitor.find(groups: _*)
        
    assert(configurable != null, "Cannot find configurable: " + classOf[CodeStyleSchemesConfigurable].getName)

    extensions.invokeLater {
      ShowSettingsUtil.getInstance.editConfigurable(project, configurable, new Runnable() {
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
        }
      })
    }
  }
  
  def createTypeAnnotationsHLink(project: Project , msg: String): HyperlinkLabel = {
    val typeAnnotationsSettings: HyperlinkLabel = new HyperlinkLabel(msg)
    typeAnnotationsSettings.addHyperlinkListener(new HyperlinkListener() {
      def hyperlinkUpdate(e: HyperlinkEvent) {
        if (e.getEventType eq HyperlinkEvent.EventType.ACTIVATED) {
          showTypeAnnotationsSettings(project)
        }
      }
    })
    
    typeAnnotationsSettings
  }
}
