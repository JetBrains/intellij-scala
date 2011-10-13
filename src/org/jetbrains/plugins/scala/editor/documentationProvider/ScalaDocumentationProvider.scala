package org.jetbrains.plugins.scala
package editor.documentationProvider

import com.intellij.codeInsight.javadoc.JavaDocUtil
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi._
import lang.psi.api.expr.ScAnnotation
import lang.psi.api.ScalaFile
import lang.psi.api.statements._
import lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause}
import lang.psi.api.toplevel._
import lang.psi.api.toplevel.templates.{ScTemplateParents, ScExtendsBlock, ScTemplateBody}
import lang.psi.api.toplevel.typedef._
import org.apache.commons.lang.StringEscapeUtils.escapeHtml

import org.jetbrains.plugins.scala.lang.structureView.StructureViewUtil
import lang.psi.types.result.{Failure, Success, TypingContext}
import lang.psi.types._
import util.{MethodSignatureBackedByPsiMethod, PsiTreeUtil}
import search.searches.SuperMethodsSearch
import com.intellij.openapi.project.IndexNotReadyException
import lang.psi.{PresentationUtil, ScalaPsiUtil}
import lang.resolve.ResolveUtils.ScalaLookupObject
import lang.psi.api.base.patterns.ScBindingPattern
import lang.psi.api.base.{ScReferenceElement, ScConstructor, ScAccessModifier, ScPrimaryConstructor}
import lang.resolve.ScalaResolveResult

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.11.2008
 */

class ScalaDocumentationProvider extends DocumentationProvider {
  import ScalaDocumentationProvider._
  def getDocumentationElementForLookupItem(psiManager: PsiManager, `object` : Object,
                                           element: PsiElement): PsiElement = {
    `object` match {
      case (_, element: PsiElement, _) => element
      case ScalaLookupObject(element: PsiElement, _, _) => element
      case element: PsiElement => element
      case _ => null
    }
  }

  def getUrlFor(element: PsiElement, originalElement: PsiElement): java.util.List[String] = {
    null
  }

  def getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement): String = {
    val substitutor = originalElement match {
      case ref: ScReferenceElement =>
        ref.bind() match {
          case Some(ScalaResolveResult(_, subst)) => subst
          case _ => ScSubstitutor.empty
        }
      case _ => ScSubstitutor.empty
    }
    element match {
      case clazz: ScTypeDefinition => generateClassInfo(clazz, substitutor)
      case function: ScFunction => generateFunctionInfo(function, substitutor)
      case value: ScNamedElement if ScalaPsiUtil.nameContext(value).isInstanceOf[ScValue]
              || ScalaPsiUtil.nameContext(value).isInstanceOf[ScVariable] => generateValueInfo(value, substitutor)
      case alias: ScTypeAlias => generateTypeAliasInfo(alias, substitutor)
      case parameter: ScParameter => generateParameterInfo(parameter, substitutor)
      case b: ScBindingPattern => generateBindingPatternInfo(b, substitutor)
      case _ => null
    }
  }

  def getDocumentationElementForLink(psiManager: PsiManager, link: String, context: PsiElement): PsiElement = {
    JavaDocUtil.findReferenceTarget(psiManager, link, context)
  }

  def generateDoc(element: PsiElement, originalElement: PsiElement): String = {
    if (!element.getContainingFile.isInstanceOf[ScalaFile]) return null
    val e = getDocedElement(element).getNavigationElement
    e match {
      case clazz: ScTypeDefinition => {
        val buffer: StringBuilder = new StringBuilder("")
        val qualName = clazz.getQualifiedName
        val pack = {
          val lastIndexOf = qualName.lastIndexOf(".")
          if (lastIndexOf >= 0) qualName.substring(0, lastIndexOf) else ""
        }

        if (pack != "") buffer.append("<font size=\"-1\"><b>" + escapeHtml(pack) + "</b></font>")


        buffer.append("<PRE>")
        buffer.append(parseAnnotations(clazz, ScType.urlText(_)))
        val start = buffer.length
        buffer.append(parseModifiers(clazz))
        buffer.append(clazz match {
          case _: ScClass => "class "
          case _: ScObject => "object "
          case _: ScTrait => "trait "
        })
        buffer.append("<b>" + escapeHtml(clazz.name) + "</b>")
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
        buffer.append("<b>" + escapeHtml(fun.name) + "</b>")
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
        buffer.append("<b>" + (element match {
          case named: ScNamedElement => escapeHtml(named.name) case _ => "unknown"
        }) + "</b>")
        buffer.append(element match {
          case typed: ScTypedDefinition => parseType(typed, ScType.urlText(_)) case _ => ": Nothing"
        } )
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
        buffer.append("<b>" + escapeHtml(param.name) + "</b>")
        buffer.append(parseType(param, ScType.urlText(_)))
        return "<html><body>" + buffer.toString + "</body></html>"
      }
      case typez: ScTypeAlias => {
        val buffer: StringBuilder = new StringBuilder("")
        buffer.append(parseClassUrl(typez))

        buffer.append("<PRE>")
        buffer.append(parseAnnotations(typez, ScType.urlText(_)))
        buffer.append(parseModifiers(typez))
        buffer.append("type <b>" + escapeHtml(typez.name) + "</b>")
        typez match {
          case definition: ScTypeAliasDefinition =>
            buffer.append(" = " +
                    ScType.urlText(definition.aliasedTypeElement.getType(TypingContext.empty).getOrAny))
          case _ =>
        }
        buffer.append("</PRE>")
        buffer.append(parseDocComment(typez))
        return "<html><body>" + buffer.toString + "</body></html>"
      }
      case pattern: ScBindingPattern =>
        val buffer: StringBuilder = new StringBuilder("")
        buffer.append("<PRE>")
        buffer.append("Pattern: ")
        buffer.append("<b>" + escapeHtml(pattern.name) + "</b>")
        buffer.append(parseType(pattern, ScType.urlText(_)))
        return "<html><body>" + buffer.toString + "</body></html>"
      case _ =>
    }
    null
  }
}

object ScalaDocumentationProvider {
  def parseType(elem: ScTypedDefinition, typeToString: ScType => String): String = {
    val buffer: StringBuilder = new StringBuilder(": ")
    val typez = elem match {
      case fun: ScFunction => fun.returnType.getOrAny
      case _ => elem.getType(TypingContext.empty).getOrAny
    }
    buffer.append(typeToString(typez))
    buffer.toString()
  }
  private def parseClassUrl(elem: ScMember): String = {
    val clazz = elem.getContainingClass
    if (clazz == null) return ""
    "<a href=\"psi_element://" + escapeHtml(clazz.getQualifiedName) + "\"><code>" +
      escapeHtml(clazz.getQualifiedName) + "</code></a>"
  }

  private def parseParameters(elem: ScParameterOwner, typeToString: ScType => String, spaces: Int): String = {
    elem.allClauses.map(parseParameterClause(_, typeToString, spaces)).mkString("\n")
  }

  private def parseParameterClause(elem: ScParameterClause, typeToString: ScType => String, spaces: Int): String = {
    val buffer: StringBuilder = new StringBuilder(" ")
    for (i <- 1 to spaces) buffer.append(" ")
    val separator = if (spaces < 0) ", " else ",\n" + buffer
    elem.parameters.map(parseParameter(_, typeToString)).
      mkString(if (elem.isImplicit) "(implicit " else "(", separator, ")")
  }

  def parseParameter(param: ScParameter, typeToString: ScType => String): String = {
    val buffer: StringBuilder = new StringBuilder("")
    buffer.append(parseAnnotations(param, typeToString, ' '))
    param match {case cl: ScClassParameter => buffer.append(parseModifiers(cl)) case _ =>}
    buffer.append(param match {
      case c: ScClassParameter if c.isVal => "val "
      case c: ScClassParameter if c.isVar => "var "
      case _ => ""
    })
    buffer.append(escapeHtml(param.name))

    buffer.append(parseType(param, t => {
      (if (param.isCallByNameParameter) "=> " else "") + typeToString(t)
    }))
    if (param.isRepeatedParameter) buffer.append("*")
    if (param.isDefaultParam) {
      buffer.append(" = ")
      param.getDefaultExpression match {
        case Some(expr) => {
          val text: String = expr.getText.replace(" /* compiled code */ ", "")
          val cutTo = 20
          buffer.append(text.substring(0, text.length.min(cutTo)))
          if (text.length > cutTo) buffer.append("...")
        }
        case None => buffer.append("...")
      }
    }
    buffer.toString()
  }

  private def parseTypeParameters(elems: ScTypeParametersOwner): String = {
    val typeParameters = elems.typeParameters
    // todo hyperlink identifiers in type bounds
    if (typeParameters.length > 0)
      escapeHtml(typeParameters.map(PresentationUtil.presentationString(_)).mkString("[", ", ", "]"))
    else ""
  }

  private def parseExtendsBlock(elem: ScExtendsBlock): String = {
    val buffer: StringBuilder = new StringBuilder("extends ")
    elem.templateParents match {
      case Some(x: ScTemplateParents) => {
        val seq = x.typeElements
        buffer.append(ScType.urlText(seq(0).getType(TypingContext.empty).getOrAny) + "\n")
        for (i <- 1 to seq.length - 1)
          buffer append " with " + ScType.urlText(seq(i).getType(TypingContext.empty).getOrAny)
      }
      case None => {
        buffer.append("<a href=\"psi_element://scala.ScalaObject\"><code>ScalaObject</code></a>")
        if (elem.isUnderCaseClass) {
          buffer.append("<a href=\"psi_element://scala.Product\"><code>Product</code></a>")
        }
      }
    }

    buffer.toString
  }

  private def parseModifiers(elem: ScModifierListOwner): String = {
    val buffer: StringBuilder = new StringBuilder("")
    def accessQualifier(x: ScAccessModifier): String = (x.getReference match {
      case null => ""
      case ref => ref.resolve match {
        case clazz: PsiClass => "[<a href=\"psi_element://" +
                escapeHtml(clazz.getQualifiedName) + "\"><code>" +
                (x.idText match {case Some(text) => text case None => ""}) + "</code></a>]"
        case pack: PsiPackage => "[" + escapeHtml(pack.getQualifiedName) + "]"
        case _ => x.idText match {case Some(text) => "[" + text + "]" case None => ""}
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
    buffer.toString
  }

  private def parseAnnotations(elem: ScAnnotationsHolder, typeToString: ScType => String,
                               sep: Char = '\n'): String = {
    val buffer: StringBuilder = new StringBuilder("")
    def parseAnnotation(elem: ScAnnotation): String = {
      var s = "@"
      val constr: ScConstructor = elem.constructor
      val attributes = elem.attributes
      s += typeToString(constr.typeElement.getType(TypingContext.empty).getOrAny)
      if (attributes.length > 0) {
        val array = attributes.map {
          ne: ScNamedElement => "val " + escapeHtml(ne.name)
        }
        s += array.mkString("{","; ","}")
      }
      s
    }
    for (ann <- elem.annotations) {
      buffer.append(parseAnnotation(ann) + sep)
    }
    buffer.toString
  }


  private def parseDocComment(elem: PsiDocCommentOwner, withDescription: Boolean = false): String = {
    def getParams(fun: ScFunction): String = {
      fun.parameters.map((param: ScParameter) => "int     " + escapeHtml(param.name)).mkString("(", ",\n", ")")
    }
    val comment = elem.getDocComment match {case null => None case x => Some(x)}
    comment match {
      case Some(x) => {
        val text = elem match {
          case _: ScTypeDefinition => x.getText + "\nclass A {\n }"
          case f: ScFunction => {
            "class A {\n" + x.getText + "\npublic int f" + getParams(f) + " {}\n}"
          }
          case m: PsiMethod => {
            "class A {\n" + m.getText + "\n}"
          }
          case _ => x.getText + "\nclass A"
        }
        val dummyFile: PsiJavaFile = PsiFileFactory.getInstance(elem.getProject).
                createFileFromText("dummy" + ".java", text).asInstanceOf[PsiJavaFile]
        val javadoc: String = elem match {
          case _: ScTypeDefinition => JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0))
          case _: ScFunction =>
            JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0).getAllMethods.apply(0))
          case _: PsiMethod =>
            JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0).getAllMethods.apply(0))
          case _ => JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0))
        }
        val (s1, s2) = elem.getContainingClass match {
          case e: PsiClass if withDescription => ("<b>Description copied from class: </b><a href=\"psi_element://" +
                  escapeHtml(e.getQualifiedName) + "\"><code>" + escapeHtml(e.getName) + "</code></a><p>", "</p>")
          case _ => ("", "")
        }
        s1 + (elem match {
          case _: ScTypeDefinition =>
            val i = javadoc.indexOf("</PRE>")
            javadoc.substring(i + 6, javadoc.length - 14)
          case f: ScFunction => {
            val i = javadoc.indexOf("</PRE>")
            javadoc.substring(i + 6, javadoc.length - 14)
          }
          case m: PsiMethod => {
            val i = javadoc.indexOf("</PRE>")
            javadoc.substring(i + 6, javadoc.length - 14)
          }
          case _ => javadoc.substring(110, javadoc.length - 14)
        }) + s2
      }
      case None => {
        elem match {
          case fun: ScFunction => {
            fun.superMethod match {
              case Some(fun: PsiMethod) => {
                fun.getNavigationElement match {
                  case fun: PsiMethod =>
                    parseDocComment(fun, true)
                  case _ =>
                    parseDocComment(fun, true)
                }
              }
              case _ => ""
            }
          }
          case method: PsiMethod => {
            var superSignature: MethodSignatureBackedByPsiMethod = null
            try {
              superSignature = SuperMethodsSearch.search(method, null, true, false).findFirst
            }
            catch {
              case e: IndexNotReadyException => {
              }
            }
            if (superSignature == null) return ""

            val meth = superSignature.getMethod
            meth.getNavigationElement match {
              case fun: PsiMethod =>
                parseDocComment(fun, true)
              case _ =>
                parseDocComment(meth, true)
            }
          }
          case _ => ""
        }
      }
    }
  }

  private def getDocedElement(originalElement: PsiElement): PsiElement = {
    originalElement match {
      case null => null
      case _: ScTypeDefinition | _: ScTypeAlias | _: ScValue
              | _: ScVariable | _: ScFunction | _: ScParameter | _: ScBindingPattern => originalElement
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

  def generateClassInfo(clazz: ScTypeDefinition, subst: ScSubstitutor): String = {
    val buffer = new StringBuilder
    val module = ModuleUtil.findModuleForPsiElement(clazz)
    if (module != null) {
      buffer.append('[').append(module.getName).append("] ")
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
          case Some(x: ScPrimaryConstructor) =>
            buffer.append(StructureViewUtil.getParametersAsString(x.parameterList, false, subst))
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
        buffer.append(ScType.presentableText(subst.subst(types(i))))
      }
    }
    buffer.toString()
  }

  def generateFunctionInfo(function: ScFunction, subst: ScSubstitutor): String = {
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(function))
    val list = function.getModifierList
    if (list != null) {
      buffer.append(ScalaPsiUtil.getModifiersPresentableText(list))
    }
    buffer.append("def ")
    buffer.append(ScalaPsiUtil.getMethodPresentableText(function, subst))
    buffer.toString()
  }

  def generateValueInfo(field: ScNamedElement, subst: ScSubstitutor): String = {
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
            val typez = subst.subst(typed.getType(TypingContext.empty).getOrAny)
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
            val typez = subst.subst(typed.getType(TypingContext.empty).getOrAny)
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
    buffer.toString()
  }

  def generateBindingPatternInfo(binding: ScBindingPattern, subst: ScSubstitutor): String = {
    val buffer = new StringBuilder
    buffer.append("Pattern: ")
    buffer.append(binding.name)
    val typez = subst.subst(subst.subst(binding.getType(TypingContext.empty).getOrAny))
    if (typez != null) buffer.append(": " + ScType.presentableText(typez))

    buffer.toString()
  }

  def generateTypeAliasInfo(alias: ScTypeAlias, subst: ScSubstitutor): String = {
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(alias))
    buffer.append("type ")
    buffer.append(alias.name)
    appendTypeParams(alias, buffer)
    alias match {
      case d: ScTypeAliasDefinition => {
        buffer.append(" = ")
        val ttype = subst.subst(d.aliasedType(TypingContext.empty) match {
          case Success(t, _) => t
          case Failure(_, _) => Any
        })
        buffer.append(ScType.presentableText(ttype))
      }
      case _ =>
    }
    buffer.toString()
  }

  def generateParameterInfo(parameter: ScParameter, subst: ScSubstitutor): String = {
    (parameter match {
      case clParameter: ScClassParameter => {
        val clazz = PsiTreeUtil.getParentOfType(clParameter, classOf[ScTypeDefinition])
        clazz.getName + " " + clazz.getPresentation.getLocationString + "\n" +
                (if (clParameter.isVal) "val " else if (clParameter.isVar) "var " else "") + clParameter.name +
                ": " + ScType.presentableText(subst.subst(clParameter.getType(TypingContext.empty).getOrAny))
      }
      case _ => parameter.name + ": " +
        ScType.presentableText(subst.subst(parameter.getType(TypingContext.empty).getOrAny))}) +
        (if (parameter.isRepeatedParameter) "*" else "")
  }
}