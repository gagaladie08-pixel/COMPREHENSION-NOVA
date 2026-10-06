package com.novastats.app.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HttpJsonTest {
    @Test
    fun `http errors do not expose api keys embedded in query or path`() {
        val queryKey = HttpJson.HttpException(
            401,
            "https://ws.audioscrobbler.com/2.0/?api_key=private-query-key&method=track.getInfo"
        )
        val pathKey = HttpJson.HttpException(
            403,
            "https://www.theaudiodb.com/api/v1/json/private-path-key/searchtrack.php?s=Artist"
        )

        assertEquals("HTTP 401 · ws.audioscrobbler.com", queryKey.message)
        assertEquals("HTTP 403 · www.theaudiodb.com", pathKey.message)
        assertFalse(queryKey.message.orEmpty().contains("private-query-key"))
        assertFalse(pathKey.message.orEmpty().contains("private-path-key"))
    }

    @Test
    fun `invalid endpoint still yields a safe diagnostic`() {
        assertEquals("HTTP 500 · API", HttpJson.HttpException(500, "not-a-valid-url").message)
    }
}
