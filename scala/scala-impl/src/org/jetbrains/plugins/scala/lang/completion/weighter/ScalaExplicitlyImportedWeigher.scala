package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.util.proximity.ProximityWeigher
import com.intellij.psi.util.{ProximityLocation, PsiTreeUtil}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScPackaging}
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
  * @author Alexander Podkhalyuzin
  */
class ScalaExplicitlyImportedWeigher extends ProximityWeigher {
  def applyQualifier(qual: String, position: PsiElement): Option[Integer] = {
    if (position == null) return None
    val index = qual.lastIndexOf('.')
    val qualNoPoint = if (index < 0) null else qual.substring(0, index)
    val tuple: (ArrayBuffer[ScImportStmt], Long) = position.getUserData(ScalaExplicitlyImportedWeigher.key)
    var buffer: ArrayBuffer[ScImportStmt] = if (tuple != null) tuple._1 else null
    val currentModCount = position.getManager.getModificationTracker.getModificationCount
    if (buffer == null || tuple._2 != currentModCount) {
      @tailrec
      def treeWalkup(place: PsiElement, lastParent: PsiElement): Unit = {
        if (place == null) return
        place match {
          case holder: ScImportsHolder =>
            buffer ++= holder.getImportsForLastParent(lastParent)
            if (place.isInstanceOf[ScalaFile]) return
          case _ =>
        }
        treeWalkup(place.getContext, place)
      }

      buffer = new ArrayBuffer[ScImportStmt]()
      treeWalkup(position.getContext, position)
      position.putUserData(ScalaExplicitlyImportedWeigher.key, (buffer, currentModCount))
    }
    val iter = buffer.iterator
    while (iter.hasNext) {
      val stmt = iter.next()
      val exprIter = stmt.importExprs.iterator
      while (exprIter.hasNext) {
        val expr = exprIter.next()
        if (expr.hasWildcardSelector && qualNoPoint != null) {
          for (qualifier <- expr.qualifier; resolve <- qualifier.multiResolveScala(false))
            resolve.element match {
              case pack: PsiPackage =>
                if (qualNoPoint == pack.getQualifiedName) return Some(4)
              case clazz: PsiClass =>
                if (qualNoPoint == clazz.qualifiedName) return Some(4)
              case _ =>
            }
        } else if (expr.selectorSet.isDefined) {
          for (selector <- expr.selectors) {
            for (ref <- selector.reference;
                 resolve <- ref.multiResolveScala(false)) {
              resolve.element match {
                case clazz: PsiClass =>
                  if (qual == clazz.qualifiedName) return Some(4)
                case _ =>
              }
            }
          }
        } else {
          expr.reference match {
            case Some(ref) =>
              for (resolve <- ref.multiResolveScala(false))
                resolve.element match {
                  case clazz: PsiClass =>
                    if (qual == clazz.qualifiedName) return Some(4)
                  case _ =>
                }
            case None =>
          }
        }
      }
    }

    val defaultImports = position.defaultImports

    if (defaultImports.contains(qualNoPoint)) {
      if (qualNoPoint == "java.lang") Option(1)
      else                            Option(2)
    } else None
  }

  def applyToMember(member: ScMember, position: PsiElement): Option[Integer] = {
    member.getContext match {
      case _: ScTemplateBody =>
        val clazz: PsiClass = member.containingClass
        clazz match {
          case obj: ScObject =>
            val qualNoPoint = obj.qualifiedName
            if (qualNoPoint != null) {
              val memberName = member match {
                case named: ScNamedElement => named.name
                case _ => member.getName
              }
              val qual = qualNoPoint + "." + memberName
              applyQualifier(qual, position) match {
                case Some(x) => return Some(x)
                case None =>
              }
            }
          case _ =>
        }
      case _ =>
    }
    None
  }

  override def weigh(element: PsiElement, location: ProximityLocation): Integer = {
    val position: PsiElement = location.getPosition
    if (position == null) {
      return 0
    }
    val elementFile: PsiFile = element.getContainingFile
    val positionFile: PsiFile = position.getContainingFile
    if (!positionFile.isInstanceOf[ScalaFile]) return 0
    if (positionFile != null && elementFile != null && positionFile.getOriginalFile == elementFile.getOriginalFile) {
      return 3
    }
    element match {
      case clazz: PsiClass if clazz.qualifiedName != null =>
        val qual: String = clazz.qualifiedName
        applyQualifier(qual, position) match {
          case Some(x) => return x
          case None =>
        }
      case member: ScMember =>
        applyToMember(member, position) match {
          case Some(x) => return x
          case None =>
        }
      case member: PsiMember if member.hasModifierProperty("static") =>
        val clazz = member.containingClass
        if (clazz != null && clazz.qualifiedName != null) {
          val qualNoPoint = clazz.qualifiedName
          val memberName = member match {
            case named: ScNamedElement => named.name
            case _ => member.getName
          }
          val qual = qualNoPoint + "." + memberName
          applyQualifier(qual, position) match {
            case Some(x) => return x
            case None =>
          }
        }
      case b: ScBindingPattern =>
        ScalaPsiUtil.nameContext(b) match {
          case v: ScValue =>
            applyToMember(v, position) match {
              case Some(x) => return x
              case None =>
            }
          case v: ScVariable =>
            applyToMember(v, position) match {
              case Some(x) => return x
              case None =>
            }
          case _ =>
        }
      case _ =>
    }

    def packageName(element: PsiElement): Option[String] = {
      val packageObject = Option(PsiTreeUtil.getContextOfType(element, classOf[ScObject]))
      val nameAsPackageObject = packageObject.collect { case po: ScObject if po.isPackageObject => po.qualifiedName }
      if (nameAsPackageObject.isEmpty) {
        Option(PsiTreeUtil.getContextOfType(element, classOf[ScPackaging])).map(_.fullPackageName)
      } else {
        nameAsPackageObject
      }
    }

    packageName(position).foreach { pName =>
      val elementModule = ModuleUtilCore.findModuleForPsiElement(element)
      if (location.getPositionModule == elementModule && packageName(element).contains(pName)) {
        return 3
      }
    }

    0
  }
}

object ScalaExplicitlyImportedWeigher {
  private[weighter] val key: Key[(ArrayBuffer[ScImportStmt], Long)] = Key.create("scala.explicitly.imported.weigher.key")
}
