# kegent

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Existing Java / Android Views project, Material 3 components, Gradle.

## Users

The owner uses kegent on BlueStacks to configure and run virtual location routes.

## Product Purpose

Configure a route, control real-time playback, and prepare one historical running record for the installed Keep account.

## Capabilities and Constraints

- Preserve the installed KmAo blue palette and logo; use the requested kegent name and com.haven.kegent package, existing KML route workflow, pause/resume/stop, speed adjustment, and the restored DUTPJ 400 m route.
- The top bar keeps the tabs close to the title, moves help left of a day/night capsule, and clips the original logo into a rounded square. Dark mode uses #121212 as the page ground.
- Route preview loads real OpenStreetMap street tiles asynchronously with attribution and an offline block-style fallback when connectivity or the tile service is unavailable.
- Historical work is one record at a time, with a past start/end time, route, and pace. Confirmed by the user on 2026-10-18.
- Start/end inputs support seconds and migrate prior minute-only settings. Natural pace starts faster for runs over 1 km, settles after 1 km, and preserves historical duration, distance and average pace; uniform mode remains available.
- Standard KML/GPX coordinates are GPS/WGS84. Keep handoff adapts coordinates to its Tencent/GCJ-02 location chain. Keep's own map layer must be visible (for example Street) to display geographic tiles.
- Distinguish local preparation, delivery to Keep, and server confirmation. Do not report unverified cloud success.
- Historical Keep handoff should preserve the native `Keep App` source label by writing the client source fields alongside the offline summary classification.
- Validate parameters, retain user input, and preserve data through recoverable errors.
- Changes are committed locally on main; no remote push is requested.

## Brand Commitments

Keep the installed light-blue primary color #DAF0FB. No green palette. Reference Anime.js for purposeful motion; use native Android animation APIs.

## Evidence on Hand

The installed KmAo APK, its decompiled classes/resources, and a pre-recovery source backup are preserved in the chat workspace. The APK confirms the KmAo name, ka_squ image, light-blue colors, and DUTPJ route. Existing readable Java source is retained where behavior matches.

## Product Principles

- Route and run state remain visible while editing parameters.
- Show parameter errors before starting playback or preparing history.
- Keep data generation separate from external submission.
- Support phone and the BlueStacks landscape/tablet workspace, including dark theme and larger text.
