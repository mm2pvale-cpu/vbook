package com.kyant.backdrop.catalog.data

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class BookViewModel(application: Application) : AndroidViewModel(application) {
    private val bookRepository: BookRepository
    val books: StateFlow<List<Book>>

    private val prefs = application.getSharedPreferences("vbook_settings", Context.MODE_PRIVATE)

    private val _fontSize = MutableStateFlow(prefs.getFloat("font_size", 16f))
    val fontSize = _fontSize.asStateFlow()

    private val _theme = MutableStateFlow(prefs.getString("reader_theme", "Cosmic") ?: "Cosmic")
    val theme = _theme.asStateFlow()

    private val _fontFamily = MutableStateFlow(prefs.getString("font_family", "SansSerif") ?: "SansSerif")
    val fontFamily = _fontFamily.asStateFlow()

    private val _lineSpacing = MutableStateFlow(prefs.getFloat("line_spacing", 1.5f))
    val lineSpacing = _lineSpacing.asStateFlow()

    private val _textAlign = MutableStateFlow(prefs.getString("text_align", "Justified") ?: "Justified")
    val textAlign = _textAlign.asStateFlow()

    private val _language = MutableStateFlow(prefs.getString("language", "English") ?: "English")
    val language = _language.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(prefs.getBoolean("tts_enabled", false))
    val ttsEnabled = _ttsEnabled.asStateFlow()

    private val _activeBookId = MutableStateFlow<String?>(null)
    val activeBookId = _activeBookId.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        bookRepository = BookRepository(db.bookDao())
        books = bookRepository.allBooks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun setFontSize(size: Float) {
        prefs.edit().putFloat("font_size", size).apply()
        _fontSize.value = size
    }

    fun setTheme(newTheme: String) {
        prefs.edit().putString("reader_theme", newTheme).apply()
        _theme.value = newTheme
    }

    fun setFontFamily(family: String) {
        prefs.edit().putString("font_family", family).apply()
        _fontFamily.value = family
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString("language", lang).apply()
        _language.value = lang
    }

    fun setTtsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("tts_enabled", enabled).apply()
        _ttsEnabled.value = enabled
    }

    fun setLineSpacing(spacing: Float) {
        prefs.edit().putFloat("line_spacing", spacing).apply()
        _lineSpacing.value = spacing
    }

    fun setTextAlign(align: String) {
        prefs.edit().putString("text_align", align).apply()
        _textAlign.value = align
    }

    fun importFile(file: File, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _importing.value = true
            try {
                bookRepository.importFile(getApplication(), file)
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _importing.value = false
            }
        }
    }

    fun renameBook(bookId: String, newTitle: String) {
        viewModelScope.launch {
            bookRepository.renameBook(bookId, newTitle)
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            bookRepository.deleteBook(getApplication(), bookId)
        }
    }

    fun setActiveBook(bookId: String?) {
        _activeBookId.value = bookId
    }

    fun getPagesForBook(bookId: String) = bookRepository.getPagesForBook(bookId)

    fun getSavedPointsForBook(bookId: String) = bookRepository.getSavedPointsForBook(bookId)

    fun savePoint(bookId: String, pageIndex: Int, chapterTitle: String, content: String, charIndex: Int = 0) {
        viewModelScope.launch {
            val snippet = if (content.length > 50) content.take(50) + "..." else content
            bookRepository.saveReadingPoint(
                SavedPoint(
                    bookId = bookId,
                    pageIndex = pageIndex,
                    chapterTitle = chapterTitle,
                    shortSnippet = snippet,
                    charIndex = charIndex
                )
            )
        }
    }

    fun deletePoint(id: Int) {
        viewModelScope.launch {
            bookRepository.deleteSavedPoint(id)
        }
    }
}
