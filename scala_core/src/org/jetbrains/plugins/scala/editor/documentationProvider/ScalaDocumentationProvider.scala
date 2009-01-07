package org.jetbrains.plugins.scala.editor.documentationProvider

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import _root_.scala.collection.immutable.HashSet
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.base.patterns.ScReferencePattern
import lang.psi.api.base.ScPrimaryConstructor
import lang.psi.api.ScalaFile
import lang.psi.api.statements._
import lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.toplevel.typedef._

import lang.psi.api.toplevel.{ScNamedElement, ScTyped, ScTypeParametersOwner}
import lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.structureView.StructureViewUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.11.2008
 */

class ScalaDocumentationProvider extends DocumentationProvider {
  import ScalaDocumentationProvider._
  def getDocumentationElementForLookupItem(psiManager: PsiManager, `object` : Object, element: PsiElement): PsiElement = {
    null
  }

  def getUrlFor(element: PsiElement, originalElement: PsiElement): String = {
    null
  }

  def getQuickNavigateInfo(element: PsiElement): String = {
    element match {
      case clazz: ScTypeDefinition => generateClassInfo(clazz)
      case function: ScFunction => generateFunctionInfo(function)
      case value: ScNamedElement if ScalaPsiUtil.nameContext(value).isInstanceOf[ScValue]
              || ScalaPsiUtil.nameContext(value).isInstanceOf[ScVariable] => generateValueInfo(value)
      case alias: ScTypeAlias => generateTypeAliasInfo(alias)
      case parameter: ScParameter => generateParameterInfo(parameter)
      case _ => null
    }
  }

  def getDocumentationElementForLink(psiManager: PsiManager, link: String, context: PsiElement): PsiElement = {
    null
  }

  def generateDoc(element: PsiElement, originalElement: PsiElement): String = {
    val e = getDocedElement(originalElement)
    e match {
      case null =>
      case _: ScTypeAlias => //todo:
      case x: ScTypeDefinition => {
        val text: StringBuffer = new StringBuffer("")
        val qualifiedName = x.getQualifiedName
        val f = qualifiedName.lastIndexOf(".", qualifiedName.length)
        if (f != -1) text.append("package " + qualifiedName.substring(0, f) + "\n")
        var prev = x.getPrevSibling
        while (prev != null && (prev.getText.charAt(0) == ' ' || prev.getText.charAt(0) == '\n')) prev = prev.getPrevSibling
        prev match {
          case _: PsiComment =>text.append(prev.getText + "\n")
          case _ =>
        }
        text.append("class " + originalElement.getText)
        if (x.supers.length > 0) {
          text.append(" extends ")
          for (sup <- x.supers) {
            text.append(sup.getQualifiedName + ", ")
          }
          text.replace(text.length - 2, text.length, "")
        }
        text.append(" {\n}")
        val dummyFile: PsiJavaFile = PsiFileFactory.getInstance(element.getProject).
                createFileFromText(originalElement.getText + ".java", text.toString).asInstanceOf[PsiJavaFile]
        val elem = dummyFile.getClasses.apply(0)
        return JavaDocumentationProvider.generateExternalJavadoc(elem)
      }
      case x: ScFunction =>
      case x: ScValue =>
      case x: ScVariable =>
      case x: ScParameter =>
      case _ =>
    }
    null
  }
}

private object ScalaDocumentationProvider {
  private def getDocedElement(originalElement: PsiElement): PsiElement = {
    originalElement match {
      case null => null
      case _:ScTypeDefinition | _: ScTypeAlias | _: ScValue
              | _: ScVariable | _: ScFunction | _: ScParameter => originalElement
      case _ => getDocedElement(originalElement.getParent)
    }
  }

  private def getMemberHeader(member: ScMember): String = {
    if (!member.getParent.isInstanceOf[ScTemplateBody]) return ""
    if (!member.getParent.getParent.getParent.isInstanceOf[ScTypeDefinition]) return ""
    member.getContainingClass.getName + " " + member.getContainingClass.getPresentation.getLocationString + "\n"
  }

  private def getOneLine(s: String): String = {
    val trimed = s.trim
    val i = trimed.indexOf('\n')
    if (i == -1) trimed
    else trimed.substring(0, i) + " ..."
  }

  private def appendTypeParams(owner: ScTypeParametersOwner, buffer: StringBuilder) {
    buffer.append(owner.typeParametersClause match {
      case Some(x) => x.getText
      case None => ""
    })
  }

  def generateClassInfo(clazz: ScTypeDefinition): String = {
    val buffer = new StringBuilder
    val module = ModuleUtil.findModuleForPsiElement(clazz)
    if (module != null) {
      buffer.append('[').append(module.getName()).append("] ")
    }
    val locationString = clazz.getPresentation.getLocationString
    val length = locationString.length
    if (length > 1) buffer.append(locationString.substring(1, length - 1))
    if (buffer.length > 0) buffer.append("\n")
    buffer.append(ScalaPsiUtil.getModifiersPresentableText(clazz.getModifierList))
    buffer.append(clazz match {
      case _: ScObject => "object "
      case _: ScClass => "class "
      case _: ScTrait => "trait "
    })
    buffer.append(clazz.name)
    appendTypeParams(clazz, buffer)
    clazz match {
      case clazz: ScClass => {
        clazz.constructor match {
          case Some(x: ScPrimaryConstructor) => buffer.append(StructureViewUtil.getParametersAsString(x.parameterList, false))
          case None =>
        }
      }
      case _ =>
    }
    buffer.append(" extends")
    val types = clazz.superTypes
    if (types.length > 0) {
      for (i <- 0 to types.length - 1) {
        buffer.append(if (i == 1)  "\n  " else " ")
        if (i != 0) buffer.append("with ")
        buffer.append(ScType.presentableText(types(i)))
      }
    }
    buffer.toString
  }

  def generateFunctionInfo(function: ScFunction): String = {
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(function))
    buffer.append(ScalaPsiUtil.getModifiersPresentableText(function.getModifierList))
    buffer.append("def ")
    buffer.append(ScalaPsiUtil.getMethodPresentableText(function))
    buffer.toString
  }

  def generateValueInfo(field: ScNamedElement): String = {
    val member = ScalaPsiUtil.nameContext(field) match {
      case x: ScMember => x
      case _ => return null
    }
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(member))
    buffer.append(ScalaPsiUtil.getModifiersPresentableText(member.getModifierList))
    member match {
      case value: ScValue => {
        buffer.append("val ")
        buffer.append(field.name)
        field match {
          case typed: ScTyped => {
            val typez = typed.calcType
            if (typez != null) buffer.append(": " + ScType.presentableText(typez))
          }
          case _ =>
        }
        value match {
          case d: ScPatternDefinition => {
            buffer.append(" = ")
            buffer.append(getOneLine(d.expr.getText))
          }
          case _ =>
        }
      }
      case variable: ScVariable => {
        buffer.append("var ")
        buffer.append(field.name)
        field match {
          case typed: ScTyped => {
            val typez = typed.calcType
            if (typez != null) buffer.append(": " + ScType.presentableText(typez))
          }
          case _ =>
        }
        variable match {
          case d: ScVariableDefinition => {
            buffer.append(" = ")
            buffer.append(getOneLine(d.expr.getText))
          }
          case _ =>
        }
      }
    }
    buffer.toString
  }

  def generateTypeAliasInfo(alias: ScTypeAlias): String = {
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(alias))
    buffer.append("type ")
    buffer.append(alias.name)
    appendTypeParams(alias, buffer)
    alias match {
      case d: ScTypeAliasDefinition => {
        buffer.append(" = ")
        buffer.append(ScType.presentableText(d.aliasedType(Set[ScNamedElement]()).resType))
      }
      case _ =>
    }
    buffer.toString
  }

  def generateParameterInfo(parameter: ScParameter): String = {
    parameter match {
      case clParameter: ScClassParameter => {
        val clazz = PsiTreeUtil.getParentOfType(clParameter, classOf[ScTypeDefinition])
        clazz.getName + " " + clazz.getPresentation.getLocationString + "\n" +
        (if (clParameter.isVal) "val " else if (clParameter.isVar) "var " else "") + clParameter.name +
        ": " + ScType.presentableText(clParameter.calcType)
      }
      case _ => parameter.name + ": " + ScType.presentableText(parameter.calcType)
    }
  }
}