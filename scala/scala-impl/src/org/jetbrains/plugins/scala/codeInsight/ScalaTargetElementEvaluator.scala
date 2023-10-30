package org.jetbrains.plugins.scala.codeInsight

import com.intellij.codeInsight.{TargetElementEvaluatorEx, TargetElementEvaluatorEx2}
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.psi._
import org.jetbrains.plugins.scala.editor.ScalaEditorUtils
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScReference, ScStableCodeReference, ScalaConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScFunctionDefinition, ScTypeAliasDefinition, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScObject}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

class ScalaTargetElementEvaluator extends TargetElementEvaluatorEx2 with TargetElementEvaluatorEx {

  override def getElementByReference(ref: PsiReference, flags: Int): PsiElement = ref.getElement match {
    case isUnapplyFromVal(binding) => binding
    case isCaseClassParameter(cp) => cp
    case isCaseClassApply(clazz) => clazz
    case isSyntheticObject(clazz) => clazz
    case isVarSetterFakeMethod(refPattern) => refPattern
    case isVarSetterWrapper(refPattern) => refPattern
    case _ => null
  }

  override def isAcceptableNamedParent(parent: PsiElement): Boolean = parent match {
    case _: ScNewTemplateDefinition => false
    case _ => true
  }

  private object isUnapplyFromVal {
    def unapply(ref: ScStableCodeReference): Option[ScBindingPattern] = {
      if (ref == null) return null
      ref.bind() match {
        case Some(resolve@ScalaResolveResult(fun: ScFunctionDefinition, _))
          if Set("unapply", "unapplySeq").contains(fun.name) =>
          resolve.innerResolveResult match {
            case Some(ScalaResolveResult(binding: ScBindingPattern, _)) => Some(binding)
            case _ => None
          }
        case _ => None
      }
    }
  }

  private object isVarSetterFakeMethod {
    private val setterSuffixes: Seq[String] = Seq("_=", "_$eq")
    def unapply(ref: ScReference): Option[ScReferencePattern] = {
      ref.resolve() match {
        case fake @ FakePsiMethod(refPattern: ScReferencePattern)
          if setterSuffixes.exists(fake.getName.endsWith) && refPattern.nameContext.is[ScVariable] => Some(refPattern)
        case _ => None
      }
    }
  }

  private object isVarSetterWrapper {
    val setterSuffix = "_$eq"
    def unapply(ref: PsiReferenceExpression): Option[ScReferencePattern] = {
      ref.resolve() match {
        case PsiTypedDefinitionWrapper(refPattern: ScReferencePattern)
          if refPattern.name.endsWith(setterSuffix) && refPattern.nameContext.is[ScVariable] => Some(refPattern)
        case _ => None
      }
    }
  }

  private object isCaseClassParameter {
    def unapply(ref: ScReference): Option[ScParameter] = {
      ref.resolve() match {
        case p: ScParameter =>
          p.owner match {
            case a: ScFunctionDefinition if a.isApplyMethod && a.isSynthetic =>
              a.containingClass match {
                case obj: ScObject =>
                  obj.fakeCompanionClassOrCompanionClass match {
                    case cl: ScClass if cl.isCase => return cl.parameters.find(_.name == p.name)
                    case _ =>
                  }
                case _ =>
              }
            case _ =>
          }
        case _ =>
      }
      None
    }
  }

  private object isCaseClassApply {
    def unapply(ref: ScReference): Option[ScClass] = {
      ref.resolve() match {
        case (fun: ScFunctionDefinition) & ContainingClass(obj: ScObject) if fun.isApplyMethod && fun.isSynthetic =>
          Option(obj.fakeCompanionClassOrCompanionClass)
            .collect { case cls: ScClass if cls.isCase => cls }
        case _ => None
      }
    }
  }

  private object isSyntheticObject {
    def unapply(ref: ScReference): Option[PsiClass] = {
      ref.resolve() match {
        case obj: ScObject if obj.isSyntheticObject => obj.baseCompanion
        case _ => None
      }
    }
  }

  override def isIdentifierPart(file: PsiFile, text: CharSequence, offset: Int): Boolean = {
    val child: PsiElement = file.findElementAt(offset)
    child != null && child.getNode != null && ScalaTokenTypes.IDENTIFIER_TOKEN_SET.contains(child.getNode.getElementType )
  }

  override def adjustElement(editor: Editor, flags: Int, element: PsiElement, contextElement: PsiElement): PsiElement =
    super.adjustElement(editor, flags, element, contextElement)

  override def adjustReference(ref: PsiReference): PsiElement =
    super.adjustReference(ref)

  override def adjustReferenceOrReferencedElement(file: PsiFile, editor: Editor, offset: Int, flags: Int, refElement: PsiElement): PsiElement =
    super.adjustReferenceOrReferencedElement(file, editor, offset, flags, refElement)

  override def adjustTargetElement(editor: Editor, offset: Int, flags: Int, targetElement: PsiElement): PsiElement = {
    findReferencedTypeAliasDefinition(editor, offset, targetElement) match {
      case Some(typeAlias) =>
        typeAlias
      case None =>
        super.adjustTargetElement(editor, offset, flags, targetElement)
    }
  }

  /**
   * This is a solution for SCL-20826
   *
   * Suppose we have this code: {{{
   *   class MyClass
   *   type MyAlias = MyClass
   *   new MyAlias
   *   val x: MyAlias
   * }}}
   *
   * When the caret is located at `: MyAlias` and we try to rename the alias it works fine
   * because the reference is resolved to the type alias definition.
   *
   * However when the caret is located at `new MyAlias` then the reference will be resolved to `MyClass` primary constructor.
   * (this logic is located in [[org.jetbrains.plugins.scala.lang.resolve.processor.ConstructorResolveProcessor]])
   * This will break rename refactoring, because it will think that we want to rename `MyClass`, not `MyAlias`.
   * In order to workaround this we detect such cases and adjust target element to the type alias definition instead of constructor
   *
   */
  private def findReferencedTypeAliasDefinition(
    editor: Editor,
    offset: Int,
    targetElement: PsiElement,
  ): Option[ScTypeAliasDefinition] = {
    targetElement match {
      case m: PsiMethod if m.isConstructor =>
      case _ =>
        return None
    }

    val project = targetElement.getProject
    val document = editor.getDocument
    val file = PsiDocumentManager.getInstance(project).getPsiFile(document)
    if (file == null)
      return None

    val reference = findReferencesAtCaret(file, document, offset) match {
      case Some(ref) => ref
      case None =>
        return None
    }

    val referenceResolved = reference.bind()
    referenceResolved match {
      case Some(resolveResult) =>
        val actualElement = resolveResult.getActualElement
        actualElement match {
          case alias: ScTypeAliasDefinition =>
            Some(alias)
          case _ => None
        }
      case _ => None
    }
  }

  private def findReferencesAtCaret(file: PsiFile, document: Document, offset: Int): Option[ScStableCodeReference] = {
    val elementAtCaret = ScalaEditorUtils.findElementAtCaret_WithFixedEOFAndWhiteSpace(file, document: Document, offset)
    if (elementAtCaret == null)
      return None

    val parent = elementAtCaret.getParent
    parent match {
      case ref: ScStableCodeReference => Some(ref)
      case _ =>
        None
    }
  }
}
