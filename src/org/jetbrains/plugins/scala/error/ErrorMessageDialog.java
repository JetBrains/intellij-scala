/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.error;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Roman Chernyatchik
 * @date: Jan 27, 2007
 */

public class ErrorMessageDialog extends JDialog {
    private JPanel myContentPane;
    private JButton myButtonOK;
    private JLabel myIconLabel;
    private JTextPane myTextPane;
    private boolean myIsOk;

    public ErrorMessageDialog(final String msg, final String title, final Icon icon) {
        super();        
        setTitle(title);
        setContentPane(myContentPane);
        setModal(true);
        getRootPane().setDefaultButton(myButtonOK);

        myButtonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        myIconLabel.setIcon(icon);
        myIconLabel.setText(null);

        myTextPane.setEditorKit(new HTMLEditorKit());
        myTextPane.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent e) {
                final HyperlinkEvent.EventType eventType = e.getEventType();
                if (eventType.equals(HyperlinkEvent.EventType.ACTIVATED)) {
                    BrowserUtil.launchBrowser(e.getURL().toExternalForm());
                } else if (eventType.equals(HyperlinkEvent.EventType.ENTERED)) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else if (eventType.equals(HyperlinkEvent.EventType.EXITED)) {
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        });
        myTextPane.setBackground(myContentPane.getBackground());
        myTextPane.setText(msg);

// call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

// call onCancel() on ESCAPE
        myContentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    public static boolean showInfoMessage(final String message,
                                          final String title,
                                          final Component parent) {
        final ErrorMessageDialog dialog =
                new ErrorMessageDialog(message, title, Messages.getInformationIcon());
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return dialog.myIsOk;
    }

    private void onCancel() {
        myIsOk = false;
        dispose();
    }

    private void onOK() {
        myIsOk = true;
        dispose();
    }
}
