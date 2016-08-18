package org.jetbrains.plugins.scala
package testingSupport

import com.intellij.codeInsight.{CodeInsightBundle, CodeInsightUtil}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.util.classMembers.MemberInfo
import com.intellij.testIntegration.TestFramework
import com.intellij.testIntegration.createTest.{CreateTestDialog, TestGenerator}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.actions.NewScalaTypeDefinitionAction
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.FormatterUtil
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.Def
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.refactoring.extractTrait.ExtractSuperUtil
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestFramework, TestConfigurationUtil}


class ScalaTestGenerator extends TestGenerator {
  def generateTest(project: Project, d: CreateTestDialog): PsiElement = {
    postponeFormattingWithin(project) {
      inWriteAction {
        try {
          val file: PsiFile = generateTestInternal(project, d)
          file
        } catch {
          case _: IncorrectOperationException =>
            invokeLater {
              val message = CodeInsightBundle.message("intention.error.cannot.create.class.message", d.getClassName)
              val title = CodeInsightBundle.message("intention.error.cannot.create.class.title")
              Messages.showErrorDialog(project, message, title)
            }
            null
        }
      }
    }
  }

  override def toString: String = ScalaFileType.SCALA_LANGUAGE.getDisplayName

  private def generateTestInternal(project: Project, d: CreateTestDialog): PsiFile = {
    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()
    val SCALA_EXTENSIOIN = "." + ScalaFileType.DEFAULT_EXTENSION
    val file = NewScalaTypeDefinitionAction.createFromTemplate(d.getTargetDirectory, d.getClassName, d.getClassName +
        SCALA_EXTENSIOIN,
      d.getSelectedTestFrameworkDescriptor match {
        case f: AbstractTestFramework if f.generateObjectTests => "Scala Object"
        case _ => "Scala Class"
      })
    val typeDefinition = file.depthFirst.filterByType(classOf[ScTypeDefinition]).next()
    val scope: GlobalSearchScope = GlobalSearchScope.allScope(project)
    val fqName = d.getSuperClassName
    if (fqName != null) {
      val psiClass = ScalaPsiManager.instance(project)
        .getCachedClass(fqName, scope, ScalaPsiManager.ClassCategory.TYPE)
      addSuperClass(typeDefinition, psiClass, fqName)
    }
    val positionElement = typeDefinition.extendsBlock.templateBody.map(_.getFirstChild).getOrElse(typeDefinition)
    var editor: Editor = CodeInsightUtil.positionCursor(project, file, positionElement)
    addTestMethods(editor, typeDefinition, d.getSelectedTestFrameworkDescriptor, d.getSelectedMethods, d
        .shouldGeneratedBefore, d.shouldGeneratedAfter, d.getClassName)
    file
  }

  private def addSuperClass(typeDefinition: ScTypeDefinition, psiClass: Option[PsiClass], fqName: String) = {
    val extendsBlock = typeDefinition.extendsBlock
    def addExtendsRef(refName: String) = {
      val (extendsToken, classParents) = ScalaPsiElementFactory.createClassTemplateParents(refName,
        typeDefinition.getManager)
      val extendsAdded = extendsBlock.addBefore(extendsToken, extendsBlock.getFirstChild)
      extendsBlock.addAfter(classParents, extendsAdded)
    }
    psiClass match {
      case Some(cls) =>
        val classParents = addExtendsRef(cls.name)
        classParents.depthFirst.filterByType(classOf[ScStableCodeReferenceElement]).next().bindToElement(cls)
      case None =>
        addExtendsRef(fqName)
    }
  }

  private def addTestMethods(editor: Editor, typeDef: ScTypeDefinition, testFramework: TestFramework, methods: java
  .util.Collection[MemberInfo], generateBefore: Boolean, generateAfter: Boolean, className: String): Unit = {
    val templateBody = typeDef.extendsBlock.templateBody
    import TestConfigurationUtil.isInheritor

    import collection.JavaConversions._
    val psiManager = PsiManager.getInstance(editor.getProject)
    templateBody match {
      case Some(body) =>
        val methodsList = methods.toList
        if (isInheritor(typeDef, "org.scalatest.FeatureSpecLike") ||
            isInheritor(typeDef, "org.scalatest.fixture.FeatureSpecLike")) {
          ScalaTestGenerator.generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef, editor.getProject)
          ScalaTestGenerator.addScalaTestFeatureSpecMethods(methodsList, psiManager, body)
        } else if (isInheritor(typeDef, "org.scalatest.FlatSpecLike") ||
            isInheritor(typeDef, "org.scalatest.fixture.FlatSpecLike")) {
          ScalaTestGenerator.generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef, editor.getProject)
          ScalaTestGenerator.addScalaTestFlatSpecMethods(methodsList, psiManager, body, className)
        } else if (isInheritor(typeDef, "org.scalatest.FreeSpecLike") ||
            isInheritor(typeDef, "org.scalatest.fixture.FreeSpecLike") ||
            isInheritor(typeDef, "org.scalatest.path.FreeSpecLike")) {
          ScalaTestGenerator.generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef, editor.getProject)
          ScalaTestGenerator.addScalaTestFreeSpecMethods(methodsList, psiManager, body)
        } else if (isInheritor(typeDef, "org.scalatest.FunSpecLike") ||
            isInheritor(typeDef, "org.scalatest.fixture.FunSpecLike")) {
          ScalaTestGenerator.generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef, editor.getProject)
          ScalaTestGenerator.addScalaTestFunSpecMethods(methodsList, psiManager, body, className)
        } else if (isInheritor(typeDef, "org.scalatest.FunSuiteLike") ||
            isInheritor(typeDef, "org.scalatest.fixture.FunSuiteLike")) {
          ScalaTestGenerator.generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef, editor.getProject)
          ScalaTestGenerator.addScalaTestFunSuiteMethods(methodsList, psiManager, body)
        } else if (isInheritor(typeDef, "org.scalatest.PropSpecLike") ||
            isInheritor(typeDef, "org.scalatest.fixture.PropSpecLike")) {
          ScalaTestGenerator.generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef, editor.getProject)
          ScalaTestGenerator.addScalaTestPropSpecMethods(methodsList, psiManager, body)
        } else if (isInheritor(typeDef, "org.scalatest.WordSpecLike") ||
            isInheritor(typeDef, "org.scalatest.fixture.WordSpecLike")) {
          ScalaTestGenerator.generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef, editor.getProject)
          ScalaTestGenerator.addScalaTestWordSpecMethods(methodsList, psiManager, body, className)
        } else if (isInheritor(typeDef, "org.specs2.specification.script.SpecificationLike")) {
          ScalaTestGenerator.generateSpecs2BeforeAndAfter(generateBefore, generateAfter, typeDef, editor.getProject)
          ScalaTestGenerator.generateSpecs2ScriptSpecificationMethods(methodsList, psiManager, body, className,
            editor.getProject, typeDef)
        } else if (isInheritor(typeDef, "org.specs2.SpecificationLike")) {
          ScalaTestGenerator.generateSpecs2BeforeAndAfter(generateBefore, generateAfter, typeDef, editor.getProject)
          ScalaTestGenerator.addSpecs2SpecificationMethods(methodsList, psiManager, body, className, editor.getProject)
        } else if (isInheritor(typeDef, "org.specs2.mutable.SpecificationLike")) {
          ScalaTestGenerator.generateSpecs2BeforeAndAfter(generateBefore, generateAfter, typeDef, editor.getProject)
          ScalaTestGenerator.generateSpecs2MutableSpecificationMethods(methodsList, psiManager, body, className)
        } else if (isInheritor(typeDef, "utest.framework.TestSuite")) {
          val file = typeDef.getContainingFile
          assert(file.isInstanceOf[ScalaFile])
          file.asInstanceOf[ScalaFile].addImportForPath("utest._")
          ScalaTestGenerator.generateUTestMethods(methodsList, psiManager, body, className, editor.getProject)
        }
      case _ =>
    }
  }
}

object ScalaTestGenerator {

  private def generateScalaTestBeforeAndAfter(generateBefore: Boolean, generateAfter: Boolean, typeDef:
  ScTypeDefinition,
                                              project: Project) {
    if (!(generateBefore || generateAfter)) return
    typeDef.extendsBlock.templateBody match {
      case Some(body) =>
        Option(ScalaPsiManager.instance(project).getCachedClass("org.scalatest.BeforeAndAfterEach",
          GlobalSearchScope.allScope(project), ScalaPsiManager.ClassCategory.TYPE)) match {
          case Some(beforeAndAfterTypeDef) if beforeAndAfterTypeDef.isInstanceOf[ScTypeDefinition] =>
            ExtractSuperUtil.addExtendsTo(typeDef, beforeAndAfterTypeDef.asInstanceOf[ScTypeDefinition])
            val closingBrace = body.getLastChild
            val psiManager = PsiManager.getInstance(project)
            if (generateBefore) {
              body.addBefore(ScalaPsiElementFactory.createMethodFromText("override def beforeEach() {\n\n}",
                psiManager),
                closingBrace)
            }
            if (generateAfter) {
              body.addBefore(ScalaPsiElementFactory.createMethodFromText("override def afterEach() {\n\n}", psiManager),
                closingBrace)
            }
          case _ =>
        }
      case _ =>
    }
  }

  private def generateSpecs2BeforeAndAfter(generateBefore: Boolean, generateAfter: Boolean, typeDef: ScTypeDefinition,
                                           project: Project) {
    if (!(generateBefore || generateAfter)) return
    typeDef.extendsBlock.templateBody match {
      case Some(body) =>
        val closingBrace = body.getLastChild
        val psiManager = PsiManager.getInstance(project)
        if (generateBefore) {
          val beforeOpt = Option(ScalaPsiManager.instance(project).getCachedClass("org.specs2.specification.BeforeEach",
            GlobalSearchScope.allScope(project), ScalaPsiManager.ClassCategory.TYPE))
          beforeOpt match {
            case Some(beforeTypeDef) =>
              ExtractSuperUtil.addExtendsTo(typeDef, beforeTypeDef.asInstanceOf[ScTypeDefinition])
              body.addBefore(ScalaPsiElementFactory.createMethodFromText("override protected def before: Any = {\n\n}",
                psiManager), closingBrace)
            case _ =>
          }
        }
        if (generateAfter) {
          val afterOpt = Option(ScalaPsiManager.instance(project).getCachedClass("org.specs2.specification.AfterEach",
            GlobalSearchScope.allScope(project), ScalaPsiManager.ClassCategory.TYPE))
          afterOpt match {
            case Some(afterTypeDef) =>
              ExtractSuperUtil.addExtendsTo(typeDef, afterTypeDef.asInstanceOf[ScTypeDefinition])
              body.addBefore(ScalaPsiElementFactory.createMethodFromText("override protected def after: Any = {\n\n}",
                psiManager), closingBrace)
            case _ =>
          }
        }
      case _ =>
    }
  }

  private def addScalaTestFeatureSpecMethods(methods: List[MemberInfo], psiManager: PsiManager, templateBody: ScTemplateBody) {
    if (methods.nonEmpty) {
      templateBody.addBefore(ScalaPsiElementFactory.createExpressionFromText(
        methods.map("scenario (\"" + _.getMember.getName + "\"){\n\n}\n").
            fold("feature(\"Methods tests\") {")(_ + "\n" + _) + "}", psiManager),
        templateBody.getLastChild)
    }
  }

  private def addScalaTestFlatSpecMethods(methods: List[MemberInfo], psiManager: PsiManager, templateBody: ScTemplateBody,
                                 className: String) {
    if (methods.nonEmpty) {
      val closingBrace = templateBody.getLastChild
      templateBody.addBefore(ScalaPsiElementFactory.createExpressionFromText("behavior of \"" + className + "\"",
        psiManager), closingBrace)
      templateBody.addBefore(ScalaPsiElementFactory.createNewLine(psiManager, "\n\n"), closingBrace)
      methods.map("it should \"" + _.getMember.getName + "\" in {\n\n}").
          map(ScalaPsiElementFactory.createExpressionFromText(_, psiManager)).
          foreach(expr => {
        templateBody.addBefore(expr, closingBrace)
        templateBody.addBefore(ScalaPsiElementFactory.createNewLine(psiManager, "\n\n"), closingBrace)
      })
    }
  }

  private def addScalaTestFreeSpecMethods(methods: List[MemberInfo], psiManager: PsiManager, templateBody: ScTemplateBody) {
    if (methods.nonEmpty) {
      templateBody.addBefore(ScalaPsiElementFactory.createExpressionFromText(
        methods.map("\"" + _.getMember.getName + "\" in {\n\n}\n").fold("\"Methods tests\" - {")(_ + "\n" + _) + "\n}",
        psiManager), templateBody.getLastChild)
    }
  }

  private def addScalaTestFunSpecMethods(methods: List[MemberInfo], psiManager: PsiManager, templateBody: ScTemplateBody,
                                className: String) {
    if (methods.nonEmpty) {
      templateBody.addBefore(ScalaPsiElementFactory.createExpressionFromText(
        methods.map("it(\"should " + _.getMember.getName + "\") {\n\n}\n").
            fold("describe(\"" + className + "\") {\n")(_ + "\n" + _) + "\n}", psiManager),
        templateBody.getLastChild)
    }
  }

  private def addScalaTestFunSuiteMethods(methods: List[MemberInfo], psiManager: PsiManager, templateBody: ScTemplateBody) {
    if (methods.nonEmpty) {
      val closingBrace = templateBody.getLastChild
      methods.map("test(\"test" + _.getMember.getName.capitalize + "\") {\n\n}").
          map(ScalaPsiElementFactory.createExpressionFromText(_, psiManager)).
          foreach(expr => {
        templateBody.addBefore(expr, closingBrace)
        templateBody.addBefore(ScalaPsiElementFactory.createNewLine(psiManager, "\n\n"), closingBrace)
      })
    }
  }

  private def addScalaTestPropSpecMethods(methods: List[MemberInfo], psiManager: PsiManager, templateBody: ScTemplateBody) {
    if (methods.nonEmpty) {
      val closingBrace = templateBody.getLastChild
      methods.map("property(\"" + _.getMember.getName + " property\"){\n\n}").
          map(ScalaPsiElementFactory.createExpressionFromText(_, psiManager)).
          foreach(expr => {
        templateBody.addBefore(expr, closingBrace)
        templateBody.addBefore(ScalaPsiElementFactory.createNewLine(psiManager, "\n\n"), closingBrace)
      })
    }
  }

  private def addScalaTestWordSpecMethods(methods: List[MemberInfo], psiManager: PsiManager, templateBody: ScTemplateBody,
                                 className: String) {
    if (methods.nonEmpty) {
      templateBody.addBefore(ScalaPsiElementFactory.createExpressionFromText(
        methods.map("\"" + _.getMember.getName + "\" in {\n\n}\n").
            fold("\"" + className + "\" should {\n")(_ + "\n" + _) + "\n}", psiManager),
        templateBody.getLastChild)
    }
  }

  private def addSpecs2SpecificationMethods(methods: List[MemberInfo], psiManager: PsiManager,
                                            templateBody: ScTemplateBody, className: String, project: Project) {
    val testNames = methods.map("test" + _.getMember.getName.capitalize)
    val normalIndentString = FormatterUtil.getNormalIndentString(project)
    val doubleIndent = normalIndentString + normalIndentString

    val checkMethodsString = if (methods.nonEmpty) testNames.map(testName => doubleIndent + testName + " $" + testName).
        fold("\n" + normalIndentString + "Methods of " + className + " should pass tests:")(_ + "\n" + _)
    else ""
    val closingBrace = templateBody.getLastChild
    templateBody.addBefore(ScalaPsiElementFactory.createMethodFromText("def is = s2\"\"\"" + checkMethodsString +
        "\n" + normalIndentString + "\"\"\"", psiManager), closingBrace)
    testNames.map(testName =>
      templateBody.addBefore(ScalaPsiElementFactory.createMethodFromText("def " + testName + " = ok", psiManager),
        closingBrace))
  }

  private def generateSpecs2ScriptSpecificationMethods(methods: List[MemberInfo], psiManager: PsiManager,
                                                       templateBody: ScTemplateBody,
                                                       className: String, project: Project, typeDef: ScTypeDefinition) {
    Option(ScalaPsiManager.instance(project).getCachedClass("org.specs2.specification.Groups",
      GlobalSearchScope.allScope(project), ScalaPsiManager.ClassCategory.TYPE)) match {
      case Some(groupsTypeDef) =>
        ExtractSuperUtil.addExtendsTo(typeDef, groupsTypeDef.asInstanceOf[ScTypeDefinition])
        val testNames = methods.map("test" + _.getMember.getName.capitalize)
        val closingBrace = templateBody.getLastChild
        val normalIndentString = FormatterUtil.getNormalIndentString(project)
        val doubleIndent = normalIndentString + normalIndentString
        val checkMethodsString = if (methods.nonEmpty) testNames.map(doubleIndent + "+ " + _).
            fold("\n" + normalIndentString + "Methods of " + className + " should pass tests:")(_ + "\n" + _)
        else ""
        templateBody.addBefore(ScalaPsiElementFactory.createMethodFromText("def is = s2\"\"\"" + checkMethodsString +
            "\n" + doubleIndent + "\"\"\"", psiManager), closingBrace)
        if (methods.nonEmpty) {
          templateBody.addBefore(ScalaPsiElementFactory.createExpressionFromText(testNames.map("eg := ok //" + _).
              fold("\"" + className + "\" - new group {")(_ + "\n" + _) + "\n}", psiManager), closingBrace)
        }
      case _ =>
    }
  }

  private def generateSpecs2MutableSpecificationMethods(methods: List[MemberInfo], psiManager: PsiManager,
                                                        templateBody: ScTemplateBody,
                                                        className: String) {
    if (methods.nonEmpty) {
      templateBody.addBefore(ScalaPsiElementFactory.createExpressionFromText(methods.
          map("\""+ _.getMember.getName + "\" in {\nok\n}\n").
          fold("\"" + className + "\" should {")(_ + "\n" + _) + "\n}", psiManager), templateBody.getLastChild)
    }
  }

  private def generateUTestMethods(methods: List[MemberInfo], psiManager: PsiManager, templateBody: ScTemplateBody,
                                   className: String, project: Project) {
    val normalIndentString = FormatterUtil.getNormalIndentString(project)
    templateBody.addBefore(ScalaPsiElementFactory.createElement("val tests = TestSuite{}", psiManager, Def.parse(_)),
      templateBody.getLastChild)
    if (methods.nonEmpty) {
      templateBody.addBefore(ScalaPsiElementFactory.createElement(methods.map(normalIndentString + "\"" +
          _.getMember.getName + "\" - {}\n").fold("val methodsTests = TestSuite{")(_ + "\n" + _) + "}", psiManager,
        Def.parse(_)), templateBody.getLastChild)
    }
  }

}
