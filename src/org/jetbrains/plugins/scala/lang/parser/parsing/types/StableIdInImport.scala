package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 14.03.2008
* Time: 16:17:51
* To change this template use File | Settings | File Templates.
*/

//TODO: fix bad style
object StableIdInImport {
  def parse(builder: PsiBuilder): Boolean = parse(builder, ScalaElementTypes.REFERENCE)
  def parse(builder: PsiBuilder,element: ScalaElementType) : Boolean = parse(builder,true,element)
  def parse(builder: PsiBuilder,dot: Boolean,element: ScalaElementType): Boolean = {
    var stableMarker = builder.mark
    def parseQualId(qualMarker: PsiBuilder.Marker, backMarker: PsiBuilder.Marker): Boolean = {
      //parsing first identifier
      builder.getTokenType match {
        case ScalaTokenTypes.tIDENTIFIER => {
          builder.advanceLexer//Ate identifier
          //Look for dot
          builder.getTokenType match {
            case ScalaTokenTypes.tDOT => {
              if (dot) {
                val dotMarker = builder.mark
                builder.advanceLexer //Ate .
                builder.getTokenType match {
                  case ScalaTokenTypes.tIDENTIFIER => {
                    dotMarker.rollbackTo
                  }
                  case _ => {
                    dotMarker.rollbackTo
                    backMarker.drop
                    qualMarker.done(element)
                    return true
                  }
                }
              }
              val newMarker = qualMarker.precede
              backMarker.drop
              qualMarker.done(element)
              val setMarker = builder.mark
              builder.advanceLexer//Ate dot
              //recursively parse qualified identifier
              parseQualId(newMarker,setMarker)
              return true
            }
            case _ => {
              //It's OK, let's close marker
              backMarker.rollbackTo
              qualMarker.drop
              return true
            }
          }
        }
        case _ => {
          builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
          qualMarker.drop
          backMarker.rollbackTo
          return true
        }
      }
    }
    def parseOtherCases(stableMarker: PsiBuilder.Marker): Boolean = {
      builder.getTokenType match {
        //In this case we of course know - Path.id
        case ScalaTokenTypes.kTHIS => {
          builder.advanceLexer //Ate this
          val stableMarker2 = stableMarker.precede
          stableMarker.done(ScalaElementTypes.THIS_REFERENCE)
          builder.getTokenType match {
                case ScalaTokenTypes.tDOT => {
                  builder.advanceLexer //Ate .
                  builder.getTokenType match {
                    case ScalaTokenTypes.tIDENTIFIER => {
                      builder.advanceLexer //Ate id
                      builder.getTokenType match {
                        case ScalaTokenTypes.tDOT => {
                          val stableMarker3 = stableMarker2.precede
                          stableMarker2.done(ScalaElementTypes.REFERENCE)
                          val backMarker = builder.mark
                          return parseQualId(stableMarker3,backMarker)
                        }
                        case _ => {
                          stableMarker2.done(ScalaElementTypes.REFERENCE)
                          return true
                        }
                      }
                    }
                    case _ => {
                      builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
                      stableMarker2.done(ScalaElementTypes.REFERENCE)
                      return true
                    }
                  }
                }
                case _ => {
                  builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
                  stableMarker2.done(element)
                  return true
                }
              }
        }
        //In this case of course it's super[id].id
        case ScalaTokenTypes.kSUPER => {
          builder.advanceLexer //Ate super
          builder.getTokenType match {
            case ScalaTokenTypes.tDOT => {
              val stableMarker2 = stableMarker.precede
              stableMarker.done(ScalaElementTypes.SUPER_REFERENCE)
              builder.getTokenType match {
                case ScalaTokenTypes.tDOT => {
                  builder.advanceLexer //Ate .
                  builder.getTokenType match {
                    case ScalaTokenTypes.tIDENTIFIER => {
                      builder.advanceLexer //Ate id
                      builder.getTokenType match {
                        case ScalaTokenTypes.tDOT => {
                          val stableMarker3 = stableMarker2.precede
                          stableMarker2.done(ScalaElementTypes.REFERENCE)
                          val backMarker = builder.mark
                          return parseQualId(stableMarker3,backMarker)
                        }
                        case _ => {
                          stableMarker2.done(ScalaElementTypes.REFERENCE)
                          return true
                        }
                      }
                    }
                    case _ => {
                      builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
                      stableMarker2.done(ScalaElementTypes.REFERENCE)
                      return true
                    }
                  }
                }
                case _ => {
                  builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
                  stableMarker2.done(element)
                  return true
                }
              }
            }
            case ScalaTokenTypes.tLSQBRACKET => {
              builder.advanceLexer //Ate [
              builder.getTokenType match {
                case ScalaTokenTypes.tIDENTIFIER => {
                  builder.advanceLexer //Ate id
                }
                case _ => {
                  builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
                }
              }
              builder.getTokenType match {
                case ScalaTokenTypes.tRSQBRACKET => {
                  builder.advanceLexer //Ate ]
                }
                case _ => {
                  builder error ScalaBundle.message("rsqbracket.expected", new Array[Object](0))
                }
              }
              val stableMarker2 = stableMarker.precede
              stableMarker.done(ScalaElementTypes.SUPER_REFERENCE)
              builder.getTokenType match {
                case ScalaTokenTypes.tDOT => {
                  builder.advanceLexer //Ate .
                  builder.getTokenType match {
                    case ScalaTokenTypes.tIDENTIFIER => {
                      builder.advanceLexer //Ate id
                      builder.getTokenType match {
                        case ScalaTokenTypes.tDOT => {
                          val stableMarker3 = stableMarker2.precede
                          stableMarker2.done(ScalaElementTypes.REFERENCE)
                          val backMarker = builder.mark
                          return parseQualId(stableMarker3,backMarker)
                        }
                        case _ => {
                          stableMarker2.done(ScalaElementTypes.REFERENCE)
                          return true
                        }
                      }
                    }
                    case _ => {
                      builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
                      stableMarker2.done(ScalaElementTypes.REFERENCE)
                      return true
                    }
                  }
                }
                case _ => {
                  builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
                  stableMarker2.done(element)
                  return true
                }
              }
            }
            case _ => {
              builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
              stableMarker.done(ScalaElementTypes.SUPER_REFERENCE)
              return true
            }
          }
        }
        case _ => {
          stableMarker.rollbackTo
          return false
        }
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate identifier
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT => {
            builder.advanceLexer //Ate .
            val newMarker = stableMarker.precede
            if (parseOtherCases(stableMarker)) {
              newMarker.drop
              return true
            }
            else {
              newMarker.rollbackTo
              stableMarker = builder.mark
              val backMarker = builder.mark
              return parseQualId(stableMarker,backMarker)
            }
          }
          case _ => {
            stableMarker.done(element)
            return true
          }
        }
      }
      case _ => {
        return parseOtherCases(stableMarker)
      }
    }
  }
}