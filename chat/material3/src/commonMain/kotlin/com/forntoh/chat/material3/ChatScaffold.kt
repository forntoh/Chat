package com.forntoh.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Optional application shell around the shared Conversation. All chrome is host-authored:
 * [title], [avatar], [navigationIcon] and [actions] form the default identity header; [topBar]
 * replaces that whole header. Empty navigation/actions add no inert controls.
 *
 * Content receives a bounded Column with Scaffold's padding already applied and consumed.
 * Call Conversation directly (or use weight for siblings); do not apply scaffold padding again.
 * The header handles its top safe inset, Scaffold handles remaining [contentWindowInsets], and
 * Conversation handles the unconsumed IME inset. This shell owns no chat/navigation/service state
 * and inherits the ambient MaterialTheme and ConversationSlots. Wrap in ChatTheme for defaults.
 */
@Composable
fun ChatScaffold(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    avatar: @Composable () -> Unit = {},
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
        topBar = topBar ?: {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        avatar()
                        title()
                    }
                },
                navigationIcon = navigationIcon,
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor, titleContentColor = contentColor),
            )
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), content = content)
    }
}
