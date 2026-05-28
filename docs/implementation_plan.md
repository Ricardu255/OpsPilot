# Editorial Chat UI Refresh

This plan outlines the steps to upgrade the chat interface to a cleaner, more editorial "workspace" design based on the provided plan document. The focus is exclusively on the frontend display, without modifying backend interfaces or core interactive logic.

## Proposed Changes

### Frontend Structure and Content (HTML & JS)
- Clean up any static text issues (ensure proper Chinese display for titles, buttons, placeholders, etc.).
- Update the welcome area in `index.html` to include a clear title, brief guide, and context, rather than just a simple greeting.
- Re-structure the sidebar in `index.html` to separate primary actions ("New Chat", "Trace Console") from the history list more cleanly.

#### [MODIFY] `src/main/resources/static/index.html`
- Update the welcome greeting content to be more structured ("Title + Guide + Context").
- Adjust the DOM structure slightly if needed for the new layout (e.g., grouping sidebar actions vs history), while strictly maintaining all existing DOM IDs and classes used by `app.js`.

#### [MODIFY] `src/main/resources/static/app.js`
- Ensure any dynamically generated HTML (like history items or messages) matches the new CSS structure.
- Verify that status text (loading, mode names) is correct and free of encoding issues.

### Styling (CSS)

#### [MODIFY] `src/main/resources/static/styles.css`
- **Layout:** Remove heavy card borders, shadows, and blurs. Transition to a "light canvas + subtle columns" structure.
- **Sidebar:** Adjust width, padding, and separators. Make history items look more like a log/directory rather than heavy buttons.
- **Main Area:** Center the welcome area properly when the chat is empty. Subdue the "AI Ops" button to reduce visual intrusion. Limit the maximum width of the content area.
- **Input Area:** Transform into a subtle workspace input panel with lighter borders, smaller buttons, stable height, and clearer left/right action separation.
- **Message Area & AIOps Reports:**
  - Reduce font sizes for titles, line heights for paragraphs, and spacing for tables to make them more suitable for continuous reading.
  - Make AIOps reports look like a "document analysis" rather than large blocks with huge titles. Control the width and font weight of code blocks, quotes, and tables.
  - Optimize the maximum width and margins of messages to resemble a page of notes rather than huge chat bubbles.

## Verification Plan

### Manual Verification
- Open the application in a browser (`http://127.0.0.1:9888/` or the local server address).
- Verify the layout in desktop wide-screen mode for three main scenarios: empty state, chat state, and long AIOps report state.
- Verify the layout in narrow screen/smaller window to ensure the sidebar, welcome area, input area, and message area don't overlap.
- Test that all interactions (input buttons, mode selector, tool menu, file upload, sending messages, AI Ops trigger, history loading/deleting, Trace Console link) still function correctly.
