package org.jetbrains.plugins.scala.actions;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * User: Dmitry.Krasilschikov
 * Date: 28.09.2006
 * Time: 15:13:03
 */
public class ScalaSdkChooser extends JPanel {
    String sdkGlobalPath = "default scala sdk path";

    public ScalaSdkChooser() {
        this.setName("choosing scala sdk");

        this.addKeyListener(new SdkKeyListener());
    }

    class SdkKeyListener implements KeyListener {
        String sdkGlobalPath;

        public void keyTyped(KeyEvent keyEvent) {
        }

        public void keyPressed(KeyEvent keyEvent) {
        }

        public void keyReleased(KeyEvent keyEvent) {
            if (KeyEvent.VK_ENTER == keyEvent.getKeyCode()) {
                sdkGlobalPath += keyEvent.getKeyChar();
            }
        }

        public String getSdkGlobalPath() {
            return sdkGlobalPath;
        }
    }

    public void addSdkDialog() {
        this.add(new JLabel("specify scala sdk"));

        JTextField scalaSdkfield = new JTextField();
        scalaSdkfield.setColumns(50);

        SdkKeyListener sdkKeyListener = new SdkKeyListener();

        scalaSdkfield.addKeyListener(sdkKeyListener);
        this.add(scalaSdkfield);
                
        sdkGlobalPath = sdkKeyListener.getSdkGlobalPath();
    }


    public String getSdkGlobalPath() {
        return sdkGlobalPath;
    }
}
