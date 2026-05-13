# Branding test data

Hand-rolled SVG fixtures for #254. Use them against a running
Plugwerk instance to exercise the upload, sanitiser, and validation
paths without having to design your own logos.

The bundled bytes are deliberately small so the table column stays
modest; the test cases focus on **behaviour**, not visual quality.

## Happy path

Upload these and confirm the corresponding surface (top bar, login
page, favicon) swaps to the ACME variant within one page reload.

| File                  | Slot         | What to verify                                  |
|-----------------------|--------------|-------------------------------------------------|
| `logo-light-acme.svg` | `logo-light` | top bar in **light** theme, login page header  |
| `logo-dark-acme.svg`  | `logo-dark`  | top bar in **dark** theme                       |
| `logomark-acme.svg`   | `logomark`   | browser tab favicon, mobile / collapsed top bar |

## Rejected by validation

Uploads return **HTTP 400** with the validation message in the body.
Nothing is persisted; the slot stays at its previous state.

| File                    | Upload as           | Why it must fail                                  |
|-------------------------|---------------------|---------------------------------------------------|
| `bad-aspect-square.svg` | `logo-light/dark`   | 1:1 instead of the required ~4:1                  |
| `bad-aspect-wide.svg`   | `logomark`          | 8:1 instead of the required ~1:1                  |
| `xxe-doctype.svg`       | any slot            | DOCTYPE / external entities are blocked at parse  |

## Sanitised (upload succeeds, malicious content stripped)

Uploads **succeed** with HTTP 200. After upload, fetch the public
asset and verify the dangerous bits are gone:

```bash
curl -s http://localhost:8080/api/v1/branding/logo-light | grep -i script   # expect: empty
curl -s http://localhost:8080/api/v1/branding/logo-light | grep -i onclick  # expect: empty
curl -s http://localhost:8080/api/v1/branding/logo-light | grep -i javascript  # expect: empty
```

| File                    | Stripped                                       |
|-------------------------|------------------------------------------------|
| `xss-script.svg`        | `<script>` element                             |
| `xss-onclick.svg`       | every `on*` event-handler attribute            |
| `xss-foreign-href.svg`  | `javascript:` and external `xlink:href`        |

## Resetting

To reset a slot back to the bundled default:

```bash
curl -sS -X DELETE \
  -H "Authorization: Bearer $SUPERADMIN_TOKEN" \
  http://localhost:8080/api/v1/admin/branding/logo-light
```

A subsequent `GET /api/v1/branding/logo-light` returns **404** and
the frontend's `useBranding()` hook falls back to `/logo-light.svg`.

## Note on PNG / WebP

Raster fixtures are intentionally omitted — generating them in this
repo would require a system-level converter (`rsvg-convert`,
`ImageMagick`) the contributor toolbox cannot rely on. The
validation surface for raster uploads is identical to SVG (size,
aspect, min/max dimensions); copy any reasonable PNG or WebP into
this directory and upload it through the admin UI to exercise it.
