package org.jetbrains.plugins.scala
package lang.lexer

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
 * User: Dmitry.Naydanov
 * Date: 29.07.14.
 */
class IncrementalLexerHighlightingTest extends ScalaLightCodeInsightFixtureTestAdapter {
  private def genericTestHighlighting(startText: String, typed: Char*) {
    val caretIndex = startText indexOf CARET_MARKER

    val fileText = startText.replace(CARET_MARKER, "")

    myFixture.configureByText("dummy.scala", fileText)
    myFixture.getEditor.getCaretModel moveToOffset caretIndex

    typed foreach {
      case '\r' =>
        CommandProcessor.getInstance.executeCommand(myFixture.getProject, new Runnable {
          def run() {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
          }
        }, "", null)
      case '\n' =>
        CommandProcessor.getInstance().executeCommand(myFixture.getProject, new Runnable {
          def run() {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
          }
        }, "", null)
      case a => myFixture.`type`(a)
    }

    val incSegments = myFixture.getEditor.asInstanceOf[EditorImpl].getHighlighter.asInstanceOf[LexerEditorHighlighter].getSegments

    val secondText = myFixture.getFile.getText
    myFixture.configureByText("dummy.scala", secondText)
    val segments = myFixture.getEditor.asInstanceOf[EditorImpl].getHighlighter.asInstanceOf[LexerEditorHighlighter].getSegments

    assert(incSegments.getSegmentCount == segments.getSegmentCount,
      s"Different segment count for incremental (${incSegments.getSegmentCount}) and full (${segments.getSegmentCount}) highlightings ")

    for (i <- 0 until incSegments.getSegmentCount) {
      val startI = incSegments getSegmentStart i
      val start = segments getSegmentStart i
      assert(start == startI, s"Different segment start in incremental ($startI) and full ($start) highlightings in segment #$i")

      val endI = incSegments getSegmentEnd i
      val end = segments getSegmentEnd i
      assert(endI == end, s"Different segment end in incremental ($endI) and full ($end) highlightings in segment #$i")

      val dataI = incSegments getSegmentData i
      val data = incSegments getSegmentData i
      assert(dataI == data, s"Different segment data in incremental ($dataI) and full ($data) highlightings in segment #$i")
    }
  }

  def testSimple() {
    val text =
      s"""
         |object dummy {
         | val a = 1
         | val b = "ololo"$CARET_MARKER
         | val c = s"ololo$$a"
         |}
       """.stripMargin

    genericTestHighlighting(text, ';')
  }

  def testScl7330() {
    val text = "object ololo {\n" + s"(escritorTexto$CARET_MARKER)\n" +
      "iniBloque(s\"\"\"filename=\"$fich\"\"\"\")\n" + "}"

    genericTestHighlighting(text, ',', ' ', '\r', '\r')
  }

  def testNestedStrings() {
    val text =
      s"""
         |object ololo {
         | val x = s"aaa $${val y = s"ccc $${val z = s"eee $CARET_MARKER fff"} ddd"} bbb"
         |}
       """.stripMargin

    genericTestHighlighting(text, '$', '$')
  }

  def testDiffNestedString() {
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
         |)""".stripMargin

    genericTestHighlighting(text, ',', ' ', '\r', '\r')
  }

  def testBig() {
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

     genericTestHighlighting(text, '\r', ' ', ' ', '\r', '\r')
  }
}
