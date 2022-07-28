package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import org.jetbrains.plugins.scala.ScalaBundle

import java.awt.event.{ItemEvent, ItemListener}
import javax.swing._

class DefaultValuesUsagePanel(labelText: String = ScalaBundle.message("default.values")) extends JPanel {
  private val myRbModifyCalls: JRadioButton = new JRadioButton
  private val myRbAddDefaultArg: JRadioButton = new JRadioButton

  init()

  def init(): Unit = {
    val boxLayout: BoxLayout = new BoxLayout(this, BoxLayout.X_AXIS)
    setLayout(boxLayout)

    add(new JLabel(labelText))
    myRbAddDefaultArg.setText(ScalaBundle.message("add.to.definition"))
    myRbModifyCalls.setText(ScalaBundle.message("modify.method.calls"))
    myRbAddDefaultArg.setMnemonic('d')
    myRbModifyCalls.setMnemonic('m')
    myRbModifyCalls.setSelected(true)

    add(myRbModifyCalls)
    add(myRbAddDefaultArg)
    add(Box.createHorizontalGlue)
    

    val bg: ButtonGroup = new ButtonGroup
    Seq(myRbAddDefaultArg, myRbModifyCalls).foreach(bg.add)

    val listener = new ItemListener {
      override def itemStateChanged(e: ItemEvent): Unit = {
        stateModified()
      }
    }
    myRbModifyCalls.addItemListener(listener)
    myRbAddDefaultArg.addItemListener(listener)
  }


  protected def stateModified(): Unit = {
  }

  def isAddDefaultArgs: Boolean = myRbAddDefaultArg.isSelected

  def forceIsModifyCalls(): Unit = {
    myRbModifyCalls.setSelected(true)
    myRbModifyCalls.setEnabled(false)
    myRbAddDefaultArg.setEnabled(false)
  }

  def release(): Unit = {
    myRbModifyCalls.setEnabled(true)
    myRbAddDefaultArg.setEnabled(true)
  }
}
