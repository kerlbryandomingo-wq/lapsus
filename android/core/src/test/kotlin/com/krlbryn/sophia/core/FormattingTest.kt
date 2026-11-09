package com.krlbryn.sophia.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test fun bytes() {
        assertEquals("512 B", Formatting.bytes(512))
        assertEquals("1.5 KB", Formatting.bytes(1536))
        assertEquals("25 MB", Formatting.bytes(25L * 1024 * 1024))
        assertEquals("Unknown size", Formatting.bytes(-1))
    }

    @Test fun relativeTime() {
        assertEquals("just now", Formatting.relativeTime(0, 10_000))
        assertEquals("5 minutes ago", Formatting.relativeTime(0, 300_000))
        assertEquals("yesterday", Formatting.relativeTime(0, 100_000_000))
    }

    @Test fun greeting() {
        assertEquals("Good morning", Formatting.greeting(8))
        assertEquals("Good evening", Formatting.greeting(22))
    }

    @Test fun sanitizeFileNames() {
        assertEquals("passwd", FileNames.sanitize("../../etc/passwd"))
        assertEquals("a_b.pdf", FileNames.sanitize("a:b.pdf"))
        assertEquals("download", FileNames.sanitize("..."))
        val long = FileNames.sanitize("x".repeat(300) + ".pdf")
        assertEquals(120, long.length)
        assertEquals("pdf", FileNames.extensionOf(long))
    }

    @Test fun typeLabels() {
        assertEquals("PDF document", FileNames.typeLabel("notes.pdf", null))
        assertEquals("Presentation", FileNames.typeLabel("w1.pptx", null))
        assertEquals("Image", FileNames.typeLabel("x", "image/png"))
    }

    @Test fun layoutClasses() {
        assertEquals(LayoutClass.COMPACT, AdaptiveLayout.classify(360f, 740f)) // 6" phone portrait
        assertEquals(LayoutClass.MEDIUM, AdaptiveLayout.classify(740f, 360f)) // phone landscape
        assertEquals(LayoutClass.MEDIUM, AdaptiveLayout.classify(600f, 960f)) // 8" tablet portrait
        assertEquals(LayoutClass.EXPANDED, AdaptiveLayout.classify(1280f, 800f)) // 10" tablet landscape
        assertEquals(LayoutClass.COMPACT, AdaptiveLayout.classify(320f, 480f)) // split-screen half
    }
}
