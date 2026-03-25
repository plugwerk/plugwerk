# PlugWerk – Web UI Design Briefing

**Document Type:** Design Briefing for Figma and HTML Templates  
**Project:** PlugWerk – Plugin Management and Marketplace Software for the Java/PF4J Ecosystem
**Created:** March 21, 2026 | Version 2.0  
**Audience:** Web Designers, UI/UX Designers, Frontend Developers

---

## 1. About the Product

PlugWerk is **self-hosted plugin management and marketplace software** for Java applications built on the PF4J framework. Organizations deploy PlugWerk on their own infrastructure (Docker / Kubernetes) to provide a central catalog where users can discover, explore, and download plugins for their PF4J-based products.

**This is not a SaaS product.** There is no public marketing site, no pricing page, and no signup funnel. The web UI is the application itself — deployed inside an organization's network or on a public server managed by the operator. The first thing a user sees is the plugin catalog, not a landing page.

**Core Metaphor:** PlugWerk combines "Plugin" with the German word "Werk" (factory / craftsmanship). The visual language should convey **engineering excellence, precision, and German quality standards** — while remaining **modern, open, and developer-friendly**. Not a cold enterprise look, but a technical product with character.

**Reference Products (UX Benchmarks):**
- JetBrains Marketplace (catalog UX, detail pages)
- Gradle Plugin Portal (developer-centric design, clean catalog)
- Docker Hub (search and filter logic)
- VS Code Extensions Marketplace (card layout, badges)
- Gitea / Forgejo (self-hosted software aesthetic — functional, clean, no marketing fluff)

---

## 2. Design Foundations

### 2.1 Color Palette

#### Primary Colors

| Role              | Hex       | RGB             | Usage                                            |
|-------------------|-----------|-----------------|--------------------------------------------------|
| **Primary**       | `#0F62FE` | 15, 98, 254     | CTAs, links, active elements, primary buttons    |
| **Primary Dark**  | `#0043CE` | 0, 67, 206      | Hover state, header accents                      |
| **Primary Light** | `#D0E2FF` | 208, 226, 255   | Badges, tag backgrounds, subtle highlights       |

#### Secondary Colors

| Role               | Hex       | RGB             | Usage                                            |
|--------------------|-----------|-----------------|--------------------------------------------------|
| **Secondary**      | `#6929C4` | 105, 41, 196    | Secondary accents, plugin category badges        |
| **Success**        | `#198038` | 25, 128, 56     | Status messages, "Published", verification icons |
| **Warning**        | `#F1C21B` | 241, 194, 27    | Deprecation notices, compatibility warnings      |
| **Danger**         | `#DA1E28` | 218, 30, 40     | Errors, "Yanked" status, critical warnings       |

#### Neutral Tones

| Role               | Hex       | Usage                                            |
|--------------------|-----------|--------------------------------------------------|
| **Gray 100**       | `#161616` | Primary text, headlines                          |
| **Gray 80**        | `#393939` | Secondary text, subheadlines                     |
| **Gray 60**        | `#6F6F6F` | Tertiary text, descriptions, timestamps          |
| **Gray 40**        | `#A8A8A8` | Placeholder text, disabled elements              |
| **Gray 20**        | `#E0E0E0` | Borders, dividers, table rules                   |
| **Gray 10**        | `#F4F4F4` | Background surfaces                              |
| **White**          | `#FFFFFF` | Cards, content area, input fields                |

#### Dark Mode (mandatory)

| Role               | Hex       | Usage                                            |
|--------------------|-----------|--------------------------------------------------|
| **Background**     | `#161616` | Page background                                  |
| **Surface**        | `#262626` | Cards, panels                                    |
| **Surface Raised** | `#393939` | Dropdowns, tooltips, overlays                    |
| **Text Primary**   | `#F4F4F4` | Primary text                                     |
| **Text Secondary** | `#C6C6C6` | Secondary text                                   |
| **Border**         | `#525252` | Borders, dividers                                |

> **Note:** Dark mode is not a nice-to-have for the developer audience — it is mandatory. All pages must be designed in both modes.

### 2.2 Typography

| Element             | Font              | Weight        | Size      | Line Height |
|---------------------|-------------------|---------------|-----------|-------------|
| **H1 (Page Title)** | Inter             | 700 (Bold)    | 36px      | 44px        |
| **H2 (Section)**    | Inter             | 600 (SemiBold)| 28px      | 36px        |
| **H3 (Subsection)** | Inter             | 600 (SemiBold)| 22px      | 28px        |
| **H4 (Card Title)** | Inter             | 600 (SemiBold)| 18px      | 24px        |
| **Body**            | Inter             | 400 (Regular) | 16px      | 24px        |
| **Body Small**      | Inter             | 400 (Regular) | 14px      | 20px        |
| **Caption**         | Inter             | 400 (Regular) | 12px      | 16px        |
| **Code / Monospace**| JetBrains Mono    | 400 (Regular) | 14px      | 20px        |
| **Code Block**      | JetBrains Mono    | 400 (Regular) | 13px      | 20px        |
| **Badge / Tag**     | Inter             | 500 (Medium)  | 12px      | 16px        |

**Fallback Stack:** `Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif`  
**Code Fallback:** `'JetBrains Mono', 'Fira Code', 'Source Code Pro', monospace`

### 2.3 Spacing & Grid

- **Base Unit:** 8px
- **Layout Grid:** 12 columns, max width `1280px`, gutter `24px`, page margin `32px`
- **Spacing Scale:** 4 · 8 · 12 · 16 · 24 · 32 · 48 · 64 · 96px
- **Card Padding:** 24px
- **Section Spacing:** 48px (vertical gap between sections)

### 2.4 Border Radius & Shadows

| Element           | Radius  | Shadow                                        |
|-------------------|---------|------------------------------------------------|
| Buttons           | 6px     | none (flat design)                              |
| Cards             | 8px     | `0 1px 3px rgba(0,0,0,0.08)`                   |
| Cards (Hover)     | 8px     | `0 4px 12px rgba(0,0,0,0.12)`                  |
| Inputs            | 6px     | none                                            |
| Modals / Dialogs  | 12px    | `0 8px 32px rgba(0,0,0,0.16)`                  |
| Tooltips          | 6px     | `0 2px 8px rgba(0,0,0,0.12)`                   |
| Avatars / Icons   | 50%     | none                                            |

### 2.5 Iconography

- **Icon Set:** Lucide Icons (open source, consistent, developer-friendly)
- **Icon Sizes:** 16px (inline), 20px (buttons/navigation), 24px (feature icons), 32px (illustrations)
- **Stroke Width:** 1.5px (consistent with Lucide default)
- **Plugin Icons:** Square, 128×128px, PNG or SVG, displayed with 8px border radius

### 2.6 Logo Requirements

The logo must be available in the following variants:

| Variant              | Usage                                   |
|----------------------|-----------------------------------------|
| Logo + Wordmark      | Header (desktop)                        |
| Logomark Only        | Favicon, mobile header                  |
| Wordmark Only        | Footer                                  |
| Inverted (White)     | Dark mode, dark backgrounds             |
| Monochrome           | Emails, print                           |

**Style Direction:** Geometric, abstracted plug symbol or gear-puzzle motif. Clearly legible at 16×16px (favicon). No gradients, no fine details — must also work as monochrome.

---

## 3. Layout Concept

### 3.1 Base Structure

PlugWerk uses a simple, application-style layout. There is no sidebar — the entire UI is driven by a top navigation bar and full-width content area.

```
┌────────────────────────────────────────────────────┐
│  Top Bar (global)                                   │
│  Logo │ Search │ Navigation │ Theme Toggle │ Avatar │
├────────────────────────────────────────────────────┤
│                                                      │
│  ┌──────────────────────────────────────────────┐   │
│  │  Content Area (max 1280px, centered)          │   │
│  │                                                │   │
│  │  Breadcrumb (where applicable)                │   │
│  │  Page Title                                    │   │
│  │                                                │   │
│  │  [ Page Content ]                             │   │
│  │                                                │   │
│  └──────────────────────────────────────────────┘   │
│                                                      │
├────────────────────────────────────────────────────┤
│  Footer                                              │
│  Logo │ Version │ API Docs Link │ Instance Name      │
└────────────────────────────────────────────────────┘
```

### 3.2 Responsive Breakpoints

| Breakpoint   | Width        | Behavior                                      |
|-------------|-------------|------------------------------------------------|
| **Desktop** | ≥ 1280px    | Full layout, 3–4 column card grid              |
| **Laptop**  | 1024–1279px | Full layout, 3 column card grid                |
| **Tablet**  | 768–1023px  | Cards in 2 columns, search collapses           |
| **Mobile**  | < 768px     | Hamburger menu, cards single column, stacked layout |

---

## 4. Component Library (Atomic Level)

### 4.1 Buttons

| Type              | Background      | Text        | Border     | Usage                             |
|-------------------|----------------|-------------|------------|-----------------------------------|
| **Primary**       | `#0F62FE`      | `#FFFFFF`   | none       | Main action (Download, Log In)    |
| **Secondary**     | transparent    | `#0F62FE`   | `#0F62FE`  | Secondary action (Cancel, Back)   |
| **Danger**        | `#DA1E28`      | `#FFFFFF`   | none       | Destructive action (Delete)       |
| **Ghost**         | transparent    | `#0F62FE`   | none       | Tertiary action, toolbar links    |
| **Disabled**      | `#E0E0E0`      | `#A8A8A8`   | none       | Unavailable actions               |

**Button Sizes:** Small (32px height), Medium (40px), Large (48px)  
**Padding:** horizontal 16px (Small), 20px (Medium), 24px (Large)

### 4.2 Form Elements

- **Inputs:** Height 40px, border `1px solid #E0E0E0`, focus border `2px solid #0F62FE`
- **Labels:** Body Small (14px), `#393939`, spacing below label 4px
- **Error Text:** Caption (12px), `#DA1E28`, icon (warning symbol) + text
- **Textareas:** Minimum 3 rows, vertically resizable
- **Selects / Dropdowns:** Same height as inputs, chevron icon on the right
- **Checkboxes / Radios:** 20×20px, custom style using primary color

### 4.3 Badges & Tags

| Type                  | Background         | Text            | Usage                             |
|-----------------------|--------------------|-----------------|-----------------------------------|
| **Status: Published** | `#DEFBE6`          | `#198038`       | Active release status             |
| **Status: Draft**     | `#E0E0E0`          | `#393939`       | Draft release                     |
| **Status: Deprecated**| `#FFF1C7`          | `#8A6A00`       | Deprecated version                |
| **Status: Yanked**    | `#FFD7D9`          | `#DA1E28`       | Withdrawn version                 |
| **Category Tag**      | `#D0E2FF`          | `#0043CE`       | Plugin category                   |
| **Version Badge**     | `#F4F4F4`          | `#161616`       | Version number (monospace)        |
| **Verified Badge**    | `#DEFBE6`          | `#198038`       | ✓ Verified plugin                 |

### 4.4 Plugin Card

The plugin card is the central UI element of the catalog.

```
┌──────────────────────────────────────────────┐
│  ┌──────┐                                     │
│  │ Icon │  Plugin Name                v1.2.0  │
│  │64×64 │  Author Name            ✓ Verified  │
│  └──────┘                                     │
│                                                │
│  Short description of the plugin, max 2 lines  │
│  with ellipsis on overflow...                  │
│                                                │
│  ┌────────┐ ┌──────┐ ┌──────────┐             │
│  │ export │ │ pdf  │ │ reporting│             │
│  └────────┘ └──────┘ └──────────┘             │
│                                                │
│  ↓ 12.4k Downloads  │  ★ 4.7  │  Updated 2d  │
└──────────────────────────────────────────────┘
```

**Dimensions:** Flexible width (grid-based, min 320px), padding 24px  
**Hover Effect:** Enhanced shadow + slight Y-translation (-2px)  
**Click Target:** Entire card is clickable, navigates to plugin detail page

### 4.5 Tables

- **Header:** `#F4F4F4` background, Bold (600), left-aligned text
- **Rows:** Alternating row colors (`#FFFFFF` / `#F9F9F9`)
- **Hover:** Row highlighted with `#E8E8E8`
- **Sortable Columns:** Chevron icon in header
- **Pagination:** At table footer, compact (< 1 2 3 ... 12 >)

---

## 5. Page Catalog – Detailed Description of Each Page

The application consists of 8 pages. No landing page, no admin dashboard, no upload wizards. The catalog is the home page.

---

### 5.1 Plugin Catalog (Home Page)

**URL:** `/` (this is the application entry point)  
**Purpose:** Browse, search, and filter all available plugins  
**Layout:** Full width, no sidebar  
**Access:** Public (no authentication required)

**Structure:**

1. **Top Bar** – Logo + wordmark (left), inline search input (center, expandable, placeholder "Search plugins..."), navigation links (Catalog active), theme toggle, login/avatar (right).

2. **Page Header** – Headline "Plugin Catalog" (H1). Below: namespace selector (dropdown, only shown if the instance hosts multiple namespaces) and result count ("142 plugins").

3. **Filter Bar** – Horizontal row below the header. Filter chips / dropdowns for:
   - Category (multi-select dropdown)
   - Tags (multi-select with autocomplete)
   - Compatibility version (dropdown or free input)
   - Sort order (dropdown: Relevance, Most Downloads, Newest, Rating)
   - "Reset filters" text link (appears when any filter is active)

4. **View Toggle** – Small icon toggle (top-right of results area) to switch between:
   - **Card View** (default) – Grid layout, 3–4 columns on desktop
   - **List View** – Compact rows with icon, name, version, author, downloads, last updated

5. **Plugin Cards** – As described in section 4.4. In list view: condensed single-row variant.

6. **Pagination** – At page bottom. Page numbers + items-per-page selector (12 / 24 / 48).

7. **Empty State** – Shown when search/filter yields no results. Illustration (puzzle piece with magnifying glass) + text "No plugins found. Try different search terms or reset your filters." + "Show all plugins" link.

8. **Footer** – Minimal: PlugWerk wordmark, version number (e.g. "v1.2.0"), link to API documentation, instance name (configurable by operator).

---

### 5.2 Plugin Detail Page

**URL:** `/plugins/{pluginId}` or `/namespaces/{ns}/plugins/{pluginId}`  
**Purpose:** All information about a single plugin  
**Layout:** Full width, content max 960px (centered)  
**Access:** Public (no authentication required)

**Structure:**

1. **Breadcrumb** – Catalog > Plugin Name

2. **Plugin Header** – Icon (96×96), plugin name (H1), author name (text, not linked), current version badge, verified badge (if applicable). Primary action button: "Download" (large, Primary). Below: compact stat row — downloads count, rating (stars + number), last updated date, license identifier.

3. **Tab Navigation** – Horizontal tabs below the header:
   - **Overview** (default) – Long description rendered from Markdown. Optional screenshots displayed as a horizontal gallery (clickable to lightbox). Feature highlights if provided.
   - **Versions** – Table of all releases: version number, release date, status badge (Published / Deprecated / Yanked), brief changelog excerpt, download link per version. Current version visually highlighted.
   - **Changelog** – Full chronological changelog (Markdown rendered, grouped per release version with date headers).
   - **Compatibility** – Matrix table showing host product version × plugin version compatibility, using ✓ / ✗ / ? icons. Simple and scannable.
   - **Dependencies** – List of required plugins (plugin ID + version range). Empty state: "This plugin has no dependencies."

4. **Metadata Sidebar (right column, desktop only)** – Sticky box alongside the tab content. Contains:
   - Install snippet (code block with copy-to-clipboard button, e.g. `plugwerk install acme-pdf-export`)
   - License (e.g. "Apache-2.0")
   - Repository link (external, opens in new tab)
   - Homepage link (if provided)
   - Min Java version
   - File size
   - SHA-256 hash (truncated, with copy button)
   - Tags (clickable, navigate back to catalog with filter applied)

5. **Mobile behavior:** Sidebar content moves below the tab content in a collapsible "Plugin Info" section.

---

### 5.3 Login Page

**URL:** `/login`  
**Purpose:** Authentication for users who need to access protected features  
**Layout:** Centered card on minimal background  
**Access:** Public (unauthenticated users only; authenticated users are redirected to catalog)

**Structure:**

1. **Centered Card** (max 420px width) on a clean, solid-color or subtly tinted background.

2. **Logo** – PlugWerk logo + wordmark at the top of the card (centered).

3. **Login Form:**
   - Username or email input (label: "Username or Email")
   - Password input (with show/hide toggle icon)
   - "Forgot password?" link (right-aligned, small, Body Small)
   - "Log In" button (Primary, full width)

4. **Divider** – Horizontal line with centered text "or" (only shown if OAuth2 providers are configured).

5. **OAuth2 Provider Buttons** – Rendered dynamically based on server configuration. Each button shows the provider icon (left) + "Sign in with {Provider}" label. Common providers: GitHub, GitLab, Google, Keycloak/Custom OIDC. Buttons are full width, stacked vertically, styled as secondary/outlined buttons. If no OAuth2 providers are configured, this entire section is hidden.

6. **Card Footer** – "Don't have an account? Sign up" as a link to `/register`. This link is only shown if self-registration is enabled by the instance operator (server-configurable).

**States to design:**
- Default (empty form)
- Validation error (inline, e.g. "Invalid credentials")
- Loading (button shows spinner, inputs disabled)

---

### 5.4 Registration Page

**URL:** `/register`  
**Purpose:** Account creation for new users  
**Layout:** Centered card, identical style to login  
**Access:** Public (only available if self-registration is enabled by instance operator)

**Structure:**

1. **Centered Card** with logo + wordmark (same as login).

2. **Registration Form:**
   - Username (with inline availability check: ✓ available / ✗ taken)
   - Email address
   - Password (with strength indicator: colored bar below, 4 levels — weak / fair / good / strong)
   - Confirm password
   - "Create Account" button (Primary, full width)

3. **OAuth2 Section** – Same as login page: divider + provider buttons (if configured). Clicking a provider creates the account via OAuth2 flow.

4. **Card Footer** – "Already have an account? Log in" as a link to `/login`.

**States to design:**
- Default (empty form)
- Inline validation (username taken, password mismatch, weak password)
- Success (redirect to login with success toast, or directly logged in)

---

### 5.5 Forgot Password Page

**URL:** `/forgot-password`  
**Purpose:** Initiate password recovery  
**Layout:** Centered card, identical style to login  
**Access:** Public

**Structure:**

1. **Centered Card** with logo.

2. **Content:**
   - Headline: "Reset your password" (H2)
   - Brief text: "Enter your email address and we'll send you a link to reset your password."
   - Email input
   - "Send Reset Link" button (Primary, full width)
   - "Back to login" link below

3. **Success State** (same card, replaces form after submission):
   - Checkmark icon (Success color)
   - Text: "If an account with that email exists, we've sent a password reset link. Check your inbox."
   - "Back to login" link

**Note:** The success message is deliberately vague to avoid revealing whether an email address is registered (security best practice).

---

### 5.6 Reset Password Page

**URL:** `/reset-password?token=...`  
**Purpose:** Set a new password using a recovery token  
**Layout:** Centered card, identical style to login  
**Access:** Public (valid token required)

**Structure:**

1. **Centered Card** with logo.

2. **Form:**
   - New password (with strength indicator)
   - Confirm new password
   - "Save New Password" button (Primary, full width)

3. **Success State** (replaces form):
   - Checkmark icon
   - Text: "Your password has been reset."
   - "Continue to login" button (Primary)

4. **Error State** (invalid or expired token):
   - Warning icon (Danger color)
   - Text: "This reset link is invalid or has expired."
   - "Request a new link" button (links to `/forgot-password`)

---

### 5.7 Error Pages

**URL:** Any non-existent or inaccessible path  
**Layout:** Centered, minimalist, full-page  
**Access:** Public

**Variants:**

| Code | Headline                   | Description                                          | Action                   |
|------|----------------------------|------------------------------------------------------|--------------------------|
| 404  | "Page Not Found"           | Illustration (missing puzzle piece) + brief text     | "Back to Catalog" button |
| 403  | "Access Denied"            | Lock icon + note about missing permissions           | "Log In" button          |
| 500  | "Something Went Wrong"     | Broken gear illustration + apology text              | "Try Again" button       |
| 503  | "Maintenance in Progress"  | Wrench illustration + estimated return time          | (no action, auto-refresh hint) |

**Design notes:** Each error page uses a single centered illustration (max 200×200px, line-art style matching Lucide aesthetic) above the headline. Minimal text, one clear action. The top bar is still visible so the user can navigate away.

---

### 5.8 Logout Transition

**URL:** `/logout` (POST action, no persistent page)  
**Purpose:** End the user session  
**Layout:** No dedicated page — logout is a POST action

**Behavior:** After logout, the user is redirected to the catalog page (`/`) with a brief toast notification: "You have been logged out." (Info type, auto-dismiss after 5 seconds). The top bar updates to show the "Log In" button instead of the avatar.

**Design note:** No dedicated logout page is needed. Design the toast notification and the top-bar state change (avatar → Log In button).

---

## 6. Navigation & Information Architecture

### 6.1 Top Navigation (Logged Out)

```
Logo │ [Search Input] │ Catalog │ [Theme Toggle] │ [Log In]
```

- **Logo:** Clickable, navigates to `/` (catalog).
- **Search Input:** Inline text input, collapsed to icon on mobile. Submitting navigates to catalog with search query applied.
- **Catalog:** Active link (highlighted) when on catalog or plugin detail pages.
- **Theme Toggle:** Sun/moon icon to switch between light and dark mode.
- **Log In:** Text button, navigates to `/login`.

### 6.2 Top Navigation (Logged In)

```
Logo │ [Search Input] │ Catalog │ [Theme Toggle] │ [Avatar → Dropdown]
```

- **Avatar Dropdown** (click to open):
  - Username + email (non-clickable header)
  - Divider
  - "Log Out" action

**Design notes:** The navigation is deliberately minimal. There is no sidebar, no dashboard link, no admin section in this version. The avatar dropdown only contains the logout action. Future versions may add profile and settings links.

### 6.3 Footer

```
PlugWerk v1.2.0  │  API Docs  │  {Instance Name}
```

- **PlugWerk version:** Wordmark + version number, small text (Caption), left-aligned.
- **API Docs:** Link to `/docs/api` or external documentation URL (configurable).
- **Instance Name:** Configurable by the operator (e.g. "ACME Corp Plugin Hub"). Right-aligned.
- **Minimal height:** The footer is unobtrusive — a single line, not a multi-column marketing footer.

---

## 7. Interaction Patterns & Micro-Interactions

### 7.1 Toast Notifications

- **Position:** Top-right, stacked (max 3 simultaneously)
- **Types:** Success (green), Error (red), Warning (yellow), Info (blue)
- **Auto-Dismiss:** 5 seconds (Success/Info), manually closable (Error/Warning)
- **Animation:** Slide-in from right, fade-out
- **Used for:** Login success, logout confirmation, copy-to-clipboard confirmation, form errors on submit

### 7.2 Modals / Dialogs

- Centered, with overlay background (`rgba(0,0,0,0.5)`)
- Max width 560px
- Close button top-right (X icon)
- Closable via Escape key and overlay click
- **Used for:** Screenshot lightbox on plugin detail page

### 7.3 Skeleton Loading

- Catalog page and plugin detail page use skeleton screens while data loads
- Gray animated blocks in the shape of the expected content (cards, text blocks, sidebar)
- Pulsating animation (opacity 0.5 → 1.0, 1.5s cycle)

### 7.4 Copy-to-Clipboard

- On plugin detail page: install snippet, SHA-256 hash
- Icon button (clipboard icon), after click: icon changes to checkmark + brief tooltip "Copied!" (1.5s)

### 7.5 Search Behavior

- **Instant search** (debounced, 300ms) filters the catalog as the user types
- Search input in top bar: on submit (Enter), navigates to catalog with query parameter
- On catalog page: search input is mirrored in the top bar and in the page header — both are synced
- **Keyboard shortcut:** `/` focuses the search input (common developer convention)

---

## 8. Accessibility (A11y)

- **WCAG 2.1 Level AA** as minimum standard
- Contrast ratios: at least 4.5:1 for text, 3:1 for large text
- All interactive elements keyboard-accessible (logical tab order)
- Focus indicators visible (2px solid outline, not just browser default)
- ARIA labels for all icon-only buttons (theme toggle, search, copy-to-clipboard)
- Alt text for all plugin icons and screenshots
- Screen-reader-compatible table structure on versions and compatibility tabs
- Skip-to-content link
- Login and registration forms: error messages associated with inputs via `aria-describedby`

---

## 9. Language & i18n

- **Primary Language:** English
- **Secondary Language:** German
- **UI Text:** All labels, buttons, error messages, and placeholders must be available in both languages
- **Language Switcher:** In the footer (compact dropdown or link toggle)
- **Text Length Considerations:** German text is approximately 20–30% longer than English — layouts must be flexible enough to accommodate this without breaking

---

## 10. Deliverables for Designers

### 10.1 Figma Deliverables

| Deliverable                      | Scope                                           |
|----------------------------------|-------------------------------------------------|
| **Design Tokens**                | Colors, typography, spacing, radii, shadows as Figma Variables |
| **Component Library**            | Buttons, inputs, cards, badges, tables, navigation, modals, toasts |
| **Light + Dark Mode**            | All 8 pages in both color modes                 |
| **Desktop + Mobile**             | All 8 pages for min 1440px and 375px width      |
| **Prototype**                    | Clickable flow: Catalog → Plugin Detail → Login → (Logged In) Catalog → Logout |
| **Icon Library**                 | Lucide subset as Figma components               |

### 10.2 HTML/CSS Deliverables

| Deliverable                      | Scope                                           |
|----------------------------------|-------------------------------------------------|
| **Design Tokens (CSS)**          | CSS custom properties for all tokens            |
| **Component Library (HTML)**     | Static HTML snippets for all atomic components  |
| **Page Templates**               | HTML templates for all 8 page types             |
| **Responsive Behavior**          | All breakpoints implemented                     |
| **Dark Mode**                    | Toggleable via CSS class or `prefers-color-scheme` |

---

## 11. Page Overview Summary

| No. | Page                 | URL Pattern                          | Public | Auth Required |
|-----|----------------------|--------------------------------------|--------|---------------|
| 1   | Plugin Catalog       | `/`                                  | ✓      |               |
| 2   | Plugin Detail        | `/plugins/{id}`                      | ✓      |               |
| 3   | Login                | `/login`                             | ✓      |               |
| 4   | Registration         | `/register`                          | ✓      |               |
| 5   | Forgot Password      | `/forgot-password`                   | ✓      |               |
| 6   | Reset Password       | `/reset-password?token=...`          | ✓      |               |
| 7   | Error Pages          | (any invalid path)                   | ✓      |               |
| 8   | Logout (transition)  | `/logout` (POST, redirect to `/`)    | —      | ✓             |

---

*This design briefing is the foundation for the visual implementation of PlugWerk. Questions and additions should be directed to the product team.*
