package org.jetbrains.plugins.scala.annotator.intention.sbt

import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.sbt.resolvers.ArtifactInfo

/**
  * Created by user on 7/21/17.
  */
object AddSbtDependencyUtils {
  val libraryDependencies: String = "libraryDependencies"

  @scala.annotation.tailrec
  private def getScPatternDefinition(psiElement: PsiElement): ScPatternDefinition = {
    psiElement match {
      case pattern: ScPatternDefinition => pattern
      case _: PsiFile => null
      case _ => getScPatternDefinition(psiElement.getParent)
    }
  }

  def processMethodCall(call: ScMethodCall)(f: PsiElement => Unit): Unit = {
    f(call)

    def processSettings(settings: ScMethodCall): Unit = {
      settings.args.exprsArray.foreach({
        case typedStmt: ScTypedStmt => processTypedStmt(typedStmt)(f)
        case infix: ScInfixExpr if infix.lOp.getText == libraryDependencies => processInfix(infix)(f)
        case call: ScMethodCall => processMethodCall(call)(f)
        case ref: ScReferenceExpression => processReferenceExpr(ref)(f)
        case _ =>
      })
    }

    if (call.deepestInvokedExpr.getText == "Seq") {

      // Probably makes more sense to move it upper
      val formalSeq: ScType = ScalaPsiElementFactory.createTypeFromText("_root_.scala.collection.Seq", call, call).get
      val formalSetting: ScType = ScalaPsiElementFactory.createTypeFromText("_root_.sbt.Def.Setting", call, call).get
      // Can be of type ModuleId and of type Setting
      // We have to somethow distinguish between 2 different type of sequences
      // process seq
      call.getType().get match {
        case parameterized: ScParameterizedType if parameterized.designator.equiv(formalSeq) =>
          val args = parameterized.typeArguments
          if (args.length == 1) {
            args.head match {
              case parameterized: ScParameterizedType if parameterized.designator.equiv(formalSetting) =>
                processSettings(call)
              case _ =>
            }
          }
        case _ =>
      }

      val canonicalText = call.getType().get.canonicalText
      if (canonicalText == "_root_.scala.collection.Seq[_root_.sbt.ModuleID]" || canonicalText == "scala.Seq[_root_.sbt.ModuleID]") {
        // Actually, don't have to do here anything, because it just a list of differet modules
      } else {
        // TODO: have to put it clearly that it will be Seq[Setting], for now we just assume it
        val x = 1
      }


    } else {
      call.getEffectiveInvokedExpr match {
        case expr: ScReferenceExpression if expr.refName == "settings" =>
          processSettings(call)
        case _ =>
      }

    }
  }

  def processPatternDefinition(patternDefinition: ScPatternDefinition)(f: PsiElement => Unit): Unit = {
    f(patternDefinition)

    if (patternDefinition.getType().get.canonicalText == "_root_.sbt.Project") {
      val settings = getSettings(patternDefinition)
      settings.foreach(processMethodCall(_)(f))
    } else {
      if (patternDefinition.expr.isEmpty)
        return

      patternDefinition.expr.get match {
        case call: ScMethodCall => processMethodCall(call)(f)
        case infix: ScInfixExpr => processInfix(infix)(f)
        case _ =>
      }
    }
  }

  def processInfix(infix: ScInfixExpr)(f: PsiElement => Unit): Unit = {
    f(infix)

    def process(expr: ScExpression): Unit = {
      expr match {
        case call: ScMethodCall => processMethodCall(call)(f)
        case infix: ScInfixExpr => processInfix(infix)(f)
        case ref: ScReferenceExpression => processReferenceExpr(ref)(f)
        case _ =>
      }
    }

    if (infix.operation.refName == "++") {
      process(infix.lOp)
      process(infix.rOp)
    } else if (infix.operation.refName == "++=") {
      process(infix.rOp)
    } else if (infix.operation.refName == ":=") {
      process(infix.rOp)
    }
  }

  def processReferenceExpr(ref: ScReferenceExpression)(f: PsiElement => Unit): Unit = {
    f(ref)

    val element = ref.resolve()
    if (element != null) {
      val patternDefinition = getScPatternDefinition(element)
      if (patternDefinition != null) {
        processPatternDefinition(patternDefinition)(f)
      }
    }
  }

  def processTypedStmt(typedStmt: ScTypedStmt)(f: PsiElement => Unit): Unit = {
    f(typedStmt)

    typedStmt.expr match {
      case seqCall: ScMethodCall => processMethodCall(seqCall)(f)
      case _ =>
    }
  }

  def getPossiblePlacesToAddFromProjectDefinition(proj: ScPatternDefinition): Seq[PsiElement] = {
    var res: Seq[PsiElement] = List()

    def action(psiElement: PsiElement): Unit = {
      psiElement match {
        case e: ScInfixExpr if e.lOp.getText == libraryDependencies && isAddableLibraryDependencies(e) => res ++= Seq(e)
        // TODO: Change it's definition
        case call: ScMethodCall if call.deepestInvokedExpr.getText == "Seq" => res ++= Seq(call)
        case typedSeq: ScTypedStmt if typedSeq.isSequenceArg =>
          typedSeq.expr match {
            case call: ScMethodCall if call.deepestInvokedExpr.getText == "Seq" => res ++= Seq(typedSeq)
            case _ =>
          }
        case settings: ScMethodCall if isAddableSettings(settings) =>
          settings.getEffectiveInvokedExpr match {
            case expr: ScReferenceExpression if expr.refName == "settings" =>
              res ++= Seq(settings)
            case _ =>
          }
        case _ =>
      }
    }

    processPatternDefinition(proj)(action)

    res
  }

  def getPossiblePlacesToAddFromLibraryDependencies(libDeps: ScInfixExpr): Seq[ScMethodCall] = {
    var res: Seq[ScMethodCall] = List()

    val action: PsiElement => Unit = {
      case call: ScMethodCall if call.deepestInvokedExpr.getText == "Seq" => res ++= Seq(call)
      case _ =>
    }

    if (libDeps.operation.refName == "++=" || libDeps.operation.refName == ":=") {
      libDeps.rOp match {
        case ref: ScReferenceExpression => processReferenceExpr(ref)(action)
        case infix: ScInfixExpr => processInfix(infix)(action)
        case _ =>
      }
    }

    res
  }

  def getTypedSeqInsideSettings(settings: ScMethodCall): Option[ScTypedStmt] = {
    val args = settings.args.exprsArray

    if (args.length == 1) {
      args(0) match {
        case typedStmt: ScTypedStmt if typedStmt.isSequenceArg =>
          typedStmt.expr match {
            case _: ScMethodCall => Some(typedStmt)
            case _ => None
          }
        case _ => None
      }
    } else {
      None
    }
  }

  def getPossiblePlacesFromSettings(settings: ScMethodCall): Seq[PsiElement] = {
    var res: Seq[PsiElement] = List()
    val args = settings.args.exprsArray

    def action(elem: PsiElement): Unit = {
      elem match {
        case seqDef: ScPatternDefinition =>
          if (seqDef.expr.isDefined && seqDef.expr.get.isInstanceOf[ScMethodCall]) {
            val seqCall: ScMethodCall = seqDef.expr.get.asInstanceOf[ScMethodCall]
            val libDepsFromProjects: Iterable[ScInfixExpr] = getLibraryDepenciesInsideSettings(seqCall)

            res ++= libDepsFromProjects
              .filter(f => isAddableLibraryDependencies(f))

            res ++= libDepsFromProjects
              .flatMap(f => getPossiblePlacesToAddFromLibraryDependencies(f))

            // Works incorrectly
            //            res ++= Seq(getTypedSeqInsideSettings(seqCall).getOrElse(seqCall))
          }
        case _ =>
      }
    }

    if (args.length == 1) {
      args(0) match {
        case ref: ScReferenceExpression => processReferenceExpr(ref)(action)
        case _ =>
      }
    }

    res
  }

  def getLibraryDepenciesInsideSettings(settings: ScMethodCall): Seq[ScInfixExpr] = {
    var args = settings.args.exprsArray

    val optCall: Option[ScTypedStmt] = getTypedSeqInsideSettings(settings)

    args = if (optCall.isDefined) optCall.get.expr.asInstanceOf[ScMethodCall].args.exprsArray else args

    args.filter({
      case infix: ScInfixExpr if infix.lOp.getText == libraryDependencies => true
      case _ => false
    }).map(f => f.asInstanceOf[ScInfixExpr])
  }

  def getSettings(patternDefinition: ScPatternDefinition): Seq[ScMethodCall] = {
    var res: Seq[ScMethodCall] = List()

    def visit(pd: ScalaPsiElement): Unit = {
      pd.acceptChildren(new ScalaElementVisitor {
        override def visitMethodCallExpression(call: ScMethodCall): Unit = {
          call.getEffectiveInvokedExpr match {
            case expr: ScReferenceExpression if expr.refName == "settings" => res ++= Seq(call)
            case _ =>
          }

          visit(call.getEffectiveInvokedExpr)
          super.visitMethodCallExpression(call)
        }
      })
    }

    visit(patternDefinition)

    res
  }

  def getTopLevelSbtProjects(psiSbtFile: ScalaFile): Seq[ScPatternDefinition] = {
    var res: Seq[ScPatternDefinition] = List()

    psiSbtFile.acceptChildren(new ScalaElementVisitor {
      override def visitPatternDefinition(pat: ScPatternDefinition): Unit = {
        if (pat.expr.isEmpty)
          return

        if (pat.expr.get.getType().get.canonicalText != "_root_.sbt.Project")
          return

        res = res ++ Seq(pat)
        super.visitPatternDefinition(pat)
      }
    })

    res
  }

  def getTopLevelLibraryDependencies(psiSbtFile: ScalaFile): Seq[ScInfixExpr] = {
    var res: Seq[ScInfixExpr] = List()

    psiSbtFile.acceptChildren(new ScalaElementVisitor {
      override def visitInfixExpression(infix: ScInfixExpr): Unit = {
        // Looking for the top level libraryDependencies element
        if (infix.lOp.getText == libraryDependencies && infix.getParent.isInstanceOf[PsiFile]) {
          res = res ++ Seq(infix)
        }
      }
    })

    res
  }

  def getTopLevelPlaceToAdd(psiFile: ScalaFile)(implicit project: Project): FileLine = {
    FileLine(getRelativePath(psiFile), psiFile.getTextLength, psiFile, Seq())
  }

  def addDependencyToTypedSeq(typedSeq: ScTypedStmt, info: ArtifactInfo)(implicit project: Project): PsiElement = {
    typedSeq.expr match {
      case seqCall: ScMethodCall =>
        val addedExpr = generateLibraryDependency(info)(project)
        doInSbtWriteCommandAction({
          seqCall.args.addExpr(addedExpr)
        }, seqCall.getContainingFile)
        addedExpr
      case _ => null
    }
  }

  def addDependencyToFile(file: PsiFile, info: ArtifactInfo)(implicit project: Project): PsiElement = {
    var addedExpr: PsiElement = null
    doInSbtWriteCommandAction({
      file.addAfter(generateNewLine(project), file.getLastChild)
      addedExpr = file.addAfter(generateLibraryDependency(info), file.getLastChild)
    }, file)
    addedExpr
  }

  def addDependency(expr: PsiElement, info: ArtifactInfo)(implicit project: Project): PsiElement = {
    expr match {
      case e: ScInfixExpr if e.lOp.getText == libraryDependencies => processLibraryDependencies(e, info)
      case call: ScMethodCall if call.deepestInvokedExpr.getText == "Seq" => addDependencyToSeq(call, info)
      case typedSeq: ScTypedStmt if typedSeq.isSequenceArg => addDependencyToTypedSeq(typedSeq, info)
      case settings: ScMethodCall if isAddableSettings(settings) =>
        settings.getEffectiveInvokedExpr match {
          case expr: ScReferenceExpression if expr.refName == "settings" =>
            addDependencyToSettings(settings, info)(project)
          case _ => null
        }
      case file: PsiFile => addDependencyToFile(file, info)(project)
      case _ => null
    }
  }

  def addDependencyToSettings(settings: ScMethodCall, info: ArtifactInfo)(implicit project: Project): PsiElement = {
    val addedExpr = generateLibraryDependency(info)(project)
    doInSbtWriteCommandAction({
      settings.args.addExpr(addedExpr)
    }, settings.getContainingFile)(project)
    addedExpr
  }

  def addDependencyToSeq(seqCall: ScMethodCall, info: ArtifactInfo)(implicit project: Project): PsiElement = {
    val formalSeq: ScType = ScalaPsiElementFactory.createTypeFromText("_root_.scala.collection.Seq", seqCall, seqCall).get
    val formalSetting: ScType = ScalaPsiElementFactory.createTypeFromText("_root_.sbt.Def.Setting", seqCall, seqCall).get

    seqCall.getType().get match {
      case parameterized: ScParameterizedType if parameterized.designator.equiv(formalSeq) =>
        val args = parameterized.typeArguments
        if (args.length == 1) {
          args.head match {
            case parameterized: ScParameterizedType if parameterized.designator.equiv(formalSetting) =>
              val addedExpr: ScInfixExpr = generateLibraryDependency(info)
              doInSbtWriteCommandAction({
                seqCall.args.addExpr(addedExpr)
              }, seqCall.getContainingFile)
              return addedExpr
            case _ =>
          }
        }
    }

    val addedExpr = generateArtifactPsiExpression(info)
    doInSbtWriteCommandAction({
      seqCall.args.addExpr(addedExpr)
    }, seqCall.getContainingFile)

    addedExpr
  }

  def isAddableSettings(settings: ScMethodCall): Boolean = {
    val args = settings.args.exprsArray

    if (args.length == 1) {
      args(0) match {
        case typedStmt: ScTypedStmt if typedStmt.isSequenceArg =>
          typedStmt.expr match {
            case _: ScMethodCall => false
            case _: ScReferenceExpression => false
            case _ => true
          }
        case _ => true
      }
    } else {
      true
    }
  }

  def isAddableLibraryDependencies(libDeps: ScInfixExpr): Boolean = {
    if (libDeps.operation.refName == "+=") {
      return true
    } else if (libDeps.operation.refName == "++=") {
      libDeps.rOp match {
          // In this case we return false, because of not to repeat it several times
        case call: ScMethodCall if call.deepestInvokedExpr.getText == "Seq" => return false
        case _ =>
      }
    }
    // TODO: Add support for ":="

    false
  }

  // isAddableLibraryDependencies should return true
  def processLibraryDependencies(infix: ScInfixExpr, info: ArtifactInfo)(implicit project: Project): PsiElement = {
    var res: PsiElement = null
    val psiFile = infix.getContainingFile
    val opName = infix.operation.refName

    if (opName == "+=") {
      val dependency: ScExpression = infix.rOp
      val seqCall: ScMethodCall = generateSeqPsiMethodCall(info)(project)

      doInSbtWriteCommandAction({
        seqCall.args.addExpr(dependency.copy().asInstanceOf[ScExpression])
        seqCall.args.addExpr(generateArtifactPsiExpression(info)(project))
        infix.operation.replace(ScalaPsiElementFactory.createElementFromText("++=")(project))
        dependency.replace(seqCall)
      }, psiFile)(project)

      res = infix.rOp
    } else if (opName == "++=") {
      val dependencies: ScExpression = infix.rOp
      dependencies match {
        case call: ScMethodCall =>
          val text = call.deepestInvokedExpr.getText
          // TODO: Add support for more other collections
          if (text == "Seq") {
            val addedExpr = generateArtifactPsiExpression(info)(project)
            doInSbtWriteCommandAction({
              call.args.addExpr(addedExpr)
            }, psiFile)(project)

            res = addedExpr
          }
        case _ =>
      }
    }

    res
  }

  private def doInSbtWriteCommandAction(f: => Unit, psiSbtFile: PsiFile)(implicit project: ProjectContext): Unit = {
    new WriteCommandAction[Unit](project, psiSbtFile) {
      override def run(result: Result[Unit]): Unit = {
        f
      }
    }.execute()
  }

  private def generateSeqPsiMethodCall(info: ArtifactInfo)(implicit ctx: ProjectContext): ScMethodCall =
    ScalaPsiElementFactory.createElementFromText("Seq()").asInstanceOf[ScMethodCall]

  private def generateLibraryDependency(info: ArtifactInfo)(implicit ctx: ProjectContext): ScInfixExpr =
    ScalaPsiElementFactory.createElementFromText(s"$libraryDependencies += ${generateArtifactText(info)}").asInstanceOf[ScInfixExpr]

  private def generateArtifactPsiExpression(info: ArtifactInfo)(implicit ctx: ProjectContext): ScExpression =
    ScalaPsiElementFactory.createElementFromText(generateArtifactText(info))(ctx).asInstanceOf[ScExpression]

  def generateNewLine(implicit ctx: ProjectContext): PsiElement = ScalaPsiElementFactory.createElementFromText("\n")

  private def generateArtifactText(info: ArtifactInfo): String =
    "\"" + s"${info.groupId}" + "\" % \"" + s"${info.artifactId}" + "\" % \"" + s"${info.version}" + "\""

  def getRelativePath(elem: PsiElement)(implicit project: Project): String = {
    val path = elem.getContainingFile.getVirtualFile.getCanonicalPath
    if (!path.startsWith(project.getBasePath))
      return null

    path.substring(project.getBasePath.length + 1)
  }

  def toFileLine(elem: PsiElement, affectedProjects: Seq[String])(implicit project: Project): FileLine = {
    val offset =
      elem match {
        case call: ScMethodCall =>
          call.getEffectiveInvokedExpr match {
            case expr: ScReferenceExpression => expr.nameId.getTextOffset
            case _ => elem.getTextOffset
          }
        case _ => elem.getTextOffset
      }

    FileLine(getRelativePath(elem), offset, elem, affectedProjects)
  }
}
