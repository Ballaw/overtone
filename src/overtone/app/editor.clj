(ns overtone.app.editor
  (:import
    (java.awt EventQueue Color Font FontMetrics Dimension BorderLayout
              GridBagLayout GridBagConstraints Insets FlowLayout)
    (java.awt.event InputEvent)
    (javax.swing JFrame JPanel JLabel JTree JEditorPane JScrollPane JTextPane
                 JSplitPane JButton JFileChooser KeyStroke)
    (javax.swing.text TextAction JTextComponent)
    (com.sun.scenario.scenegraph JSGPanel SGText SGShape SGGroup
                                 SGAbstractShape$Mode SGComponent SGTransform)
    (jsyntaxpane DefaultSyntaxKit)
    (java.io File))
  (:use (overtone.core event util)
        (overtone.gui swing)
        [clojure.contrib.fcase :only (case)]
        (clojure.contrib duck-streams)))

(def TAB-STOP 4)
(def CARET-COLOR Color/BLACK)

(defonce editor* (ref {}))

(def OPEN-CHARS #{ \( \[ \{ })
(def CLOSE-CHARS #{ \) \] \} })
(def MATCHES { \{ \}
               \} \{
               \[ \]
               \] \[
               \( \)
               \) \( })

; Iterate threw the string putting any OPEN-CHARS onto a stack.  If we hit a CLOSE-CHAR
; and it matches the top of the stack we pop, and if the stack is empty after a match we
; found the end point.  If the top of the stack ever doesn't match then there isn't a
; correct pair.  Reverse everything for going backwards.
(defn match-next
  ([txt start] (match-next txt start 0 (list)))
  ([txt start cnt stack]
    (if (OPEN-CHARS (first txt))
     (recur (next txt) start (inc cnt) (conj stack (first txt)))
     (if (CLOSE-CHARS (first txt))
       (if (= nil (first txt)) ; Finish me!!!
       (recur (next txt) start (inc cnt) (conj stack )))))))

(defn matching-pos [txt pos]
  (let [cur (nth txt pos)]
    (cond
      OPEN-CHARS (match-next txt pos CLOSE-CHARS)
      CLOSE-CHARS (match-next (reverse txt) (- (count txt) pos) OPEN-CHARS))))


(defn- status-panel [editor]
  (let [status-pane (JPanel.)
        general-status (JLabel. "general status")
        stroke-status (JLabel. "stroke status")
        mode-status (JLabel. "mode-status")]

    (doto status-pane
      (.setLayout (GridBagLayout.))
      (.add general-status (GridBagConstraints. 0 0 1 1 1.0 0.0 GridBagConstraints/WEST
                                               GridBagConstraints/HORIZONTAL, (Insets. 0 0 0 0) 111 0))
      (.add stroke-status (GridBagConstraints. 1 0 1 1 0.0 0.0
                                               GridBagConstraints/WEST
                                               GridBagConstraints/VERTICAL
                                               (Insets. 0 2 0 0) 0 0))
      (.add mode-status (GridBagConstraints. 2 0 1 1 0.0 0.0
                                             GridBagConstraints/WEST
                                             GridBagConstraints/VERTICAL
                                             (Insets. 0 2 0 0) 0 0)))))

(defn file-open-dialog [parent & [path]]
  (let [chooser (if path
                  (JFileChooser. path)
                  (JFileChooser.))
        ret (.showOpenDialog chooser parent)]
    (case ret
      JFileChooser/APPROVE_OPTION (-> chooser (.getSelectedFile) (.getAbsolutePath))
      JFileChooser/CANCEL_OPTION nil
      JFileChooser/ERROR_OPTION  nil)))

(defn file-save-dialog [parent & [path]]
  (let [chooser (if path
                  (JFileChooser. path)
                  (JFileChooser.))
        ret (.showSaveDialog chooser parent)]
    (case ret
      JFileChooser/APPROVE_OPTION (-> chooser (.getSelectedFile) (.getAbsolutePath))
      JFileChooser/CANCEL_OPTION nil
      JFileChooser/ERROR_OPTION  nil)))

(load "editor/actions")
(load "editor/keymap")

(defn editor-buttons [editor]
  (let [panel (JPanel. (FlowLayout. FlowLayout/LEFT))
        open (button "Open"
                     "org/freedesktop/tango/16x16/actions/document-open.png"
                     file-open)
        save (button "Save"
                     "org/freedesktop/tango/16x16/actions/document-save.png"
                     file-save)
        save-as (button "Save As"
                        "org/freedesktop/tango/16x16/actions/document-save-as.png"
                        #(if-let [path (file-save-dialog editor)]
                           (file-save-as path)))]
    (doto panel
      (.add open)
      (.add save)
      (.add save-as))
    panel))

(defn editor-panel [app]
    (DefaultSyntaxKit/initKit)
  (let [editor-pane (JPanel.)
        editor (JEditorPane.)
        button-pane (editor-buttons editor)
        scroller (JScrollPane. editor)
        font (.getFont editor)
        fm (.getFontMetrics editor font)
        width (* 81 (.charWidth fm \space))
        height (* 10 (.getHeight fm))
        insert-mode (insert-mode-map editor)]

    (dosync (alter editor* assoc :editor editor
                   :keymaps {:insert insert-mode}
                   :current-keymap :insert))

    (doto button-pane
      (.setBackground (:background app)))

    (doto editor
      (.setKeymap (:keymap insert-mode))
      (.setFont (:edit-font app))
      (.setContentType "text/clojure")
      (.setCaretColor CARET-COLOR)
      (.setBackground (Color. (float 1.0) (float 1.0) (float 1.0)))
      (.requestFocusInWindow))

    (file-open "src/examples/basic.clj")

    (doto editor-pane
      (.setLayout (BorderLayout.))
      (.add button-pane BorderLayout/NORTH)
      (.add scroller BorderLayout/CENTER)
      (.add (status-panel editor) BorderLayout/SOUTH))))

(defn editor-keymap [k]
  (.setKeymap (:editor @editor*) (:keymap k)))
