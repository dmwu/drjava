package edu.rice.cs.drjava.model;

import javax.swing.text.*;
import javax.swing.ListModel;
import javax.swing.DefaultListModel;
import java.io.*;
import java.util.*;

import edu.rice.cs.util.swing.FindReplaceMachine;

import edu.rice.cs.drjava.DrJava;
import edu.rice.cs.util.UnexpectedException;
import edu.rice.cs.drjava.model.definitions.*;
import edu.rice.cs.drjava.model.repl.*;
import edu.rice.cs.drjava.model.compiler.*;

/**
 * Handles the bulk of DrJava's program logic.
 * The UI components interface with the GlobalModel through its public methods,
 * and GlobalModel responds via the GlobalModelListener interface.
 * This removes the dependency on the UI for the logical flow of the program's
 * features.  With the current implementation, we can finally test the compile
 * functionality of DrJava, along with many other things.
 *
 * @version $Id$
 */
public class DefaultGlobalModel implements GlobalModel {
  private DefinitionsEditorKit _editorKit;
  private DefaultListModel _definitionsDocs;
  private InteractionsDocument _interactionsDoc;
  private Document _consoleDoc;
  private CompilerError[] _compileErrors;  // goes away
  private JavaInterpreter _interpreter;
  private LinkedList _listeners;

  /**
   * Constructor.  Initializes all the documents and the interpreter.
   */
  public DefaultGlobalModel()
  {
    _editorKit = new DefinitionsEditorKit();
    _definitionsDocs = new DefaultListModel();
    _interactionsDoc = new InteractionsDocument();
    _consoleDoc = new DefaultStyledDocument();
    _compileErrors = new CompilerError[0];  // goes away
    _interpreter = new DynamicJavaAdapter();
    _listeners = new LinkedList();
  }


  /**
   * Add a listener to this global model.
   * @param listener a listener that reacts on events generated by the GlobalModel
   */
  public void addListener(GlobalModelListener listener) {
    _listeners.addLast(listener);
  }

  /**
   * Remove a listener from this global model.
   * @param listener a listener that reacts on events generated by the GlobalModel
   */
  public void removeListener(GlobalModelListener listener) {
    _listeners.remove(listener);
  }

  // getter methods for the private fields

  public DefinitionsEditorKit getEditorKit() {
    return _editorKit;
  }

  public ListModel getDefinitionsDocuments() {
    return _definitionsDocs;
  }

  public Document getInteractionsDocument() {
    return _interactionsDoc;
  }
  public Document getConsoleDocument() {
    return _consoleDoc;
  }
  public CompilerError[] getCompileErrors() {  // this method goes away
    return _compileErrors;
  }


  /**
   * Creates a new document in the definitions pane and
   * adds it to the list of open documents.
   * @return The new open document
   */
  public OpenDefinitionsDocument newFile() {
    final OpenDefinitionsDocument doc = _createOpenDefinitionsDocument();
    doc.getDocument().setFile(null);
    _definitionsDocs.addElement(doc);
    notifyListeners(new EventNotifier() {
      public void notifyListener(GlobalModelListener l) {
        l.newFileCreated(doc);
      }
    });
    return doc;
  }

  /**
   * Open a file and read it into the definitions.
   * The provided file selector chooses a file, and on a successful
   * open, the fileOpened() event is fired.
   * @param com a command pattern command that selects what file
   *            to open
   * @return The open document, or null if unsuccessful
   * @exception IOException
   * @exception OperationCanceledException if the open was canceled
   * @exception AlreadyOpenException if the file is already open
   */
  public OpenDefinitionsDocument openFile(FileOpenSelector com)
    throws IOException, OperationCanceledException, AlreadyOpenException
  {
    DefinitionsDocument tempDoc = (DefinitionsDocument)
      _editorKit.createDefaultDocument();
    try {
      final File file = com.getFile();

      OpenDefinitionsDocument openDoc = _getOpenDocument(file);
      if (openDoc != null) {
        throw new AlreadyOpenException(openDoc);
      }

      _editorKit.read(new FileReader(file), tempDoc, 0);
      tempDoc.setFile(file);
      tempDoc.resetModification();

      final OpenDefinitionsDocument doc =
        new DefinitionsDocumentHandler(tempDoc);
      _definitionsDocs.addElement(doc);

      notifyListeners(new EventNotifier() {
        public void notifyListener(GlobalModelListener l) {
          l.fileOpened(doc);
        }
      });

      return doc;
    }
    catch (BadLocationException docFailed) {
      throw new UnexpectedException(docFailed);
    }
  }

  /**
   * Closes an open definitions document, prompting to save if
   * the document has been changed.  Returns whether the file
   * was successfully closed.
   * @return true if the document was closed
   */
  public boolean closeFile(OpenDefinitionsDocument doc) {
    boolean canClose = doc.canAbandonFile();
    final OpenDefinitionsDocument closedDoc = doc;
    if (canClose) {
      // Only fire event if doc exists and was removed from list
      if (_definitionsDocs.removeElement(doc)) {
        notifyListeners(new EventNotifier() {
          public void notifyListener(GlobalModelListener l) {
            l.fileClosed(closedDoc);
          }
        });
        return true;
      }
    }
    return false;
  }

  /**
   * Attempts to close all open documents.
   * @return true if all documents were closed
   */
  public boolean closeAllFiles() {
    boolean keepClosing = true;
    while (!_definitionsDocs.isEmpty() && keepClosing) {
      OpenDefinitionsDocument openDoc = (OpenDefinitionsDocument)
        _definitionsDocs.get(0);
      keepClosing = closeFile(openDoc);
    }
    return keepClosing;
  }

  /**
   * Exits the program.
   * Only quits if all documents are successfully closed.
   */
  public void quit() {
    if (closeAllFiles()) {
      System.exit(0);
    }
  }
  
  /**
   * Returns the OpenDefinitionsDocument for the specified
   * File, opening a new copy if one is not already open.
   * @param file File contained by the document to be returned
   * @return OpenDefinitionsDocument containing file
   */
  public OpenDefinitionsDocument getDocumentForFile(File file) 
    throws IOException, OperationCanceledException
  {
    // Check if this file is already open
    OpenDefinitionsDocument doc = _getOpenDocument(file);
    if (doc == null) {
      // If not, open and return it
      final File f = file;
      FileOpenSelector selector = new FileOpenSelector() {
        public File getFile() throws OperationCanceledException {
          return f;
        }
      };
      try {
        doc = openFile(selector);
      }
      catch (AlreadyOpenException aoe) {
        doc = aoe.getOpenDocument();
      }
    }
    return doc;
  }

  /**
   * Set the indent tab size for all definitions documents.
   * @param indent the number of spaces to make per level of indent
   */
  void setDefinitionsIndent(int indent) {
    for (int i = 0; i < _definitionsDocs.size(); i++) {
      OpenDefinitionsDocument doc = (OpenDefinitionsDocument)
        _definitionsDocs.get(i);
      doc.setDefinitionsIndent(indent);
    }
  }



  /**
   * Clears and resets the interactions pane.
   * First it makes sure it's in the right package given the
   * package specified by the definitions.  If it can't,
   * the package for the interactions becomes the defualt
   * top level. In either case, this method calls a helper
   * which fires the interactionsReset() event.
   */
  public void resetInteractions() {
    try {
      File[] sourceRoots = getSourceRootSet();
      _resetInteractions(sourceRoots);
    }
    catch (InvalidPackageException e) {
      // Oh well, couldn't get package. Just reset the thing
      // without adding to the classpath.
      _resetInteractions(new File[0]);
    }
  }


  /**
   * Resets the console.
   * Fires consoleReset() event.
   */
  public void resetConsole() {
    try {
      _consoleDoc.remove(0, _consoleDoc.getLength());
    }
    catch (BadLocationException ble) {
      throw new UnexpectedException(ble);
    }

    notifyListeners(new EventNotifier() {
      public void notifyListener(GlobalModelListener l) {
        l.consoleReset();
      }
    });
  }


  /**
   * Forwarding method to remove logical dependency of InteractionsPane on
   * the InteractionsDocument.  Gets the previous interaction in the
   * InteractionsDocument's history and replaces whatever is on the current
   * interactions input line with this interaction.
   */
  public void recallPreviousInteractionInHistory(Runnable failed) {
      if (_interactionsDoc.hasHistoryPrevious()) {
        _interactionsDoc.moveHistoryPrevious();
      }
      else {
        failed.run();
      }
  }

  /**
   * Forwarding method to remove logical dependency of InteractionsPane on
   * the InteractionsDocument.  Gets the next interaction in the
   * InteractionsDocument's history and replaces whatever is on the current
   * interactions input line with this interaction.
   */
  public void recallNextInteractionInHistory(Runnable failed) {
    if (_interactionsDoc.hasHistoryNext()) {
      _interactionsDoc.moveHistoryNext();
    }
    else {
      failed.run();
    }
  }

  /**
   * Interprets the current given text at the prompt in the interactions
   * pane.
   */
  public void interpretCurrentInteraction() {
    try {
      String text = _interactionsDoc.getCurrentInteraction();
      _interactionsDoc.addToHistory(text);
      String toEval = text.trim();
      // Result of interpretation, or JavaInterpreter.NO_RESULT if none.
      Object result;
      // Do nothing but prompt if there's nothing to evaluate!
      if (toEval.length() == 0) {
        result = JavaInterpreter.NO_RESULT;
      }
      else {
        if (toEval.startsWith("java ")) {
          toEval = _testClassCall(toEval);
        }
        result = _interpreter.interpret(toEval);
        String resultStr;
        try {
          resultStr = String.valueOf(result);
        } catch (Throwable t) {
          // Very weird. toString() on result must have thrown this exception!
          // Let's act like DynamicJava would have if this exception were thrown
          // and rethrow as RuntimeException
          throw  new RuntimeException(t.toString());
        }
      }

      if (result != JavaInterpreter.NO_RESULT) {
       _interactionsDoc.insertString(_interactionsDoc.getLength(),
                                     "\n" + String.valueOf(result), null);
      }

      _interactionsDoc.prompt();
    }
    catch (BadLocationException e) {
      throw new UnexpectedException(e);
    }
    catch (Throwable e) {
      String message = e.getMessage();
      // Don't let message be null. Java sadly makes getMessage() return
      // null if you construct an exception without a message.
      if (message == null) {
        message = e.toString();
        e.printStackTrace();
      }
      // Hack to prevent long syntax error messages
      try {
        if (message.startsWith("koala.dynamicjava.interpreter.InterpreterException: Encountered")) {
          _interactionsDoc.insertString(_interactionsDoc.getLength(),
                                        "\nError in evaluation: " +
                                        "Invalid syntax",
                                        null);
        }
        else {
          _interactionsDoc.insertString(_interactionsDoc.getLength(),
                                        "\nError in evaluation: " + message,
                                        null);
        }

        _interactionsDoc.prompt();
      } catch (BadLocationException willNeverHappen) {}
    }
  }

  /**
   * Returns all registered compilers that are actually available.
   * That is, for all elements in the returned array, .isAvailable()
   * is true.
   * This method will never return null or a zero-length array.
   *
   * @see CompilerRegistry#getAvailableCompilers
   */
  public CompilerInterface[] getAvailableCompilers() {
    return CompilerRegistry.ONLY.getAvailableCompilers();
  }

  /**
   * Sets which compiler is the "active" compiler.
   *
   * @param compiler Compiler to set active.
   *
   * @see #getActiveCompiler
   * @see CompilerRegistry#setActiveCompiler
   */
  public void setActiveCompiler(CompilerInterface compiler) {
    CompilerRegistry.ONLY.setActiveCompiler(compiler);
  }

  /**
   * Gets the compiler is the "active" compiler.
   *
   * @see #setActiveCompiler
   * @see CompilerRegistry#getActiveCompiler
   */
  public CompilerInterface getActiveCompiler() {
    return CompilerRegistry.ONLY.getActiveCompiler();
  }

  /**
   * Gets an array of all sourceRoots for the open definitions
   * documents, without duplicates.
   * @throws InvalidPackageException if the package statement in one
   *  of the open documents is invalid.
   */
  public File[] getSourceRootSet() throws InvalidPackageException {
    LinkedList roots = new LinkedList();

    for (int i = 0; i < _definitionsDocs.size(); i++) {
      OpenDefinitionsDocument doc = (OpenDefinitionsDocument)
        _definitionsDocs.get(i);
      File root = doc.getSourceRoot();

      // Don't add duplicate Files, based on path
      if (!roots.contains(root)) {
        roots.add(root);
      }
    }

    return (File[]) roots.toArray(new File[0]);
  }



  // ---------- DefinitionsDocumentHandler inner class ----------

  /**
   * Inner class to handle operations on each of the open
   * DefinitionsDocuments by the GlobalModel.
   */
  private class DefinitionsDocumentHandler implements OpenDefinitionsDocument {
    private final DefinitionsDocument _doc;

    /**
     * Constructor.  Initializes this handler's document.
     * @param doc DefinitionsDocument to manage
     */
    DefinitionsDocumentHandler(DefinitionsDocument doc) {
      _doc = doc;
    }

    /**
     * Gets the definitions document being handled.
     * @return document being handled
     */
    public DefinitionsDocument getDocument() {
      return _doc;
    }

    /**
     * Returns whether this document is currently untitled
     * (indicating whether it has a file yet or not).
     * @return true if the document is untitled and has no file
     */
    public boolean isUntitled() {
      return _doc.isUntitled();
    }

    /**
     * Returns the file for this document.  If the document
     * is untitled and has no file, it throws an IllegalStateException.
     * @return the file for this document
     * @exception IllegalStateException if no file exists
     */
    public File getFile() throws IllegalStateException {
      return _doc.getFile();
    }


    /**
     * Saves the document with a FileWriter.  If the file name is already
     * set, the method will use that name instead of whatever selector
     * is passed in.
     * @param com a selector that picks the file name
     * @exception IOException
     */
    public void saveFile(FileSaveSelector com) throws IOException {
      FileSaveSelector realCommand;
      final File file;

      try {
        if (_doc.isUntitled()) {
          realCommand = com;
        }
        else {
          file = _doc.getFile();
          realCommand = new FileSaveSelector() {
            public File getFile() throws OperationCanceledException {
              return file;
            }
          };
        }

        saveFileAs(realCommand);
      }
      catch (IllegalStateException ise) {
        // No file; this should be caught by isUntitled()
        throw new UnexpectedException(ise);
      }
    }

    /**
     * Saves the document with a FileWriter.  The FileSaveSelector will
     * either provide a file name or prompt the user for one.  It is
     * up to the caller to decide what needs to be done to choose
     * a file to save to.  Once the file has been saved succssfully,
     * this method fires fileSave(File).  If the save fails for any
     * reason, the event is not fired.
     * @param com a selector that picks the file name.
     * @exception IOException
     */
    public void saveFileAs(FileSaveSelector com) throws IOException {
      try {
        final OpenDefinitionsDocument openDoc = this;
        final File file = com.getFile();
        FileWriter writer = new FileWriter(file);
        _editorKit.write(writer, _doc, 0, _doc.getLength());
        writer.close();
        _doc.resetModification();
        _doc.setFile(file);
        notifyListeners(new EventNotifier() {
          public void notifyListener(GlobalModelListener l) {
            l.fileSaved(openDoc);
          }
        });
      }
      catch (OperationCanceledException oce) {
        // OK, do nothing as the user wishes.
      }
      catch (BadLocationException docFailed) {
        throw new UnexpectedException(docFailed);
      }
    }

    /**
     * Called to demand that one or more listeners saves the
     * definitions document before proceeding.  It is up to the caller
     * of this method to check if the document has been saved.
     * Fires saveBeforeProceeding(SaveReason) if isModifiedSinceSave() is true.
     * @param reason the reason behind the demand to save the file
     */
    public void saveBeforeProceeding(final GlobalModelListener.SaveReason reason)
    {
      if (isModifiedSinceSave()) {
        notifyListeners(new EventNotifier() {
          public void notifyListener(GlobalModelListener l) {
            l.saveBeforeProceeding(reason);
          }
        });
      }
    }


    /**
     * Starts compiling the source.  Demands that the definitions be
     * saved before proceeding with the compile. If the compile can
     * proceed, a compileStarted event is fired which guarantees that
     * a compileEnded event will be fired when the compile finishes or
     * fails.  If the compilation succeeds, then calls are
     * made to resetConsole() and _resetInteractions(), which fire
     * events of their own, contingent on the conditions.  If the current
     * package as determined by getSourceRoot(String) and getPackageName()
     * is invalid, compileStarted and compileEnded will fire, and
     * an error will be put in compileErrors.
     */
    public void startCompile() {
      saveBeforeProceeding(GlobalModelListener.COMPILE_REASON);

      if (isModifiedSinceSave()) {
        // if the file hasn't been saved after we told our
        // listeners to do so, don't proceed with the rest
        // of the compile.
      }
      else {
        try {
          File file = _doc.getFile();

          // These are the defaults to send to _resetInteractions
          // in the case that we fail to find the package.
          String packageName = "";
          File sourceRoot = null;

          try {
            notifyListeners(new EventNotifier() {
              public void notifyListener(GlobalModelListener l) {
                l.compileStarted();
              }
            });

            packageName = _doc.getPackageName();
            sourceRoot = _getSourceRoot(packageName);

            File[] files = new File[] { file };

            CompilerInterface compiler =CompilerRegistry.ONLY.getActiveCompiler();

            _compileErrors = compiler.compile(sourceRoot, files);
          }
          catch (InvalidPackageException e) {
            CompilerError err = new CompilerError(file.getAbsolutePath(),
                                                  -1,
                                                  -1,
                                                  e.getMessage(),
                                                  false);
            _compileErrors = new CompilerError[] { err };
          }
          finally {
            notifyListeners(new EventNotifier() {
              public void notifyListener(GlobalModelListener l) {
                l.compileEnded();
              }
            });

            // Only clear console/interactions if there were no errors
            if (_compileErrors.length == 0) {
              resetConsole();
              resetInteractions();
            }
          }
        }
        catch (IllegalStateException ise) {
          // No file exists, don't try to compile
        }
      }
    }

    /**
     * Determines if the definitions document has changed since the
     * last save.
     * @return true if the document has been modified
     */
    public boolean isModifiedSinceSave() {
      return _doc.isModifiedSinceSave();
    }

    /**
     * Asks the listeners if the GlobalModel can abandon the current document.
     * Fires the canAbandonFile(File) event if isModifiedSinceSave() is true.
     * @return true if the current document may be abandoned, false if the
     * current action should be halted in its tracks (e.g., file open when
     * the document has been modified since the last save).
     */
    public boolean canAbandonFile() {
      final OpenDefinitionsDocument doc = this;
      if (isModifiedSinceSave()) {
        return pollListeners(new EventPoller() {
          public boolean poll(GlobalModelListener l) {
            return l.canAbandonFile(doc);
          }
        });
      }
      else {
        return true;
      }
    }

    /**
     * Moves the definitions document to the given line, and returns
     * the character position in the document it's gotten to.
     * @param line Number of the line to go to. If line exceeds the number
     *             of lines in the document, it is interpreted as the last line.
     * @return Index into document of where it moved
     */
    public int gotoLine(int line) {
      _doc.gotoLine(line);
      return _doc.getCurrentLocation();
    }

    /**
     * Forwarding method to sync the definitions with whatever view
     * component is representing them.
     */
    public void syncCurrentLocationWithDefinitions(int location) {
      _doc.setCurrentLocation(location);
    }

    /**
     * Get the location of the cursor in the definitions according
     * to the definitions document.
     */
    public int getCurrentDefinitionsLocation() {
      return _doc.getCurrentLocation();
    }

   /**
     * Forwarding method to find the match for the closing brace
     * immediately to the left, assuming there is such a brace.
     * @return the relative distance backwards to the offset before
     *         the matching brace.
     */
    public int balanceBackward() {
      return _doc.balanceBackward();
    }

    /**
     * Set the indent tab size for this document.
     * @param indent the number of spaces to make per level of indent
     */
    public void setDefinitionsIndent(int indent) {
      _doc.setIndent(indent);
    }

    /**
     * A forwarding method to indent the current line or selection
     * in the definitions.
     */
    public void indentLinesInDefinitions(int selStart, int selEnd) {
      _doc.indentLines(selStart, selEnd);
    }

    /**
     * Create a find and replace mechanism starting at the current
     * character offset in the definitions.
     */
    public FindReplaceMachine createFindReplaceMachine() {
      try {
        return new FindReplaceMachine(_doc, _doc.getCurrentLocation());
      }
      catch (BadLocationException e) {
        throw new UnexpectedException(e);
      }
    }

    /**
     * Finds the root directory of the source files.
     * @return The root directory of the source files,
     *         based on the package statement.
     * @throws InvalidPackageException If the package statement is invalid,
     *                                 or if it does not match up with the
     *                                 location of the source file.
     */
    public File getSourceRoot() throws InvalidPackageException
    {
      return _getSourceRoot(_doc.getPackageName());
    }

    /**
     * Finds the root directory of the source files.
     * @param packageName Package name, already fetched from the document
     * @return The root directory of the source files,
     *         based on the package statement.
     * @throws InvalidPackageException If the package statement is invalid,
     *                                 or if it does not match up with the
     *                                 location of the source file.
     */
    private File _getSourceRoot(String packageName)
      throws InvalidPackageException
    {
      File sourceFile;
      try {
        sourceFile = _doc.getFile();
      }
      catch (IllegalStateException ise) {
        throw new InvalidPackageException(-1, "Can not get source root for " +
                                              "unsaved file. Please save.");
      }

      if (packageName.equals("")) {
        return sourceFile.getParentFile();
      }

      Stack packageStack = new Stack();
      int dotIndex = packageName.indexOf('.');
      int curPartBegins = 0;

      while (dotIndex != -1)
      {
        packageStack.push(packageName.substring(curPartBegins, dotIndex));
        curPartBegins = dotIndex + 1;
        dotIndex = packageName.indexOf('.', dotIndex + 1);
      }

      // Now add the last package component
      packageStack.push(packageName.substring(curPartBegins));

      File parentDir = sourceFile;
      while (!packageStack.empty()) {
        String part = (String) packageStack.pop();
        parentDir = parentDir.getParentFile();

        if (parentDir == null) {
          throw new RuntimeException("parent dir is null?!");
        }

        // Make sure the package piece matches the directory name
        if (! part.equals(parentDir.getName())) {
          String msg = "The source file " + sourceFile.getAbsolutePath() +
                       " is in the wrong directory or in the wrong package. " +
                       "The directory name " + parentDir.getName() +
                       " does not match the package component " + part + ".";

          throw new InvalidPackageException(-1, msg);
        }
      }

      // OK, now parentDir points to the directory of the first component of the
      // package name. The parent of that is the root.
      parentDir = parentDir.getParentFile();
      if (parentDir == null) {
        throw new RuntimeException("parent dir of first component is null?!");
      }

      return parentDir;
    }

  }

  /**
   * Creates a DefinitionsDocumentHandler for a new DefinitionsDocument,
   * using the DefinitionsEditorKit.
   * @return OpenDefinitionsDocument object for a new document
   */
  private OpenDefinitionsDocument _createOpenDefinitionsDocument() {
    DefinitionsDocument doc = (DefinitionsDocument)
      _editorKit.createDefaultDocument();
    return new DefinitionsDocumentHandler(doc);
  }

  /**
   * Returns the OpenDefinitionsDocument corresponding to the given
   * File, or null if that file is not open.
   * @param file File object to search for
   * @return Corresponding OpenDefinitionsDocument, or null
   */
  private OpenDefinitionsDocument _getOpenDocument(File file) {
    OpenDefinitionsDocument doc = null;

    for (int i=0; ((i < _definitionsDocs.size()) && (doc == null)); i++) {
      OpenDefinitionsDocument thisDoc =
        (OpenDefinitionsDocument) _definitionsDocs.get(i);
      try {
        if (thisDoc.getFile().equals(file)) {
          doc = thisDoc;
        }
      }
      catch (IllegalStateException ise) {
        // No file in thisDoc
      }
    }

    return doc;
  }


  /**
   *Assumes a trimmed String. Returns a string of the main call that the
   *interpretor can use.
   */
  private String _testClassCall(String s) {
    LinkedList ll = new LinkedList();
    if (s.endsWith(";"))
      s = _deleteSemiColon(s);
    StringTokenizer st = new StringTokenizer(s);
    st.nextToken();             //don't want to get back java
    String argument = st.nextToken();           // must have a second Token
    while (st.hasMoreTokens())
      ll.add(st.nextToken());
    argument = argument + ".main(new String[]{";
    ListIterator li = ll.listIterator(0);
    while (li.hasNext()) {
      argument = argument + "\"" + (String)(li.next()) + "\"";
      if (li.hasNext())
        argument = argument + ",";
    }
    argument = argument + "});";
    return  argument;
  }


  /**
   * Private method to keep outsiders from resetting the interactions
   * pane without the GlobalModel's permission.
   * Sets up a new interpreter to clear out the interpreter's environment.
   * If the setup works and the package directory exists,
   * interactionsReset() is fired.
   */
  private void _resetInteractions(File[] sourceRoots) {
    _interactionsDoc.reset();
    _interpreter = new DynamicJavaAdapter();

    for (int i = 0; i < sourceRoots.length; i++) {
      _interpreter.addClassPath(sourceRoots[i].getAbsolutePath());
    }

    //_interpreter.setPackageScope("");

    notifyListeners(new EventNotifier() {
      public void notifyListener(GlobalModelListener l) {
        l.interactionsReset();
      }
    });
  }

  /**
   * Deletes the last character of a string.  Assumes semicolon at the
   * end, but does not check.  Helper for _testClassCall(String).
   * @param s
   * @return
   */
  private String _deleteSemiColon(String s) {
    return  s.substring(0, s.length() - 1);
  }


  /**
   * Allows the GlobalModel to ask its listeners a yes/no question and
   * receive a response.
   * @param EventPoller p the question being asked of the listeners
   * @return the listeners' responses ANDed together, true if they all
   * agree, false if some disagree
   */
  protected boolean pollListeners(EventPoller p) {
    ListIterator i = _listeners.listIterator();
    boolean poll = true;

    while(i.hasNext()) {
      GlobalModelListener cur = (GlobalModelListener) i.next();
      poll = poll && p.poll(cur);
    }
    return poll;
  }

  /**
   * Lets the listeners know some event has taken place.
   * @param EventNotifier n tells the listener what happened
   */
  protected void notifyListeners(EventNotifier n) {
    ListIterator i = _listeners.listIterator();

    while(i.hasNext()) {
      GlobalModelListener cur = (GlobalModelListener) i.next();
      n.notifyListener(cur);
    }
  }

  /**
   * Class model for notifying listeners of an event.
   */
  protected abstract class EventNotifier {
    public abstract void notifyListener(GlobalModelListener l);
  }

  /**
   * Class model for asking listeners a yes/no question.
   */
  protected abstract class EventPoller {
    public abstract boolean poll(GlobalModelListener l);
  }
}
