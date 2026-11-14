package dev.forntoh.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.forntoh.chat.core.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class StaticLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyComposerCentersPlaceholderVerticallyWithSend() {
        compose.setContent {
            val typography = androidx.compose.material3.Typography()
            ChatTheme(typography = typography.copy(bodyLarge = typography.bodyLarge.copy(lineHeight = 20.sp))) {
                Box(Modifier.width(360.dp)) {
                    MessageComposer(dev.forntoh.chat.ui.ComposerContent(Draft(""), false, {}, {}))
                }
            }
        }
        val hint = compose.onNodeWithText("Type a message…", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithContentDescription("Send").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(hint.center.y - send.center.y) <= 1f,
            "Placeholder must be vertically centered with Send: hint=$hint, send=$send")
    }

    @Test fun composerKeepsSendInsideRoundedFieldAndBottomAlignedAsDraftGrows() {
        val draft = mutableStateOf("Hi")
        val base = chatConversationSlots()
        compose.setContent {
            ChatTheme {
                Box(Modifier.width(360.dp)) {
                    MessageComposer(dev.forntoh.chat.ui.ComposerContent(Draft(draft.value), false, { draft.value = it }, {}),
                        base.copy(composerSend = {
                            Button(onClick = it.onSend, modifier = Modifier.testTag("host-send")) { Text("Submit") }
                        }))
                }
            }
        }
        fun field() = compose.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot
        fun send() = compose.onNodeWithTag("host-send").fetchSemanticsNode().boundsInRoot
        val single = field()
        assertTrue(send().right <= 344f, "Send belongs inside the rounded input's 4dp inner edge")
        compose.onNodeWithTag("composer").performTextReplacement("One\nTwo\nThree\nFour\nFive\nSix")
        assertTrue(field().height > single.height, "Multiline draft must grow the input")
        assertTrue(kotlin.math.abs(send().bottom - field().bottom) <= 4f,
            "A consumer-provided Send remains at the bottom of the growing input: field=${field()}, send=${send()}")
        compose.onNodeWithTag("host-send").assertIsDisplayed().assertIsEnabled()
    }

    @Test fun shortTextKeepsTimeBesideBodyAndDeliverySharesItsBaseline() {
        val row = Message("one", Participant("avery", "Avery Chen"), 0, "09:41", "Hi", Delivery.Sending)
        compose.setContent {
            ChatTheme {
                Box(Modifier.width(360.dp)) {
                    MessageBubble(row, false, null, chatConversationSlots().copy(
                        messageBody = { Text(it.text, Modifier.testTag("body")) },
                        messageMetadata = { Text(it.displayTime, Modifier.testTag("time")) },
                        messageDelivery = { Text(it.name, Modifier.testTag("delivery")) },
                    ))
                }
            }
        }
        val body = compose.onNodeWithTag("body", true).fetchSemanticsNode().boundsInRoot
        val time = compose.onNodeWithTag("time", true).fetchSemanticsNode().boundsInRoot
        val delivery = compose.onNodeWithTag("delivery", true).fetchSemanticsNode().boundsInRoot
        assertTrue(time.left >= body.right && time.top < body.bottom, "Short text must keep time beside it")
        assertTrue(kotlin.math.abs(time.bottom - delivery.bottom) <= 1f, "Time and delivery must align")
    }
    @Test fun defaultTextUsesFinalLineSpaceAndFallsBackAtConstrainedWidth() {
        val text = mutableStateOf("A reasonably wide first line\nAnother full line\nHi")
        val outgoing = mutableStateOf(false)
        val viewport = mutableStateOf(360.dp)
        val delivery = mutableStateOf(Delivery.Sent)
        compose.setContent {
            ChatTheme {
                Box(Modifier.width(viewport.value)) {
                    MessageBubble(Message("one", Participant("other", "Avery"), 0, "09:41", text.value, delivery.value), outgoing.value, null)
                }
            }
        }
        fun body() = compose.onNodeWithText(text.value, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        fun time() = compose.onNodeWithText("09:41", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val multiline = body()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text.value, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val lastLine = layout.lineCount - 1
        assertTrue(time().top >= multiline.top + layout.getLineTop(lastLine) && time().top < multiline.bottom,
            "Time should share the short final line of multiline text")
        assertTrue(time().left >= multiline.left + layout.getLineRight(lastLine),
            "Time must clear the actual final text line, not the full multiline text bounds")
        compose.runOnIdle { outgoing.value = true; text.value = "Hi"; delivery.value = Delivery.Sending }
        assertTrue(time().left >= body().right && time().top < body().bottom,
            "Outgoing short text should fit beside time and pending status")
        val origin = body().topLeft
        compose.runOnIdle { delivery.value = Delivery.Failed }
        assertTrue(time().left >= body().right && body().topLeft == origin,
            "Delivery changes must preserve the text origin and avoid overlap")
        compose.runOnIdle { viewport.value = 220.dp; text.value = "WWWWWWWW"; delivery.value = Delivery.Sent }
        assertTrue(time().top >= body().bottom,
            "A full final line at constrained width needs a separate metadata row")
    }

    @Test fun outgoingFitUsesVisibleTimeWidthAcrossDeliveryTransitions() {
        val delivery = mutableStateOf(Delivery.Sent)
        compose.setContent {
            ChatTheme {
                Box(Modifier.width(240.dp)) {
                    MessageBubble(Message("one", Participant("me", "You"), 0, "09:41", "WWWWWW", delivery.value), true, null)
                }
            }
        }
        fun body() = compose.onNodeWithText("WWWWWW", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        fun time() = compose.onNodeWithText("09:41", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val origin = body().topLeft
        assertTrue(time().left >= body().right && time().top < body().bottom,
            "Visible Sent timestamp fits even when an 88dp reservation would not")
        compose.runOnIdle { delivery.value = Delivery.Sending }
        assertTrue(time().top >= body().bottom, "Visible pending status must wrap if it would overlap")
        assertTrue(body().topLeft == origin, "Status changes preserve the text origin")
        compose.runOnIdle { delivery.value = Delivery.Failed }
        assertTrue(time().top >= body().bottom && body().topLeft == origin)
        compose.runOnIdle { delivery.value = Delivery.Sent }
        assertTrue(time().top < body().bottom && time().left >= body().right && body().topLeft == origin)
    }

    @Test fun absentAvatarAndDeliveryChangesKeepTextGeometryStableAndLongContentFits() {
        val row = mutableStateOf(Message("one", Participant("avery", "Avery Chen"), 0, "09:41", "Hi", Delivery.Sending))
        val avatar = mutableStateOf(true)
        compose.setContent {
            ChatTheme {
                Box(Modifier.width(320.dp)) {
                    MessageBubble(row.value, false, null, chatConversationSlots().copy(
                        messageAvatar = { if (avatar.value) Box(Modifier.size(30.dp)) },
                        messageBody = { Text(it.text, Modifier.testTag("body")) },
                        messageMetadata = { Text(it.displayTime, Modifier.testTag("time")) },
                    ))
                }
            }
        }
        fun body() = compose.onNodeWithTag("body", true).fetchSemanticsNode().boundsInRoot
        val initial = body()
        compose.runOnIdle { avatar.value = false; row.value = row.value.copy(delivery = Delivery.Failed) }
        val changed = body()
        assertTrue(initial.left == changed.left && initial.top == changed.top, "Avatar/status cannot shift the text origin")
        compose.runOnIdle { row.value = row.value.copy(text = "unbroken".repeat(40)) }
        val long = body()
        val time = compose.onNodeWithTag("time", true).fetchSemanticsNode().boundsInRoot
        assertTrue(long.right < 320f && long.height > initial.height, "Long text must wrap with an opposite gutter")
        assertTrue(time.top >= long.bottom, "Long text cannot overlap metadata")
    }

    @Test fun blankContentAndSenderHaveReadableDefaultFallbacks() {
        val row = Message("missing", Participant("other", ""), 0, "09:41", "", Delivery.Sent)
        compose.setContent { ChatTheme { MessageBubble(row, false, null) } }
        compose.onNodeWithContentDescription("Unknown sender, Message unavailable, 09:41, Sent").assertExists()
        compose.onNodeWithText("Unknown sender", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Message unavailable", useUnmergedTree = true).assertExists()
    }

    @Test fun metadataSlotOwnsSenderAndCanReplaceOrOmitVisibleMetadata() {
        val showMetadata = mutableStateOf(true)
        val row = Message("one", Participant("avery", "Avery Chen"), 0, "09:41", "Hi", Delivery.Sent)
        compose.setContent {
            ChatTheme {
                MessageBubble(row, false, null, chatConversationSlots().copy(
                    messageMetadata = {
                        if (showMetadata.value) MessageMetadata(header = { Text("Host sender") }) { Text("Host time") }
                    },
                ))
            }
        }
        compose.onNodeWithText("Host sender", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Host time", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Avery Chen", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { showMetadata.value = false }
        compose.onNodeWithText("Host sender", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Host time", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Hi", useUnmergedTree = true).assertExists()
    }

    @Test fun senderStaysOutsideWhileTimeIsInsideAndBubblesWrapWithinGutters() {
        val text = mutableStateOf("OK")
        val senderName = mutableStateOf("Avery")
        val outgoing = mutableStateOf(false)
        val viewport = mutableStateOf(360.dp)
        val bubbleColor = Color.Green
        compose.setContent {
            ChatTheme(colorScheme = chatColorScheme().copy(primary = bubbleColor, surfaceContainerHighest = bubbleColor)) {
                Box(Modifier.width(viewport.value).padding(12.dp)) {
                    MessageBubble(Message("one", Participant(if (outgoing.value) "me" else "other", senderName.value), 0, "09:41", text.value, Delivery.Sent), outgoing.value, null)
                }
            }
        }
        data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
        fun bubbleBounds(): Bounds {
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            var left = pixels.width; var top = pixels.height; var right = -1; var bottom = -1
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                if (pixels[x, y].toArgb() == bubbleColor.toArgb()) {
                    left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
                }
            }
            assertTrue(right >= left, "A visible bubble must be present")
            return Bounds(left, top, right + 1, bottom + 1)
        }
        val short = bubbleBounds()
        assertTrue(short.right - short.left < 180, "A short bubble must wrap content rather than expand to the row width")
        val sender = compose.onNodeWithText("Avery", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val time = compose.onNodeWithText("09:41", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithText("OK", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(sender.bottom <= short.top, "The sender must be above and outside the colored bubble")
        assertTrue(time.left >= body.right && time.top < body.bottom && time.bottom <= short.bottom, "Time must fit beside short text inside the bubble")
        compose.runOnIdle { senderName.value = "Participant with a very long display name" }
        val longName = bubbleBounds()
        assertTrue(longName.right - longName.left < 180, "A long external sender name cannot stretch a short bubble")
        compose.runOnIdle { outgoing.value = true }
        val outgoingShort = bubbleBounds()
        assertTrue(outgoingShort.right - outgoingShort.left < 180, "A short outgoing bubble must wrap its content")
        compose.runOnIdle { text.value = "unbroken".repeat(40) }
        val mobile = bubbleBounds()
        assertTrue(mobile.left >= 64 && mobile.right <= 348, "A long mobile bubble must leave both screen gutters")
        compose.runOnIdle { viewport.value = 1000.dp }
        val desktop = bubbleBounds()
        assertTrue(desktop.right - desktop.left <= 560, "Desktop bubbles must respect the width cap")
    }

}
