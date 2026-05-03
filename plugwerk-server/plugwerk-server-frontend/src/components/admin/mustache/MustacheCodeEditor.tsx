/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * This file is part of Plugwerk.
 *
 * Plugwerk is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Plugwerk is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Plugwerk. If not, see <https://www.gnu.org/licenses/>.
 */
import { useMemo, useRef } from "react";
import { Box, useTheme } from "@mui/material";
import CodeMirror, {
  EditorView,
  type ReactCodeMirrorRef,
  type Extension,
} from "@uiw/react-codemirror";
import { html } from "@codemirror/lang-html";
import { tokens } from "../../../theme/tokens";
import { mustacheExtension } from "./mustacheExtension";

export type EditorLanguage = "plain" | "html";

interface MustacheCodeEditorProps {
  value: string;
  onChange: (next: string) => void;
  placeholders: readonly string[];
  language: EditorLanguage;
  ariaLabel: string;
  /** Min height in CSS units (e.g. "320px"). Editor grows beyond this with content. */
  minHeight?: string;
  disabled?: boolean;
  onFocus?: () => void;
}

export interface MustacheCodeEditorHandle {
  /** Insert text at the current selection. Used by placeholder-chip clicks. */
  insertAtCursor: (text: string) => void;
  /** Move focus into the editor. */
  focus: () => void;
}

/**
 * CodeMirror 6 editor wrapper tuned for Mustache-templated mail bodies.
 *
 * One handle is exposed via `ref` so the placeholder chips on the parent
 * page can insert `{{name}}` at the caret without the parent having to
 * reach into CodeMirror state itself.
 *
 * Languages:
 *   - `plain` — no language extension, just word-wrap + Mustache decorations
 *   - `html` — HTML language with autocomplete + tag matching, layered with
 *     Mustache decorations on top
 *
 * Theme: derives the background + foreground from the active MUI palette
 * so light/dark switches at the app level cascade through without an
 * editor-specific toggle.
 */
export const MustacheCodeEditor = Object.assign(
  function MustacheCodeEditor({
    value,
    onChange,
    placeholders,
    language,
    ariaLabel,
    minHeight = "320px",
    disabled,
    onFocus,
    editorRef,
  }: MustacheCodeEditorProps & {
    editorRef?: React.RefObject<MustacheCodeEditorHandle | null>;
  }) {
    const muiTheme = useTheme();
    const isDark = muiTheme.palette.mode === "dark";
    const cmRef = useRef<ReactCodeMirrorRef>(null);

    if (editorRef) {
      // Wire the imperative handle on every render — refs are cheap and
      // forwardRef + useImperativeHandle adds friction here for one method.
      editorRef.current = {
        insertAtCursor(text: string) {
          const view = cmRef.current?.view;
          if (!view) return;
          const { from, to } = view.state.selection.main;
          view.dispatch({
            changes: { from, to, insert: text },
            selection: { anchor: from + text.length },
          });
          view.focus();
        },
        focus() {
          cmRef.current?.view?.focus();
        },
      };
    }

    const extensions = useMemo<Extension[]>(() => {
      const base: Extension[] = [
        EditorView.lineWrapping,
        mustacheExtension(placeholders),
      ];
      if (language === "html") {
        base.unshift(html({ autoCloseTags: true, matchClosingTags: true }));
      }
      return base;
    }, [placeholders, language]);

    return (
      <Box
        // Wrapping a labelled region around the contenteditable so testing
        // libraries + screen readers can find the editor by name.
        role="group"
        aria-label={ariaLabel}
        sx={{
          border: "1px solid",
          borderColor: "divider",
          borderRadius: tokens.radius.input,
          overflow: "hidden",
          background: isDark ? "rgba(255,255,255,0.02)" : tokens.color.white,
          transition: "border-color 120ms ease, box-shadow 120ms ease",
          "&:focus-within": {
            borderColor: tokens.color.primary,
            boxShadow: `0 0 0 3px rgba(15, 98, 254, 0.18)`,
          },
          "& .cm-editor": {
            minHeight,
            fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
            fontSize: "0.85rem",
            lineHeight: 1.55,
          },
          "& .cm-editor.cm-focused": { outline: "none" },
          "& .cm-scroller": { fontFamily: "inherit" },
          "& .cm-gutters": {
            background: isDark ? "rgba(255,255,255,0.04)" : tokens.color.gray10,
            color: tokens.color.gray60,
            borderRight: "1px solid",
            borderRightColor: "divider",
          },
        }}
      >
        <CodeMirror
          ref={cmRef}
          value={value}
          onChange={onChange}
          extensions={extensions}
          theme={isDark ? "dark" : "light"}
          editable={!disabled}
          onFocus={onFocus}
          basicSetup={{
            lineNumbers: language === "html",
            highlightActiveLineGutter: false,
            foldGutter: language === "html",
            dropCursor: true,
            allowMultipleSelections: false,
            indentOnInput: language === "html",
            bracketMatching: true,
            closeBrackets: true,
            autocompletion: false,
            highlightActiveLine: false,
            highlightSelectionMatches: false,
            searchKeymap: false,
          }}
        />
      </Box>
    );
  },
  { displayName: "MustacheCodeEditor" },
);
