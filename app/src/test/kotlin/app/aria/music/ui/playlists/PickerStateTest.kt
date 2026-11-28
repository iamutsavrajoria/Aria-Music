// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.playlists

import app.aria.music.ui.playlists.PlaylistPickerViewModel.Outcome
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PickerStateTest {
    @Test fun busyChangesNothingSoTheNameDialogStaysOpen() {
        // run() only closes the name dialog when after() is non-null.
        assertNull(PickerState(null, false).after(Outcome.Busy, "Mix"))
        assertNull(PickerState(null, true).after(Outcome.Busy, "Mix"))
    }

    @Test fun anAddSettlesTheDialog() {
        assertEquals(PickerState("Mix", false), PickerState(null, true).after(Outcome.Added("p1"), "Mix"))
    }

    @Test fun aBlankNameSettlesWithAnError() {
        assertEquals(PickerState(null, true), PickerState(null, false).after(Outcome.BlankName, ""))
    }
}
