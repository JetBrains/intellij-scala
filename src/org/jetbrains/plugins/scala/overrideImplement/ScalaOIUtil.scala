package org.jetbrains.plugins.scala
package overrideImplement

import com.intellij.codeInsight.generation.{GenerateMembersUtil, PsiElementClassMember}
import com.intellij.openapi.editor.Editor

import com.intellij.psi.util.PsiTreeUtil

import com.intellij.ui.NonFocusableCheckBox
import com.intellij.util.IncorrectOperationException
import com.intellij.psi._
import lang.psi.api.toplevel.typedef.{ScTrait, ScTypeDefinition, ScMember, ScTemplateDefinition}
import lang.psi.api.toplevel.ScTypedDefinition
import lang.psi.api.statements._
import lang.psi.types._
import lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.util.ScalaUtils
import com.intellij.ide.util.MemberChooser
import scala.collection.mutable.ListBuffer
import com.intellij.openapi.project.Project
import settings.ScalaApplicationSettings
import javax.swing.{JComponent, JCheckBox}
import collection.immutable.HashSet
import extensions._
import com.intellij.openapi.application.ApplicationManager

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2008
 */

object ScalaOIUtil {

  private def toMembers(candidates: Seq[AnyRef], isImplement: Boolean): Seq[PsiElementClassMember[_ <: PsiDocCommentOwner]] = {
    val classMembersBuf = new ListBuffer[PsiElementClassMember[_ <: PsiDocCommentOwner]]
    for (candidate <- candidates) {
      candidate match {
        case sign: PhysicalSignature =>
          val method = sign.method
          assert(method.containingClass != null, "Containing Class is null: " + method.getText)
          classMembersBuf += new ScMethodMember(sign, isImplement)
        case (name: PsiNamedElement, subst: ScSubstitutor) =>
          ScalaPsiUtil.nameContext(name) match {
            case x: ScValue =>
              assert(x.containingClass != null, "Containing Class is null: " + x.getText)
              name match {
                case y: ScTypedDefinition => classMembersBuf += new ScValueMember(x, y, subst, isImplement)
                case _ => throw new IncorrectOperationException("Not supported type:" + x)
              }
            case x: ScVariable =>
              assert(x.containingClass != null, "Containing Class is null: " + x.getText)
              name match {
                case y: ScTypedDefinition => classMembersBuf += new ScVariableMember(x, y, subst, isImplement)
                case _ => throw new IncorrectOperationException("Not supported type:" + x)
              }
            case x: ScTypeAlias =>
              assert(x.containingClass != null, "Containing Class is null: " + x.getText)
              classMembersBuf += new ScAliasMember(x, subst, isImplement)
            case x => throw new IncorrectOperationException("Not supported type:" + x)
          }
        case x => throw new IncorrectOperationException("Not supported type:" + x)
      }
    }
    classMembersBuf.toList
  }

  def invokeOverrideImplement(project: Project, editor: Editor, file: PsiFile, isImplement: Boolean,
                              methodName: String = null) {
    val elem = file.findElementAt(editor.getCaretModel.getOffset - 1)
    val clazz = PsiTreeUtil.getParentOfType(elem, classOf[ScTemplateDefinition], /*strict = */false)
    if (clazz == null) return
    
    val classMembers =
      if (isImplement) getMembersToImplement(clazz, withSelfType = true)
      else getMembersToOverride(clazz, withSelfType = true)
    if (classMembers.isEmpty) return
    
    val specifyRetTypeChb: JCheckBox = new NonFocusableCheckBox(
      ScalaBundle.message("specify.return.type.explicitly"))
    specifyRetTypeChb.setSelected(ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY)

    import scala.collection.JavaConversions._

    val selectedMembers = ListBuffer[PsiElementClassMember[_ <: PsiDocCommentOwner]]()
    if (!ApplicationManager.getApplication.isUnitTestMode) {

      object chooser extends MemberChooser[PsiElementClassMember[_ <: PsiDocCommentOwner]] (classMembers.toArray, false, true, project, null, Array[JComponent](specifyRetTypeChb)) {
        def needsInferType = specifyRetTypeChb.isSelected
      }
      chooser.setTitle(if (isImplement) ScalaBundle.message("select.method.implement") else ScalaBundle.message("select.method.override"))
      chooser.show()

      selectedMembers ++= chooser.getSelectedElements
      if (selectedMembers.size == 0) return
      ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY = chooser.needsInferType
    } else {
      selectedMembers ++= classMembers.find {
        case named: ScalaNamedMembers if named.name == methodName => true
        case _ => false
      }.toSeq
    }

    runAction(selectedMembers, isImplement, clazz, editor, ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY)
  }


  def runAction(selectedMembers: Seq[PsiElementClassMember[_ <: PsiDocCommentOwner]],
               isImplement: Boolean, clazz: ScTemplateDefinition, editor: Editor, needsInferType: Boolean) {
    ScalaUtils.runWriteAction(new Runnable {
      def run() {
        import scala.collection.JavaConversions._

        val genInfos = selectedMembers.map(new ScalaGenerationInfo(_, needsInferType))
        val anchor = getAnchor(editor.getCaretModel.getOffset, clazz)
        val inserted = GenerateMembersUtil.insertMembersBeforeAnchor(clazz, anchor.getOrElse(null), genInfos)
        inserted.headOption.foreach(_.positionCaret(editor, toEditMethodBody = true))
      }
    }, clazz.getProject, if (isImplement) "Implement method" else "Override method")
  }

  def getMembersToImplement(clazz: ScTemplateDefinition, withOwn: Boolean = false, withSelfType: Boolean = false): Seq[PsiElementClassMember[_ <: PsiDocCommentOwner]] = {
    val filtered = allMembers(clazz, withSelfType).filter {
      case sign: PhysicalSignature => needImplement(sign, clazz, withOwn)
      case (named: PsiNamedElement, subst: ScSubstitutor) => needImplement(named, clazz, withOwn)
      case _ => false
    }
    toMembers(filtered.toSeq, isImplement = true)
  }


  def isProductAbstractMethod(m: PsiMethod, clazz: PsiClass,
                              visited: HashSet[PsiClass] = new HashSet) : Boolean = {
    if (visited.contains(clazz)) return false
    clazz match {
      case td: ScTypeDefinition if td.isCase =>
        if (m.name == "apply") return true
        if (m.name == "canEqual") return true
        val clazz = m.containingClass
        clazz != null && clazz.qualifiedName == "scala.Product" &&
          (m.name match {
            case "productArity" | "productElement" => true
            case _ => false
          })
      case x: ScTemplateDefinition =>
        x.superTypes.map(t => ScType.extractClass(t)).find {
          case Some(c) => isProductAbstractMethod(m, c, visited + clazz)
          case _ => false
        } match {
          case Some(_) => true
          case _ => false
        }
      case _ => false
    }
  }

  def getMembersToOverride(clazz: ScTemplateDefinition, withSelfType: Boolean): Seq[PsiElementClassMember[_ <: PsiDocCommentOwner]] = {
    val filtered = allMembers(clazz, withSelfType).filter {
      case sign: PhysicalSignature => needOverride(sign, clazz)
      case (named: PsiNamedElement, _: ScSubstitutor) => needOverride(named, clazz)
      case _ => false
    }
    toMembers(filtered.toSeq, isImplement = false)
  }


  private def allMembers(clazz: ScTemplateDefinition, withSelfType: Boolean): Iterable[Object] = {
    if (withSelfType) clazz.allMethodsIncludingSelfType ++ clazz.allTypeAliasesIncludingSelfType ++ clazz.allValsIncludingSelfType
    else clazz.allMethods ++ clazz.allTypeAliases ++ clazz.allVals
  }

  private def needOverride(sign: PhysicalSignature, clazz: ScTemplateDefinition): Boolean = {
    sign.method match {
      case _ if isProductAbstractMethod(sign.method, clazz) => true
      case f: ScFunctionDeclaration if f.hasAnnotation("scala.native") == None => false
      case x if x.name == "$tag" || x.name == "$init$"=> false
      case x: ScFunction if x.isSyntheticCopy => false
      case x if x.containingClass == clazz => false
      case x: PsiModifierListOwner if (x.hasModifierPropertyScala("abstract") &&
              !x.isInstanceOf[ScFunctionDefinition])
              || x.hasModifierPropertyScala("final") => false
      case x if x.isConstructor => false
      case method =>
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
        !flag
    }
  }

  private def needImplement(sign: PhysicalSignature, clazz: ScTemplateDefinition, withOwn: Boolean): Boolean = {
    val m = sign.method
    val name = if (m == null) "" else m.name
    m match {
      case _ if isProductAbstractMethod(m, clazz) => false
      case x if name == "$tag" || name == "$init$" => false
      case x if !withOwn && x.containingClass == clazz => false
      case x if x.containingClass != null && x.containingClass.isInterface &&
              !x.containingClass.isInstanceOf[ScTrait] => true
      case x if x.hasModifierPropertyScala("abstract") && !x.isInstanceOf[ScFunctionDefinition] &&
              !x.isInstanceOf[ScPatternDefinition] && !x.isInstanceOf[ScVariableDefinition] => true
      case x: ScFunctionDeclaration if x.hasAnnotation("scala.native") == None => true
      case _ => false
    }
  }

  private def needOverride(named: PsiNamedElement, clazz: ScTemplateDefinition) = {
    ScalaPsiUtil.nameContext(named) match {
      case x: PsiModifierListOwner if x.hasModifierPropertyScala("final") => false
      case x @ (_: ScPatternDefinition | _: ScVariableDefinition) if x.asInstanceOf[ScMember].containingClass != clazz =>
        val declaredElements = x match {case v: ScValue => v.declaredElements case v: ScVariable => v.declaredElements}
        var flag = false
        for (signe <- clazz.allMethods if signe.method.containingClass == clazz) {
          //containingClass == clazz so we sure that this is ScFunction (it is safe cast)
          signe.method match {
            case fun: ScFunction => if (fun.parameters.length == 0 && declaredElements.exists(_.name == fun.name)) flag = true
            case _ =>  //todo: ScPrimaryConstructor?
          }
        }
        for (pair <- clazz.allVals; v = pair._1) if (v.name == named.name) {
          ScalaPsiUtil.nameContext(v) match {
            case x: ScValue if x.containingClass == clazz => flag = true
            case x: ScVariable if x.containingClass == clazz => flag = true
            case _ =>
          }
        }
        !flag
      case x: ScTypeAliasDefinition if x.containingClass != clazz => true
      case _ => false
    }
  }

  private def needImplement(named: PsiNamedElement, clazz: ScTemplateDefinition, withOwn: Boolean): Boolean = {
    ScalaPsiUtil.nameContext(named) match {
      case x: ScValueDeclaration if withOwn || x.containingClass != clazz => true
      case x: ScVariableDeclaration if withOwn || x.containingClass != clazz => true
      case x: ScTypeAliasDeclaration if withOwn || x.containingClass != clazz => true
      case _ => false
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

  def methodSignaturesToOverride(clazz: ScTemplateDefinition, withSelfType: Boolean): Iterable[PhysicalSignature] = {
    val all = if (withSelfType) clazz.allMethodsIncludingSelfType else clazz.allMethods
    all.filter(needOverride(_, clazz))
  }
}