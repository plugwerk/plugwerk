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
import {
  Decoration,
  type DecorationSet,
  EditorView,
  ViewPlugin,
  type ViewUpdate,
} from "@codemirror/view";
import {
  autocompletion,
  type CompletionContext,
  type CompletionResult,
} from "@codemirror/autocomplete";
import { type Extension, RangeSetBuilder } from "@codemirror/state";

const MUSTACHE_TAG_RE = /\{\{\{?[#^/!&]?\s*([a-zA-Z_][\w.]*)\s*\}?\}\}/g;
const TAG_CLASS = "cm-mustache-tag";
const KNOWN_PLACEHOLDER_CLASS = "cm-mustache-known";
const UNKNOWN_PLACEHOLDER_CLASS = "cm-mustache-unknown";

/**
 * CodeMirror 6 bundle: tinted Mustache tag highlighting + autocomplete
 * scoped to the registered template placeholders.
 *
 * Highlight colours match the existing Carbon palette so a known
 * `{{username}}` tint reads as primary-blue, an unknown reference as
 * warning-orange (with a wavy underline). Autocomplete fires inside
 * `{{ ... }}` only — we do not pretend the field is JavaScript.
 */
export function mustacheExtension(placeholders: readonly string[]): Extension {
  const known = new Set(placeholders);

  const decorationPlugin = ViewPlugin.fromClass(
    class {
      decorations: DecorationSet;
      constructor(view: EditorView) {
        this.decorations = buildDecorations(view, known);
      }
      update(update: ViewUpdate) {
        if (update.docChanged || update.viewportChanged) {
          this.decorations = buildDecorations(update.view, known);
        }
      }
    },
    {
      decorations: (v) => v.decorations,
    },
  );

  const theme = EditorView.baseTheme({
    [`.${TAG_CLASS}`]: { color: "#6F6F6F" },
    [`.${KNOWN_PLACEHOLDER_CLASS}`]: {
      color: "#0043CE",
      fontWeight: "600",
      backgroundColor: "rgba(15, 98, 254, 0.08)",
      borderRadius: "3px",
      padding: "0 2px",
    },
    [`.${UNKNOWN_PLACEHOLDER_CLASS}`]: {
      color: "#A84400",
      backgroundColor: "rgba(241, 194, 27, 0.18)",
      borderRadius: "3px",
      padding: "0 2px",
      textDecoration: "underline wavy #A84400",
    },
    "&.cm-focused .cm-mustache-known": {
      backgroundColor: "rgba(15, 98, 254, 0.14)",
    },
    "&.cm-dark .cm-mustache-tag": { color: "#A8A8A8" },
    "&.cm-dark .cm-mustache-known": {
      color: "#D0E2FF",
      backgroundColor: "rgba(208, 226, 255, 0.12)",
    },
    "&.cm-dark .cm-mustache-unknown": {
      color: "#FFC069",
      backgroundColor: "rgba(241, 194, 27, 0.22)",
    },
  });

  const completion = autocompletion({
    override: [(ctx) => suggestPlaceholders(ctx, placeholders)],
    activateOnTyping: true,
    defaultKeymap: true,
  });

  return [decorationPlugin, theme, completion];
}

function suggestPlaceholders(
  ctx: CompletionContext,
  placeholders: readonly string[],
): CompletionResult | null {
  const before = ctx.state.sliceDoc(Math.max(0, ctx.pos - 60), ctx.pos);
  const insideTag = /\{\{\{?\s*([a-zA-Z_]\w*)?$/.test(before);
  if (!insideTag && !ctx.explicit) {
    return null;
  }
  const typedMatch = /([a-zA-Z_]\w*)$/.test(before)
    ? /([a-zA-Z_]\w*)$/.exec(before)?.[1]
    : "";
  const typed = typedMatch ?? "";
  const from = ctx.pos - typed.length;
  return {
    from,
    options: placeholders.map((name) => ({
      label: name,
      type: "variable",
      detail: "placeholder",
      apply: name,
    })),
    validFor: /^[a-zA-Z_]\w*$/,
  };
}

function buildDecorations(view: EditorView, known: Set<string>): DecorationSet {
  const builder = new RangeSetBuilder<Decoration>();
  const text = view.state.doc.toString();
  for (const match of text.matchAll(MUSTACHE_TAG_RE)) {
    if (match.index === undefined) continue;
    const start = match.index;
    const end = start + match[0].length;
    const innerName = match[1];
    const isKnown = known.has(innerName);
    builder.add(start, end, Decoration.mark({ class: TAG_CLASS }));
    const innerStart = match[0].indexOf(innerName, 1) + start;
    const innerEnd = innerStart + innerName.length;
    builder.add(
      innerStart,
      innerEnd,
      Decoration.mark({
        class: isKnown ? KNOWN_PLACEHOLDER_CLASS : UNKNOWN_PLACEHOLDER_CLASS,
      }),
    );
  }
  return builder.finish();
}
