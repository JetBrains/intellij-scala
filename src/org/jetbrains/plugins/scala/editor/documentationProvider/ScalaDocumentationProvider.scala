package org.jetbrains.plugins.scala
package editor.documentationProvider

import com.intellij.codeInsight.javadoc.JavaDocUtil
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi._
import javadoc.{PsiDocTag, PsiDocComment}
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
import lang.psi.api.base.{ScReferenceElement, ScConstructor, ScAccessModifier, ScPrimaryConstructor}
import lang.resolve.ScalaResolveResult
import lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.extensions
import lang.scaladoc.lexer.ScalaDocTokenType
import lang.scaladoc.parser.parsing.MyScaladocParsing
import lang.scaladoc.psi.api.{ScDocTag, ScDocComment}
import lang.psi.api.base.patterns.ScBindingPattern
import com.intellij.lang.documentation.CodeDocumentationProvider
import java.lang.String
import collection.mutable.HashMap
import lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.extensions.{toPsiMemberExt, toPsiNamedElementExt, toPsiClassExt}

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.11.2008
 */

class ScalaDocumentationProvider extends CodeDocumentationProvider {
  import ScalaDocumentationProvider._
  def getDocumentationElementForLookupItem(psiManager: PsiManager, obj : Object,
                                           element: PsiElement): PsiElement = {
    obj match {
      case (_, element: PsiElement, _) => element
      case el: ScalaLookupItem => el.element
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
    val docedElement = getDocedElement(element)
    if (docedElement == null) return null
    val e = docedElement.getNavigationElement
    e match {
      case clazz: ScTypeDefinition => {
        val buffer: StringBuilder = new StringBuilder("")
        val qualName = clazz.qualifiedName
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
        if (pattern.getContext != null)
          pattern.getContext.getContext match {
            case co: PsiDocCommentOwner => buffer.append(parseDocComment(co, false))
            case _ =>
          }
        return "<html><body>" + buffer.toString + "</body></html>"
      case _ =>
    }
    null
  }

  def findExistingDocComment(contextElement: PsiComment): PsiComment = {
    if (contextElement.isInstanceOf[ScDocComment]) {
      val commentOwner = contextElement.asInstanceOf[ScDocComment].getOwner
      if (commentOwner != null) {
        return commentOwner.getDocComment
      }
    }

    null
  }

  def generateDocumentationContentStub(contextComment: PsiComment): String = {
    if (!contextComment.isInstanceOf[ScDocComment]) {
      return ""
    }

    val comment = contextComment.asInstanceOf[ScDocComment]
    val commentOwner = comment.getOwner
    val buffer = new StringBuilder("")
    val leadingAsterisks = "* "
    
    val inheritedParams = HashMap.apply[String, PsiDocTag]()
    val inheritedTParams = HashMap.apply[String, PsiDocTag]()

    import MyScaladocParsing._

    def registerInheritedParam(allParams: HashMap[String, PsiDocTag], param: PsiDocTag) {
      if (!allParams.contains(param.getValueElement.getText)) {
        allParams.put(param.getValueElement.getText, param)
      }
    }

    def processProbablyJavaDocCommentWithOwner(owner: PsiDocCommentOwner) {
      owner.getDocComment match {
        case scalaComment: ScDocComment =>
          for (docTag <- scalaComment.findTagsByName(Set(PARAM_TAG, TYPE_PARAM_TAG).contains( _ ))) {
            docTag.name match {
              case PARAM_TAG => registerInheritedParam(inheritedParams, docTag)
              case TYPE_PARAM_TAG => registerInheritedParam(inheritedTParams, docTag)
            }
          }
        case javaComment: PsiDocComment =>
          for (paramTag <- javaComment.findTagsByName("param")) {
            if (paramTag.getValueElement.getText.startsWith("<")) {
              registerInheritedParam(inheritedTParams, paramTag)
            } else {
              registerInheritedParam(inheritedParams, paramTag)
            }
          }
        case _ =>
      }
    }

    def processParams(owner: ScParameterOwner) {
      for (param <- owner.parameters) {
        if (inheritedParams.contains(param.name)) {
          val paramText = inheritedParams.get(param.name).get.getText
          buffer.append(leadingAsterisks).append(paramText.substring(0, paramText.lastIndexOf("\n") + 1))
        } else {
          buffer.append(leadingAsterisks).append(PARAM_TAG).append(" ").append(param.name).append("\n")
        }
      }
    }

    def processTypeParams(owner: ScTypeParametersOwner) {
      for (tparam <- owner.typeParameters) {
        if (inheritedTParams.contains(tparam.name)) {
          val paramText = inheritedTParams.get(tparam.name).get.getText
          buffer.append(leadingAsterisks).append(paramText.substring(0, paramText.lastIndexOf("\n") + 1))
        } else if (inheritedTParams.contains("<" + tparam +">")) {
          val paramTag = inheritedTParams.get("<" + tparam.name + ">").get
          val descriptionText =
            paramTag.getText.substring(paramTag.getValueElement.getTextOffset + paramTag.getValueElement.getTextLength)
          val parameterName = paramTag.getValueElement.getText

          buffer.append(leadingAsterisks).append("@").append(paramTag.name).append(" ").
                  append(parameterName.substring(1, parameterName.length - 1)).append(" ").
                  append(descriptionText.substring(0, descriptionText.lastIndexOf("\n") + 1))
        } else {
          buffer.append(leadingAsterisks).append(TYPE_PARAM_TAG).append(" ").append(tparam.name).append("\n")
        }
      }
    }
    
    commentOwner match {
      case clazz: ScClass =>
        val parents = clazz.getSupers
        
        for (parent <- parents) {
          processProbablyJavaDocCommentWithOwner(parent)
        }
        
        processParams(clazz)
        processTypeParams(clazz)
      case function: ScFunction =>
        val parents = function.findSuperMethods()
        var returnTag: String = null
        val needReturnTag = function.getReturnType != null && function.getReturnType.getCanonicalText != "void"
        
        for (parent <- parents) {
          processProbablyJavaDocCommentWithOwner(parent)

          if (needReturnTag) {
            var inherRetTag: PsiDocTag = null
            val a = parent
            parent.getDocComment match {
              case scComment: ScDocComment =>
                inherRetTag = scComment.findTagByName("@return")
              case comment: PsiDocComment =>
                inherRetTag = comment.findTagByName("return")
              case _ =>
            }
            if (inherRetTag != null) {
              returnTag = inherRetTag.getText.substring(0, inherRetTag.getText.lastIndexOf("\n") + 1)
            }
          }
        }
        
        processParams(function)
        processTypeParams(function)

        for (annotation <- function.annotations if annotation.annotationExpr.getText.startsWith("throws")) {
          buffer.append(leadingAsterisks).append(MyScaladocParsing.THROWS_TAG).append(" ")
          annotation.constructor.args.foreach( a =>
            a.exprs match {
              case exprHead :: _ =>
                exprHead.getType(TypingContext.empty) match {
                  case Success(head, _) =>
                    head match {
                      case ScParameterizedType(_, args) =>
                        args.headOption match {
                          case a: Some[ScType] =>
                            ScType.extractClass(a.get, Option(function.getProject)) match {
                              case Some(clazz) => buffer append clazz.qualifiedName
                              case _ =>
                            }
                          case _ =>
                        }
                      case _ =>
                    }
                  case _ =>
                }
              case _ =>
            }
          )

          buffer.append(" \n")
        }

        if (returnTag != null) {
          buffer.append(leadingAsterisks).append(returnTag)
        } else if (needReturnTag) {
          buffer.append(leadingAsterisks).append(MyScaladocParsing.RETURN_TAG).append(" \n")
        }
      case scType: ScTypeAlias =>
        val parents = ScalaPsiUtil.superTypeMembers(scType)
        for (parent <- parents if parent.isInstanceOf[ScTypeAlias]) {
          processProbablyJavaDocCommentWithOwner(parent.asInstanceOf[ScTypeAlias])
        }
        processTypeParams(scType)
      case traitt: ScTrait =>
        val parents = traitt.getSupers

        for (parent <- parents) {
          processProbablyJavaDocCommentWithOwner(parent)
        }
        processTypeParams(traitt)
      case _ =>
    }

    buffer.toString()
  }
}

object ScalaDocumentationProvider {
  val replaceWikiScheme = Map("__" -> "u>", "'''" -> "b>", "''" -> "i>", "`" -> "tt>", ",," -> "sub>", "^" -> "sup>")

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
    val clazz = elem.containingClass
    if (clazz == null) return ""
    "<a href=\"psi_element://" + escapeHtml(clazz.qualifiedName) + "\"><code>" +
      escapeHtml(clazz.qualifiedName) + "</code></a>"
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

  def parseParameter(param: ScParameter, typeToString: ScType => String, escape: Boolean = true): String = {
    val buffer: StringBuilder = new StringBuilder("")
    buffer.append(parseAnnotations(param, typeToString, ' ', escape))
    param match {case cl: ScClassParameter => buffer.append(parseModifiers(cl)) case _ =>}
    buffer.append(param match {
      case c: ScClassParameter if c.isVal => "val "
      case c: ScClassParameter if c.isVar => "var "
      case _ => ""
    })
    buffer.append(if (escape) escapeHtml(param.name) else param.name)

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
      case null => ""             case ref => ref.resolve match {
        case clazz: PsiClass => "[<a href=\"psi_element://" +
                escapeHtml(clazz.qualifiedName) + "\"><code>" +
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
    for (modifier <- modifiers if elem.hasModifierPropertyScala(modifier)) buffer.append(modifier + " ")
    buffer.toString()
  }

  private def parseAnnotations(elem: ScAnnotationsHolder, typeToString: ScType => String,
                               sep: Char = '\n', escape: Boolean = true): String = {
    val buffer: StringBuilder = new StringBuilder("")
    def parseAnnotation(elem: ScAnnotation): String = {
      var res = new StringBuilder("@")
      val constr: ScConstructor = elem.constructor
      res.append(typeToString(constr.typeElement.getType(TypingContext.empty).getOrAny))
      res.toString()
    }
    for (ann <- elem.annotations) {
      buffer.append(parseAnnotation(ann) + sep)
    }
    buffer.toString()
  }


  private def parseDocComment(elem: PsiDocCommentOwner, withDescription: Boolean = false): String = {
    def getParams(fun: ScParameterOwner): String = {
      fun.parameters.map((param: ScParameter) => "int     " + escapeHtml(param.name)).mkString("(", ",\n", ")")
    }
    def getTypeParams(fun: ScTypeParametersOwner): String = {
      if (fun.typeParameters.length > 0) {
        fun.typeParameters.map(param => escapeHtml(param.name)).mkString("<", " , ", ">")
      } else {
        ""
      }
    }
    val comment = elem.getDocComment match {case null => None case x => Some(x)}
    comment match {
      case Some(y) => {
        val x = replaceWikiWithTags(y)
        val text = elem match {
          case clazz: ScClass =>
            "\nclass A {\n " + x.getText + " \npublic " + getTypeParams(clazz) + "void f" +
                    getParams(clazz) + " {\n}\n}"
          case typeAlias: ScTypeAlias => x.getText + "\n class A" + getTypeParams(typeAlias) + " {}"
          case _: ScTypeDefinition => x.getText + "\nclass A {\n }"
          case f: ScFunction => {
            "class A {\n" + x.getText + "\npublic " + getTypeParams(f) + "int f" + getParams(f) + " {}\n}"
          }
          case m: PsiMethod => {
            "class A {\n" + m.getText + "\n}"
          }
          case _ => x.getText + "\nclass A"
        }
        val dummyFile: PsiJavaFile = PsiFileFactory.getInstance(elem.getProject).
                createFileFromText("dummy" + ".java", text).asInstanceOf[PsiJavaFile]
        val javadoc: String = elem match {
          case _: ScFunction | _: ScClass =>
            JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0).getAllMethods.apply(0))
          case _: ScTypeDefinition | _: ScTypeAlias =>
            JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0))
          case _: PsiMethod =>
            JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0).getAllMethods.apply(0))
          case _ => JavaDocumentationProvider.generateExternalJavadoc(dummyFile.getClasses.apply(0))
        }
        val (s1, s2) = elem.containingClass match {
          case e: PsiClass if withDescription => ("<b>Description copied from class: </b><a href=\"psi_element://" +
                  escapeHtml(e.qualifiedName) + "\"><code>" + escapeHtml(e.name) + "</code></a><p>", "</p>")
          case _ => ("", "")
        }
        s1 + (elem match {
          case _: ScFunction | _: ScTypeAlias | _: PsiMethod | _: ScTypeDefinition | _: ScPatternDefinition => {
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

  private def replaceWikiWithTags(comment: PsiDocComment): PsiDocComment = {
    if (!comment.isInstanceOf[ScDocComment]) return comment
    val commentBody = new StringBuilder("")
    val tagsPart = new StringBuilder("")
    var isFirst = true

    def visitTags(element: ScDocTag) {
      element.name match {
        case MyScaladocParsing.TODO_TAG | MyScaladocParsing.NOTE_TAG | MyScaladocParsing.EXAMPLE_TAG =>
          if (isFirst) {
            commentBody.append("<br/><br/>")
            isFirst = false
          }
          element.getNode.getChildren(null).foreach(node => visitElementInner(node.getPsi))
          commentBody.append("<br/><br/>")
        case MyScaladocParsing.SEE_TAG =>
          element.getNode.getChildren(null).foreach(node => visitElementInner(node.getPsi, commentBody))
          commentBody.append("</dl>")
        case _ =>
          element.getNode.getChildren(null).foreach(node => visitElementInner(node.getPsi, tagsPart))
      }
    }

    def visitElementInner(element: PsiElement, result: StringBuilder = commentBody) {
      if (element.getFirstChild == null) {
        element.getNode.getElementType match {
          case ScalaDocTokenType.DOC_TAG_NAME =>
            element.getText match {
              case MyScaladocParsing.TYPE_PARAM_TAG => result.append("@param ")
              case MyScaladocParsing.NOTE_TAG | MyScaladocParsing.TODO_TAG | MyScaladocParsing.EXAMPLE_TAG => 
                result.append("<b>").append(element.getText.substring(1).capitalize).append(":</b><br/>")
              case MyScaladocParsing.SEE_TAG => result.append("<dl><dt><b>See Also:</b></dt>")
              case _ => result.append(element.getText)
            }
          case ScalaDocTokenType.DOC_TAG_VALUE_TOKEN
            if element.getParent.getParent.getFirstChild.getText == MyScaladocParsing.TYPE_PARAM_TAG  =>
              result.append("<" + element.getText + ">")
          case ScalaDocTokenType.DOC_INNER_CODE_TAG => result.append(" <pre> {@code ")
          case ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG => result.append(" } </pre> ")
          case ScalaDocTokenType.VALID_DOC_HEADER =>
            val headerSize = if (element.getText.length() <= 6) element.getText.length() else 6
            result.append("<h" + headerSize + ">")
          case ScalaDocTokenType.DOC_HEADER => 
            if (element.getParent.getFirstChild.getNode.getElementType == ScalaDocTokenType.VALID_DOC_HEADER) {
              val headerSize = if (element.getText.length() <= 6) element.getText.length() else 6
              result.append("</h" + headerSize + ">")
            } else {
              result.append(element.getText)
            }
          case ScalaDocTokenType.DOC_HTTP_LINK_TAG => 
            result.append("<a href=\"")
          case ScalaDocTokenType.DOC_LINK_TAG => result.append("{@link ")
          case ScalaDocTokenType.DOC_LINK_CLOSE_TAG => 
            if (element.getParent.getNode.getFirstChildNode.getElementType == ScalaDocTokenType.DOC_HTTP_LINK_TAG) {
              val linkText = element.getPrevSibling.getText
              if (linkText.trim().contains(" ")) {
                val trimmedText = linkText.trim()
                val spaceIndex = trimmedText.indexOf(" ")
                result.append(trimmedText.substring(0, spaceIndex)).append("\">").append(trimmedText.substring(spaceIndex + 1)).append("</a>")
              } else {
                result.append("\">" + linkText + "</a>")
              }
            } else {
              result.append("}")
            }
          case ScalaDocTokenType.DOC_COMMENT_DATA if element.getParent.isInstanceOf[ScDocTag] &&
                  element.getParent.asInstanceOf[ScDocTag].name == MyScaladocParsing.SEE_TAG =>
            result.append("<dd>").append(element.getText.trim()).append("</dd>")
          case ScalaDocTokenType.DOC_COMMENT_DATA
            if element.getPrevSibling != null && element.getPrevSibling.getNode.getElementType == ScalaDocTokenType.DOC_HTTP_LINK_TAG =>
            if (!element.getText.trim().contains(" ")) {
              result.append(element.getText)
            }
          case _ if replaceWikiScheme.contains(element.getText) &&
                  (element.getParent.getFirstChild == element || element.getParent.getLastChild == element) =>
            val prefix =  if (element.getParent.getFirstChild == element) "<" else "</"
            result.append(prefix + replaceWikiScheme.get(element.getText).get)
          case _ if element.getParent.getLastChild == element &&                 // do not swap this & last cases
                  replaceWikiScheme.contains(element.getParent.getFirstChild.getText) => 
            result.append(element.getText).append("</")
            result.append(replaceWikiScheme.get(element.getParent.getFirstChild.getText).get)
          case ScalaDocTokenType.DOC_COMMENT_END => tagsPart.append(element.getText)
          case _ => result.append(element.getText)
        }
      } else {
        for (child <- element.getNode.getChildren(null)) {
          child.getPsi match {
            case tag: ScDocTag => visitTags(tag)
            case _ => visitElementInner(child.getPsi, result)
          }
        }
      }
    }

    visitElementInner(comment)
    val scalaComment = ScalaPsiElementFactory.createScalaFile(commentBody.append("<br/>\n").
            append(tagsPart).toString() + " class a {}", comment.getManager).typeDefinitions(0).getDocComment

    scalaComment
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
    member.containingClass.name + " " + member.containingClass.getPresentation.getLocationString + "\n"
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
            d.expr.foreach(it => buffer.append(getOneLine(it.getText)))
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
            d.expr.foreach(it => buffer.append(getOneLine(it.getText)))
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
        clazz.name + " " + clazz.getPresentation.getLocationString + "\n" +
                (if (clParameter.isVal) "val " else if (clParameter.isVar) "var " else "") + clParameter.name +
                ": " + ScType.presentableText(subst.subst(clParameter.getType(TypingContext.empty).getOrAny))
      }
      case _ => parameter.name + ": " +
        ScType.presentableText(subst.subst(parameter.getType(TypingContext.empty).getOrAny))}) +
        (if (parameter.isRepeatedParameter) "*" else "")
  }
}