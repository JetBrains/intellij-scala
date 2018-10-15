package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt._
import java.awt.event._

import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import javax.swing.{JComponent, JFrame, WindowConstants}

object Main {
//  def main(args: Array[String]): Unit = {
//    val frame = new JFrame()
//
//    val font = new Font(Font.DIALOG, Font.PLAIN, 25)
//
////    val metrics = FontInfo.getFontMetrics(font, FontInfo.getFontRenderContext(frame.getContentPane))
//
//    var component0: JComponent = null
//
//    val context: Context = new Context {
//      private val metrics: FontMetrics = fontMetrics(font)
//
//      override def ascent: Int = metrics.getAscent
//
//      override def descent: Int = metrics.getDescent
//
//      override def lineHeight: Int = ascent + descent
//
//      override def charHeight: Int = lineHeight
//
//      override def fontMetrics(font: Font): FontMetrics = frame.getFontMetrics(font)
//
//      override def component: JComponent = component0
//
//      override def font: Font = component.getFont
//    }
//
//    val factory: PresentationFactory = new PresentationFactoryImpl(context)
//
//    val link = new TextAttributes()
//    link.setForegroundColor(Color.BLUE)
//    link.setEffectType(EffectType.LINE_UNDERSCORE)
//    link.setEffectColor(Color.BLUE)
//
//    val pair = new TextAttributes()
//    pair.setBackgroundColor(Color.GRAY)
//    pair.setForegroundColor(Color.YELLOW)
//    pair.setFontType(Font.BOLD)
//
//    import factory._
//
//    val (left, right) = synchronous(attributes(pair, _), background(Color.LIGHT_GRAY, text("(", font)), background(Color.LIGHT_GRAY, text(")", font)))
//
//    val presentation: Presentation =
//      insets(15, 15,
//        rounding(10, 10,
//          background(Color.LIGHT_GRAY,
//            insets(10, 10,
//              sequence(
//                left,
//                background(Color.YELLOW, expansion(sequence(text(" T[", font), expansion(text("Foo", font), text("...", font)), text("] ", font)), text(" ... ", font))),
//                background(Color.GRAY, space(10)),
//                background(Color.GREEN, navigation(attributes(link, _), _ => println("Tooltip"), _ => println("Navigation"), effects(font, text(" Bar ", font)))),
//                background(Color.LIGHT_GRAY, right))))))
//
//    component0 = new JComponent() {
//      override def paint(g: Graphics): Unit = {
//        presentation.paint(g.asInstanceOf[Graphics2D], new TextAttributes())
//      }
//
//      def isValid(e: MouseEvent): Boolean = {
//        (0 <= e.getX && e.getX < presentation.width) &&
//        (0 <= e.getY && e.getY < context.lineHeight)
//      }
//
//      addMouseListener(new MouseAdapter {
//        override def mouseClicked(e: MouseEvent): Unit = if (isValid(e)) {
//          presentation.mouseClicked(e)
//        }
//      })
//
//      addMouseMotionListener(new MouseMotionAdapter {
//        override def mouseMoved(e: MouseEvent): Unit = if (isValid(e)) {
//          presentation.mouseMoved(e)
//        } else {
//          presentation.mouseExited()
//        }
//      })
//
//      addKeyListener(new KeyAdapter {
//        override def keyPressed(e: KeyEvent): Unit = {
//          if (e.getKeyCode == KeyEvent.VK_ESCAPE) {
//            presentation.expand(0)
//          } else if (e.getKeyCode == KeyEvent.VK_ENTER) {
//            presentation.expand(100)
//          } else {
//            // Why, in Windows, Control key press events are generated on mouse movement?
//            if (e.getKeyCode != KeyEvent.VK_CONTROL) {
//              presentation.mouseExited()
//            }
//          }
//        }
//
//        override def keyReleased(e: KeyEvent): Unit = {
//          presentation.mouseExited()
//        }
//      })
//    }
//
//    presentation.addPresentationListener(new PresentationListener {
//      override def contentChanged(area: Rectangle): Unit = component0.repaint()
//
//      override def sizeChanged(previous: Dimension, current: Dimension): Unit = component0.repaint()
//    })
//
//    frame.getContentPane.setLayout(null)
//    frame.getContentPane.add(component0)
//    frame.setPreferredSize(new Dimension(800, 600))
//    frame.pack()
//    component0.setSize(500, 500)
//    component0.setLocation(100, 100)
//    component0.setFocusable(true)
//    frame.setLocationRelativeTo(null)
//    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
//    frame.setVisible(true)
//  }
}
