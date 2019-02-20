package org.jetbrains.plugins.scala
package lang.refactoring.extractTrait

import java.util

import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.util.RefactoringMessageUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScalaConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createClassTemplateParents
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.IntroduceException

import scala.collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 2014-05-20
 */
object ExtractSuperUtil {
  def afterClassChoosing(element: PsiElement,
                         project: Project,
                         editor: Editor,
                         file: PsiFile,
                         isSuitableClass: PsiClass => Boolean)
                        (action: => Unit) {
    try {
      val classes = ScalaPsiUtil.getParents(element, file).collect {
        case t: ScTemplateDefinition if isSuitableClass(t) => t
      }.toArray[PsiClass]
      classes.size match {
        case 0 =>
        case 1 => action
        case _ =>
          val selection = classes(0)
          val processor = new PsiElementProcessor[PsiClass] {
            def execute(aClass: PsiClass): Boolean = {
              action
              false
            }
          }
          NavigationUtil.getPsiElementPopup(classes, new PsiClassListCellRenderer() {
            override def getElementText(element: PsiClass): String = super.getElementText(element).replace("$", "")
          }, "Choose class", processor, selection).showInBestPositionFor(editor)
      }
    }
    catch {
      case _: IntroduceException => return
    }
  }

  def classPresentableName(clazz: ScTemplateDefinition): String = {
    clazz match {
      case td: ScTypeDefinition => td.qualifiedName
      case anon: ScNewTemplateDefinition =>
        val anonymous = "<anonymous>"
        PsiTreeUtil.getParentOfType(anon, classOf[ScTemplateDefinition], classOf[ScFunctionDefinition]) match {
          case td: ScTemplateDefinition => s"$anonymous in ${td.name}"
          case fun: ScFunctionDefinition => s"$anonymous in ${fun.name}"
          case _ => anonymous
        }
      case _ => ""
    }
  }

  def packageName(clazz: ScTemplateDefinition): String =
    clazz.containingScalaFile.map {
      _.getPackageName
    }.getOrElse("")

  def addExtendsTo(clazz: ScTemplateDefinition, typeToExtend: ScTypeDefinition, parameters: String = "") {
    val name = typeToExtend.name
    val text = name + parameters
    val oldExtBlock = clazz.extendsBlock
    implicit val projectContext = clazz.projectContext

    val templParents = oldExtBlock.templateParents match {
      case Some(tp: ScTemplateParents) =>
        val tpText = s"${tp.getText} with $text"
        val (_, newTp) = createClassTemplateParents(tpText)
        tp.replace(newTp).asInstanceOf[ScTemplateParents]
      case None =>
        val (extKeyword, newTp) = createClassTemplateParents(text)
        oldExtBlock.addRangeBefore(extKeyword, newTp, oldExtBlock.getFirstChild)
        oldExtBlock.templateParents.get
    }
    templParents.typeElementsWithoutConstructor.foreach {
      case s: ScSimpleTypeElement if s.reference.exists(_.refName == name) => s.reference.foreach(_.bindToElement(typeToExtend))
      case _ =>
    }
  }

  def getDirUnderSameSourceRoot(clazz: PsiClass, directories: Array[PsiDirectory]): PsiDirectory = {
    val sourceFile: VirtualFile = clazz.getContainingFile.getVirtualFile
    if (sourceFile != null) {
      val fileIndex: ProjectFileIndex = ProjectRootManager.getInstance(clazz.getProject).getFileIndex
      val sourceRoot: VirtualFile = fileIndex.getSourceRootForFile(sourceFile)
      if (sourceRoot != null) {
        for (dir <- directories) {
          if (Comparing.equal(fileIndex.getSourceRootForFile(dir.getVirtualFile), sourceRoot)) {
            return dir
          }
        }
      }
    }
    directories(0)
  }

  def checkPackage(targetPackageName: String, targetClassName: String, sourceClass: PsiClass): String = {
    val pckg: PsiPackage = JavaPsiFacade.getInstance(sourceClass.getProject).findPackage(targetPackageName)
    if (pckg == null) return s"Cannot find package with name: $targetPackageName"
    
    val dirs: Array[PsiDirectory] = pckg.getDirectories
    if (dirs.length == 0) return s"Cannot find directory for package: $targetPackageName"
    
    if (pckg.containsClassNamed(targetClassName)) return s"Class with name $targetClassName already exists in the package $targetPackageName"
    
    val dir: PsiDirectory = ExtractSuperUtil.getDirUnderSameSourceRoot(sourceClass, dirs)
    val cantCreateFile: String = RefactoringMessageUtil.checkCanCreateFile(dir, targetClassName + ".scala")
    if (cantCreateFile != null) return cantCreateFile
    
    null
  }

  def possibleMembersToExtract(clazz: ScTemplateDefinition): util.List[ScalaExtractMemberInfo] = {
    clazz.members.filter {
      case m if m.isPrivate => false
      case ScalaConstructor(_) => false
      case _: ScTypeDefinition => false
      case _ => true
    }.map(new ScalaExtractMemberInfo(_)).asJava
  }

  def declarationScope(m: ScMember): Seq[PsiElement] = {
    m match {
      case decl: ScDeclaration => Seq(decl)
      case fun: ScFunctionDefinition => fun.children.takeWhile(!fun.body.contains(_)).toSeq
      case patDef: ScPatternDefinition => patDef.children.takeWhile(!patDef.expr.contains(_)).toSeq
      case varDef: ScVariableDefinition => varDef.children.takeWhile(!varDef.expr.contains(_)).toSeq
      case _ => Seq(m)
    }
  }
}
