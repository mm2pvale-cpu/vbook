package com.kyant.backdrop.catalog.data

import android.content.Context
import com.kyant.backdrop.catalog.utils.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class BookRepository(private val bookDao: BookDao) {
    val allBooks: Flow<List<Book>> = bookDao.getAllBooks()

    suspend fun insertBook(book: Book) = bookDao.insertBook(book)

    suspend fun getBookById(bookId: String): Book? = bookDao.getBookById(bookId)

    suspend fun renameBook(bookId: String, newTitle: String) = bookDao.renameBook(bookId, newTitle)

    suspend fun deleteBook(context: Context, bookId: String) {
        bookDao.deleteBook(bookId)
        bookDao.deletePagesForBook(bookId)
        val bookDir = File(context.filesDir, "books/$bookId")
        if (bookDir.exists()) {
            bookDir.deleteRecursively()
        }
        val coverFile = File(context.filesDir, "covers/${bookId}_cover.jpg")
        if (coverFile.exists()) {
            coverFile.delete()
        }
    }

    fun getPagesForBook(bookId: String): Flow<List<BookPage>> = bookDao.getPagesForBook(bookId)

    suspend fun getPagesForBookDirect(bookId: String): List<BookPage> = bookDao.getPagesForBookDirect(bookId)

    fun getSavedPointsForBook(bookId: String): Flow<List<SavedPoint>> = bookDao.getSavedPointsForBook(bookId)

    suspend fun saveReadingPoint(point: SavedPoint) = bookDao.insertSavedPoint(point)

    suspend fun deleteSavedPoint(id: Int) = bookDao.deleteSavedPoint(id)

    suspend fun importFile(context: Context, file: File) = withContext(Dispatchers.IO) {
        val parsed = if (file.extension.equals("pdf", ignoreCase = true)) {
            EpubParser.parsePdf(context, file)
        } else {
            EpubParser.parseEpub(context, file)
        }
        val book = Book(
            id = parsed.id,
            title = parsed.title,
            author = parsed.author,
            coverPath = parsed.coverPath
        )
        bookDao.insertBook(book)

        val pagesList = parsed.pages.mapIndexed { index, p ->
            BookPage(
                bookId = parsed.id,
                pageIndex = index,
                chapterTitle = p.chapterTitle,
                content = p.content
            )
        }
        bookDao.insertPages(pagesList)
    }
}
