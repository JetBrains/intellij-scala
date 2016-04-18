package org.jetbrains.plugins.scala
package overrideImplement

import com.intellij.codeInsight.generation.{ClassMember => JClassMember, GenerateMembersUtil}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.util.ScalaUtils

import scala.collection.JavaConversions
import scala.collection.immutable.HashSet
import scala.collection.mutable.ListBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2008
 */

object ScalaOIUtil {

  def toClassMember(candidate: AnyRef, isImplement: Boolean): Option[ClassMember] = {
    candidate match {
      case sign: PhysicalSignature =>
        val method = sign.method
        assert(method.containingClass != null, "Containing Class is null: " + method.getText)
        Some(new ScMethodMember(sign, !isImplement))
      case (named: PsiNamedElement, subst: ScSubstitutor) =>
        ScalaPsiUtil.nameContext(named) match {
          case x: ScValue =>
            assert(x.containingClass != null, "Containing Class is null: " + x.getText)
            named match {
              case y: ScTypedDefinition => Some(new ScValueMember(x, y, subst, !isImplement))
              case _ => throw new IncorrectOperationException("Not supported type:" + x)
            }
          case x: ScVariable =>
            assert(x.containingClass != null, "Containing Class is null: " + x.getText)
            named match {
              case y: ScTypedDefinition => Some(new ScVariableMember(x, y, subst, !isImplement))
              case _ => throw new IncorrectOperationException("Not supported type:" + x)
            }
          case x: ScTypeAlias =>
            assert(x.containingClass != null, "Containing Class is null: " + x.getText)
            Some(new ScAliasMember(x, subst, !isImplement))
          case x: PsiField => Some(new JavaFieldMember(x, subst))
          case x => None
        }
      case x => None
    }
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

    val selectedMembers = ListBuffer[ClassMember]()
    if (!ApplicationManager.getApplication.isUnitTestMode) {

      val chooser = new ScalaMemberChooser[ClassMember](classMembers.toArray, false, true, isImplement, true, clazz)
      chooser.setTitle(if (isImplement) ScalaBundle.message("select.method.implement") else ScalaBundle.message("select.method.override"))
      if (isImplement) chooser.selectElements(classMembers.toArray[JClassMember])
      chooser.show()

      val elements = chooser.getSelectedElements
      if (elements != null) selectedMembers ++= JavaConversions.asScalaBuffer(elements)
      if (selectedMembers.isEmpty) return
    } else {
      selectedMembers ++= classMembers.find {
        case named: ScalaNamedMember if named.name == methodName => true
        case _ => false
      }.toSeq
    }

    runAction(selectedMembers, isImplement, clazz, editor)
  }


  def runAction(selectedMembers: Seq[ClassMember],
               isImplement: Boolean, clazz: ScTemplateDefinition, editor: Editor) {
    ScalaUtils.runWriteAction(new Runnable {
      def run() {
        import scala.collection.JavaConversions._
        val sortedMembers = ScalaMemberChooser.sorted(selectedMembers, clazz)
        val genInfos = sortedMembers.map(new ScalaGenerationInfo(_))
        val anchor = getAnchor(editor.getCaretModel.getOffset, clazz)
        val inserted = GenerateMembersUtil.insertMembersBeforeAnchor(clazz, anchor.orNull, genInfos.reverse)
        inserted.headOption.foreach(_.positionCaret(editor, toEditMethodBody = true))
      }
    }, clazz.getProject, if (isImplement) "Implement method" else "Override method")
  }

  def getMembersToImplement(clazz: ScTemplateDefinition, withOwn: Boolean = false, withSelfType: Boolean = false): Iterable[ClassMember] = {
    allMembers(clazz, withSelfType).filter {
      case sign: PhysicalSignature => needImplement(sign, clazz, withOwn)
      case (named: PsiNamedElement, subst: ScSubstitutor) => needImplement(named, clazz, withOwn)
      case _ => false
    }.flatMap(toClassMember(_, isImplement = true))
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
        implicit val typeSystem = x.typeSystem
        x.superTypes.map(_.extractClass()).find {
          case Some(c) => isProductAbstractMethod(m, c, visited + clazz)
          case _ => false
        } match {
          case Some(_) => true
          case _ => false
        }
      case _ => false
    }
  }

  def getMembersToOverride(clazz: ScTemplateDefinition, withSelfType: Boolean): Iterable[ClassMember] = {
    allMembers(clazz, withSelfType).filter {
      case sign: PhysicalSignature => needOverride(sign, clazz)
      case (named: PsiNamedElement, _: ScSubstitutor) => needOverride(named, clazz)
      case _ => false
    }.flatMap(toClassMember(_, isImplement = false))
  }


  def allMembers(clazz: ScTemplateDefinition, withSelfType: Boolean): Iterable[Object] = {
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
      case method if !ResolveUtils.isAccessible(method, clazz.extendsBlock, forCompletion = false) => false
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
    val place = clazz.extendsBlock
    m match {
      case _ if isProductAbstractMethod(m, clazz) => false
      case method if !ResolveUtils.isAccessible(method, place, forCompletion = false) => false
      case x if name == "$tag" || name == "$init$" => false
      case x if !withOwn && x.containingClass == clazz => false
      case x if x.containingClass != null && x.containingClass.isInterface &&
              !x.containingClass.isInstanceOf[ScTrait] && x.hasModifierProperty("abstract") => true
      case x if x.hasModifierPropertyScala("abstract") && !x.isInstanceOf[ScFunctionDefinition] &&
              !x.isInstanceOf[ScPatternDefinition] && !x.isInstanceOf[ScVariableDefinition] => true
      case x: ScFunctionDeclaration if x.hasAnnotation("scala.native") == None => true
      case _ => false
    }
  }

  private def needOverride(named: PsiNamedElement, clazz: ScTemplateDefinition) = {
    ScalaPsiUtil.nameContext(named) match {
      case x: PsiModifierListOwner if x.hasModifierPropertyScala("final") => false
      case m: PsiMember if !ResolveUtils.isAccessible(m, clazz.extendsBlock, forCompletion = false) => false
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
      case m: PsiMember if !ResolveUtils.isAccessible(m, clazz.extendsBlock, forCompletion = false) => false
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