/**
 * Web layer: controllers may return Thymeleaf fragment names OR JSON (@RestController).
 * Services and the {@code kube} package stay framework-agnostic — they return records
 * and lists, never HTML or Model mutations. This keeps the door open to swap the
 * Thymeleaf+HTMX frontend for an Angular SPA without rewriting the data layer.
 *
 * See CLAUDE.md → "Keep the Angular door open" for the full rules.
 */
package com.cockpit.clustercockpit.web;
