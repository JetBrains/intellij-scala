package org.jetbrains.plugins.scala
package editor.documentationProvider

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.codeInsight.javadoc.JavaDocUtil
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.base.{ScConstructor, ScAccessModifier, ScPrimaryConstructor}
import lang.psi.api.expr.{ScAnnotation}
import lang.psi.api.ScalaFile
import lang.psi.api.statements._
import lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause}
import lang.psi.api.toplevel._
import lang.psi.api.toplevel.templates.{ScTemplateParents, ScExtendsBlock, ScTemplateBody}
import lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.Any

import lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.structureView.StructureViewUtil
import lang.psi.types.result.{Failure, Success, TypingContext}
import lang.psi.types._

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.11.2008
 */

class ScalaDocumentationProvider extends DocumentationProvider {
  import ScalaDocumentationProvider._
  def getDocumentationElementForLookupItem(psiManager: PsiManager, `object` : Object, element: PsiElement): PsiElement = {
    null
  }

  def getUrlFor(element: PsiElement, originalElement: PsiElement): java.util.List[String] = {
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
    if (!element.getContainingFile.isInstanceOf[ScalaFile]) return null
    var e = getDocedElement(element).getNavigationElement
    e match {
      case clazz: ScTypeDefinition => {
        val buffer: StringBuilder = new StringBuilder("")
        val qualName = clazz.getQualifiedName
        val pack = {
          val lastIndexOf = qualName.lastIndexOf(".")
          if (lastIndexOf >= 0) qualName.substring(0, lastIndexOf) else ""
        }

        if (pack != "") buffer.append("<font size=\"-1\"><b>" + pack + "</b></font>")


        buffer.append("<PRE>")
        buffer.append(parseAnnotations(clazz, ScType.urlText(_)))
        val start = buffer.length
        buffer.append(parseModifiers(clazz))
        buffer.append(clazz match {
          case _: ScClass => "class "
          case _: ScObject => "object "
          case _: ScTrait => "trait "
        })
        buffer.append("<b>" + clazz.name + "</b>")
        buffer.append(parseTypeParameters(clazz))
        val end = buffer.length
        clazz match {
          case par: ScParameterOwner => buffer.append(parseParameters(par, ScType.urlText(_), end - start - 7))
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
        buffer.append(parseAnnotations(fun, ScType.urlText(_)))
        val start = buffer.length
        buffer.append(parseModifiers(fun))
        buffer.append("def ")
        buffer.append("<b>" + fun.name + "</b>")
        buffer.append(parseTypeParameters(fun))
        val end = buffer.length
        buffer.append(parseParameters(fun, ScType.urlText(_), end - start - 7))
        buffer.append(parseType(fun, ScType.urlText(_)))
        buffer.append("</PRE>")
        buffer.append(parseDocComment(fun))
        return "<html><body>" + buffer.toString + "</body></html>"
      }
      case decl: ScDeclaredElementsHolder if decl.isInstanceOf[ScValue] || decl.isInstanceOf[ScVariable] => {
        val buffer: StringBuilder = new StringBuilder("")
        decl match {case decl: ScMember => buffer.append(parseClassUrl(decl)) case _ =>}
        buffer.append("<PRE>")
        decl match {case an: ScAnnotationsHolder => buffer.append(parseAnnotations(an, ScType.urlText(_))) case _ =>}
        decl match {case m: ScModifierListOwner => buffer.append(parseModifiers(m)) case _ =>}
        buffer.append(decl match {case _: ScValue => "val " case _: ScVariable => "var " case _ => ""})
        buffer.append("<b>" + (element match {case named: ScNamedElement => named.name case _ => "unknown"}) + "</b>")
        buffer.append(element match {case typed: ScTypedDefinition => parseType(typed, ScType.urlText(_)) case _ => ": Nothing"} )
        buffer.append("</PRE>")
        decl match {case doc: ScDocCommentOwner => buffer.append(parseDocComment(doc)) case _ =>}
        return "<html><body>" + buffer.toString + "</body></html>"
      }
      case param: ScParameter => {
        val buffer: StringBuilder = new StringBuilder("")
        buffer.append("<PRE>")
        buffer.append(parseAnnotations(param, ScType.urlText(_)))
        param match {case cl: ScClassParameter => buffer.append(parseModifiers(cl)) case _ => }
        buffer.append(param match {
          case c: ScClassParameter if c.isVal => "val "
          case c: ScClassParameter if c.isVar => "var "
          case _ => ""
        })
        buffer.append("<b>" + param.name + "</b>")
        buffer.append(parseType(param, ScType.urlText(_)))
        return "<html><body>" + buffer.toString + "</body></html>"
      }
      case typez: ScTypeAlias => {
        val buffer: StringBuilder = new StringBuilder("")
        buffer.append(parseClassUrl(typez))

        buffer.append("<PRE>")
        buffer.append(parseAnnotations(typez, ScType.urlText(_)))
        buffer.append(parseModifiers(typez))
        buffer.append("type <b>" + typez.name + "</b>")
        typez match {
          case definition: ScTypeAliasDefinition =>
            buffer.append(" = " + ScType.urlText(definition.aliasedTypeElement.calcType))
          case _ =>
        }
        buffer.append("</PRE>")
        buffer.append(parseDocComment(typez))
        return "<html><body>" + buffer.toString + "</body></html>"
      }
      case _ =>
    }
    null
  }
}

object ScalaDocumentationProvider {
  def parseType(elem: ScTypedDefinition, typeToString: ScType => String): String = {
    val buffer: StringBuilder = new StringBuilder(": ")
    val typez = elem match {
      case fun: ScFunction => fun.returnType.getOrElse(Any)
      case _ => elem.getType(TypingContext.empty).getOrElse(Any)
    }
    buffer.append(typeToString(typez))
    return buffer.toString
  }
  private def parseClassUrl(elem: ScMember): String = {
    val clazz = elem.getContainingClass
    if (clazz == null) return ""
    return "<a href=\"psi_element://" + clazz.getQualifiedName + "\"><code>" + clazz.getQualifiedName + "</code></a>"
  }

  private def parseParameters(elem: ScParameterOwner, typeToString: ScType => String, spaces: Int): String = {
    elem.allClauses.map(parseParameterClause(_, typeToString, spaces)).mkString("\n")
  }

  private def parseParameterClause(elem: ScParameterClause, typeToString: ScType => String, spaces: Int): String = {
    val buffer: StringBuilder = new StringBuilder(" ")
    for (i <- 1 to spaces) buffer.append(" ")
    val separator = if (spaces < 0) ", " else ",\n" + buffer
    elem.parameters.map(parseParameter(_, typeToString)).mkString(if (elem.isImplicit) "(implicit " else "(", separator, ")")
  }

  def parseParameter(param: ScParameter, typeToString: ScType => String): String = {
    val buffer: StringBuilder = new StringBuilder("")
    buffer.append(parseAnnotations(param, typeToString))
    param match {case cl: ScClassParameter => buffer.append(parseModifiers(cl)) case _ =>}
    buffer.append(param match {
      case c: ScClassParameter if c.isVal => "val "
      case c: ScClassParameter if c.isVar => "var "
      case _ => ""
    })
    buffer.append(param.name)
    buffer.append(parseType(param, typeToString))
    if (param.isRepeatedParameter) buffer.append("*")
    if (param.isDefaultParam) {
      buffer.append(" = ")
      param.getDefaultExpression match {
        case Some(expr) => {
          val text: String = expr.getText
          val cutTo = 20
          buffer.append(text.substring(0, text.length.min(cutTo)))
          if (text.length > cutTo) buffer.append("...")
        }
        case None => buffer.append("...")
      }
    }
    buffer.toString
  }

  private def parseTypeParameters(elems: ScTypeParametersOwner): String = {
    val typeParameters = elems.typeParameters
    if (typeParameters.length > 0)typeParameters.map(_.name).mkString("[", ", ", "]")
    else ""
  }

  private def parseExtendsBlock(elem: ScExtendsBlock): String = {
    val buffer: StringBuilder = new StringBuilder("extends ")
    elem.templateParents match {
      case Some(x: ScTemplateParents) => {
        val seq = x.typeElements
        buffer.append(ScType.urlText(seq(0).getType(TypingContext.empty).getOrElse(Any)) + "\n")
        for (i <- 1 to seq.length - 1) buffer append " with " + ScType.urlText(seq(i).getType(TypingContext.empty).getOrElse(Any))
      }
      case None => {
        buffer.append("<a href=\"psi_element://scala.ScalaObject\"><code>ScalaObject</code></a>")
        if (elem.isUnderCaseClass) {
          buffer.append("<a href=\"psi_element://scala.Product\"><code>Product</code></a>")
        }
      }
    }

    return buffer.toString
  }

  private def parseModifiers(elem: ScModifierListOwner): String = {
    val buffer: StringBuilder = new StringBuilder("")
    def accessQualifier(x: ScAccessModifier): String = (x.getReference match {
      case null => ""
      case ref => ref.resolve match {
        case clazz: PsiClass => "[<a href=\"psi_element://" +
                clazz.getQualifiedName + "\"><code>" +
                (x.id match {case Some(x) => x.getText case None => ""}) + "</code></a>]"
        case pack: PsiPackage => "[" + pack.getQualifiedName + "]"
        case _ => x.id match {case Some(x) => "[" + x.getText + "]" case None => ""}
      }
    }) + " "

    buffer.append(elem.getModifierList.accessModifier match {
      case Some(x: ScAccessModifier) => x.access match {
        case x.Access.PRIVATE => "private" + accessQualifier(x)
        case x.Access.PROTECTED => "protected" + accessQualifier(x)
        case x.Access.THIS_PRIVATE => "private[this] "
        case x.Access.THIS_PROTECTED => "protected[this] "
      }
      case None => ""
    })
    val modifiers = Array("abstract", "final", "sealed", "implicit", "lazy", "override")
    for (modifier <- modifiers if elem.hasModifierProperty(modifier)) buffer.append(modifier + " ")
    return buffer.toString
  }

  private def parseAnnotations(elem: ScAnnotationsHolder, typeToString: ScType => String): String = {
    val buffer: StringBuilder = new StringBuilder("")
    def parseAnnotation(elem: ScAnnotation): String = {
      var s = "@"
      val constr: ScConstructor = elem.constructor
      val attributes = elem.attributes
      s += typeToString(constr.typeElement.getType(TypingContext.empty).getOrElse(Any))
      if (attributes.length > 0) {
        val array = attributes.map("val " + _.name)
        s += array.mkString("{","; ","}")
      }
      return s
    }
    for (ann <- elem.annotations) {
      buffer.append(parseAnnotation(ann) + "\n")
    }
    return buffer.toString
  }

  
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
          case typed: ScTypedDefinition => {
            val typez = typed.getType(TypingContext.empty).getOrElse(Any)
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
          case typed: ScTypedDefinition => {
            val typez = typed.getType(TypingContext.empty).getOrElse(Any)
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
        val ttype = d.aliasedType(TypingContext.empty) match {
          case Success(t, _) => t
          case Failure(_, _) => Any
        }
        buffer.append(ScType.presentableText(ttype))
      }
      case _ =>
    }
    buffer.toString
  }

  def generateParameterInfo(parameter: ScParameter): String = {
    (parameter match {
      case clParameter: ScClassParameter => {
        val clazz = PsiTreeUtil.getParentOfType(clParameter, classOf[ScTypeDefinition])
        clazz.getName + " " + clazz.getPresentation.getLocationString + "\n" +
                (if (clParameter.isVal) "val " else if (clParameter.isVar) "var " else "") + clParameter.name +
                ": " + ScType.presentableText(clParameter.getType(TypingContext.empty).getOrElse(Any))
      }
      case _ => parameter.name + ": " + ScType.presentableText(parameter.getType(TypingContext.empty).getOrElse(Any))
    }) + (if (parameter.isRepeatedParameter) "*" else "")
  }
}