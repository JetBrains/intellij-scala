package org.jetbrains.plugins.scala.editor.documentationProvider

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import _root_.scala.collection.immutable.HashSet
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.codeInsight.javadoc.JavaDocUtil
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.base.patterns.ScReferencePattern
import lang.psi.api.base.ScPrimaryConstructor
import lang.psi.api.ScalaFile
import lang.psi.api.statements._
import lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause, ScTypeParam}
import lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import lang.psi.api.toplevel.typedef._

import lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement, ScTyped, ScTypeParametersOwner}
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
    JavaDocUtil.findReferenceTarget(psiManager, link, context)
  }

  def generateDoc(element: PsiElement, originalElement: PsiElement): String = {
    var e = getDocedElement(element).getNavigationElement
    e match {
      case clazz: ScTypeDefinition => {
        val buffer: StringBuilder = new StringBuilder("")
        val qualName = clazz.getQualifiedName
        val pack = {
          val lastIndexOf = qualName.lastIndexOf(".", 0)
          if (lastIndexOf >= 0) qualName.substring(0, lastIndexOf) else ""
        }

        if (pack != "") buffer.append("<font size=\"-1\"><b>" + pack + "</b></font>")


        buffer.append("<PRE>")
        buffer.append(parseAnnotations(clazz))
        buffer.append(parseModifiers(clazz))
        buffer.append(clazz match {
          case _: ScClass => "class "
          case _: ScObject => "object "
          case _: ScTrait => "trait "
        })
        buffer.append("<b>" + clazz.name + "</b>")
        buffer.append(parseTypeParameters(clazz))
        clazz match {
          case par: ScParameterOwner => buffer.append(parseParameters(par))
          case _ =>
        }
        buffer.append("\n")
        buffer.append(parseExtendsBlock(clazz.extendsBlock))
        buffer.append("</PRE>")
        buffer.append(parseDocComment(clazz))

        return "<html><body>" + buffer.toString + "</body></html>"
      }
      case fun: ScFunction => {
        val buffer: StringBuilder = new StringBuilder("")
        buffer.append(parseClassUrl(fun))
        buffer.append("<PRE>")
        buffer.append(parseAnnotations(fun))
        buffer.append(parseModifiers(fun))
        buffer.append("def ")
        buffer.append("<b>" + fun.name + "</b>")
        buffer.append(parseTypeParameters(fun))
        buffer.append(parseParameters(fun))
        buffer.append(parseType(fun))
        buffer.append("</PRE>")
        buffer.append(parseDocComment(fun))
        return "<html><body>" + buffer.toString + "</body></html>"
      }
      case decl: ScDeclaredElementsHolder if decl.isInstanceOf[ScValue] || decl.isInstanceOf[ScVariable] => {
        val buffer: StringBuilder = new StringBuilder("")
        decl match {case decl: ScMember => buffer.append(parseClassUrl(decl)) case _ =>}
        buffer.append("<PRE>")
        decl match {case an: ScAnnotationsHolder => buffer.append(parseAnnotations(an)) case _ =>}
        decl match {case m: ScModifierListOwner => buffer.append(parseModifiers(m)) case _ =>}
        buffer.append(decl match {case _: ScValue => "val " case _: ScVariable => "var " case _ => ""})
        buffer.append("<b>" + (element match {case named: ScNamedElement => named.name case _ => "unknown"}) + "</b>")
        buffer.append(element match {case typed: ScTyped => parseType(typed) case _ => ": Nothing"} )
        buffer.append("</PRE>")
        decl match {case doc: ScDocCommentOwner => buffer.append(parseDocComment(doc)) case _ =>}
        return "<html><body>" + buffer.toString + "</body></html>"
      }
      case param: ScParameter => {
        val buffer: StringBuilder = new StringBuilder("")
        buffer.append("<PRE>")
        buffer.append(parseAnnotations(param))
        param match {case cl: ScClassParameter => buffer.append(parseModifiers(cl)) case _ => }
        buffer.append(param match {
          case c: ScClassParameter if c.isVal => "val "
          case c: ScClassParameter if c.isVar => "var "
          case _ => ""
        })
        buffer.append("<b>" + param.name + "</b>")
        buffer.append(parseType(param))
        return "<html><body>" + buffer.toString + "</body></html>"
      }
      case _: ScTypeAlias => {
        //tested code
        val codeText = """
         package test
         import com.sun.istack.internal.NotNull;

         @NotNull
         public class Test extends Object {
           public int x() {
             return 0;
           }
         }
         """
        val dummyFile: PsiJavaFile = PsiFileFactory.getInstance(element.getProject).
                createFileFromText("dummy" + ".java", codeText).asInstanceOf[PsiJavaFile]
        val javadoc = JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0)/*.getAllMethods.apply(0)*/)
        println("\n" + javadoc)
      }
      case _ =>
    }
    null
  }
}

private object ScalaDocumentationProvider {
  private def parseType(elem: ScTyped): String = "" 
  private def parseClassUrl(elem: ScMember): String = {
    val clazz = elem.getContainingClass
    if (clazz == null) return ""
    return "<a href=\"psi_element://" + clazz.getQualifiedName + "\"><code>" + clazz.getQualifiedName + "</code></a>"
  }

  private def parseParameters(elem: ScParameterOwner): String = {
    elem.allClauses.map(parseParameterClause(_)).mkString("")
  }

  private def parseParameterClause(elem: ScParameterClause): String = {
    elem.parameters.map(parseParameter(_)).mkString(if (elem.isImplicit) "(implicit " else "(", ", ", ")")
  }

  private def parseParameter(param: ScParameter): String = {
    val buffer: StringBuilder = new StringBuilder("")
    buffer.append(parseAnnotations(param))
    param match {case cl: ScClassParameter => buffer.append(parseModifiers(cl)) case _ =>}
    buffer.append(param match {
      case c: ScClassParameter if c.isVal => "val "
      case c: ScClassParameter if c.isVar => "var "
      case _ => ""
    })
    buffer.append(param.name)
    buffer.append(parseType(param))
    buffer.toString
  }

  private def parseTypeParameters(elems: ScTypeParametersOwner): String = ""

  private def parseExtendsBlock(elem: ScExtendsBlock): String = ""

  private def parseModifiers(elem: ScModifierListOwner): String = ""

  private def parseAnnotations(elem: ScAnnotationsHolder): String = ""

  
  private def parseDocComment(elem: ScDocCommentOwner): String = {
    def getParams(fun: ScFunction): String = {
      fun.parameters.map((param: ScParameter) => "int     " + param.name).mkString("(", ",\n", ")")
    }
    val comment = elem.docComment
    comment match {
      case Some(x) => {
        val text = elem match {
          case _: ScTypeDefinition => x.getText + "\nclass A"
          case f: ScFunction => {
            "class A {\n" + x.getText + "\npublic int f" + getParams(f) + " {}\n}"
          }
          case _ => x.getText + "\nclass A"
        }
        val dummyFile: PsiJavaFile = PsiFileFactory.getInstance(elem.getProject).
                createFileFromText("dummy" + ".java", text).asInstanceOf[PsiJavaFile]
        val javadoc: String = elem match {
          case _: ScTypeDefinition => JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0))
          case _: ScFunction => JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0).getAllMethods.apply(0))
          case _ => JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0))
        }
        return elem match {
          case _: ScTypeDefinition => javadoc.substring(110, javadoc.length - 14)
          case f: ScFunction => {
            val i = javadoc.indexOf("</PRE>")
            javadoc.substring(i + 6, javadoc.length - 14)
          }
          case _ => javadoc.substring(110, javadoc.length - 14)
        }
      }
      case None => return ""
    }
  }

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
    val list = function.getModifierList
    if (list != null) {
      buffer.append(ScalaPsiUtil.getModifiersPresentableText(list))
    }
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