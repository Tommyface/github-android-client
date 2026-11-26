/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** M1-09：`Link` 头分页解析。 */
class LinkHeaderTest {
    @Test
    fun parsesNextRelation() {
        val header =
            "<https://api.github.com/user/repos?per_page=100&page=2>; rel=\"next\", " +
                "<https://api.github.com/user/repos?per_page=100&page=9>; rel=\"last\""
        assertEquals(
            "https://api.github.com/user/repos?per_page=100&page=2",
            LinkHeader.next(header),
        )
    }

    @Test
    fun parsesPreviousRelation() {
        val header = "<https://api.github.com/x?page=1>; rel=\"prev\""
        assertEquals("https://api.github.com/x?page=1", LinkHeader.rel(header, "prev"))
    }

    @Test
    fun handlesUrlContainingComma() {
        val header =
            "<https://api.github.com/search?q=a,b&page=2>; rel=\"next\", " +
                "<https://api.github.com/search?q=a,b&page=5>; rel=\"last\""
        assertEquals("https://api.github.com/search?q=a,b&page=2", LinkHeader.next(header))
    }

    @Test
    fun returnsNullWhenAbsent() {
        assertNull(LinkHeader.next(null))
        assertNull(LinkHeader.next(""))
        assertNull(LinkHeader.next("<https://api.github.com/x>; rel=\"last\""))
    }

    @Test
    fun ignoresUnknownRelations() {
        val header = "<https://api.github.com/x>; rel=\"first\""
        assertNull(LinkHeader.next(header))
    }
}
