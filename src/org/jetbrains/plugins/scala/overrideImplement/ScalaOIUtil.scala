package org.jetbrains.plugins.scala
package overrideImplement

import com.intellij.codeInsight.generation.ClassMember
import com.intellij.openapi.editor.Editor

import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil

import com.intellij.ui.NonFocusableCheckBox
import com.intellij.util.IncorrectOperationException
import com.intellij.psi._
import lang.psi.api.toplevel.typedef.{ScTrait, ScTypeDefinition, ScMember, ScTemplateDefinition}
import lang.psi.api.toplevel.ScTypedDefinition
import lang.psi.api.statements._
import lang.psi.impl.ScalaPsiElementFactory
import lang.psi.types.{ScType, PhysicalSignature, ScSubstitutor}
import lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.util.ScalaUtils
import com.intellij.ide.util.MemberChooser
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.openapi.project.Project
import settings.ScalaApplicationSettings
import lang.psi.types.result.{Failure, Success, TypingContext}
import javax.swing.{JComponent, JCheckBox}
import collection.immutable.HashSet
import extensions._
import org.jetbrains.plugins.scala.actions.ScalaFileTemplateUtil
import java.util.Properties
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager}
import lang.psi.api.expr.ScBlockExpr
import com.intellij.openapi.util.TextRange

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2008
 */

object ScalaOIUtil {
  def toMembers(candidates: Seq[Any]): Array[ClassMember] = {
    val classMembersBuf = new ArrayBuffer[ClassMember]
    for (candidate <- candidates) {
      candidate match {
        case sign: PhysicalSignature => {
          assert(sign.method.containingClass != null, "Containing Class is null: " + sign.method.getText)
          classMembersBuf += new ScMethodMember(sign)
        }
        case (name: PsiNamedElement, subst: ScSubstitutor) => {
          ScalaPsiUtil.nameContext(name) match {
            case x: ScValue => {
              assert(x.containingClass != null, "Containing Class is null: " + x.getText)
              name match {
                case y: ScTypedDefinition => classMembersBuf += new ScValueMember(x, y, subst)
                case _ => throw new IncorrectOperationException("Not supported type:" + x)
              }
            }
            case x: ScVariable => {
              assert(x.containingClass != null, "Containing Class is null: " + x.getText)
              name match {
                case y: ScTypedDefinition => classMembersBuf += new ScVariableMember(x, y, subst)
                case _ => throw new IncorrectOperationException("Not supported type:" + x)
              }
            }
            case x: ScTypeAlias => {
              assert(x.containingClass != null, "Containing Class is null: " + x.getText)
              classMembersBuf += new ScAliasMember(x, subst)
            }
            case x => throw new IncorrectOperationException("Not supported type:" + x)
          }
        }
        case x => throw new IncorrectOperationException("Not supported type:" + x)
      }
    }
    classMembersBuf.toArray
  }

  def invokeOverrideImplement(project: Project, editor: Editor, file: PsiFile, isImplement: Boolean) {
    val elem = file.findElementAt(editor.getCaretModel.getOffset - 1)
    def getParentClass(elem: PsiElement): PsiElement = {
      elem match {
        case _: ScTemplateDefinition | null => elem
        case _ => getParentClass(elem.getParent)
      }
    }
    val parent = getParentClass(elem)
    if (parent == null) return
    val clazz = parent.asInstanceOf[ScTemplateDefinition]
    val candidates = if (isImplement) getMembersToImplement(clazz) else getMembersToOverride(clazz)
    if (candidates.isEmpty) return
    val classMembers = toMembers(candidates)
    val dontInferReturnTypeCheckBox: JCheckBox = new NonFocusableCheckBox(
      ScalaBundle.message("specify.return.type.explicitly"))
    if (ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY != null)
      dontInferReturnTypeCheckBox.setSelected(ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY.booleanValue)
    class ScalaMemberChooser extends MemberChooser[ClassMember](classMembers, false, true, project,
      Array[JComponent](dontInferReturnTypeCheckBox)) {
      def needsInferType = dontInferReturnTypeCheckBox.isSelected
    }
    val chooser = new ScalaMemberChooser
    chooser.setTitle(if (isImplement) ScalaBundle.message("select.method.implement")
                     else ScalaBundle.message("select.method.override"))
    chooser.show()

    val selectedMembers = chooser.getSelectedElements
    if (selectedMembers == null || selectedMembers.size == 0) return
    val needsInferType = chooser.needsInferType
    ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY = needsInferType
    runAction(selectedMembers, isImplement, clazz, editor, needsInferType)
  }

  def runAction(selectedMembers: java.util.List[ClassMember],
               isImplement: Boolean, clazz: ScTemplateDefinition, editor: Editor, needsInferType: Boolean) {
    ScalaUtils.runWriteAction(new Runnable {
      def run() {
        def addUpdateThisType(subst: ScSubstitutor) = clazz.getType(TypingContext.empty) match {
          case Success(tpe, _) => subst.addUpdateThisType(tpe)
          case Failure(_, _) => subst
        }

        for (member <- selectedMembers.toArray(new Array[ClassMember](selectedMembers.size)).reverse) {
          val offset = editor.getCaretModel.getOffset
          val anchor = getAnchor(offset, clazz)
          member match {
            case member: ScMethodMember => {
              val method: PsiMethod = member.getElement
              val sign = member.sign.updateSubst(addUpdateThisType)
              val isAbstract = method match {
                case _: ScFunctionDeclaration => true
                case _: ScFunctionDefinition => false
                case _ => method.hasModifierProperty(PsiModifier.ABSTRACT)
              }

              val templateName = if (isAbstract) ScalaFileTemplateUtil.SCALA_IMPLEMENTED_METHOD_TEMPLATE else
                ScalaFileTemplateUtil.SCALA_OVERRIDDEN_METHOD_TEMPLATE

              val template = FileTemplateManager.getInstance().getCodeTemplate(templateName)

              val properties = new Properties()

              val returnType = method match {
                case fun: ScFunction => sign.substitutor.subst(fun.returnType.getOrAny)
                case method: PsiMethod =>
                  sign.substitutor.subst(ScType.create(Option(method.getReturnType).getOrElse(PsiType.VOID),
                    method.getProject, method.getResolveScope
                  ))
              }
              import org.jetbrains.plugins.scala.lang.psi.types.Unit
              val isUnitCall = returnType.equiv(Unit)
              properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, ScType.presentableText(returnType))
              properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE,
                ScalaPsiElementFactory.getStandardValue(returnType))
              properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, "super." + method.name + (method match {
                case fun: ScFunction =>
                  fun.paramClauses.clauses.map(_.parameters.map(_.name).mkString("(", ", ", ")")).mkString
                case method: PsiMethod =>
                  if (method.isAccessor) ""
                  else method.getParameterList.getParameters.map(_.name).mkString("(", ", ", ")")
              }))

              ScalaFileTemplateUtil.setClassAndMethodNameProperties(properties, method.containingClass, method)

              val body = template.getText(properties)

              val m = ScalaPsiElementFactory.createOverrideImplementMethod(sign, method.getManager,
                !isImplement, needsInferType, body)
              adjustTypesAndSetCaret(clazz.addMember(m, anchor), editor)
            }
            case member: ScAliasMember => {
              val alias = member.getElement
              val substitutor = addUpdateThisType(member.substitutor)
              val m = ScalaPsiElementFactory.createOverrideImplementType(alias, substitutor, alias.getManager, !isImplement)
              adjustTypesAndSetCaret(clazz.addMember(m, anchor), editor)
            }
            case _: ScValueMember | _: ScVariableMember => {
              val isVal = member match {case _: ScValueMember => true case _: ScVariableMember => false}
              val value = member match {case x: ScValueMember => x.element case x: ScVariableMember => x.element}
              val origSubstitutor = member match {
                case x: ScValueMember => x.substitutor
                case x: ScVariableMember => x.substitutor
              }
              val substitutor = addUpdateThisType(origSubstitutor)
              val m = ScalaPsiElementFactory.createOverrideImplementVariable(value, substitutor, value.getManager,
                !isImplement, isVal, needsInferType)
              adjustTypesAndSetCaret(clazz.addMember(m, anchor), editor)
            }
            case _ =>
          }
        }
      }
    }, clazz.getProject, if (isImplement) "Implement method" else "Override method")
  }

  def getMembersToImplement(clazz: ScTemplateDefinition, withOwn: Boolean = false): Seq[ScalaObject] = {
    val buf = new ArrayBuffer[ScalaObject]
    buf ++= clazz.allSignatures
    buf ++= clazz.allTypeAliases
    buf ++= clazz.allVals
    val buf2 = new ArrayBuffer[ScalaObject]
    for (element <- buf) {
      element match {
        case sign: PhysicalSignature => {
          val m = sign.method
          val name = if (m == null) "" else m.name
          m match {
            case _ if isProductAbstractMethod(m, clazz) =>
            case x if name == "$tag" || name == "$init$" =>
            case x if !withOwn && x.containingClass == clazz =>
            case x if x.containingClass != null && x.containingClass.isInterface &&
              !x.containingClass.isInstanceOf[ScTrait] => buf2 += sign
            case x if x.hasModifierPropertyScala("abstract") => buf2 += sign
            case x: ScFunctionDeclaration if x.hasAnnotation("scala.native") == None =>
              buf2 += sign
            case _ =>
          }
        }
        case (name: PsiNamedElement, subst: ScSubstitutor) => {
          ScalaPsiUtil.nameContext(name) match {
            case x: ScValueDeclaration if withOwn || x.containingClass != clazz => buf2 += element
            case x: ScVariableDeclaration if withOwn || x.containingClass != clazz => buf2 += element
            case x: ScTypeAliasDeclaration if withOwn || x.containingClass != clazz => buf2 += element
            case _ =>
          }
        }
        case _ =>
      }
    }
    buf2.toSeq
  }

  def isProductAbstractMethod(m: PsiMethod, clazz: PsiClass,
                              visited: HashSet[PsiClass] = new HashSet) : Boolean = {
    if (visited.contains(clazz)) return false
    clazz match {
      case td: ScTypeDefinition if td.isCase => {
        if (m.name == "apply") return true
        if (m.name == "canEqual") return true
        val clazz = m.containingClass
        clazz != null && clazz.qualifiedName == "scala.Product" &&
          (m.name match {
            case "productArity" | "productElement" => true
            case _ => false
          })
      }
      case x: ScTemplateDefinition => (x.superTypes.map(t => ScType.extractClass(t)).find {
        case Some(c) => isProductAbstractMethod(m, c, visited + clazz)
        case _ => false
      }) match {
        case Some(_) => true
        case _ => false
      }
      case _ => false
    }
  }

  def getMembersToOverride(clazz: ScTemplateDefinition): Seq[ScalaObject] = {
    val buf = new ArrayBuffer[ScalaObject]
    buf ++= clazz.allMethods
    buf ++= clazz.allTypeAliases
    buf ++= clazz.allVals
    val buf2 = new ArrayBuffer[ScalaObject]
    for (element <- buf) {
      element match {
        case sign: PhysicalSignature => {
          sign.method match {
            case _ if isProductAbstractMethod(sign.method, clazz) => buf2 += sign
            case f: ScFunctionDeclaration if f.hasAnnotation("scala.native") == None =>
            case x if x.name == "$tag" || x.name == "$init$"=>
            case x: ScFunction if x.isSyntheticCopy =>
            case x if x.containingClass == clazz =>
            case x: PsiModifierListOwner if x.hasModifierPropertyScala("abstract")
                || x.hasModifierPropertyScala("final") /*|| x.hasModifierProperty("sealed")*/ =>
            case x if x.isConstructor =>
            case method => {
              var flag = false
              if (method match {case x: ScFunction => x.parameters.length == 0 case _ => method.getParameterList.getParametersCount == 0}) {
                for (pair <- clazz.allVals; v = pair._1) if (v.name == method.name) {
                  ScalaPsiUtil.nameContext(v) match {
                    case x: ScValue if x.containingClass == clazz => flag = true
                    case x: ScVariable if x.containingClass == clazz => flag = true
                    case _ =>
                  }
                }
              }
              if (!flag) buf2 += sign
            }
          }
        }
        case (name: PsiNamedElement, subst: ScSubstitutor) => {
          ScalaPsiUtil.nameContext(name) match {
            case x: PsiModifierListOwner if x.hasModifierPropertyScala("final") =>
            case x: ScPatternDefinition if x.containingClass != clazz => {
              var flag = false
              for (signe <- clazz.allMethods if signe.method.containingClass == clazz) {
                //containingClass == clazz so we sure that this is ScFunction (it is safe cast)
                signe.method match {
                  case fun: ScFunction => if (fun.parameters.length == 0 && x.declaredElements.exists(_.name == fun.name)) flag = true
                  case _ =>  //todo: ScPrimaryConstructor?
                }
              }
              for (pair <- clazz.allVals; v = pair._1) if (v.name == name.name) {
                ScalaPsiUtil.nameContext(v) match {
                  case x: ScValue if x.containingClass == clazz => flag = true
                  case x: ScVariable if x.containingClass == clazz => flag = true
                  case _ =>
                }
              }
              if (!flag) buf2 += element
            }
            case x: ScVariableDefinition if x.containingClass != clazz => {
              var flag = false
              for (signe <- clazz.allMethods if signe.method.containingClass == clazz) {
                //containingClass == clazz so we sure that this is ScFunction (it is safe cast)
                if (signe.method.isInstanceOf[ScFunction] &&
                        signe.method.asInstanceOf[ScFunction].parameters.length == 0 &&
                        x.declaredElements.exists(_.name == signe.method.name)) flag = true
              }
              for (pair <- clazz.allVals; v = pair._1) if (v.name == name.name) {
                ScalaPsiUtil.nameContext(v) match {
                  case x: ScValue if x.containingClass == clazz => flag = true
                  case x: ScVariable if x.containingClass == clazz => flag = true
                  case _ =>
                }
              }
              if (!flag) buf2 += element
            }
            case x: ScTypeAliasDefinition if x.containingClass != clazz => buf2 += element
            case _ =>
          }
        }
        case _ =>
      }
    }

    buf2.toSeq
  }

  // TODO: this is only called from tests, too much code here that is being tested that *isn't* real.
  def getMethod(clazz: ScTypeDefinition, methodName: String, isImplement: Boolean, needsInferType: Boolean = true): ScMember = {
    val seq: Seq[ScalaObject] = if (isImplement) getMembersToImplement(clazz) else getMembersToOverride(clazz)
    def getObjectByName: ScalaObject = {
      for (obj <- seq) {
        obj match {
          case sign: PhysicalSignature if sign.method.name == methodName => return sign
          case obj@(name: PsiNamedElement, subst: ScSubstitutor) if name.name == methodName => return obj
          case _ =>
        }
      }
      null
    }
    val obj = getObjectByName
    if (obj == null) return null

    def addUpdateThisType(subst: ScSubstitutor) = clazz.getType(TypingContext.empty) match {
      case Success(tpe, _) => subst.addUpdateThisType(tpe)
      case Failure(_, _) => subst
    }

    obj match {
      case sign: PhysicalSignature => {
        val method: PsiMethod = sign.method
        val sign1 = sign.updateSubst(addUpdateThisType)
        ScalaPsiElementFactory.createOverrideImplementMethod(sign1, method.getManager, !isImplement, needsInferType, "null")
      }
      case (name: PsiNamedElement, subst: ScSubstitutor) => {
        val element: PsiElement = ScalaPsiUtil.nameContext(name)
        element match {
          case alias: ScTypeAlias => {
            val subst1 = addUpdateThisType(subst)
            ScalaPsiElementFactory.createOverrideImplementType(alias, subst1, alias.getManager, !isImplement)
          }
          case _: ScValue | _: ScVariable => {
            val typed: ScTypedDefinition = name match {case x: ScTypedDefinition => x case _ => return null}
            val subst1 = addUpdateThisType(subst)
            ScalaPsiElementFactory.createOverrideImplementVariable(typed, subst1, typed.getManager, !isImplement,
              element match {case _: ScValue => true case _ => false}, true)
          }
          case _ => null
        }
      }
    }
  }

  def getAnchor(offset: Int, clazz: ScTemplateDefinition) : Option[ScMember] = {
    val body = clazz.extendsBlock.templateBody match {
      case Some(x) => x
      case None => return None
    }
    var element: PsiElement = body.getContainingFile.findElementAt(offset)
    while (element != null && element.getParent != body) element = element.getParent

    element match {
      case member: ScMember => Some(member)
      case null => None
      case _ => PsiTreeUtil.getNextSiblingOfType(element, classOf[ScMember]) match {
        case null => None
        case member => Some(member)
      }
    }
  }

  private def adjustTypesAndSetCaret(meth: PsiElement, editor: Editor) {
    ScalaPsiUtil.adjustTypes(meth)
    //hack for postformatting IDEA bug.
    val member = CodeStyleManager.getInstance(meth.getProject).reformat(meth)
    //Setting selection
    val body: PsiElement = member match {
      case meth: ScTypeAliasDefinition => meth.aliasedTypeElement
      case meth: ScPatternDefinition => meth.expr
      case meth: ScVariableDefinition => meth.expr
      case method: ScFunctionDefinition => method.body match {
        case Some(x) => x
        case None => return
      }
      case _ => return
    }

    body match {
      case e: ScBlockExpr =>
        val statements = e.statements
        if (statements.length == 0) {
          editor.getCaretModel.moveToOffset(body.getTextRange.getStartOffset + 1)
        } else {
          val range = new TextRange(statements(0).getTextRange.getStartOffset, statements(statements.length - 1).getTextRange.getEndOffset)
          editor.getCaretModel.moveToOffset(range.getStartOffset)
          editor.getSelectionModel.setSelection(range.getStartOffset, range.getEndOffset)
        }
      case _ =>
        val range = body.getTextRange
        editor.getCaretModel.moveToOffset(range.getStartOffset)
        editor.getSelectionModel.setSelection(range.getStartOffset, range.getEndOffset)
    }
  }
}