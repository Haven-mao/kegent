---
version: 1
slug: "run-workspace"
primary_target: "app/src/main/java/com/haven/kgent/FakeGPSActivity.java"
related_targets: ["app/src/main/java/com/haven/kgent/ui/RoutePreviewView.java", "app/src/main/java/com/haven/kgent/ui/Ui.java"]
---

# Running workspace

Mode: Operate. Native Android. The user configures real-time virtual location or prepares one historical run. Preserve the installed blue identity, KML routes and existing controls. The user has renamed the application to kgent and the package to com.haven.kgent.

## Direction contract

THESIS: Route-first running configuration with a persistent status/control area; replace the long undifferentiated form with a clear route, parameters, and action hierarchy.

OWN-WORLD: Retain #DAF0FB with ink-blue text, cool neutral surfaces, Material 3 outlined fields, tonal actions, and consistent 16–24 dp spacing. Dark theme preserves the same blue family. No green.

STORY: Choose real-time or historical mode, inspect the actual route, enter parameters, then start playback or review one historical record before handing it to Keep.

FIRST VIEWPORT: Native top bar with two tabs. On landscape, route preview/status occupies the left pane and a compact configuration form the right; on a phone the panes stack, with one clear primary action and context-sensitive secondary actions. The route preview is drawn from real KML geometry.

FORM: Precisely scoped existing-world redesign; no concept seed is needed. Native code-first build. Signature interaction: route progress and state changes animate with short easing, while the history preview updates calculated distance and route coverage without shifting the form. System Remove animations is honored.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
