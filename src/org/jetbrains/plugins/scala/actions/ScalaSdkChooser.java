package org.jetbrains.plugins.scala.actions;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * User: Dmitry.Krasilschikov
 * Date: 28.09.2006
 * Time: 15:13:03
 */
public class ScalaSdkChooser extends JPanel {
  String sdkGlobalPath = "";

  public ScalaSdkChooser() {
    this.setName("choosing scala sdk");
    this.addKeyListener(new SdkKeyListener());

    this.add(new JLabel("specify scala sdk"));

    JTextField scalaSdkfield = new JTextField();
    scalaSdkfield.setColumns(40);

    SdkKeyListener sdkKeyListener = new SdkKeyListener();

    scalaSdkfield.addKeyListener(sdkKeyListener);
    this.add(scalaSdkfield);
    System.out.println(sdkGlobalPath);

    sdkGlobalPath = sdkKeyListener.getInputString();
  }

  class SdkKeyListener implements KeyListener {
    String sdkGlobalPathOnPanel = "";

    public void keyTyped(KeyEvent keyEvent) {
      if (keyEvent.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
        sdkGlobalPathOnPanel = sdkGlobalPathOnPanel.substring(0, sdkGlobalPathOnPanel.length() - 1);
        return;
      }

      sdkGlobalPathOnPanel += keyEvent.getKeyChar();
      System.out.println("sdk path:" + sdkGlobalPathOnPanel);
    }

    public void keyPressed(KeyEvent keyEvent) {
    }

    public void keyReleased(KeyEvent keyEvent) {
    }

    public String getInputString() {
      return sdkGlobalPathOnPanel;
    }
  }

  public String getSdkGlobalPath() {
    return sdkGlobalPath;
  }
}