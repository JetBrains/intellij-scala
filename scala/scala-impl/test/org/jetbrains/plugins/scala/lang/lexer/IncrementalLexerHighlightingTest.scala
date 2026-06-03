package org.jetbrains.plugins.scala
package lang
package lexer

import com.intellij.openapi.editor.ex.util.{LexerEditorHighlighter, SegmentArrayWithData}
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.junit.Assert.assertEquals

class IncrementalLexerHighlightingTest extends EditorActionTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  private def segments() = {
    val editor = myFixture.getEditor match {
      case impl: EditorImpl => impl
    }

    val highlighter = editor.getHighlighter match {
      case impl: LexerEditorHighlighter => impl
    }

    highlighter.getSegments
  }

  private def doTest(text: String, typed: Char*): Unit = {
    configureByText(text)

    typed.foreach {
      case '\r' => performBackspaceAction()
      case '\n' => performEnterAction()
      case char => performTypingAction(char)
    }
    val actualSegments = segments()

    myFixture.configureByText(fileType, myFixture.getFile.getText)
    val expectedSegments = segments()

    assertEquals(actualSegments.getSegmentCount, expectedSegments.getSegmentCount)

    import IncrementalLexerHighlightingTest.asTuples
    asTuples(actualSegments)
      .zip(asTuples(expectedSegments))
      .foreach {
        case (actual, expected) => assertEquals(actual, expected)
      }
  }

  def testSimple(): Unit = {
    val text =
      s"""
         |object dummy {
         | val a = 1
         | val b = "ololo"$CARET_MARKER
         | val c = s"ololo$$a"
         |}
       """.stripMargin

    doTest(text, ';')
  }

  def testScl7330(): Unit = {
    val text = "object ololo {\n" + s"(escritorTexto$CARET_MARKER)\n" +
      "iniBloque(s\"\"\"filename=\"$fich\"\"\"\")\n" + "}"

    doTest(text, ',', ' ', '\r', '\r')
  }

  def testNestedStrings(): Unit = {
    val text =
      s"""
         |object ololo {
         | val x = s"aaa $${val y = s"ccc $${val z = s"eee $CARET_MARKER fff"} ddd"} bbb"
         |}
       """.stripMargin

    doTest(text, '$', '$')
  }

  def testDiffNestedString(): Unit = {
    val text =
      s"""
         |fooboo(
         | s${"\"\"\""}
         | $${val yy = s"aaa $${
         |   val zz =
         |     s${"\"\"\""}
         |       Boo!
         |     ${"\"\"\""}.stripMargin
         |   } bbb"}
         | ${"\"\"\""}$CARET_MARKER
         |)""".stripMargin.replace("\r", "")

    doTest(text, ',', ' ', '\r', '\r')
  }

  /**
    * That relates straight to incremental highlighting - see SCL-8958 itself and comment to
    * [[ScalaLexer#previousToken]]
    */
  def testScl8958(): Unit = {
    val before =
      s"""
         |class Test {
         |  val test1 = <div></div>
         |}
         |
        |class Test2 {$CARET_MARKER}
      """.stripMargin

    val after =
      s"""
         |class Test {
         |  val test1 = <div></div>
         |}
         |
        |class Test2 {
         |  $CARET_MARKER
         |}
      """.stripMargin

    checkGeneratedTextAfterEnter(before, after)
  }

  def testInterpolatedString(): Unit = {
    val text = "s\"\"\"\n    ${if (true)" + CARET_MARKER + "}\n\n\"\"\"\n{}\nval a = 1"
    doTest(text, ' ')
  }

  def testBig(): Unit = {
    val text =
      s"""package es.fcc.bibl.bd

import javax.xml.parsers.DocumentBuilderFactory

/** Sincronizacion bidireccional del contenido de tablas con el servidor.
  *
  *
  * Uso:
  * - Establecer antes autentificacion si es necesario: {{{
  *   Authenticator.setDefault(new Authenticator() {
  *     override def getPasswordAuthentication() = new PasswordAuthentication("miusuario", "micontraseña")
  *   })
  * }}}
  * Pendiente:
  * - Soporte de redirecciones
  * Ver https://fccmadoc.atlassian.net/browse/RDS-4858 para especificaciones.
  *
  * @param servidor         Servidor a donde enviar, sin dominio fccma.com
  *                   de las tablas de las que se depende.
  * @param mensaje          Mensaje que aparecen en la notificacion de progreso.*/
class Sincronizador(servidor: String, ruta: String, soporte: String, tblsProcesar: Seq[TablaBase] = Seq(),
  params: Map[String, String] = Map.empty, mensaje: String = "Sincronizar datos")(implicit ctx: Activity) {
  protected val msj = ListBuffer[String]()
  /** Subconjunto de [[tblsProcesar]] que van sincronizadas bidireccionalmente.*/
  protected val tblsEnviar = tblsProcesar.collect { case t: TablaSincronizada[_] => t }


  future {
    val url = s"https://$$servidor.fccma.com/fccma/"
    if (hayErrores)
      notif.setSmallIcon(R.drawable.stat_notify_sync_error)
    notif.setOngoing(false)
    if (msj.size > 0) {
      val detalles = new NotificationCompat.InboxStyle()
      detalles.setBigContentTitle("Avisos:")
      msj.foreach(detalles.addLine(_))
      notif.setStyle(detalles)
    }
    notifica()
  }

  /** Fabrica el XML que va dentro de la peticion.
    * Metodo con test.*/
  protected[bd] def fabricaXml(os: OutputStream) {
    val esc = new OutputStreamWriter(os, "UTF-8")
    esc.write(s"Content-Type: multipart/mixed;boundary=$$frontera\r\n")
    iniBloque()
    val totalFilas = (for (t <- tblsEnviar) yield t.cuantos).sum
    val gen = new GeneradorNombresFichero
    notif.setContentText("Enviando cambios locales")
    notif.setProgress(totalFilas, 0, false)
    notifica()
    var filas = 0

    new EscritorXml(esc) {
      ele("Sincronizacion") {
        ele("soporte") {
          ele("nombre") { txt(soporte) }
        }
      }
    }
    if (gen.contador > 0) {
      notif.setContentTitle("Enviando binarios")
      notif.setProgress(gen.contador, 0, false)
      notifica()
      val gen2 = new GeneradorNombresFichero
      for {
        tbl <- tblsEnviar
        blob <- tbl.leeBlobs
      } {
        val fich = gen2.genera(blob)
        iniBloque(s${"\"\"\""}Content-Disposition:form-data;name="$$fich";filename="$$fich" $CARET_MARKER${"\"\"\""}, "Content-Transfer-Encoding:binary")
        // No hace falta seguramente pero por si acaso
        notif.setProgress(gen.contador, gen2.contador, false)
        notifica()
      }
    }
    // Cerrar multipart MIME
    esc.write(s"\r\n--$$frontera--\r\n")
    esc.close()
  }
}"""

    doTest(text, '\r', ' ', ' ', '\r', '\r')
  }

  def testScl9396(): Unit = {
    val text =
      """
        |package azaza
        |
        |
        |object Main {
        |  def fooboo() {
        |    val paymentType = 123
        |
        |    if (true) {
        |      """ + CARET_MARKER +
        """"Unsupported payment type: [$paymentType]" ; val a = 1
          |    }
          |  }
          |}
        """.stripMargin

    doTest(text, 's')
  }
}

object IncrementalLexerHighlightingTest {
  private def asTuples(array: SegmentArrayWithData) =
    for (i <- 0 until array.getSegmentCount)
      yield (array.getSegmentStart(i), array.getSegmentEnd(i), array.getSegmentData(i))
}
