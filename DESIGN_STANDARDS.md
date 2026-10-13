# Player1 Design Standards

## Icon States
All icons used across the app must adhere to one of the following 3 states:

1. **Disabled**
   - **Color Rule:** Deactivated button color/state.
   - **Code mapping:** MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
   - **Usage:** For inactive/disabled states, like the resume button in the taskbar when there's no video to resume.

2. **Active**
   - **Color Rule:** Muted Material You / theme color.
   - **Code mapping:** MaterialTheme.colorScheme.onSurfaceVariant
   - **Usage:** For regular active icons, such as the navbar buttons and context menu icons.

3. **Colored**
   - **Color Rule:** Most vibrant Material You / theme color.
   - **Code mapping:** MaterialTheme.colorScheme.primary
   - **Usage:** For hollow stroked buttons or highly emphasized icon buttons.

## Blurred Backgrounds (Frosted Glass)
- **Application:** All popups, context menus, the pause/play button in the middle, and the navbar.
- **Tint/Opacity:** 50% transparency of white or black, depending on the system mode (light or dark).
- **Blur Radius:** 24.dp (standard).

