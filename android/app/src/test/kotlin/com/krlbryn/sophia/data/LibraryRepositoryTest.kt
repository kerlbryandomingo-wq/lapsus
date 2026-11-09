package com.krlbryn.sophia.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.krlbryn.sophia.data.db.SophiaDatabase
import com.krlbryn.sophia.data.repo.LibraryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryRepositoryTest {
    private lateinit var db: SophiaDatabase
    private lateinit var repo: LibraryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SophiaDatabase::class.java).allowMainThreadQueries().build()
        repo = LibraryRepository(db.bookmarks(), db.history(), db.shortcuts())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun builtInPortalIsAlwaysBookmarked() = runTest {
        assertTrue(repo.isBookmarked(LibraryRepository.BUILT_IN_URL).first())
        assertTrue(LibraryRepository.isBuiltIn("https://discipulusv2.amasystem.net"))
        assertFalse(LibraryRepository.isBuiltIn("https://example.com/"))
    }

    @Test
    fun builtInPortalCannotBeDuplicated() = runTest {
        assertTrue(repo.addBookmark("My portal", LibraryRepository.BUILT_IN_URL, null))
        assertTrue(repo.searchBookmarks("").first().isEmpty())
    }

    @Test
    fun editingABookmarkIntoThePortalRemovesTheCopy() = runTest {
        repo.addBookmark("Docs", "https://example.com/docs", null)
        val saved = repo.searchBookmarks("").first().single()
        repo.updateBookmark(saved, "Portal", LibraryRepository.BUILT_IN_URL, null)
        assertTrue(repo.searchBookmarks("").first().isEmpty())
        assertTrue(repo.isBookmarked(LibraryRepository.BUILT_IN_URL).first())
    }

    @Test
    fun duplicateBookmarksAreIgnored() = runTest {
        repo.addBookmark("Docs", "https://example.com/docs", null)
        repo.addBookmark("Docs again", "https://example.com/docs", null)
        assertEquals(1, repo.searchBookmarks("").first().size)
    }

    @Test
    fun clearingHistoryKeepsBookmarksAndThePortal() = runTest {
        repo.addBookmark("Docs", "https://example.com/docs", null)
        repo.recordVisit("https://example.com/a", "A")
        repo.recordVisit(LibraryRepository.BUILT_IN_URL, "Discipulus")
        assertEquals(2, repo.searchHistory("").first().size)

        repo.clearHistory()

        assertTrue(repo.searchHistory("").first().isEmpty())
        assertEquals(1, repo.searchBookmarks("").first().size)
        assertTrue(repo.isBookmarked(LibraryRepository.BUILT_IN_URL).first())
    }

    @Test
    fun repeatVisitsWithinHalfAnHourAreMerged() = runTest {
        repo.recordVisit("https://example.com/a", "A", now = 1_000_000)
        repo.recordVisit("https://example.com/a", "A", now = 1_000_000 + 60_000)
        assertEquals(1, repo.searchHistory("").first().size)
    }

    @Test
    fun historySearchMatchesTitlesAndTreatsWildcardsLiterally() = runTest {
        repo.recordVisit("https://example.com/a", "Physics notes")
        repo.recordVisit("https://example.com/b", "Chemistry")
        assertEquals(listOf("Physics notes"), repo.searchHistory("phys").first().map { it.title })
        assertTrue(repo.searchHistory("%").first().isEmpty())
    }
}
