/*BEGIN_COPYRIGHT_BLOCK
 *
 * Copyright (c) 2001-2015, JavaPLT group at Rice University (drjava@rice.edu)
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the names of DrJava, DrScala, the JavaPLT group, Rice University, nor the
 *      names of its contributors may be used to endorse or promote products
 *      derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This software is Open Source Initiative approved Open Source Software.
 * Open Source Initative Approved is a trademark of the Open Source Initiative.
 * 
 * This file is part of DrScala.  Download the current version of this project
 * from http://www.drscala.org/.
 * 
 * END_COPYRIGHT_BLOCK*/

package edu.rice.cs.drjava.ui;

import javax.swing.*;

import edu.rice.cs.util.swing.ScrollableDialog;

import edu.rice.cs.drjava.DrScala;
import edu.rice.cs.drjava.config.OptionConstants;

/** A JDialog with a scrollable text area and a button panel. Uses DrScala's configurable Main Font in the text area.
 *  @version $Id: DrScalaScrollableDialog.java 5594 2012-06-21 11:23:40Z rcartwright $
 */
public class DrScalaScrollableDialog extends ScrollableDialog {

  /** Creates a new DrScalaScrollableDialog with the default width and height.
   *  @param parent Parent frame for this dialog
   *  @param title Title for this dialog
   *  @param header Message to display at the top of this dialog
   *  @param text Text to insert into the scrollable JTextArea
   */
  public DrScalaScrollableDialog(JFrame parent, String title, String header, String text) {
    this(parent, title, header, text, DEFAULT_WIDTH, DEFAULT_HEIGHT, false);
  }

  /** Creates a new DrScalaScrollableDialog.
   * @param parent Parent frame for this dialog
   * @param title Title for this dialog
   * @param header Message to display at the top of this dialog
   * @param text Text to insert into the scrollable JTextArea
   * @param width Width for this dialog
   * @param height Height for this dialog
   */
  public DrScalaScrollableDialog(JFrame parent, String title, String header,
                                String text, int width, int height) {
    this(parent, title, header, text, width, height, false);
  }
  
  /** Creates a new DrScalaScrollableDialog with the default width and height.
   *  @param parent Parent frame for this dialog
   *  @param title Title for this dialog
   *  @param header Message to display at the top of this dialog
   *  @param text Text to insert into the scrollable JTextArea
   *  @param wrap whether to wrap long lines
   */
  public DrScalaScrollableDialog(JFrame parent, String title, String header, String text, boolean wrap) {
    this(parent, title, header, text, DEFAULT_WIDTH, DEFAULT_HEIGHT, wrap);
  }

  /** Creates a new DrScalaScrollableDialog.
   * @param parent Parent frame for this dialog
   * @param title Title for this dialog
   * @param header Message to display at the top of this dialog
   * @param text Text to insert into the scrollable JTextArea
   * @param width Width for this dialog
   * @param height Height for this dialog
   * @param wrap whether to wrap long lines
   */
  public DrScalaScrollableDialog(JFrame parent, String title, String header,
                                String text, int width, int height, boolean wrap) {
    super(parent, title, header, text, width, height, wrap);
    setTextFont(DrScala.getConfig().getSetting(OptionConstants.FONT_MAIN));
  }
}