package org.jetbrains.plugins.scala
package codeInsight.generation

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.{CodeInsightBundle, CodeInsightUtilBase}
import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{DialogWrapper, Messages}
import com.intellij.openapi.util.Computable
import com.intellij.psi._
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.codeInsight.generation.ui.ScalaGenerateEqualsWizard
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * Nikolay.Tropin
 * 8/19/13
 */
class ScalaGenerateEqualsHandler extends LanguageCodeInsightActionHandler {
  private val myEqualsFields = collection.mutable.LinkedHashSet[ScNamedElement]()
  private val myHashCodeFields = collection.mutable.LinkedHashSet[ScNamedElement]()

  def chooseOriginalMembers(aClass: ScClass, project: Project, editor: Editor)
                           (implicit typeSystem: TypeSystem): Boolean = {
    val equalsMethod = hasEquals(aClass)
    val hashCodeMethod = hasHashCode(aClass)
    var needEquals = equalsMethod.isEmpty
    var needHashCode = hashCodeMethod.isEmpty
    if (!needEquals && !needHashCode) {
      val text: String =
        if (aClass.isInstanceOf[PsiAnonymousClass]) CodeInsightBundle.message("generate.equals.and.hashcode.already.defined.warning.anonymous")
        else CodeInsightBundle.message("generate.equals.and.hashcode.already.defined.warning", aClass.qualifiedName)
      if (Messages.showYesNoDialog(project, text, CodeInsightBundle.message("generate.equals.and.hashcode.already.defined.title"), Messages.getQuestionIcon) == DialogWrapper.OK_EXIT_CODE) {
        val deletedOk = ApplicationManager.getApplication.runWriteAction(new Computable[Boolean] {
          def compute: Boolean = {
            try {
              equalsMethod.get.delete()
              hashCodeMethod.get.delete()
              true
            }
            catch {
              case e: IncorrectOperationException => false
            }
          }
        })
        if (!deletedOk) return false
        else {
          needEquals = true
          needHashCode = true
        }
      }
      else return false
    }

    val allFields: Seq[ScNamedElement] = GenerationUtil.getAllFields(aClass)
    if (allFields.isEmpty) {
      HintManager.getInstance.showErrorHint(editor, "No fields to include in equals/hashCode have been found")
      return false
    }

    if (ApplicationManager.getApplication.isUnitTestMode) {
      myEqualsFields ++= allFields
      myHashCodeFields ++= allFields.filterNot(GenerationUtil.isVar)
    } else {
      val wizard = new ScalaGenerateEqualsWizard(project, aClass, needEquals, needHashCode)
      wizard.show()
      if (!wizard.isOK) return false
      myEqualsFields ++= wizard.getEqualsFields
      myHashCodeFields ++= wizard.getHashCodeFields
    }
    true
  }


  protected def cleanup() {
    myEqualsFields.clear()
    myHashCodeFields.clear()
  }

  protected def createHashCode(aClass: ScClass)
                              (implicit typeSystem: TypeSystem): ScFunction = {
    val declText = "def hashCode(): Int"
    val signature = new PhysicalSignature(
      ScalaPsiElementFactory.createMethodWithContext(declText + " = 0", aClass, aClass.extendsBlock),
      ScSubstitutor.empty)
    val superCall = Option(if (!overridesFromJavaObject(aClass, signature)) "super.hashCode()" else null)
    val usedFields = superCall ++: myHashCodeFields.map(_.name)
    val stateText = usedFields.mkString("Seq(", ", ", ")")
    val firstStmtText = s"val state = $stateText"
    val arrow = ScalaPsiUtil.functionArrow(aClass.getProject)
    val calculationText = s"state.map(_.hashCode()).foldLeft(0)((a, b) $arrow 31 * a + b)"
    val methodText =
      s"""override $declText = {
        |  $firstStmtText
        |  $calculationText
        |}""".stripMargin.replace("\r", "")
    ScalaPsiElementFactory.createMethodWithContext(methodText, aClass, aClass.extendsBlock)
  }

  protected def createCanEqual(aClass: ScClass, project: Project)
                              (implicit typeSystem: TypeSystem): ScFunction = {
    val declText = "def canEqual(other: Any): Boolean"
    val sign = new PhysicalSignature(
      ScalaPsiElementFactory.createMethodWithContext(declText + " = true", aClass, aClass.extendsBlock),
      ScSubstitutor.empty)
    val overrideMod = overrideModifier(aClass, sign)
    val text = s"$overrideMod $declText = other.isInstanceOf[${aClass.name}]"
    ScalaPsiElementFactory.createMethodWithContext(text, aClass, aClass.extendsBlock)
  }

  protected def createEquals(aClass: ScClass, project: Project)
                            (implicit typeSystem: TypeSystem): ScFunction = {
    val fieldComparisons = myEqualsFields.map(_.name).map(name => s"$name == that.$name")
    val declText = "def equals(other: Any): Boolean"
    val signature = new PhysicalSignature(
      ScalaPsiElementFactory.createMethodWithContext(declText + " = false", aClass, aClass.extendsBlock),
      ScSubstitutor.empty)
    val superCheck = Option(if (!overridesFromJavaObject(aClass, signature)) "super.equals(that)" else null)
    val canEqualCheck = Option(if (aClass.hasFinalModifier) null else "(that canEqual this)")
    val allChecks = superCheck ++: canEqualCheck ++: fieldComparisons
    val checksText = allChecks.mkString(" &&\n")
    val arrow = ScalaPsiUtil.functionArrow(project)
    val text = s"""override $declText = other match {
                 |  case that: ${aClass.name} $arrow
                 |    $checksText
                 |  case _ $arrow false
                 |}""".stripMargin.replace("\r", "")
    ScalaPsiElementFactory.createMethodWithContext(text, aClass, aClass.extendsBlock)
  }


  def invoke(project: Project, editor: Editor, file: PsiFile) {
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return
    if (!FileDocumentManager.getInstance.requestWriting(editor.getDocument, project)) return

    try {
      implicit val typeSystem = project.typeSystem
      val aClass = GenerationUtil.classAtCaret(editor, file).getOrElse(return)
      val isOk = chooseOriginalMembers(aClass, project, editor)
      if (!isOk) return

      extensions.inWriteAction {
        val needHashCode = hasHashCode(aClass).isEmpty
        val hashCodeMethod = Option(
          if (needHashCode) createHashCode(aClass) else null)

        val needEquals = hasEquals(aClass).isEmpty
        val equalsMethod = Option(
          if (needEquals) createEquals(aClass, project) else null)

        val needCanEqual = needEquals && hasCanEqual(aClass).isEmpty && !aClass.hasFinalModifier
        val canEqualMethod = Option(
          if (needCanEqual) createCanEqual(aClass, project) else null)

        val newMethods = hashCodeMethod ++: equalsMethod ++: canEqualMethod ++: Nil
        GenerationUtil.addMembers(aClass, newMethods, editor.getDocument)
      }
    }
    finally {
      cleanup()
    }
  }

  def startInWriteAction(): Boolean = true

  def isValidFor(editor: Editor, file: PsiFile): Boolean = {
    lazy val isSuitableClass = GenerationUtil.classAtCaret(editor, file) match {
      case Some(c: ScClass) if !c.isCase => true
      case _ => false
    }
    file != null && ScalaFileType.SCALA_FILE_TYPE == file.getFileType && isSuitableClass
  }

  private def hasEquals(aClass: ScClass)(implicit typeSystem: api.TypeSystem): Option[ScFunction] = {
    val method = ScalaPsiElementFactory.createMethodFromText("def equals(that: Any): Boolean", aClass.getManager)
    findSuchMethod(aClass, "equals", method.methodType)
  }

  private def hasHashCode(aClass: ScClass)(implicit typeSystem: api.TypeSystem): Option[ScFunction] = {
    val method = ScalaPsiElementFactory.createMethodFromText("def hashCode(): Int", aClass.getManager)
    findSuchMethod(aClass, "hashCode", method.methodType)
  }

  private def hasCanEqual(aClass: ScClass)(implicit typeSystem: api.TypeSystem): Option[ScFunction] = {
    val method = ScalaPsiElementFactory.createMethodFromText("def canEqual(that: Any): Boolean", aClass.getManager)
    findSuchMethod(aClass, "canEqual", method.methodType)
  }

  private def findSuchMethod(aClass: ScClass, name: String, methodType: ScType)
                            (implicit typeSystem: api.TypeSystem): Option[ScFunction] = {
    aClass.functions
            .filter(_.name == name)
            .find(fun => fun.methodType(None) equiv methodType)
  }

  private def overrideModifier(aClass: ScTemplateDefinition, signature: Signature): String = {
    val needModifier = ScalaOIUtil.methodSignaturesToOverride(aClass, withSelfType = false).exists {
      case sign: PhysicalSignature => sign.equiv(signature)
      case _ => false
    }
    if (needModifier) ScalaKeyword.OVERRIDE else ""
  }

  private def overridesFromJavaObject(aClass: ScTemplateDefinition, signature: Signature): Boolean = {
    val methodsToOverride = ScalaOIUtil.methodSignaturesToOverride(aClass, withSelfType = false)
    methodsToOverride exists {
      case sign: PhysicalSignature if sign.equiv(signature) =>
        //used only for equals and hashcode methods
        sign.isJava && sign.method.findSuperMethods(false).isEmpty
      case _ => false
    }
  }
}
