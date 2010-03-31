package org.jetbrains.plugins.scala
package lang
package psi
package impl

import api.base.{ScIdList, ScPatternList, ScStableCodeReferenceElement}
import api.ScalaFile
import api.toplevel.packaging.ScPackaging
import api.toplevel.templates.{ScTemplateBody}
import api.toplevel.typedef.{ScTypeDefinition, ScMember}
import api.toplevel.{ScNamedElement, ScTypedDefinition}
import com.intellij.lang.{PsiBuilderFactory, PsiBuilder, ASTNode}
import com.intellij.psi.impl.compiled.ClsParameterImpl
import api.statements._
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import collection.mutable.HashSet
import com.intellij.psi.impl.source.tree.{TreeElement, FileElement}
import com.intellij.psi.impl.source.DummyHolderFactory
import formatting.settings.ScalaCodeStyleSettings
import lexer.{ScalaLexer}
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.scala.lang.psi.api.base.types._

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import parser.parsing.statements.{Dcl, Def}
import types._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import refactoring.util.{ScTypeUtil, ScalaNamesUtil}

object ScalaPsiElementFactory extends ScTypeInferenceHelper {

  private val DUMMY = "dummy."

  /*def createMethodFromVariable(variable: ScVariable): PsiMethod = {
    val clazz = variable.getContainingClass
    if (clazz == null || !clazz.isInstanceOf[ScTypeDefinition]) return null
    val text = "package " + clazz.asInstanceOf[ScTypeDefinition].getPath + "\n" +
            "class " +
  } */

  def createWildcardNode(manager: PsiManager): ASTNode = {
    val text = "import a._"

    val dummyFile: ScalaFile =
      PsiFileFactory.getInstance(manager.getProject).
              createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.getLastChild.getLastChild.getLastChild.getNode
  }

  def createMethodFromText(text: String, manager: PsiManager): ScFunction = {
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.getFirstChild.asInstanceOf[ScFunction]
  }

  def createExpressionFromText(buffer: String, manager: PsiManager): ScExpression = {
    val text = "class a {val b = (" + buffer + ")}"

    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val classDef = dummyFile.typeDefinitions()(0)
    val p = classDef.members()(0).asInstanceOf[ScPatternDefinition]
    p.expr match {
      case x: ScParenthesisedExpr => x.expr match {
        case Some(y) => y
        case _ => x
      }
      case x => x
    }
  }


  def createOptionExpressionFromText(text: String, manager: PsiManager): Option[ScExpression] = {
    val dummyFile: ScalaFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text.trim).asInstanceOf[ScalaFile]
    val child = dummyFile.getFirstChild
    child match {
      case expr: ScExpression => if (expr.getNextSibling == null && !PsiTreeUtil.hasErrorElements(dummyFile)) Some(expr) else None
      case _ => None
    }
  }

  def createIdentifier(name: String, manager: PsiManager): ASTNode = {
    val text = "package " + (if (!ScalaNamesUtil.isKeyword(name)) name else "`" + name + "`")
    val dummyFile = PsiFileFactory.getInstance(manager.getProject()).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    return dummyFile.getNode.getLastChildNode.getLastChildNode.getLastChildNode
  }

  def createModifierFromText(name: String, manager: PsiManager): ASTNode = {
    val text = name + " class a"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject()).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    return dummyFile.typeDefinitions.apply(0).getModifierList.getFirstChild.getNode
  }

  def createImportExprFromText(name: String, manager: PsiManager): ScImportExpr = {
    val text = "import " + name
    val dummyFile = PsiFileFactory.getInstance(manager.getProject()).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.getLastChild.getLastChild.asInstanceOf[ScImportExpr]
  }

  def createImportFromText(text: String, manager: PsiManager): ScImportStmt = {
    val dummyFile: ScalaFile = PsiFileFactory.getInstance(manager.getProject()).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    return dummyFile.getFirstChild.asInstanceOf[ScImportStmt]
  }

  def createReferenceFromText(name: String, manager: PsiManager): ScStableCodeReferenceElement = {
    val text = "import " + name
    val dummyFile = PsiFileFactory.getInstance(manager.getProject()).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val imp: ScImportStmt = dummyFile.getFirstChild.asInstanceOf[ScImportStmt]
    val expr: ScImportExpr = imp.importExprs.apply(0)
    val ref = expr.reference match {case Some(x) => x case None => return null}
    return ref
  }

  def createImportStatementFromClass(holder: ScImportsHolder, clazz: PsiClass, manager: PsiManager): ScImportStmt = {
    val qualifiedName = clazz.getQualifiedName
    val packageName = holder match {
      case packaging: ScPackaging => packaging.getPackageName
      case _ => {
        var element: PsiElement = holder
        while (element != null && !element.isInstanceOf[ScalaFile] && !element.isInstanceOf[ScPackaging]) element = element.getParent
        element match {
          case packaging: ScPackaging => packaging.getPackageName
          case _ => null
        }
      }
    }
    val name = getShortName(qualifiedName, packageName)
    val text = "import " + (if (isResolved(name, clazz, packageName, manager)) name else "_root_." + qualifiedName)
    val dummyFile = PsiFileFactory.getInstance(manager.getProject()).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.getImportStatements.firstOption match {
      case Some(x) => return x
      case None => {
        //cannot be
        return null
      }
    }
  }

  def createBigImportStmt(expr: ScImportExpr, exprs: Array[ScImportExpr], manager: PsiManager): ScImportStmt = {
    val qualifier = expr.qualifier.getText
    var text = "import " + qualifier
    val names = new HashSet[String]
    names ++= expr.getNames
    for (expr <- exprs) names ++= expr.getNames
    if ((names("_") ||
            CodeStyleSettingsManager.getSettings(manager.getProject).
                    getCustomSettings(classOf[ScalaCodeStyleSettings]).CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND <= names.size) &&
            names.filter(_.indexOf("=>") != -1).toSeq.size == 0) text = text + "._"
    else {
      text = text + ".{"
      for (string <- names) {
        text = text + string
        text = text + ", "
      }
      text = text.substring(0, text.length - 2)
      text = text + "}"
    }
    val dummyFile = PsiFileFactory.getInstance(manager.getProject()).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.getImportStatements.firstOption match {
      case Some(x) => return x
      case None => {
        //cannot be
        return null
      }
    }
  }

  def createScalaFile(text: String, manager: PsiManager): ScalaFile =
    PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]

  def createStableReferenceElement(name: String, manager: PsiManager) = {
    val file = createScalaFile("class A extends B with " + name, manager)
    val classDef = file.typeDefinitions()(0)
    val extendsBlock = classDef.extendsBlock
    val parents = extendsBlock.templateParents
    (parents: @unchecked) match {
      case Some(p) => {
        val elements = p.typeElements
        (elements.first.asInstanceOf[ScSimpleTypeElement].reference: @unchecked) match {case Some(r) => r}
      }
      case _ => throw new com.intellij.util.IncorrectOperationException()
    }
  }
  def createDeclaration(typez: ScType, name: String, isVariable: Boolean,
                        expr: ScExpression, manager: PsiManager): ScMember = {
    createDeclaration(typez, name, isVariable, expr.getText, manager)
  }
  def createDeclaration(typez: ScType, name: String, isVariable: Boolean,
                        exprText: String, manager: PsiManager): ScMember = {
    val text = "class a {" + (if (isVariable) "var " else "val ") +
              name + (if (typez != null && ScType.canonicalText(typez) != "") ": "  +
            ScType.canonicalText(typez) else "") + " = " + exprText + "}"
    val dummyFile = createScalaFile(text, manager)
    val classDef = dummyFile.typeDefinitions()(0)
    if (!isVariable) classDef.members()(0).asInstanceOf[ScValue]
    else classDef.members()(0).asInstanceOf[ScVariable]
  }

  def createEnumerator(name: String, expr: ScExpression, manager: PsiManager): ScEnumerator = {
    val text = "for {\n  i <- 1 to 239\n  " + name + " = " + expr.getText + "\n}"
    val dummyFile = createScalaFile(text, manager)
    val forStmt: ScForStatement = dummyFile.getFirstChild.asInstanceOf[ScForStatement]
    forStmt.enumerators.getOrElse(null).enumerators.apply(0)
  }

  def createNewLineNode(manager: PsiManager): ASTNode = createNewLineNode(manager, "\n")
  def createNewLineNode(manager: PsiManager, text: String): ASTNode = {
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    return dummyFile.getNode.getFirstChildNode
  }

  def createBlockFromExpr(expr: ScExpression, manager: PsiManager): ScExpression = {
    val text = "class a {\nval b = {\n" + expr.getText + "\n}\n}"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val classDef = dummyFile.typeDefinitions()(0)
    val p = classDef.members()(0).asInstanceOf[ScPatternDefinition]
    p.expr
  }

  def createBodyFromMember(element: PsiElement, manager: PsiManager): ScTemplateBody = {
    val text = "class a {\n" + element.getText + "}"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject()).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val classDef: ScTypeDefinition = dummyFile.typeDefinitions()(0)
    val body = classDef.extendsBlock.templateBody match {
      case Some(x) => x
      case None => return null
    }
    return body
  }

  def createOverrideImplementMethod(sign: PhysicalSignature, manager: PsiManager, isOverride: Boolean,
                                   needsInferType: Boolean): ScFunction = {
    val text = "class a {\n  " + getOverrideImplementSign(sign, "null", isOverride, needsInferType) + "\n}"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject()).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val classDef = dummyFile.typeDefinitions()(0)
    val function = classDef.functions()(0)
    return function
  }

  def createOverrideImplementType(alias: ScTypeAlias, substitutor: ScSubstitutor, manager: PsiManager,
                                  isOverride: Boolean): ScTypeAlias = {
    val text = "class a {" + getOverrideImplementTypeSign(alias, substitutor, "this.type", isOverride) + "}"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val classDef = dummyFile.typeDefinitions()(0)
    val al = classDef.aliases()(0)
    return al
  }

  def createOverrideImplementVariable(variable: ScTypedDefinition, substitutor: ScSubstitutor, manager: PsiManager,
                                      isOverride: Boolean, isVal: Boolean, needsInferType: Boolean): ScMember = {
    val text = "class a {" + getOverrideImplementVariableSign(variable, substitutor, "_", isOverride, isVal, needsInferType) + "}"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject()).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val classDef = dummyFile.typeDefinitions()(0)
    classDef.members()(0) match {case member : ScMember => member}
  }

  def createSemicolon(manager: PsiManager): PsiElement = {
    val text = ";"
    val dummyFile = createScalaFile(text, manager)
    dummyFile.findElementAt(0)
  }

  private def isResolved(name: String, clazz: PsiClass, packageName: String, manager: PsiManager): Boolean = {
    if (packageName == null) return true
    val text = "package " + packageName + "\nimport " + name
    val dummyFile = PsiFileFactory.getInstance(manager.getProject()).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val imp: ScStableCodeReferenceElement = (dummyFile.getImportStatements.firstOption match {
      case Some(x) => x
      case None =>
        //cannot be
        null
    }).importExprs(0).reference match {
      case Some(x) => x
      case None => return false
    }
    imp.resolve match {
      case x: PsiClass => {
        return x.getQualifiedName == clazz.getQualifiedName
      }
      case _ => return false
    }
  }

  private def getOverrideImplementSign(sign: PhysicalSignature, defaultBody: String, isOverride: Boolean,
                                       needsInferType: Boolean): String = {
    var body = defaultBody
    var res = ""
    val method = sign.method
    // do not substitute aliases
    val substitutor = sign.substitutor
    method match {
      case method: ScFunction => {
        body = getStandardValue(substitutor subst method.getType(TypingContext.empty).getOrElse(Any))
        res = res + method.getFirstChild.getText
        if (res != "") res = res + "\n"
        if (!method.getModifierList.hasModifierProperty("override") && isOverride) res = res + "override "
        res = res + method.getModifierList.getText + " "
        res = res + "def " + method.getName
        //adding type parameters
        if (method.typeParameters.length > 0) {
          def get(typeParam: ScTypeParam): String = {
            var res: String = ""
            res += (if (typeParam.isContravariant) "-" else if (typeParam.isCovariant) "+" else "")
            res += typeParam.getName
            typeParam.lowerBound foreach {
              case psi.types.Nothing =>
              case x => res =  res + " >: " + ScType.canonicalText(substitutor.subst(x))
            }
            typeParam.upperBound foreach {
              case psi.types.Any =>
              case x => res = res + " <: " + ScType.canonicalText(substitutor.subst(x))
            }
            typeParam.viewBound foreach {
              x => res = res + " <% " + ScType.canonicalText(substitutor.subst(x))
            }
            typeParam.contextBound foreach {
              (tp: ScType) => res = res + " : " + ScType.canonicalText(ScTypeUtil.stripTypeArgs(substitutor.subst(tp)))
            }
            return res
          }
          val strings = (for (t <- method.typeParameters) yield get(t))
          res += strings.mkString("[", ", ", "]")
        }
        if (method.paramClauses != null) {
          for (paramClause <- method.paramClauses.clauses) {
            def get(param: ScParameter): String = {
              var res: String = param.getName
              param.typeElement foreach {
                x => res = res + ": " + ScType.canonicalText(substitutor.subst(x.getType(TypingContext.empty).getOrElse(Any)))
              }
              return res
            }
            val strings = (for (t <- paramClause.parameters) yield get(t))
            res += strings.mkString(if (paramClause.isImplicit) "(implicit " else "(", ", ", ")")
          }
        }
        if (needsInferType) {
          method.returnTypeElement foreach {
            x => res = res + ": " + ScType.canonicalText(substitutor.subst(x.getType(TypingContext.empty).getOrElse(Any)))
          }
        }
        res = res + " = "
        res = res + body
      }
      case _ => {
        body = getStandardValue(substitutor subst ScType.create(method.getReturnType, method.getProject))
        var hasOverride = false
        if (method.getModifierList.getNode != null)
        //todo!!! add appropriate readPSI to get all modifiers
        for (modifier <- method.getModifierList.getNode.getChildren(null); m = modifier.getText) {
          m match {
            case "override" => hasOverride = true
            case "protected" => res = res + "protected "
            case "final" => res = res + "final "
            case _ =>
          }
        }
        if (isOverride && !hasOverride) res = res + "override "
        res = res + "def " + changeKeyword(method.getName)
        if (method.hasTypeParameters) {
          val params = method.getTypeParameters
          val strings = for (param <- params) yield {
            var res = ""
            val par: PsiTypeParameter = param
            res = par.getName
            val types = par.getExtendsListTypes
            if (types.length > 0) {
              res += " <: "
              val map: Iterable[String] = types.map((t: PsiClassType) =>
                  ScType.canonicalText(substitutor.subst(ScType.create(t, method.getProject))))
              res += map.mkString(" with ")
            }
            res
          }
          res = res + strings.mkString("[", ", ", "]")
        }
        res = res + (if (method.getParameterList.getParametersCount == 0) "" else "(")
        for (param <- method.getParameterList.getParameters) {
          //todo: create
          val paramName = param.getName match {
            case null => param match {case param: ClsParameterImpl => param.getStub.getName case _ => null}
            case x => x
          }
          res = res + changeKeyword(paramName) + ": "
          val scType: ScType = substitutor.subst(ScType.create(param.getTypeElement.getType, method.getProject))
          var text = ScType.canonicalText(scType)
          if (text == "_root_.java.lang.Object") text = "Any"
          res = res + text + ", "
        }
        if (method.getParameterList.getParametersCount != 0) res = res.substring(0, res.length - 2)
        res = res + (if (method.getParameterList.getParametersCount == 0) "" else ")")
        if (needsInferType) {
          var text = ScType.canonicalText(substitutor.subst(ScType.create(method.getReturnType, method.getProject)))
          if (text == "_root_.java.lang.Object") text = "AnyRef"
          res = res + ": " + text
        }
        res = res + " = " + body
      }
    }
    return res
  }

  private def changeKeyword(s: String): String = {
    if (ScalaNamesUtil.isKeyword(s)) return "`" + s + "`"
    else return s
  }

  def getOverrideImplementTypeSign(alias: ScTypeAlias, substitutor: ScSubstitutor, body: String,
                                   isOverride: Boolean): String = {
    try {
      alias match {
        case alias: ScTypeAliasDefinition => {
          return "override type " + alias.getName + " = " +
                  ScType.canonicalText(substitutor.subst(alias.aliasedType(TypingContext.empty).getOrElse(Any)))
        }
        case alias: ScTypeAliasDeclaration => {
          return "type " + alias.getName + " = " + body
        }
      }
    }
    catch {
      case e: Exception => e.printStackTrace()
      return ""
    }
  }

  def getOverrideImplementVariableSign(variable: ScTypedDefinition, substitutor: ScSubstitutor, body: String, isOverride: Boolean,
                                       isVal: Boolean, needsInferType: Boolean): String = {
    var res = ""
    if (isOverride) res = res + "override "
    res = res + (if (isVal) "val " else "var ")
    res = res + variable.name
    if (needsInferType && ScType.canonicalText(substitutor.subst(variable.getType(TypingContext.empty).getOrElse(Any))) != "")
      res = res + ": " + ScType.canonicalText(substitutor.subst(variable.getType(TypingContext.empty).getOrElse(Any)))
    res = res + " = " + body
    return res
  }

  private def getShortName(qualifiedName: String, packageName: String): String = {
    if (packageName == null) return qualifiedName
    val qArray = qualifiedName.split("[.]")
    val pArray = packageName.split("[.]")
    var i = 0
    while (i < qArray.length - 1 && i < pArray.length && qArray(i) == pArray(i)) i = i + 1
    var res = ""
    while (i < qArray.length) {
      res = res + qArray(i)
      res = res + "."
      i = i + 1
    }
    return res.substring(0, res.length - 1)
  }

  private def getStandardValue(typez: ScType): String = {
    typez match {
      case ValType("Unit") => "{}"
      case ValType("Boolean") => "false"
      case ValType("Char" | "Int" | "Byte") => "0"
      case ValType("Long") => "0L"
      case ValType("Float" | "Double") => "0.0"
      case ScDesignatorType(c: PsiClass) if c.getQualifiedName == "java.lang.String" => "\"\""
      case _ => "null"
    }
  }

  def createTypeFromText(text: String, context: PsiElement): ScType = {
    val te = createTypeElementFromText(text, context)
    if (te == null) return null
    else return te.getType(TypingContext.empty).getOrElse(Any)
  }

  def createReferenceFromText(text: String, context: PsiElement): ScStableCodeReferenceElement = {
    val holder: FileElement = DummyHolderFactory.createHolder(context.getManager, context).getTreeElement
    val builder: PsiBuilder = PsiBuilderFactory.getInstance.createBuilder(context.getProject, holder,
        new ScalaLexer, ScalaFileType.SCALA_LANGUAGE, text)
    StableId.parse(builder, ScalaElementTypes.REFERENCE)
    val node = builder.getTreeBuilt
    holder.rawAddChildren(node.asInstanceOf[TreeElement])
    val psi = node.getPsi
    if (psi.isInstanceOf[ScStableCodeReferenceElement]) {
      val referenceElement = psi.asInstanceOf[ScStableCodeReferenceElement]
      referenceElement.asInstanceOf[ScalaPsiElement].setContext(context)
      return referenceElement
    } else return null
  }

  def createExpressionWithContextFromText(text: String, context: PsiElement): ScExpression = {
    val holder: FileElement = DummyHolderFactory.createHolder(context.getManager, context).getTreeElement
    val builder: PsiBuilder = PsiBuilderFactory.getInstance.createBuilder(context.getProject, holder,
      new ScalaLexer, ScalaFileType.SCALA_LANGUAGE, "def a = " + text)
    Def.parse(builder)
    val node = builder.getTreeBuilt
    holder.rawAddChildren(node.asInstanceOf[TreeElement])
    val psi = node.getPsi
    if (psi.isInstanceOf[ScFunctionDefinition]) {
      val fun = psi.asInstanceOf[ScFunctionDefinition]
      val expr = fun.body.get
      expr.setContext(context)
      return expr
    } else return null
  }

  def createTypeElementFromText(text: String, context: PsiElement): ScTypeElement = {
    val holder: FileElement = DummyHolderFactory.createHolder(context.getManager, context).getTreeElement
    val builder: PsiBuilder = PsiBuilderFactory.getInstance.createBuilder(context.getProject, holder,
        new ScalaLexer, ScalaFileType.SCALA_LANGUAGE, text)
    Type.parse(builder)
    val node = builder.getTreeBuilt
    holder.rawAddChildren(node.asInstanceOf[TreeElement])
    val psi = node.getPsi
    if (psi.isInstanceOf[ScTypeElement]) {
      psi.asInstanceOf[ScalaPsiElement].setContext(context)
      psi.asInstanceOf[ScTypeElement]
    } else null
  }

  def createPatterListFromText(text: String, context: PsiElement): ScPatternList = {
    val holder: FileElement = DummyHolderFactory.createHolder(context.getManager, context).getTreeElement
    val builder: PsiBuilder = PsiBuilderFactory.getInstance.createBuilder(context.getProject, holder,
        new ScalaLexer, ScalaFileType.SCALA_LANGUAGE, "val " + text + " = 239")
    Def.parse(builder)
    val node = builder.getTreeBuilt
    holder.rawAddChildren(node.asInstanceOf[TreeElement])
    val psi = node.getPsi
    if (psi.isInstanceOf[ScPatternDefinition]) {
      val pList: ScPatternList = psi.asInstanceOf[ScPatternDefinition].pList
      pList.asInstanceOf[ScalaPsiElement].setContext(context)
      pList
    } else null
  }

  def createIdsListFromText(text: String, context: PsiElement): ScIdList = {
    val holder: FileElement = DummyHolderFactory.createHolder(context.getManager, context).getTreeElement
    val builder: PsiBuilder = PsiBuilderFactory.getInstance.createBuilder(context.getProject, holder,
        new ScalaLexer, ScalaFileType.SCALA_LANGUAGE, "val " + text + " : Int")
    Dcl.parse(builder)
    val node = builder.getTreeBuilt
    holder.rawAddChildren(node.asInstanceOf[TreeElement])
    val psi = node.getPsi
    if (psi.isInstanceOf[ScPatternDefinition]) {
      val idList: ScIdList = psi.asInstanceOf[ScValueDeclaration].getIdList
      idList.asInstanceOf[ScalaPsiElement].setContext(context)
      idList
    } else null
  }
}
