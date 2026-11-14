# Sample messaging simulation

Run the existing sample (`:sample:run`, Android app, or iOS host). The default
conversation automatically acknowledges a send, delivers the same row, and receives
an Avery reply. Delivery and connection outcomes are simulated: this proves public
UI/state behavior, not network reliability, permissions, storage or real uploads.

Open **Conversation options → Simulation debug panel**. Choose a scenario and integer
seed, then **Start selected scenario** to restore the six-message fixture. The seed
controls reply pacing (700–1099 logical milliseconds). Acknowledgement defaults to
400 ms and delivery to 900 ms; toggle them independently to 1200/2000 ms. Changes
apply to newly scheduled work. **Reset** restores the selected seed/scenario and
current delay settings. **Pause/Resume** freezes/restarts logical time; **Step** applies
one queued event while paused, including events with the same deadline in insertion
order. Closing the panel keeps the schedule running. All state is in memory.

Reproduce these bounded scenarios with seed 42:

1. **Normal exchange:** start, pause, close the panel, type and send a message.
   Send is disabled while awaiting acknowledgement. Reopen the panel and step:
   acknowledgement clears the submitted draft and inserts one Sending row; the
   next step delivers it; the third adds an incoming reply. Resume for paced motion.
2. **Refuse next submission:** start, type exact whitespace/newlines, send, and step
   once. The draft remains unchanged and send becomes available again. The next
   submission succeeds. Use the acknowledgement toggle to expose the pending state.
3. **Fail next delivery:** start, send and step twice. The outgoing row fails. Close
   the panel and use the row's Retry control: its identity stays unchanged. Step to
   delivery, then the incoming reply. Rapid retry activation cannot add another row.
4. **Cancellation:** send with a delayed acknowledgement, then Reset or Switch room
   before it fires. Advance/resume: the obsolete event cannot change the new room
   or draft. Switching back retains the old draft, releases its pending latch, and
   marks interrupted outgoing deliveries Failed so they can be retried explicitly.
   Reset during retry also discards the old delivery/reply schedule.

The debug trace retains at most 80 intent/schedule/result entries, with logical time,
conversation, stable fixture message IDs and draft revisions. It excludes text and
attachments, even if a developer types personal content. Reset clears the trace.

Simulation code lives only in `sample`; published modules retain their ordinary
public state/event APIs. The small `SimulationTest` set drives those events and
advances the logical clock directly, without real-time sleeps. Existing manual
host journeys remain available for focused action/history/media work. Expanded
history, action, image, performance and broad platform/configuration campaigns
remain in their own slices.
