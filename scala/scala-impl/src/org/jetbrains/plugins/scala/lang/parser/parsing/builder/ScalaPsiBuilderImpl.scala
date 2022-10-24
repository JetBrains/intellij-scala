package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.util.text.StringUtil.isWhiteSpace
import com.intellij.psi.impl.source.resolve.FileContextUtil.CONTAINING_FILE_KEY
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.parser.IndentationWidth
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectPsiElementExt, ProjectPsiFileExt, ScalaFeaturePusher, ScalaFeatures, ScalaLanguageLevel}

// TODO: now isScala3 is properly set only in org.jetbrains.plugins.scala.lang.parser.ScalaParser
//  update all ScalaPsiBuilderImpl instantiations passing proper isScala3 value
class ScalaPsiBuilderImpl(delegate: PsiBuilder, override val isScala3: Boolean) extends PsiBuilderAdapter(delegate)
  with ScalaPsiBuilder {

  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType._
  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._

  private var newlinesEnabled = List.empty[Boolean]

  private var inQuotedPattern: Int = 0

  private lazy val containingFile = Option {
    myDelegate.getUserData(CONTAINING_FILE_KEY)
  }

  override def skipExternalToken(): Boolean = false

  override final lazy val isMetaEnabled: Boolean =
    containingFile.exists(_.isMetaEnabled)

  override final lazy val isStrictMode: Boolean =
    containingFile.exists(_.isCompilerStrictMode)

  override final lazy val features: ScalaFeatures = {
    def featuresByVersion(features: ScalaFeatures): ScalaFeatures =
      features.copy(if (isScala3) ScalaVersion.Latest.Scala_3_0 else ScalaVersion.Latest.Scala_2_13)

    def fallback = featuresByVersion(ScalaFeatures.default)

    containingFile match {
      case Some(file) =>
        file.module match {
          case Some(module) =>
            val features = module.features

            // If we don't have a module or a concrete scala version of the file
            // force the version given by isScala
            if (isScala3 == features.isScala3) features
            else featuresByVersion(features)
          case None =>
            // try get from file
            ScalaFeaturePusher.getFeatures(file)
              .getOrElse(fallback)
        }
      case None =>
        fallback
    }
  }

  override final lazy val underscoreWildcardsDisabled: Boolean =
    containingFile.exists(_.underscoreWidlcardsDisabled)

  private lazy val _isTrailingCommasEnabled =
    containingFile.exists(_.isTrailingCommasEnabled)

  private lazy val _isIdBindingEnabled =
    containingFile.exists(_.isIdBindingEnabled)

  override final def isTrailingComma: Boolean = getTokenType match {
    case `tCOMMA` => _isTrailingCommasEnabled
    case _ => false
  }

  override final def isIdBinding: Boolean =
    this.invalidVarId || _isIdBindingEnabled

  override def newlineBeforeCurrentToken: Boolean =
    findPreviousNewLineSafe.isDefined

  override def twoNewlinesBeforeCurrentToken: Boolean =
    findPreviousNewLineSafe.exists { text =>
      s"start $text end".split('\n').exists { line =>
        line.forall(isWhiteSpace)
      }
    }

  override final def disableNewlines(): Unit = {
    newlinesEnabled = false :: newlinesEnabled
  }

  override final def enableNewlines(): Unit = {
    newlinesEnabled = true :: newlinesEnabled
  }

  override final def enterQuotedPattern(): Unit = {
    inQuotedPattern += 1
  }

  override final def exitQuotedPattern(): Unit = {
    assert(inQuotedPattern > 0)
    inQuotedPattern -= 1
  }

  override final def isInQuotedPattern: Boolean =
    inQuotedPattern > 0

  override final def restoreNewlinesState(): Unit = {
    assert(newlinesEnabled.nonEmpty)
    newlinesEnabled = newlinesEnabled.tail
  }

  protected final def isNewlinesEnabled: Boolean =
    newlinesEnabled.isEmpty || newlinesEnabled.head

  private def findPreviousNewLineSafe =
    if (isNewlinesEnabled && canStartStatement)
      this.findPreviousNewLine
    else
      None

  private def canStartStatement: Boolean = getTokenType match {
    case null => false
    case `kCATCH` |
         `kELSE` |
         `kEXTENDS` |
         `kFINALLY` |
         `kMATCH` |
         `kWITH` |
         `kYIELD` |
         `tCOMMA` |
         `tDOT` |
         `tSEMICOLON` |
         `tCOLON` |
         `tASSIGN` |
         `tFUNTYPE` |
         `ImplicitFunctionArrow` |
         `tCHOOSE` |
         `tUPPER_BOUND` |
         `tLOWER_BOUND` |
         `tVIEW` |
         `tINNER_CLASS` |
         `tLSQBRACKET` |
         `tRSQBRACKET` |
         `tRPARENTHESIS` |
         `tRBRACE` => false
    case `kCASE` =>
      this.predict { builder =>
        builder.getTokenType match {
          case ObjectKeyword |
               ClassKeyword |
               `tIDENTIFIER` => true
          case _ =>
            false
        }
      }
    case _ => true
  }

  private var indentationStack = List(IndentationWidth.initial)

  def isScala3IndentationBasedSyntaxEnabled: Boolean =
    features.indentationBasedSyntaxEnabled

  override def currentIndentationWidth: IndentationWidth =
    indentationStack.head

  override def pushIndentationWidth(width: IndentationWidth): Unit =
    indentationStack ::= width

  override def popIndentationWidth(): IndentationWidth = {
    val top :: rest = indentationStack
    indentationStack = rest
    top
  }
}