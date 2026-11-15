package dev.forntoh.chat.material3

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.ui.LocalConversationViewportPadding

/**
 * Optional application shell around the shared Conversation. All chrome is host-authored:
 * [title], [avatar], [navigationIcon] and [actions] form the default identity header; [topBar]
 * replaces that whole header. Empty navigation/actions add no inert controls.
 *
 * Content receives a bounded Column with horizontal padding applied and all Scaffold insets
 * consumed. Vertical chrome is supplied through [LocalConversationViewportPadding]: Conversation
 * extends behind the default Material header and keeps its initial content/composer clear.
 * The header uses the ambient Material top-app-bar background; replace [topBar] to customize it.
 * Custom non-conversation content must apply that local padding itself; custom [topBar] and
 * [bottomBar] retain their own backgrounds. The header handles its top safe inset, and
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
    contentWindowInsets: WindowInsets = defaultChatWindowInsets(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
        topBar = {
            Box(Modifier.testTag("conversation-header")) {
                if (topBar != null) {
                    topBar()
                } else {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                avatar()
                                title()
                            }
                        },
                        navigationIcon = navigationIcon,
                        actions = actions,
                        colors = TopAppBarDefaults.topAppBarColors(
                            titleContentColor = contentColor,
                        ),
                    )
                }
            }
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
    ) { padding ->
        val direction = LocalLayoutDirection.current
        CompositionLocalProvider(
            LocalConversationViewportPadding provides PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
        ) {
            Column(
                Modifier.fillMaxSize().padding(
                    start = padding.calculateStartPadding(direction),
                    end = padding.calculateEndPadding(direction),
                ).consumeWindowInsets(padding),
                content = content,
            )
        }
    }
}
